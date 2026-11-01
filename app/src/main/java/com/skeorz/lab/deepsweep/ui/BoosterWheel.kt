package com.skeorz.lab.deepsweep.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skeorz.lab.deepsweep.meta.BOOSTERS
import com.skeorz.lab.deepsweep.meta.Store
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Fan of cards: opens the booster wheel; shows the booster being aimed, if any. */
@Composable
fun FanButton(aiming: String?, open: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val active = aiming != null || open
    val bg = if (active) cs.primary else cs.surfaceVariant
    val fg = if (active) cs.onPrimary else cs.primary
    Box(
        Modifier.size(width = 64.dp, height = 46.dp).clip(RoundedCornerShape(12.dp)).background(bg)
            .border(1.dp, cs.primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (aiming != null) BoosterIcon(aiming, 30.dp, fg, bg)
        else Canvas(Modifier.size(34.dp)) {
            val cw = size.width * 0.42f
            val ch = size.height * 0.62f
            val pivot = Offset(size.width / 2, size.height * 0.95f)
            for (deg in listOf(-24f, 0f, 24f)) rotate(deg, pivot) {
                val tl = Offset(size.width / 2 - cw / 2, size.height * 0.95f - ch)
                drawRoundRect(bg, tl, Size(cw, ch), CornerRadius(cw * 0.18f))
                drawRoundRect(fg, tl, Size(cw, ch), CornerRadius(cw * 0.18f), style = Stroke(size.width * 0.06f))
            }
            drawCircle(fg, size.width * 0.07f, Offset(size.width / 2, size.height * 0.95f - ch * 0.62f))
        }
    }
}

/**
 * Half-circle booster picker rising from the bottom: three boosters on the arc at a time,
 * swipe or arrows turn the wheel through all of them; tapping one picks it.
 */
@Composable
fun BoosterWheel(store: Store, bottomGap: androidx.compose.ui.unit.Dp, onPick: (String) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    store.rev
    val cs = MaterialTheme.colorScheme
    val n = BOOSTERS.size
    val pos = remember { Animatable(0f) } // index at the top of the arc; fractional while turning
    val scope = rememberCoroutineScope()
    fun settle(target: Float) {
        scope.launch { pos.animateTo(target.roundToInt().toFloat(), spring(stiffness = Spring.StiffnessMediumLow)) }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClose)
    ) {
        val w = maxWidth
        val h = w / 2
        val shape = RoundedCornerShape(topStart = h, topEnd = h)
        val slotPx = with(LocalDensity.current) { (w * 0.3f).toPx() }
        val density = LocalDensity.current
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = bottomGap).size(w, h)
                .clip(shape).background(cs.surface).border(2.dp, cs.primary, shape)
                .clickable(remember { MutableInteractionSource() }, null) {} // taps inside don't close
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragEnd = { settle(pos.value) }) { _, dx ->
                        scope.launch { pos.snapTo(pos.value - dx / slotPx) }
                    }
                }
        ) {
            val radius = w * 0.35f
            val slot = 104.dp
            for (i in 0 until n) {
                // distance from the top of the arc, wrapped so the wheel is endless
                var rel = (i - pos.value) % n
                if (rel > n / 2f) rel -= n
                if (rel < -n / 2f) rel += n
                if (abs(rel) > 1.6f) continue
                val a = ((90 - rel * 50) * PI / 180).toFloat()
                val x = w / 2 + radius * cos(a) - slot / 2
                val y = h - radius * sin(a) - slot / 2
                val near = 1f - minOf(1f, abs(rel))
                val b = BOOSTERS[i]
                val count = store.d.boosters[b.id] ?: 0
                Column(
                    Modifier.offset { with(density) { IntOffset(x.roundToPx(), y.roundToPx()) } }.width(slot)
                        .graphicsLayer {
                            val s = 0.75f + 0.25f * near
                            scaleX = s; scaleY = s
                            alpha = (1.6f - abs(rel)).coerceIn(0f, 1f)
                        }
                        .clip(RoundedCornerShape(16.dp)).clickable { onPick(b.id) }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(62.dp).clip(CircleShape)
                            .background(if (near > 0.5f) cs.primary.copy(alpha = 0.18f) else cs.surfaceVariant)
                            .border(if (near > 0.5f) 2.dp else 1.dp, cs.primary.copy(alpha = if (near > 0.5f) 1f else 0.35f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { BoosterIcon(b.id, 38.dp, back = cs.surface) }
                    // one line: a second line for the count ran into the description under the arc
                    Text("${b.name} ×$count", fontSize = if (b.name.length > 9) 10.sp else 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                        color = if (count > 0) cs.onSurface else cs.onSurface.copy(alpha = 0.5f))
                }
            }

            val centre = BOOSTERS[((pos.value.roundToInt() % n) + n) % n]
            Text(
                centre.desc, Modifier.align(Alignment.BottomCenter).width(w * 0.5f).padding(bottom = 8.dp),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = cs.onSurface.copy(alpha = 0.8f),
            )
            Arrow("‹", Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 8.dp)) { settle(pos.value - 1) }
            Arrow("›", Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 8.dp)) { settle(pos.value + 1) }
        }
    }
}

@Composable
private fun Arrow(glyph: String, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.size(44.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.15f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = cs.primary) }
}
