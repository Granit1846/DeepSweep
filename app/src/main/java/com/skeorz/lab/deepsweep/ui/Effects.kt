package com.skeorz.lab.deepsweep.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private fun hexPath(c: Offset, r: Float) = Path().apply {
    for (k in 0 until 6) {
        val a = Math.toRadians(60.0 * k + 30).toFloat()
        val p = Offset(c.x + r * cos(a), c.y + r * sin(a))
        if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

/** Centres of a honeycomb covering the canvas. */
private fun DrawScope.hexCentres(r: Float): List<Offset> {
    val w = sqrt(3f) * r
    val h = 1.5f * r
    val out = ArrayList<Offset>()
    var row = 0
    var y = 0f
    while (y < size.height + r) {
        var x = if (row % 2 == 0) 0f else w / 2
        while (x < size.width + w) { out += Offset(x, y); x += w }
        y += h; row++
    }
    return out
}

/**
 * Active shield: honeycomb in the theme colour over the whole board. It appears fully opaque
 * (transparency 0) and fades to 90% transparency so the field stays readable.
 */
@Composable
fun ShieldOverlay() {
    val c = MaterialTheme.colorScheme.primary
    val opacity = remember { Animatable(1f) }
    LaunchedEffect(Unit) { opacity.animateTo(0.1f, tween(1400)) }
    Canvas(Modifier.fillMaxSize()) {
        val r = size.width / 9
        val o = opacity.value
        for (p in hexCentres(r)) {
            drawPath(hexPath(p, r * 0.96f), c.copy(alpha = 0.35f * o))
            drawPath(hexPath(p, r * 0.96f), c.copy(alpha = o), style = Stroke(r * 0.05f))
        }
    }
}

/** The shield cracks and its cells fall apart; replays every time [signal] grows. */
@Composable
fun ShieldShatter(signal: Int) {
    if (signal == 0) return
    val c = MaterialTheme.colorScheme.primary
    val t = remember(signal) { Animatable(0f) }
    LaunchedEffect(signal) { t.animateTo(1f, tween(1100, easing = LinearEasing)) }
    if (t.value >= 1f) return
    val rnd = remember(signal) { Random(signal) }
    val drift = remember(signal) { List(400) { Triple(rnd.nextFloat() - 0.5f, rnd.nextFloat(), rnd.nextFloat() * 2 - 1) } }
    Canvas(Modifier.fillMaxSize()) {
        val r = size.width / 9
        val v = t.value
        val crack = (v / 0.25f).coerceAtMost(1f)
        hexCentres(r).forEachIndexed { i, p ->
            val (dx, delay, spin) = drift[i % drift.size]
            val fall = ((v - 0.25f - delay * 0.25f) / 0.5f).coerceIn(0f, 1f)
            val pos = p + Offset(dx * r * 2 * fall, size.height * 0.6f * fall * fall)
            rotate(spin * 140f * fall, pos) {
                drawPath(hexPath(pos, r * 0.9f), c.copy(alpha = 0.18f * (1 - fall)))
                drawPath(hexPath(pos, r * 0.9f), c.copy(alpha = 0.5f * (1 - fall)), style = Stroke(r * 0.05f))
            }
            // cracks run through the cells before they fall
            if (fall == 0f && crack > 0f) drawLine(Color.White.copy(alpha = 0.5f * crack), pos - Offset(r * 0.5f * crack, r * 0.3f * dx),
                pos + Offset(r * 0.5f * crack, r * 0.4f * spin), strokeWidth = r * 0.04f)
        }
    }
}

/** Oxygen: bubbles rising from the bottom of the screen; replays every time [signal] grows. */
@Composable
fun Bubbles(signal: Int) {
    if (signal == 0) return
    val c = MaterialTheme.colorScheme.primary
    val t = remember(signal) { Animatable(0f) }
    LaunchedEffect(signal) { t.animateTo(1f, tween(1800, easing = LinearEasing)) }
    if (t.value >= 1f) return
    val rnd = remember(signal) { Random(signal * 31) }
    val bubbles = remember(signal) { List(40) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 0.4f, 0.4f + rnd.nextFloat() * 0.6f, rnd.nextFloat() * 6f) } }
    Canvas(Modifier.fillMaxSize()) {
        for ((x, delay, size01, phase) in bubbles.map { listOf(it[0], it[1], it[2], it[3]) }) {
            val p = ((t.value - delay) / 0.6f).coerceIn(0f, 1f)
            if (p == 0f || p == 1f) continue
            val r = size.width * 0.025f * size01
            val pos = Offset(size.width * x + sin(phase + p * 8f) * r * 1.5f, size.height * (1.05f - p * 1.1f))
            drawCircle(c.copy(alpha = 0.18f * (1 - p)), r, pos)
            drawCircle(Color.White.copy(alpha = 0.7f * (1 - p)), r, pos, style = Stroke(r * 0.18f))
            drawCircle(Color.White.copy(alpha = 0.6f * (1 - p)), r * 0.25f, pos - Offset(r * 0.35f, r * 0.35f))
        }
    }
}

/** Repeated diagonal «DEBUG · ЧИТЫ» marks; [alpha] 0.1 in game (90% transparent), stronger in the cheats window. */
@Composable
fun CheatWatermark(alpha: Float) {
    val paint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        paint.textSize = size.width / 13
        paint.color = android.graphics.Color.argb((alpha * 255).toInt(), 255, 77, 94)
        val stepX = size.width / 1.6f
        val stepY = size.width / 3.2f
        rotate(-30f, Offset(size.width / 2, size.height / 2)) {
            drawContext.canvas.nativeCanvas.let { c ->
                var row = 0
                var y = -size.height * 0.4f
                while (y < size.height * 1.4f) {
                    var x = -size.width * 0.6f + (row % 2) * stepX / 2
                    while (x < size.width * 1.6f) { c.drawText("DEBUG · ЧИТЫ", x, y, paint); x += stepX }
                    y += stepY; row++
                }
            }
        }
    }
}
