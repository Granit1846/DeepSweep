package com.skeorz.lab.deepsweep.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skeorz.lab.deepsweep.game.CS
import com.skeorz.lab.deepsweep.game.Kind
import com.skeorz.lab.deepsweep.game.OPEN
import com.skeorz.lab.deepsweep.game.LandmarkType
import com.skeorz.lab.deepsweep.game.Session
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt

/** Something worth coming back to, at sector ([cx], [cy]). */
private class Target(val icon: String, val color: Color, val cx: Int, val cy: Int)

private const val NEAREST = 3 // pointers per kind of target

private val LANDMARK_ICONS = mapOf(
    LandmarkType.SHELL to "🐚", LandmarkType.REEF to "🪸", LandmarkType.WRECK to "🚢",
    LandmarkType.ANCHOR to "⚓", LandmarkType.STAR to "⭐",
)

/**
 * Edge-of-screen pointers to off-screen targets: the start, sectors locked by a mine, solved
 * vaults still waiting for their neighbours and started landmarks (3 nearest of each). Tapping one moves
 * the camera there ([onJump] gets the camera offset that centres it).
 */
@Composable
fun Trackers(session: Session, tilePx: Float, cam: Offset, view: IntSize, onJump: (Offset) -> Unit) {
    session.tick
    val w = session.world
    if (!w.started || view == IntSize.Zero) return
    val density = LocalDensity.current
    val top = with(density) { 150.dp.toPx() }
    val bottom = view.height - with(density) { 100.dp.toPx() }
    val side = with(density) { 26.dp.toPx() }
    val cs = CS * tilePx
    val centre = Offset(view.width / 2f, (top + bottom) / 2f)
    val ccx = Math.floorDiv(((cam.x + centre.x) / cs).roundToInt(), 1)
    val ccy = Math.floorDiv(((cam.y + centre.y) / cs).roundToInt(), 1)
    fun near(cx: Int, cy: Int) = max(abs(cx - ccx), abs(cy - ccy))

    // only things waiting on the player, the 3 nearest of each kind
    val targets = ArrayList<Target>()
    targets += Target("🏁", Color(0xFF3FE0D0), w.ox, w.oy)
    w.chunks.values.filter { it.locked }.sortedBy { near(it.x, it.y) }.take(NEAREST)
        .forEach { targets += Target("🔒", Color(0xFFFF4D5E), it.x, it.y) }
    // a vault already solved whose neighbours have not opened it yet
    w.chunks.values.filter { it.kind == Kind.VAULT && it.solved && !it.vaultOpen }.sortedBy { near(it.x, it.y) }.take(NEAREST)
        .forEach { targets += Target("💎", Color(0xFFFFC857), it.x, it.y) }
    // a landmark that has been started but not finished
    w.chunks.values.asSequence().filter { c -> c.solved || c.state.any { it == OPEN } }
        .mapNotNull { w.landmarkAt(it.x, it.y) }.distinct().filter { it.key !in w.landmarksDone }
        .sortedBy { near(it.x + it.type.w / 2, it.y + it.type.h / 2) }.take(NEAREST)
        .forEach { targets += Target(LANDMARK_ICONS.getValue(it.type), Color(0xFFFFB4A2), it.x + it.type.w / 2, it.y + it.type.h / 2) }

    val sizePx = with(density) { 52.dp.toPx() }
    for (t in targets) {
        val pos = Offset((t.cx + 0.5f) * cs - cam.x, (t.cy + 0.5f) * cs - cam.y)
        if (pos.x in side..view.width - side && pos.y in top..bottom) continue // on screen: no pointer
        // where the ray from the centre to the target leaves the visible area
        val d = pos - centre
        val kx = if (d.x == 0f) Float.MAX_VALUE else (if (d.x > 0) view.width - side - centre.x else side - centre.x) / d.x
        val ky = if (d.y == 0f) Float.MAX_VALUE else (if (d.y > 0) bottom - centre.y else top - centre.y) / d.y
        val k = minOf(kx, ky)
        val at = centre + d * k
        val angle = Math.toDegrees(atan2(d.y, d.x).toDouble()).toFloat()
        val dist = near(t.cx, t.cy)
        Pointer(t, angle, dist, Modifier.offset { IntOffset((at.x - sizePx / 2).roundToInt(), (at.y - sizePx / 2).roundToInt()) }) {
            onJump(Offset((t.cx + 0.5f) * cs - centre.x, (t.cy + 0.5f) * cs - centre.y))
        }
    }
}

@Composable
private fun Pointer(t: Target, angle: Float, dist: Int, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(modifier.size(52.dp), contentAlignment = Alignment.Center) {
        // arrow ring pointing at the target
        Canvas(Modifier.size(52.dp)) {
            rotate(angle) {
                val r = size.minDimension / 2
                drawPath(Path().apply {
                    moveTo(center.x + r, center.y)
                    lineTo(center.x + r * 0.62f, center.y - r * 0.32f)
                    lineTo(center.x + r * 0.62f, center.y + r * 0.32f)
                    close()
                }, t.color)
            }
        }
        Column(
            Modifier.size(40.dp).clip(CircleShape).background(cs.surface.copy(alpha = 0.92f))
                .border(1.5.dp, t.color, CircleShape).clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(t.icon, fontSize = 14.sp, lineHeight = 15.sp)
            Text("$dist", fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, color = t.color)
        }
    }
}

/**
 * «Сектор X, Y» of the sector in the middle of the view, under the top bar. It shows up while
 * the board is being moved ([moved] changes) and fades out 3 seconds after the last move.
 */
@Composable
fun SectorCoords(session: Session, tilePx: Float, cam: Offset, view: IntSize, moved: Int) {
    val w = session.world
    var shown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(moved) {
        if (moved == 0) return@LaunchedEffect
        shown = true
        kotlinx.coroutines.delay(3000)
        shown = false
    }
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (shown && w.started) 1f else 0f, label = "coords")
    if (alpha == 0f || view == IntSize.Zero) return
    val density = LocalDensity.current
    val cs = CS * tilePx
    val top = with(density) { 150.dp.toPx() }
    val bottom = view.height - with(density) { 100.dp.toPx() }
    val cx = kotlin.math.floor((cam.x + view.width / 2f) / cs).toInt()
    val cy = kotlin.math.floor((cam.y + (top + bottom) / 2f) / cs).toInt()
    val (x, y) = w.coords(cx, cy)
    val cs2 = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(top = 152.dp), contentAlignment = Alignment.TopCenter) {
        Text(
            "Сектор $x, $y",
            Modifier.graphicsLayer { this.alpha = alpha }.clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .background(cs2.surface.copy(alpha = 0.92f)).border(1.dp, cs2.primary.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            color = cs2.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold,
        )
    }
}
