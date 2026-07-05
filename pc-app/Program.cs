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
using System.Drawing.Drawing2D;
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
            Application.SetCompatibleTextRenderingDefault(false);
            Native.SetProcessDPIAware();
            Startup.EnsureAutoStart();
            Application.Run(new PulseForm());
        }
    }

    // ---- Main window (branded) + tray + STA invoke target for clipboard ----
    class PulseForm : Form
    {
        static readonly Color Ink = Color.FromArgb(0x0B, 0x0B, 0x0F);
        static readonly Color Surface1 = Color.FromArgb(0x14, 0x14, 0x19);
        static readonly Color Stroke = Color.FromArgb(0x26, 0x26, 0x30);
        static readonly Color Purple = Color.FromArgb(0x8B, 0x5C, 0xF6);
        static readonly Color PurpleBright = Color.FromArgb(0xA7, 0x8B, 0xFA);
        static readonly Color PurpleDeep = Color.FromArgb(0x6D, 0x28, 0xD9);
        static readonly Color TextPrimary = Color.FromArgb(0xF4, 0xF4, 0xF6);
        static readonly Color TextSecondary = Color.FromArgb(0x9A, 0x9A, 0xA6);
        static readonly Color GreenC = Color.FromArgb(0x34, 0xD3, 0x99);

        static readonly Font FTitle = new Font("Segoe UI", 26f, FontStyle.Bold);
        static readonly Font FSub = new Font("Segoe UI", 11f);
        static readonly Font FStatus = new Font("Segoe UI", 12f, FontStyle.Bold);
        static readonly Font FKey = new Font("Segoe UI", 9.5f);
        static readonly Font FVal = new Font("Segoe UI", 10.5f, FontStyle.Bold);
        static readonly Font FSmall = new Font("Segoe UI", 9f);

        private NotifyIcon _tray;
        private bool _reallyExit;
        private HttpServer _server;
        private Beacon _beacon;
        private LanInfo _info;

        public PulseForm()
        {
            _info = LanInfo.Primary();
            Text = "Pulse PC";
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            ClientSize = new Size(440, 560);
            BackColor = Ink;
            ForeColor = TextPrimary;
            Font = new Font("Segoe UI", 9f);
            DoubleBuffered = true;
            Icon = BoltIcon(32);

            var btnHide = MakeButton("Minimize to tray", Purple, TextPrimary);
            btnHide.SetBounds(40, 456, 360, 46);
            btnHide.Click += delegate { HideToTray(); };
            Controls.Add(btnHide);

            var btnQuit = MakeButton("Quit Pulse PC", Surface1, TextSecondary);
            btnQuit.SetBounds(40, 510, 360, 38);
            btnQuit.FlatAppearance.BorderColor = Stroke;
            btnQuit.FlatAppearance.BorderSize = 1;
            btnQuit.Click += delegate { _reallyExit = true; Close(); };
            Controls.Add(btnQuit);

            _tray = new NotifyIcon();
            _tray.Icon = BoltIcon(16);
            _tray.Text = "Pulse PC — running";
            _tray.Visible = true;
            var menu = new ContextMenuStrip();
            var open = new ToolStripMenuItem("Open Pulse PC");
            open.Click += delegate { ShowFromTray(); };
            var quit = new ToolStripMenuItem("Quit");
            quit.Click += delegate { _reallyExit = true; Close(); };
            menu.Items.Add(open); menu.Items.Add(new ToolStripSeparator()); menu.Items.Add(quit);
            _tray.ContextMenuStrip = menu;
            _tray.DoubleClick += delegate { ShowFromTray(); };

            Clip.Init(this);
            _server = new HttpServer(Program.HttpPort); _server.Start();
            _beacon = new Beacon(Program.BeaconPort); _beacon.Start();
        }

        protected override void OnHandleCreated(EventArgs e)
        {
            base.OnHandleCreated(e);
            try { int on = 1; DwmSetWindowAttribute(Handle, 20, ref on, 4); } catch { }
        }
        [DllImport("dwmapi.dll")] static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int val, int size);

        private void HideToTray()
        {
            Hide();
            try { _tray.ShowBalloonTip(1500, "Pulse PC", "Still running in the tray.", ToolTipIcon.None); } catch { }
        }
        private void ShowFromTray() { Show(); WindowState = FormWindowState.Normal; Activate(); }

        protected override void OnFormClosing(FormClosingEventArgs e)
        {
            if (!_reallyExit && e.CloseReason == CloseReason.UserClosing)
            {
                e.Cancel = true; HideToTray(); return;
            }
            try { _beacon.Stop(); } catch { }
            try { _server.Stop(); } catch { }
            if (_tray != null) { _tray.Visible = false; _tray.Dispose(); }
            base.OnFormClosing(e);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            base.OnPaint(e);
            var g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;

            int cx = ClientSize.Width / 2;
            int r = 44, ly = 44;

            var circle = new Rectangle(cx - r, ly, r * 2, r * 2);
            using (var lg = new LinearGradientBrush(circle, PurpleBright, PurpleDeep, 90f))
                g.FillEllipse(lg, circle);
            DrawBolt(g, cx, ly + r, 46f, Color.White);

            DrawCenter(g, "Pulse", FTitle, TextPrimary, cx, ly + 2 * r + 12);
            DrawCenter(g, "PC companion", FSub, TextSecondary, cx, ly + 2 * r + 56);

            var card = new Rectangle(40, 252, ClientSize.Width - 80, 178);
            FillRounded(g, card, 18, Surface1, Stroke);
            int lx = card.Left + 22, ty = card.Top + 20;

            using (var gb = new SolidBrush(GreenC)) g.FillEllipse(gb, lx, ty + 4, 10, 10);
            DrawLeft(g, "Running", FStatus, TextPrimary, lx + 18, ty - 2);
            ty += 36;
            if (_info != null)
            {
                DrawKV(g, "This PC", _info.Name, lx, ty); ty += 28;
                DrawKV(g, "Address", _info.Ip, lx, ty); ty += 28;
                DrawKV(g, "MAC", _info.Mac, lx, ty);
            }
            DrawLeft(g, "Discovery · Remote Desktop · Commands · Clipboard", FSmall, TextSecondary, lx, card.Bottom - 26);
        }

        private void DrawKV(Graphics g, string k, string v, int x, int y)
        {
            DrawLeft(g, k, FKey, TextSecondary, x, y + 1);
            DrawLeft(g, v, FVal, TextPrimary, x + 100, y);
        }

        static void DrawCenter(Graphics g, string t, Font f, Color c, float cx, float y)
        {
            var sf = new StringFormat(); sf.Alignment = StringAlignment.Center;
            using (var b = new SolidBrush(c)) g.DrawString(t, f, b, cx, y, sf);
        }
        static void DrawLeft(Graphics g, string t, Font f, Color c, float x, float y)
        {
            using (var b = new SolidBrush(c)) g.DrawString(t, f, b, x, y);
        }

        static PointF[] BoltPoints(float cx, float cy, float size)
        {
            float s = size / 24f;
            var raw = new PointF[] {
                new PointF(13,2), new PointF(4.5f,13.5f), new PointF(11,13.5f),
                new PointF(10,22), new PointF(19.5f,10.5f), new PointF(13,10.5f)
            };
            var pts = new PointF[raw.Length];
            for (int i = 0; i < raw.Length; i++)
                pts[i] = new PointF(cx + (raw[i].X - 12f) * s, cy + (raw[i].Y - 12f) * s);
            return pts;
        }
        static void DrawBolt(Graphics g, float cx, float cy, float size, Color color)
        {
            using (var b = new SolidBrush(color)) g.FillPolygon(b, BoltPoints(cx, cy, size));
        }
        static Icon BoltIcon(int size)
        {
            var bmp = new Bitmap(size, size);
            using (var g = Graphics.FromImage(bmp))
            {
                g.SmoothingMode = SmoothingMode.AntiAlias;
                g.Clear(Color.Transparent);
                DrawBolt(g, size / 2f, size / 2f, size, PurpleBright);
            }
            return Icon.FromHandle(bmp.GetHicon());
        }

        static GraphicsPath RoundPath(Rectangle r, int radius)
        {
            int d = radius * 2;
            var path = new GraphicsPath();
            path.AddArc(r.X, r.Y, d, d, 180, 90);
            path.AddArc(r.Right - d, r.Y, d, d, 270, 90);
            path.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
            path.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
            path.CloseFigure();
            return path;
        }
        static void FillRounded(Graphics g, Rectangle r, int radius, Color fill, Color border)
        {
            using (var path = RoundPath(r, radius))
            {
                using (var b = new SolidBrush(fill)) g.FillPath(b, path);
                using (var p = new Pen(border, 1)) g.DrawPath(p, path);
            }
        }
        static Button MakeButton(string text, Color back, Color fore)
        {
            var b = new Button();
            b.Text = text; b.FlatStyle = FlatStyle.Flat;
            b.BackColor = back; b.ForeColor = fore;
            b.FlatAppearance.BorderSize = 0;
            b.Font = new Font("Segoe UI", 10.5f, FontStyle.Bold);
            b.Cursor = Cursors.Hand;
            return b;
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
