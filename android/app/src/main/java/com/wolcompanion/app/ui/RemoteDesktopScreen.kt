package com.wolcompanion.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wolcompanion.app.core.remote.RemoteControlClient
import com.wolcompanion.app.core.remote.RemoteControlClient.Companion.VK_ALT
import com.wolcompanion.app.core.remote.RemoteControlClient.Companion.VK_CONTROL
import com.wolcompanion.app.core.remote.RemoteControlClient.Companion.VK_DELETE
import com.wolcompanion.app.core.remote.RemoteControlClient.Companion.VK_DOWN
import com.wolcompanion.app.core.remote.RemoteControlClient.Companion.VK_ESCAPE
import com.wolcompanion.app.core.remote.RemoteControlClient.Companion.VK_LEFT
import com.wolcompanion.app.core.remote.RemoteControlClient.Companion.VK_RIGHT
import com.wolcompanion.app.core.remote.RemoteControlClient.Companion.VK_SHIFT
import com.wolcompanion.app.core.remote.RemoteControlClient.Companion.VK_TAB
import com.wolcompanion.app.core.remote.RemoteControlClient.Companion.VK_UP
import com.wolcompanion.app.core.remote.RemoteControlClient.Companion.VK_WIN
import com.wolcompanion.app.ui.theme.Ink
import com.wolcompanion.app.ui.theme.Purple
import com.wolcompanion.app.ui.theme.Surface2
import com.wolcompanion.app.ui.theme.TextPrimary
import com.wolcompanion.app.ui.theme.TextSecondary
import kotlinx.coroutines.isActive
import kotlin.math.abs

@Composable
fun RemoteDesktopScreen(host: String, pcName: String, onExit: () -> Unit) {
    val client = remember(host) { RemoteControlClient(host) }
    val haptic = LocalHapticFeedback.current

    var frame by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var remote by remember { mutableStateOf<RemoteControlClient.RemoteSize?>(null) }
    var connected by remember { mutableStateOf(false) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    // View transform + trackpad cursor (cursor is in REMOTE coordinates).
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var cursor by remember { mutableStateOf(Offset.Zero) }

    var showKeyboard by remember { mutableStateOf(false) }
    var showKeys by remember { mutableStateOf(false) }
    val activeMods = remember { mutableStateListOf<Int>() }  // sticky Ctrl/Alt/Shift/Win
    var showHint by remember { mutableStateOf(true) }
    var retry by remember { mutableStateOf(0) }
    var trouble by remember { mutableStateOf(false) }

    DisposableEffect(client) { onDispose { client.close() } }

    // Show a helpful message if we can't reach the PC within a few seconds.
    LaunchedEffect(host, retry) {
        trouble = false
        kotlinx.coroutines.delay(12000)
        if (frame == null) trouble = true
    }

    LaunchedEffect(host, retry) {
        remote = client.fetchInfo()?.also { cursor = Offset(it.width / 2f, it.height / 2f) }
        while (isActive) {
            val bmp = client.fetchFrame()
            if (bmp != null) {
                frame = bmp.asImageBitmap(); connected = true
                if (remote == null) remote = client.fetchInfo()?.also { cursor = Offset(it.width / 2f, it.height / 2f) }
            } else {
                // Back off on failure (PC asleep / agent starting) and keep retrying info.
                connected = false
                if (remote == null) remote = client.fetchInfo()?.also { cursor = Offset(it.width / 2f, it.height / 2f) }
                kotlinx.coroutines.delay(600)
            }
        }
    }

    fun baseScale(rem: RemoteControlClient.RemoteSize): Float =
        minOf(boxSize.width / rem.width.toFloat(), boxSize.height / rem.height.toFloat())

    fun clampPan(p: Offset, z: Float, rem: RemoteControlClient.RemoteSize): Offset {
        val bs = baseScale(rem)
        val maxX = maxOf(0f, (rem.width * bs * z - boxSize.width) / 2f)
        val maxY = maxOf(0f, (rem.height * bs * z - boxSize.height) / 2f)
        return Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
    }

    /** Map a remote-space point to on-screen pixels (for the cursor overlay). */
    fun remoteToScreen(c: Offset, rem: RemoteControlClient.RemoteSize): Offset {
        val bs = baseScale(rem)
        val offX = (boxSize.width - rem.width * bs) / 2f
        val offY = (boxSize.height - rem.height * bs) / 2f
        val fit = Offset(offX + c.x * bs, offY + c.y * bs)
        val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
        return center + (fit - center) * zoom + pan
    }

    fun pressKey(vk: Int) {
        if (activeMods.isEmpty()) client.key(vk)
        else { client.keyWithModifiers(vk, activeMods.toList()); activeMods.clear() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .onSizeChanged { boxSize = it },
    ) {
        val img = frame
        val rem = remote

        if (img != null && rem != null) {
            androidx.compose.foundation.Image(
                bitmap = img,
                contentDescription = "PC screen",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y),
            )

            // Gesture + cursor overlay (topmost, transparent).
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(rem) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val first = awaitFirstDown(requireUnconsumed = false)
                            val downTime = System.currentTimeMillis()
                            var moved = false
                            var dragging = false
                            var twoFinger = false
                            var twoFingerMoved = false
                            var lastSend = 0L

                            while (true) {
                                val event = awaitPointerEvent()
                                val pts = event.changes.filter { it.pressed }
                                if (pts.isEmpty()) break

                                if (pts.size >= 2) {
                                    twoFinger = true
                                    val p0 = pts[0]; val p1 = pts[1]
                                    val curDist = (p0.position - p1.position).getDistance()
                                    val prevDist = (p0.previousPosition - p1.previousPosition).getDistance()
                                    val centroidNow = (p0.position + p1.position) / 2f
                                    val centroidPrev = (p0.previousPosition + p1.previousPosition) / 2f
                                    val panDelta = centroidNow - centroidPrev
                                    val distDelta = curDist - prevDist
                                    if (abs(distDelta) > 1f || panDelta.getDistance() > 1f) twoFingerMoved = true
                                    val pinching = abs(distDelta) > 2f
                                    if (!pinching && zoom <= 1.01f) {
                                        if (abs(panDelta.y) > 0.5f) client.scroll((panDelta.y * 3f).toInt())
                                    } else {
                                        val k = if (prevDist > 0f) curDist / prevDist else 1f
                                        val newZoom = (zoom * k).coerceIn(1f, 4f)
                                        val actualK = if (zoom > 0f) newZoom / zoom else 1f
                                        val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                                        pan = clampPan((centroidPrev - center) * (1f - actualK) + pan * actualK + panDelta, newZoom, rem)
                                        zoom = newZoom
                                    }
                                    pts.forEach { it.consume() }
                                } else if (!twoFinger) {
                                    val p = pts[0]
                                    val delta = p.position - p.previousPosition
                                    if (!moved && (p.position - first.position).getDistance() > slop) {
                                        moved = true
                                        if (System.currentTimeMillis() - downTime >= 350) {
                                            // Touchpad drag: press the button at the CURRENT cursor,
                                            // then move relatively — never jump to the finger.
                                            dragging = true
                                            client.move(cursor.x.toInt(), cursor.y.toInt())
                                            client.leftDown()
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                    if (moved) {
                                        val eff = baseScale(rem) * zoom
                                        val sens = 1.3f
                                        cursor = Offset(
                                            (cursor.x + delta.x / eff * sens).coerceIn(0f, rem.width - 1f),
                                            (cursor.y + delta.y / eff * sens).coerceIn(0f, rem.height - 1f),
                                        )
                                        val now = System.currentTimeMillis()
                                        if (now - lastSend >= 24) {
                                            client.move(cursor.x.toInt(), cursor.y.toInt()); lastSend = now
                                        }
                                        p.consume()
                                    }
                                }
                            }

                            when {
                                dragging -> { client.move(cursor.x.toInt(), cursor.y.toInt()); client.leftUp() }
                                // Touchpad tap: click at the CURRENT cursor position (not the finger).
                                twoFinger -> if (!twoFingerMoved) {
                                    client.rightTap(cursor.x.toInt(), cursor.y.toInt())
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                !moved -> client.tap(cursor.x.toInt(), cursor.y.toInt())
                            }
                            if (showHint) showHint = false
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val sp = remoteToScreen(cursor, rem)
                    // Larger, high-contrast pointer arrow (tip exactly at the cursor point),
                    // so you always know where a tap will click — like a touchpad pointer.
                    val s = 1.7f
                    val path = Path().apply {
                        moveTo(sp.x, sp.y)
                        lineTo(sp.x, sp.y + 22f * s)
                        lineTo(sp.x + 6f * s, sp.y + 16f * s)
                        lineTo(sp.x + 11f * s, sp.y + 25f * s)
                        lineTo(sp.x + 15f * s, sp.y + 23f * s)
                        lineTo(sp.x + 10f * s, sp.y + 14f * s)
                        lineTo(sp.x + 18f * s, sp.y + 14f * s)
                        close()
                    }
                    // Soft halo for visibility on any background.
                    drawCircle(Color(0x552196F3), radius = 7f, center = sp)
                    drawPath(path, Color.Black, style = Stroke(width = 5f))
                    drawPath(path, Color.White)
                }
            }
        } else {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                if (trouble) {
                    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Can't reach $pcName", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Make sure the PC is awake and the Pulse agent is running (allowed in your antivirus).",
                            color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.height(20.dp))
                        Text("Retry", color = Purple, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable { retry++ }.padding(8.dp))
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Purple, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Connecting to $pcName…", color = TextSecondary, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        // Top toolbar
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(Icons.Rounded.Close, "Disconnect") { onExit() }
            Spacer(Modifier.width(8.dp))
            Text(
                if (connected) pcName else "$pcName • reconnecting…",
                color = TextPrimary, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (zoom > 1.01f) {
                ToolButton(Icons.Rounded.ZoomOutMap, "Reset zoom") { zoom = 1f; pan = Offset.Zero }
                Spacer(Modifier.width(6.dp))
            }
            ToolButton(Icons.Rounded.Mouse, "Keys") { showKeys = !showKeys }
            Spacer(Modifier.width(6.dp))
            ToolButton(Icons.Rounded.Keyboard, "Keyboard") { showKeyboard = !showKeyboard }
        }

        // First-run gesture hint
        AnimatedVisibility(visible = showHint && frame != null, modifier = Modifier.align(Alignment.Center)) {
            Text(
                "Touchpad mode — drag anywhere to move the pointer, tap to click where it is.\ntwo-finger tap = right-click  •  hold + drag = drag  •  pinch = zoom  •  two-finger swipe = scroll",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .background(Surface2.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
            )
        }

        // Bottom controls
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars)) {
            if (showKeys) {
                KeysBar(
                    activeMods = activeMods,
                    onMod = { vk -> if (activeMods.contains(vk)) activeMods.remove(vk) else activeMods.add(vk) },
                    onKey = { vk -> pressKey(vk) },
                )
            }
        }

        if (showKeyboard) HiddenKeyboard(client, activeMods) { showKeyboard = false }
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).background(Surface2.copy(alpha = 0.85f), CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = TextPrimary, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun KeysBar(activeMods: List<Int>, onMod: (Int) -> Unit, onKey: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Surface2.copy(alpha = 0.95f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyChip("Esc") { onKey(VK_ESCAPE) }
        KeyChip("Tab") { onKey(VK_TAB) }
        KeyChip("Ctrl", active = activeMods.contains(VK_CONTROL)) { onMod(VK_CONTROL) }
        KeyChip("Alt", active = activeMods.contains(VK_ALT)) { onMod(VK_ALT) }
        KeyChip("Shift", active = activeMods.contains(VK_SHIFT)) { onMod(VK_SHIFT) }
        KeyChip("Win", active = activeMods.contains(VK_WIN)) { onMod(VK_WIN) }
        KeyChip("Del") { onKey(VK_DELETE) }
        KeyChip("←") { onKey(VK_LEFT) }
        KeyChip("↑") { onKey(VK_UP) }
        KeyChip("↓") { onKey(VK_DOWN) }
        KeyChip("→") { onKey(VK_RIGHT) }
    }
}

@Composable
private fun KeyChip(label: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (active) Purple else Ink, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/** Invisible field that captures the soft keyboard; streams keystrokes (with active modifiers). */
@Composable
private fun HiddenKeyboard(client: RemoteControlClient, activeMods: List<Int>, onClosed: () -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue("")) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }

    BasicTextField(
        value = value,
        onValueChange = { new ->
            val oldS = value.text; val newS = new.text
            var i = 0
            while (i < oldS.length && i < newS.length && oldS[i] == newS[i]) i++
            repeat(oldS.length - i) { client.key(RemoteControlClient.VK_BACK) }
            newS.substring(i).forEach { ch ->
                when {
                    ch == '\n' -> client.key(RemoteControlClient.VK_RETURN)
                    activeMods.isNotEmpty() -> client.keyWithModifiers(ch.uppercaseChar().code, activeMods.toList())
                    else -> client.text(ch.toString())
                }
            }
            value = if (newS.length > 64) TextFieldValue("") else new
        },
        modifier = Modifier.size(1.dp).focusRequester(focus),
        cursorBrush = SolidColor(Color.Transparent),
    )
}
