package com.skeorz.lab.deepsweep.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class Palette(
    val bg: Color, val hidden: Color, val hiddenHi: Color, val open: Color, val openAlt: Color,
    val line: Color, val accent: Color, val text: Color, val panel: Color, val mine: Color,
    val special: Color = Color(0xFFB57BFF), val vault: Color = Color(0xFFFFC857), val danger: Color = Color(0xFFFF4D5E),
)

fun palette(id: String) = when (id) {
    "theme_kelp" -> Palette(Color(0xFF07150E), Color(0xFF1F4D33), Color(0xFF2D6B47), Color(0xFF0C2117), Color(0xFF0F271B),
        Color(0xFF3E8E5E), Color(0xFF9BE564), Color(0xFFE6F5E9), Color(0xFF10291C), Color(0xFF0B1A10))
    "theme_coral" -> Palette(Color(0xFF1E0F1A), Color(0xFF8C3B4F), Color(0xFFB4566A), Color(0xFF2B1622), Color(0xFF321A28),
        Color(0xFFE07A5F), Color(0xFFFFB4A2), Color(0xFFFFEDE8), Color(0xFF341A27), Color(0xFF14070E))
    "theme_arctic" -> Palette(Color(0xFFDCE8EE), Color(0xFF8FB0C2), Color(0xFFADC8D6), Color(0xFFF7FBFD), Color(0xFFEDF4F8),
        Color(0xFF6B8FA3), Color(0xFF1E5A96), Color(0xFF0E2A3B), Color(0xFFC9DCE5), Color(0xFF122633),
        special = Color(0xFF7B4FD6), vault = Color(0xFFC98A00))
    "theme_volcano" -> Palette(Color(0xFF120A08), Color(0xFF4A1F14), Color(0xFF6B2D1C), Color(0xFF1E1210), Color(0xFF241613),
        Color(0xFFFF6B35), Color(0xFFFFD166), Color(0xFFFFF1E0), Color(0xFF2A1612), Color(0xFF050202))
    "theme_neon" -> Palette(Color(0xFF07020F), Color(0xFF2A0F4D), Color(0xFF3F1A70), Color(0xFF0E0620), Color(0xFF120828),
        Color(0xFFFF2BD6), Color(0xFF00F0FF), Color(0xFFF3E9FF), Color(0xFF1A0B33), Color(0xFF000000))
    else -> Palette(Color(0xFF061423), Color(0xFF12395A), Color(0xFF1B527D), Color(0xFF0A2236), Color(0xFF0C2840),
        Color(0xFF1F6F8B), Color(0xFF3FE0D0), Color(0xFFE3F6FA), Color(0xFF0D2B44), Color(0xFF020A12))
}

fun numberColors(id: String, p: Palette): List<Color> = when (id) {
    "num_pastel" -> listOf(0xFFA0C4FF, 0xFFB9FBC0, 0xFFFFADAD, 0xFFBDB2FF, 0xFFFFD6A5, 0xFF9BF6FF, 0xFFFDFFB6, 0xFFFFC6FF).map { Color(it) }
    "num_mono" -> List(8) { p.accent }
    "num_heat" -> listOf(0xFF4CC9F0, 0xFF4895EF, 0xFF7209B7, 0xFFB5179E, 0xFFF72585, 0xFFFF6D00, 0xFFFFB700, 0xFFFFFFFF).map { Color(it) }
    else -> listOf(0xFF3FE0D0, 0xFF7CFC9A, 0xFFFFE066, 0xFFFF9F43, 0xFFFF5E78, 0xFFD67BFF, 0xFFFFFFFF, 0xFF9AA9B5).map { Color(it) }
}.let { if (p.bg.luminance() > 0.5f && id == "num_sonar") it.map { c -> c.darker() } else it }

private fun Color.luminance() = 0.3f * red + 0.59f * green + 0.11f * blue
private fun Color.darker() = Color(red * 0.55f, green * 0.55f, blue * 0.55f, alpha)

private fun star(c: Offset, outer: Float, inner: Float, points: Int, rot: Float = -PI.toFloat() / 2) = Path().apply {
    for (k in 0 until points * 2) {
        val r = if (k % 2 == 0) outer else inner
        val a = rot + k * PI.toFloat() / points
        val pt = Offset(c.x + r * cos(a), c.y + r * sin(a))
        if (k == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
    }
    close()
}

fun DrawScope.drawMine(skin: String, c: Offset, r: Float, p: Palette) {
    when (skin) {
        "mine_urchin" -> {
            for (k in 0 until 16) {
                val a = k * PI.toFloat() / 8
                drawLine(p.mine, c, Offset(c.x + r * cos(a), c.y + r * sin(a)), strokeWidth = r * 0.12f)
            }
            drawCircle(p.mine, r * 0.5f, c)
            drawCircle(p.accent.copy(alpha = 0.5f), r * 0.15f, Offset(c.x - r * 0.15f, c.y - r * 0.15f))
        }
        "mine_orb" -> {
            drawCircle(p.accent.copy(alpha = 0.25f), r, c)
            drawCircle(p.mine, r * 0.62f, c)
            drawCircle(p.accent, r * 0.85f, c, style = Stroke(r * 0.1f))
            drawCircle(p.danger, r * 0.18f, c)
        }
        "mine_shell" -> {
            val shell = Path().apply {
                moveTo(c.x, c.y + r * 0.8f)
                lineTo(c.x - r * 0.9f, c.y - r * 0.2f)
                quadraticTo(c.x, c.y - r * 1.3f, c.x + r * 0.9f, c.y - r * 0.2f)
                close()
            }
            drawPath(shell, p.mine)
            for (k in -2..2) drawLine(p.accent.copy(alpha = 0.6f), Offset(c.x, c.y + r * 0.75f),
                Offset(c.x + k * r * 0.33f, c.y - r * 0.55f), strokeWidth = r * 0.07f)
        }
        else -> {
            for (k in 0 until 8) {
                val a = k * PI.toFloat() / 4
                drawLine(p.mine, Offset(c.x + r * 0.5f * cos(a), c.y + r * 0.5f * sin(a)),
                    Offset(c.x + r * 0.95f * cos(a), c.y + r * 0.95f * sin(a)), strokeWidth = r * 0.16f)
                drawCircle(p.mine, r * 0.1f, Offset(c.x + r * 0.95f * cos(a), c.y + r * 0.95f * sin(a)))
            }
            drawCircle(p.mine, r * 0.62f, c)
            drawCircle(Color.White.copy(alpha = 0.55f), r * 0.16f, Offset(c.x - r * 0.22f, c.y - r * 0.22f))
        }
    }
}

fun DrawScope.drawFlag(skin: String, c: Offset, r: Float, p: Palette) {
    val red = p.danger
    when (skin) {
        "flag_pennant" -> {
            drawLine(p.text, Offset(c.x - r * 0.45f, c.y + r * 0.8f), Offset(c.x - r * 0.45f, c.y - r * 0.8f), strokeWidth = r * 0.12f)
            drawPath(Path().apply {
                moveTo(c.x - r * 0.4f, c.y - r * 0.8f); lineTo(c.x + r * 0.75f, c.y - r * 0.4f); lineTo(c.x - r * 0.4f, c.y); close()
            }, red)
        }
        "flag_star" -> {
            drawPath(star(c, r * 0.85f, r * 0.38f, 5), Color(0xFFFF8A5B))
            drawCircle(Color(0xFFFFD3B6), r * 0.15f, c)
        }
        "flag_beacon" -> {
            drawPath(Path().apply {
                moveTo(c.x - r * 0.35f, c.y + r * 0.8f); lineTo(c.x - r * 0.18f, c.y - r * 0.35f)
                lineTo(c.x + r * 0.18f, c.y - r * 0.35f); lineTo(c.x + r * 0.35f, c.y + r * 0.8f); close()
            }, Color.White)
            drawRect(red, Offset(c.x - r * 0.28f, c.y + r * 0.15f), Size(r * 0.56f, r * 0.22f))
            drawCircle(p.vault, r * 0.22f, Offset(c.x, c.y - r * 0.5f))
            drawCircle(p.vault.copy(alpha = 0.3f), r * 0.5f, Offset(c.x, c.y - r * 0.5f))
        }
        else -> {
            drawLine(p.text, Offset(c.x, c.y - r * 0.85f), Offset(c.x, c.y + r * 0.1f), strokeWidth = r * 0.1f)
            drawPath(Path().apply {
                moveTo(c.x - r * 0.6f, c.y + r * 0.15f)
                quadraticTo(c.x, c.y + r * 1.15f, c.x + r * 0.6f, c.y + r * 0.15f)
                close()
            }, red)
            drawRect(Color.White, Offset(c.x - r * 0.6f, c.y + r * 0.1f), Size(r * 1.2f, r * 0.16f))
            drawCircle(p.vault, r * 0.16f, Offset(c.x, c.y - r * 0.85f))
        }
    }
}

fun DrawScope.drawCrystal(c: Offset, r: Float, color: Color) {
    drawPath(Path().apply {
        moveTo(c.x, c.y - r); lineTo(c.x + r * 0.7f, c.y - r * 0.15f); lineTo(c.x, c.y + r); lineTo(c.x - r * 0.7f, c.y - r * 0.15f); close()
    }, color)
    drawPath(Path().apply {
        moveTo(c.x, c.y - r); lineTo(c.x + r * 0.25f, c.y - r * 0.15f); lineTo(c.x, c.y + r); lineTo(c.x - r * 0.25f, c.y - r * 0.15f); close()
    }, Color.White.copy(alpha = 0.55f))
}

/** Booster icons: sonar sweep, crossed-out mine, bathyscaphe. */
fun DrawScope.drawBooster(id: String, c: Offset, r: Float, color: Color, back: Color) {
    val w = r * 0.14f
    when (id) {
        "flash" -> { // sonar: radar sweep
            drawCircle(color, r * 0.95f, c, style = Stroke(w))
            drawCircle(color.copy(alpha = 0.7f), r * 0.6f, c, style = Stroke(w * 0.8f))
            drawArc(color.copy(alpha = 0.35f), -90f, 70f, true, Offset(c.x - r * 0.95f, c.y - r * 0.95f), Size(r * 1.9f, r * 1.9f))
            drawLine(color, c, Offset(c.x + r * 0.62f, c.y - r * 0.72f), strokeWidth = w)
            drawCircle(color, r * 0.16f, c)
            drawCircle(color, r * 0.12f, Offset(c.x - r * 0.35f, c.y + r * 0.3f))
        }
        "defuse" -> {
            for (k in 0 until 8) {
                val a = k * PI.toFloat() / 4
                drawLine(color, c, Offset(c.x + r * 0.8f * cos(a), c.y + r * 0.8f * sin(a)), strokeWidth = w)
            }
            drawCircle(color, r * 0.5f, c)
            drawLine(back, Offset(c.x - r * 0.95f, c.y + r * 0.95f), Offset(c.x + r * 0.95f, c.y - r * 0.95f), strokeWidth = w * 2.6f)
            drawLine(Color(0xFFFF4D5E), Offset(c.x - r * 0.95f, c.y + r * 0.95f), Offset(c.x + r * 0.95f, c.y - r * 0.95f), strokeWidth = w * 1.3f)
        }
        "echo" -> {
            // 3×3 grid with a pinged centre
            val cell = r * 0.52f
            for (dy in -1..1) for (dx in -1..1) {
                val tl = Offset(c.x + dx * cell * 1.15f - cell / 2, c.y + dy * cell * 1.15f - cell / 2)
                if (dx == 0 && dy == 0) drawRect(color, tl, Size(cell, cell))
                else drawRect(color, tl, Size(cell, cell), style = Stroke(w * 0.6f))
            }
        }
        "trawl" -> {
            // horseshoe magnet
            drawArc(color, 0f, 180f, false, Offset(c.x - r * 0.65f, c.y - r * 0.55f), Size(r * 1.3f, r * 1.3f), style = Stroke(w * 2.2f))
            drawLine(color, Offset(c.x - r * 0.65f, c.y + r * 0.1f), Offset(c.x - r * 0.65f, c.y - r * 0.75f), strokeWidth = w * 2.2f)
            drawLine(color, Offset(c.x + r * 0.65f, c.y + r * 0.1f), Offset(c.x + r * 0.65f, c.y - r * 0.75f), strokeWidth = w * 2.2f)
            drawRect(Color(0xFFFF4D5E), Offset(c.x - r * 0.81f, c.y - r * 0.95f), Size(r * 0.32f, r * 0.28f))
            drawRect(Color(0xFFFF4D5E), Offset(c.x + r * 0.49f, c.y - r * 0.95f), Size(r * 0.32f, r * 0.28f))
        }
        "sonar" -> { // periscope: tube with an eyepiece and a lens
            drawRect(color, Offset(c.x - r * 0.15f, c.y - r * 0.55f), Size(r * 0.3f, r * 1.5f))
            drawRect(color, Offset(c.x - r * 0.15f, c.y - r * 0.75f), Size(r * 0.85f, r * 0.32f))
            drawCircle(back, r * 0.1f, Offset(c.x + r * 0.55f, c.y - r * 0.59f))
            drawCircle(color, r * 0.55f, Offset(c.x, c.y + r * 0.1f), style = Stroke(w))
            drawLine(color.copy(alpha = 0.6f), Offset(c.x - r * 0.95f, c.y + r * 0.85f), Offset(c.x + r * 0.95f, c.y + r * 0.85f), strokeWidth = w)
        }
        "shield" -> {
            drawPath(Path().apply {
                moveTo(c.x, c.y - r * 0.95f)
                lineTo(c.x + r * 0.8f, c.y - r * 0.6f)
                quadraticTo(c.x + r * 0.75f, c.y + r * 0.5f, c.x, c.y + r * 0.95f)
                quadraticTo(c.x - r * 0.75f, c.y + r * 0.5f, c.x - r * 0.8f, c.y - r * 0.6f)
                close()
            }, color)
            drawLine(back, Offset(c.x - r * 0.35f, c.y), Offset(c.x - r * 0.05f, c.y + r * 0.3f), strokeWidth = w * 1.3f)
            drawLine(back, Offset(c.x - r * 0.05f, c.y + r * 0.3f), Offset(c.x + r * 0.4f, c.y - r * 0.3f), strokeWidth = w * 1.3f)
        }
        "lockpick" -> { // bathyscaphe
            drawOval(color, Offset(c.x - r * 0.95f, c.y - r * 0.15f), Size(r * 1.9f, r * 0.8f))
            drawRoundRect(color, Offset(c.x - r * 0.35f, c.y - r * 0.6f), Size(r * 0.7f, r * 0.55f),
                androidx.compose.ui.geometry.CornerRadius(r * 0.15f))
            drawCircle(back, r * 0.14f, Offset(c.x - r * 0.35f, c.y + r * 0.25f))
            drawCircle(back, r * 0.14f, Offset(c.x + r * 0.15f, c.y + r * 0.25f))
            drawLine(color, Offset(c.x + r * 0.95f, c.y + r * 0.25f), Offset(c.x + r * 1.05f, c.y - r * 0.1f), strokeWidth = w)
            drawCircle(color.copy(alpha = 0.5f), r * 0.1f, Offset(c.x - r * 0.1f, c.y - r * 0.85f))
            drawCircle(color.copy(alpha = 0.35f), r * 0.07f, Offset(c.x + r * 0.15f, c.y - r * 1.0f))
        }
        "solve" -> { // torpedo
            drawRoundRect(color, Offset(c.x - r * 0.85f, c.y - r * 0.22f), Size(r * 1.5f, r * 0.44f),
                androidx.compose.ui.geometry.CornerRadius(r * 0.22f))
            drawPath(Path().apply {
                moveTo(c.x - r * 0.75f, c.y); lineTo(c.x - r * 1.0f, c.y - r * 0.45f); lineTo(c.x - r * 0.95f, c.y)
                lineTo(c.x - r * 1.0f, c.y + r * 0.45f); close()
            }, color)
            drawLine(back, Offset(c.x + r * 0.35f, c.y - r * 0.22f), Offset(c.x + r * 0.35f, c.y + r * 0.22f), strokeWidth = w)
            drawCircle(Color(0xFFFF4D5E), r * 0.12f, Offset(c.x + r * 0.62f, c.y))
            drawLine(color.copy(alpha = 0.6f), Offset(c.x + r * 0.75f, c.y - r * 0.55f), Offset(c.x + r * 0.95f, c.y - r * 0.75f), strokeWidth = w * 0.7f)
            drawLine(color.copy(alpha = 0.6f), Offset(c.x + r * 0.75f, c.y + r * 0.55f), Offset(c.x + r * 0.95f, c.y + r * 0.75f), strokeWidth = w * 0.7f)
        }
        "oxygen" -> {
            // tank with bubbles
            drawRoundRect(color, Offset(c.x - r * 0.38f, c.y - r * 0.55f), Size(r * 0.76f, r * 1.45f),
                androidx.compose.ui.geometry.CornerRadius(r * 0.35f))
            drawRect(color, Offset(c.x - r * 0.15f, c.y - r * 0.8f), Size(r * 0.3f, r * 0.25f))
            drawRect(back, Offset(c.x - r * 0.38f, c.y - r * 0.05f), Size(r * 0.76f, r * 0.12f))
            drawCircle(color.copy(alpha = 0.7f), r * 0.13f, Offset(c.x + r * 0.62f, c.y - r * 0.55f))
            drawCircle(color.copy(alpha = 0.5f), r * 0.09f, Offset(c.x + r * 0.78f, c.y - r * 0.88f))
        }
        else -> {}
    }
}

fun DrawScope.drawLock(c: Offset, r: Float, color: Color) {
    drawArc(color, 180f, 180f, false, Offset(c.x - r * 0.45f, c.y - r * 0.95f), Size(r * 0.9f, r * 0.9f), style = Stroke(r * 0.16f))
    drawRect(color, Offset(c.x - r * 0.65f, c.y - r * 0.5f), Size(r * 1.3f, r * 1.1f))
}
