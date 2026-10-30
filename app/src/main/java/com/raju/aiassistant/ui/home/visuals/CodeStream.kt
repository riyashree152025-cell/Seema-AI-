package com.raju.aiassistant.ui.home.visuals

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.hypot

/** Lines of the "core kernel" terminal (decoration only: nothing here is executed). ASCII for fast drawing. */
private val KERNEL = listOf(
    "import seema.core as core",
    "from seema.voice import Listener, Speaker",
    "class HoloCore(core.Reactor):",
    "    def __init__(self, energy=1.0):",
    "        self.rings = [Ring(r) for r in (1.0, 1.2, 1.4)]",
    "        self.state = State.READY",
    "    def pulse(self, bpm):",
    "        return sin(t * bpm / 60) ** 8",
    "    async def listen(self):",
    "        audio = await mic.capture(vad=True)",
    "        text = stt.decode(audio, lang='hi-IN')",
    "        return intent.parse(text)",
    "def boot_sequence():",
    "    log('>> neural link ........ OK')",
    "    log('>> voice matrix ....... OK')",
    "    log('>> holo projector ..... OK')",
    "    for node in mesh.nodes:",
    "        node.sync(checksum=0x3F9A)",
    "while core.online:",
    "    frame = render(core, fps=30)",
    "    core.react(state=engine.state)",
    "    if engine.speaking:",
    "        core.energy = audio.level",
    "    yield frame",
    "fun route(intent: Intent) = when (intent) {",
    "    is Call -> phone.dial(intent.contact)",
    "    is Search -> web.query(intent.text)",
    "    else -> brain.reply(intent)",
    "}",
    "val shield = Encryption(AES_256_GCM)",
    "core.calibrate(gyro=True, thermal=True)",
    "assert core.integrity() == 0x00",
)

/** Lines of the "system monitor" stream on the other side. */
private val MONITOR = listOf(
    "SYS.SCAN   sector 0x0F  ......  CLEAN",
    "NET.LINK   latency 24ms   [##########]",
    "MEM.HEAP   alloc 0x7F3A9C00  OK",
    "CORE.TEMP  nominal  ++ stable",
    "VOICE.DSP  hi-IN / bn-IN  ready",
    "AI.ROUTER  gemini <-> groq  standby",
    "HOLO.GRID  48x48  sync 100%",
    "SEC.AUTH   keystore AES-GCM  locked",
    "PKT  4E 61 6D 61 73 74 65 21  >>",
    "PKT  0A FF 3C 9D 12 7B E4 08  >>",
    "TRACE  [core] ring[1].spin += 0.22",
    "TRACE  [core] heartbeat 64 bpm",
    "SCAN  ######......  58%",
    "SCAN  ##########..  86%",
    "SCAN  ############  100%  DONE",
    "UPLINK  ...... handshake OK",
)

/** Redraw rate of the code stream (independent of the core's frame rate). */
private const val STREAM_FPS = 15f

/**
 * The 3D dashboard's "JARVIS" backdrop: two terminal panels of code and system logs scrolling
 * upwards, the newest line being typed with a blinking cursor, plus a thin hex ticker. It is
 * dim and fades out around the core, so the core stays the star. Driven by the shared,
 * frame-capped animation clock (it stops when the core stops).
 *
 * @param focus centre of the core as a fraction of the screen, where the text fades out.
 */
@Composable
fun HoloCodeStream(
    color: Color,
    accent: Color,
    time: () -> Float,
    modifier: Modifier = Modifier,
    focus: Offset = Offset(0.5f, 0.42f),
) {
    val paint = remember { Paint().apply { isAntiAlias = true; typeface = Typeface.MONOSPACE } }
    val tick by remember(time) { derivedStateOf { (time() * STREAM_FPS).toInt() } }
    Canvas(modifier) {
        val t = tick / STREAM_FPS
        val fx = size.width * focus.x
        val fy = size.height * focus.y
        val clear = size.minDimension * 0.30f
        // Left: the kernel code; right (lower): the system monitor. Different speeds give depth.
        terminal(paint, KERNEL, color, accent, t, linesPerSec = 1.6f, x = 14f * density, top = size.height * 0.10f,
            width = size.width * 0.62f, height = size.height * 0.52f, fx, fy, clear, seed = 1)
        terminal(paint, MONITOR, accent, color, t, linesPerSec = 2.3f, x = size.width * 0.40f, top = size.height * 0.40f,
            width = size.width * 0.58f, height = size.height * 0.48f, fx, fy, clear, seed = 7)
        hexTicker(paint, color, t)
    }
}

/** One scrolling terminal: lines move up; the bottom line types itself out with a cursor. */
private fun DrawScope.terminal(
    paint: Paint, lines: List<String>, color: Color, accent: Color, t: Float, linesPerSec: Float,
    x: Float, top: Float, width: Float, height: Float, fx: Float, fy: Float, clear: Float, seed: Int,
) {
    val text = 9f * density
    val lineH = text * 1.45f
    val visible = (height / lineH).toInt().coerceAtLeast(3)
    val pos = t * linesPerSec
    val newest = pos.toInt()
    val frac = pos - newest // how much of the newest line is typed
    paint.textSize = text
    clipRect(x, top, x + width, top + height) {
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            for (k in 0 until visible) {
                val index = newest - k
                if (index < 0) break
                val line = lines[(index + seed) % lines.size]
                val y = top + height - k * lineH - 4f * density
                // Fade towards the top of the panel and around the core.
                val age = 1f - k / visible.toFloat()
                val d = hypot(x + width * 0.3f - fx, y - fy)
                val nearCore = ((d - clear * 0.7f) / clear).coerceIn(0.25f, 1f)
                val shown = if (k == 0) line.take((line.length * frac * 1.6f).toInt().coerceAtMost(line.length)) else line
                paint.isFakeBoldText = k == 0
                paint.color = (if (k == 0) accent else color).copy(alpha = (if (k == 0) 0.75f else 0.38f * age) * nearCore).toArgb()
                nc.drawText(shown, x, y, paint)
                if (k == 0 && ((t * 2.5f).toInt() % 2 == 0)) {
                    // Blinking block cursor after the typed text.
                    val cx = x + paint.measureText(shown) + 2f * density
                    paint.color = accent.copy(alpha = 0.85f).toArgb()
                    nc.drawRect(cx, y - text * 0.8f, cx + text * 0.55f, y + text * 0.15f, paint)
                }
            }
        }
    }
    // Thin glowing edge on the panel's side, like a holographic display frame.
    drawLine(
        Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.35f), Color.Transparent), startY = top, endY = top + height),
        Offset(x - 5f * density, top), Offset(x - 5f * density, top + height), 1.2f * density,
    )
}

/** A thin hex/binary ticker gliding along the very top and bottom edges. */
private fun DrawScope.hexTicker(paint: Paint, color: Color, t: Float) {
    val text = 8f * density
    paint.textSize = text
    paint.isFakeBoldText = false
    val hex = "0123456789ABCDEF"
    val sb = StringBuilder()
    val cells = (size.width / (text * 0.62f)).toInt() + 8
    val shift = (t * 6f).toInt()
    for (i in 0 until cells) {
        sb.append(hex[((pseudoRandom(i + shift) * 16f).toInt()).coerceIn(0, 15)])
        if (i % 2 == 1) sb.append(' ')
    }
    val s = sb.toString()
    val offset = -((t * 18f * density) % (text * 1.86f))
    drawIntoCanvas { canvas ->
        paint.color = color.copy(alpha = 0.22f).toArgb()
        canvas.nativeCanvas.drawText(s, offset, size.height * 0.035f + text, paint)
        canvas.nativeCanvas.drawText(s.reversed(), offset, size.height * 0.985f, paint)
    }
    drawRect(color.copy(alpha = 0.05f), topLeft = Offset(0f, size.height * 0.035f - 2f * density), size = Size(size.width, text * 1.4f))
}
