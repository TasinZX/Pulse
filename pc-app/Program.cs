// Pulse PC — native Windows companion for the Pulse phone app.
// One tray app that replaces the PowerShell scripts: LAN discovery beacon,
// remote-desktop server (screen capture + input), and command endpoints
// (lock/sleep/reboot/shutdown/run/clipboard). Compiled with the built-in
// .NET Framework csc.exe — no SDK, and no PowerShell/AMSI antivirus block.
//
// HTTP API (port 42990) — identical to what the phone already calls:
//   GET  /info       -> {"w":W,"h":H}
//   GET  /frame      -> screen JPEG
//   POST /input      -> remote-desktop mouse/keyboard verbs
//   POST /command    -> lock/sleep/reboot/shutdown/run/clipset
//   GET  /status     -> {"awake":true,"idleSeconds":N}
//   GET  /clipboard  -> PC clipboard text
//   POST /shutdown   -> graceful shutdown (?dry=1 to preview)
// UDP discovery beacon on 42999.

using System;
using System.Collections;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;
using System.Windows.Forms;

namespace PulsePC
{
    static class Program
    {
        public const int HttpPort = 42990;
        public const int BeaconPort = 42999;

        [STAThread]
        static void Main()
        {
            Application.EnableVisualStyles();
            Native.SetProcessDPIAware();
            Startup.EnsureAutoStart();
            Application.Run(new TrayContext());
        }
    }

    // ---- System tray host (also the STA invoke target for clipboard) --------
    class TrayContext : ApplicationContext
    {
        private readonly NotifyIcon _icon;
        private readonly Form _invokeTarget; // hidden, STA, for clipboard marshaling
        private readonly HttpServer _server;
        private readonly Beacon _beacon;

        public TrayContext()
        {
            _invokeTarget = new Form();
            _invokeTarget.ShowInTaskbar = false;
            _invokeTarget.WindowState = FormWindowState.Minimized;
            _invokeTarget.FormBorderStyle = FormBorderStyle.FixedToolWindow;
            _invokeTarget.Load += delegate { _invokeTarget.Size = new Size(0, 0); _invokeTarget.Hide(); };
            _invokeTarget.CreateControl();
            var forceHandle = _invokeTarget.Handle; // create the window handle so Invoke works
            GC.KeepAlive(forceHandle);

            Clip.Init(_invokeTarget);

            var menu = new ContextMenuStrip();
            var info = LanInfo.Primary();
            menu.Items.Add(new ToolStripMenuItem("Pulse PC — running") { Enabled = false });
            if (info != null)
                menu.Items.Add(new ToolStripMenuItem(info.Name + "  •  " + info.Ip) { Enabled = false });
            menu.Items.Add(new ToolStripSeparator());
            var quit = new ToolStripMenuItem("Quit");
            quit.Click += delegate { ExitThread(); };
            menu.Items.Add(quit);

            _icon = new NotifyIcon();
            _icon.Icon = System.Drawing.SystemIcons.Application;
            _icon.Text = "Pulse PC";
            _icon.Visible = true;
            _icon.ContextMenuStrip = menu;

            _server = new HttpServer(Program.HttpPort);
            _server.Start();
            _beacon = new Beacon(Program.BeaconPort);
            _beacon.Start();
        }

        protected override void Dispose(bool disposing)
        {
            if (disposing)
            {
                try { _beacon.Stop(); } catch { }
                try { _server.Stop(); } catch { }
                if (_icon != null) { _icon.Visible = false; _icon.Dispose(); }
            }
            base.Dispose(disposing);
        }
    }

    // ---- LAN adapter / identity --------------------------------------------
    class LanInfo
    {
        public string Name;
        public string Mac;
        public string Ip;
        public string Broadcast;

        public static LanInfo Primary()
        {
            NetworkInterface best = null;
            UnicastIPAddressInformation bestAddr = null;
            long bestSpeed = -1;

            foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (ni.OperationalStatus != OperationalStatus.Up) continue;
                if (ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
                if (ni.NetworkInterfaceType == NetworkInterfaceType.Tunnel) continue;
                var desc = (ni.Description + " " + ni.Name);
                if (System.Text.RegularExpressions.Regex.IsMatch(desc,
                    "Remote NDIS|Internet Sharing|Virtual|Hyper-V|Loopback|WARP|VPN|TAP|Bluetooth",
                    System.Text.RegularExpressions.RegexOptions.IgnoreCase)) continue;

                var props = ni.GetIPProperties();
                bool hasGateway = props.GatewayAddresses.Any(g => g.Address.AddressFamily == AddressFamily.InterNetwork
                    && !g.Address.Equals(IPAddress.Any));
                if (!hasGateway) continue;

                var addr = props.UnicastAddresses.FirstOrDefault(u =>
                    u.Address.AddressFamily == AddressFamily.InterNetwork
                    && !IPAddress.IsLoopback(u.Address));
                if (addr == null) continue;

                if (ni.Speed > bestSpeed)
                {
                    bestSpeed = ni.Speed;
                    best = ni;
                    bestAddr = addr;
                }
            }
            if (best == null || bestAddr == null) return null;

            var ipBytes = bestAddr.Address.GetAddressBytes();
            byte[] mask;
            try { mask = bestAddr.IPv4Mask.GetAddressBytes(); }
            catch { mask = new byte[] { 255, 255, 255, 0 }; }
            var bc = new byte[4];
            for (int i = 0; i < 4; i++) bc[i] = (byte)(ipBytes[i] | (~mask[i] & 0xFF));

            var macBytes = best.GetPhysicalAddress().GetAddressBytes();
            var mac = string.Join(":", macBytes.Select(b => b.ToString("X2")).ToArray());

            var info = new LanInfo();
            info.Name = Environment.MachineName;
            info.Mac = mac;
            info.Ip = bestAddr.Address.ToString();
            info.Broadcast = string.Join(".", bc.Select(b => b.ToString()).ToArray());
            return info;
        }
    }

    // ---- UDP discovery beacon ----------------------------------------------
    class Beacon
    {
        private readonly int _port;
        private Thread _thread;
        private volatile bool _run;

        public Beacon(int port) { _port = port; }

        public void Start()
        {
            _run = true;
            _thread = new Thread(Loop);
            _thread.IsBackground = true;
            _thread.Start();
        }
        public void Stop() { _run = false; }

        private void Loop()
        {
            var udp = new UdpClient();
            udp.EnableBroadcast = true;
            while (_run)
            {
                try
                {
                    var info = LanInfo.Primary();
                    if (info != null)
                    {
                        var json = "{\"pulse\":\"v1\",\"name\":\"" + Json.Esc(info.Name) + "\",\"mac\":\"" +
                                   info.Mac + "\",\"ip\":\"" + info.Ip + "\",\"broadcast\":\"" + info.Broadcast + "\"}";
                        var bytes = Encoding.UTF8.GetBytes(json);
                        foreach (var target in new[] { "255.255.255.255", info.Broadcast })
                        {
                            try { udp.Send(bytes, bytes.Length, new IPEndPoint(IPAddress.Parse(target), _port)); }
                            catch { }
                        }
                    }
                }
                catch { }
                Thread.Sleep(3000);
            }
            try { udp.Close(); } catch { }
        }
    }

    // ---- HTTP server --------------------------------------------------------
    class HttpServer
    {
        private readonly HttpListener _listener = new HttpListener();
        private readonly int _port;
        private volatile bool _run;

        public HttpServer(int port)
        {
            _port = port;
            // urlacl for http://+:42990/ already exists; fall back to localhost.
            try { _listener.Prefixes.Add("http://+:" + port + "/"); }
            catch { _listener.Prefixes.Add("http://localhost:" + port + "/"); }
        }

        public void Start()
        {
            try { _listener.Start(); }
            catch
            {
                _listener.Prefixes.Clear();
                _listener.Prefixes.Add("http://localhost:" + _port + "/");
                _listener.Start();
            }
            _run = true;
            var t = new Thread(Loop); t.IsBackground = true; t.Start();
        }

        public void Stop() { _run = false; try { _listener.Stop(); } catch { } }

        private void Loop()
        {
            while (_run)
            {
                HttpListenerContext ctx;
                try { ctx = _listener.GetContext(); }
                catch { break; }
                ThreadPool.QueueUserWorkItem(delegate { Handle(ctx); });
            }
        }

        private void Handle(HttpListenerContext ctx)
        {
            var req = ctx.Request;
            var res = ctx.Response;
            try
            {
                res.Headers["Access-Control-Allow-Origin"] = "*";
                var path = req.Url.AbsolutePath.ToLowerInvariant();

                if (path == "/info")
                {
                    var b = Screen.Size();
                    Write(res, "application/json", "{\"w\":" + b.Width + ",\"h\":" + b.Height + "}");
                }
                else if (path == "/frame")
                {
                    var jpg = Screen.Jpeg(1280, 45L);
                    res.ContentType = "image/jpeg";
                    res.ContentLength64 = jpg.Length;
                    res.OutputStream.Write(jpg, 0, jpg.Length);
                }
                else if (path == "/input")
                {
                    var d = ReadJson(req);
                    if (d != null) Input.Handle(d);
                    res.StatusCode = 204;
                }
                else if (path == "/command")
                {
                    var d = ReadJson(req);
                    if (d != null) Commands.Handle(d);
                    res.StatusCode = 200;
                }
                else if (path == "/status")
                {
                    Write(res, "application/json", "{\"awake\":true,\"idleSeconds\":" + Native.IdleSeconds() + "}");
                }
                else if (path == "/clipboard")
                {
                    Write(res, "text/plain; charset=utf-8", Clip.Get());
                }
                else if (path == "/shutdown")
                {
                    if (req.QueryString["dry"] != null)
                        Write(res, "text/plain", "would run: shutdown /s /t 0");
                    else { Commands.Shutdown(); res.StatusCode = 200; }
                }
                else res.StatusCode = 404;
            }
            catch { try { res.StatusCode = 500; } catch { } }
            finally { try { res.OutputStream.Close(); } catch { } }
        }

        private static Dictionary<string, object> ReadJson(HttpListenerRequest req)
        {
            try
            {
                using (var r = new StreamReader(req.InputStream, req.ContentEncoding))
                {
                    var body = r.ReadToEnd();
                    var ser = new JavaScriptSerializer();
                    return ser.Deserialize<Dictionary<string, object>>(body);
                }
            }
            catch { return null; }
        }

        private static void Write(HttpListenerResponse res, string type, string text)
        {
            var b = Encoding.UTF8.GetBytes(text);
            res.ContentType = type;
            res.ContentLength64 = b.Length;
            res.OutputStream.Write(b, 0, b.Length);
        }
    }

    // ---- Screen capture -----------------------------------------------------
    static class Screen
    {
        public static Size Size()
        {
            return new Size(Native.GetSystemMetrics(0), Native.GetSystemMetrics(1));
        }

        public static byte[] Jpeg(int maxWidth, long quality)
        {
            int w = Native.GetSystemMetrics(0), h = Native.GetSystemMetrics(1);
            using (var full = new Bitmap(w, h))
            {
                using (var g = Graphics.FromImage(full))
                    g.CopyFromScreen(0, 0, 0, 0, new Size(w, h));

                double scale = Math.Min(1.0, (double)maxWidth / w);
                int ow = (int)(w * scale), oh = (int)(h * scale);
                using (var outBmp = new Bitmap(ow, oh))
                {
                    using (var g2 = Graphics.FromImage(outBmp))
                    {
                        g2.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.Bilinear;
                        g2.DrawImage(full, 0, 0, ow, oh);
                    }
                    var codec = ImageCodecInfo.GetImageEncoders().First(c => c.FormatID == ImageFormat.Jpeg.Guid);
                    var ep = new EncoderParameters(1);
                    ep.Param[0] = new EncoderParameter(System.Drawing.Imaging.Encoder.Quality, quality);
                    using (var ms = new MemoryStream())
                    {
                        outBmp.Save(ms, codec, ep);
                        return ms.ToArray();
                    }
                }
            }
        }
    }

    // ---- Remote-desktop input verbs ----------------------------------------
    static class Input
    {
        public static void Handle(Dictionary<string, object> c)
        {
            if (c == null || !c.ContainsKey("t")) return;
            string t = Convert.ToString(c["t"]);
            switch (t)
            {
                case "m": Native.SetCursorPos(I(c, "x"), I(c, "y")); break;
                case "tap": Native.SetCursorPos(I(c, "x"), I(c, "y")); Left(); break;
                case "double": Native.SetCursorPos(I(c, "x"), I(c, "y")); Left(); Left(); break;
                case "rtap": Native.SetCursorPos(I(c, "x"), I(c, "y")); Right(); break;
                case "ld": Native.mouse_event(Native.LEFTDOWN, 0, 0, 0, IntPtr.Zero); break;
                case "lu": Native.mouse_event(Native.LEFTUP, 0, 0, 0, IntPtr.Zero); break;
                case "click": Left(); break;
                case "rclick": Right(); break;
                case "scroll": Native.mouse_event(Native.WHEEL, 0, 0, (uint)I(c, "d"), IntPtr.Zero); break;
                case "key": Key((byte)I(c, "vk")); break;
                case "kd": Native.keybd_event((byte)I(c, "vk"), 0, 0, IntPtr.Zero); break;
                case "ku": Native.keybd_event((byte)I(c, "vk"), 0, Native.KEYUP, IntPtr.Zero); break;
                case "text": Text(Convert.ToString(c.ContainsKey("s") ? c["s"] : "")); break;
            }
        }

        private static int I(Dictionary<string, object> c, string k)
        {
            if (c == null || !c.ContainsKey(k) || c[k] == null) return 0;
            try { return Convert.ToInt32(c[k], CultureInfo.InvariantCulture); } catch { return 0; }
        }
        private static void Left() { Native.mouse_event(Native.LEFTDOWN, 0, 0, 0, IntPtr.Zero); Native.mouse_event(Native.LEFTUP, 0, 0, 0, IntPtr.Zero); }
        private static void Right() { Native.mouse_event(Native.RIGHTDOWN, 0, 0, 0, IntPtr.Zero); Native.mouse_event(Native.RIGHTUP, 0, 0, 0, IntPtr.Zero); }
        private static void Key(byte vk) { Native.keybd_event(vk, 0, 0, IntPtr.Zero); Native.keybd_event(vk, 0, Native.KEYUP, IntPtr.Zero); }
        private static void Text(string s)
        {
            foreach (char ch in s)
            {
                Native.keybd_event(0, (byte)ch, Native.UNICODE, IntPtr.Zero);
                Native.keybd_event(0, (byte)ch, Native.UNICODE | Native.KEYUP, IntPtr.Zero);
            }
        }
    }

    // ---- Control commands ---------------------------------------------------
    static class Commands
    {
        public static void Handle(Dictionary<string, object> c)
        {
            if (c == null || !c.ContainsKey("cmd")) return;
            switch (Convert.ToString(c["cmd"]))
            {
                case "lock": Native.LockWorkStation(); break;
                case "sleep": Application.SetSuspendState(PowerState.Suspend, false, false); break;
                case "reboot": Run("shutdown.exe", "/r /t 3"); break;
                case "shutdown": Shutdown(); break;
                case "clipset": if (c.ContainsKey("text")) Clip.Set(Convert.ToString(c["text"])); break;
                case "run":
                    if (c.ContainsKey("entries") && c["entries"] is IEnumerable)
                        foreach (var e in (IEnumerable)c["entries"])
                            try { System.Diagnostics.Process.Start(Convert.ToString(e)); } catch { }
                    break;
            }
        }
        public static void Shutdown() { Run("shutdown.exe", "/s /t 3"); }
        private static void Run(string exe, string args)
        {
            try
            {
                var p = new System.Diagnostics.ProcessStartInfo(exe, args);
                p.WindowStyle = System.Diagnostics.ProcessWindowStyle.Hidden;
                p.CreateNoWindow = true;
                System.Diagnostics.Process.Start(p);
            }
            catch { }
        }
    }

    // ---- Clipboard (marshaled to the STA UI thread) -------------------------
    static class Clip
    {
        private static Control _ui;
        public static void Init(Control ui) { _ui = ui; }

        public static string Get()
        {
            if (_ui == null || !_ui.IsHandleCreated) return "";
            for (int attempt = 0; attempt < 3; attempt++)
            {
                try
                {
                    return (string)_ui.Invoke(new Func<string>(delegate
                    {
                        try { return Clipboard.ContainsText() ? Clipboard.GetText() : ""; }
                        catch { return ""; }
                    }));
                }
                catch { Thread.Sleep(60); }
            }
            return "";
        }

        public static void Set(string text)
        {
            if (_ui == null || text == null || !_ui.IsHandleCreated) return;
            for (int attempt = 0; attempt < 3; attempt++)
            {
                try
                {
                    _ui.Invoke(new Action(delegate
                    {
                        try { if (text.Length > 0) Clipboard.SetText(text); } catch { }
                    }));
                    return;
                }
                catch { Thread.Sleep(60); }
            }
        }
    }

    // ---- Auto-start ---------------------------------------------------------
    static class Startup
    {
        public static void EnsureAutoStart()
        {
            try
            {
                var exe = Application.ExecutablePath;
                using (var key = Microsoft.Win32.Registry.CurrentUser.OpenSubKey(
                    "Software\\Microsoft\\Windows\\CurrentVersion\\Run", true))
                {
                    if (key != null) key.SetValue("PulsePC", "\"" + exe + "\"");
                }
            }
            catch { }
        }
    }

    static class Json { public static string Esc(string s) { return (s ?? "").Replace("\\", "\\\\").Replace("\"", "\\\""); } }

    // ---- Win32 --------------------------------------------------------------
    static class Native
    {
        public const uint LEFTDOWN = 0x0002, LEFTUP = 0x0004, RIGHTDOWN = 0x0008, RIGHTUP = 0x0010, WHEEL = 0x0800;
        public const uint KEYUP = 0x0002, UNICODE = 0x0004;

        [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
        [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint dx, uint dy, uint data, IntPtr extra);
        [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint f, IntPtr extra);
        [DllImport("user32.dll")] public static extern int GetSystemMetrics(int i);
        [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
        [DllImport("user32.dll")] public static extern bool LockWorkStation();
        [DllImport("kernel32.dll")] public static extern uint GetTickCount();

        [StructLayout(LayoutKind.Sequential)]
        struct LASTINPUTINFO { public uint cbSize; public uint dwTime; }
        [DllImport("user32.dll")] static extern bool GetLastInputInfo(ref LASTINPUTINFO plii);

        public static uint IdleSeconds()
        {
            var lii = new LASTINPUTINFO();
            lii.cbSize = (uint)Marshal.SizeOf(lii);
            if (!GetLastInputInfo(ref lii)) return 0;
            return (GetTickCount() - lii.dwTime) / 1000;
        }
    }
}
