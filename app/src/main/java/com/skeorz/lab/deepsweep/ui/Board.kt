package com.skeorz.lab.deepsweep.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.skeorz.lab.deepsweep.game.BOOM
import com.skeorz.lab.deepsweep.game.CS
import com.skeorz.lab.deepsweep.game.FLAG
import com.skeorz.lab.deepsweep.game.HIDDEN
import com.skeorz.lab.deepsweep.game.Kind
import com.skeorz.lab.deepsweep.game.OPEN
import com.skeorz.lab.deepsweep.game.Fx
import com.skeorz.lab.deepsweep.game.Session
import com.skeorz.lab.deepsweep.meta.Store
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun Board(session: Session, store: Store, modifier: Modifier = Modifier) {
    store.rev
    val d = store.d
    val p = palette(d.theme)
    val nums = numberColors(d.numbers, p)
    val base = with(LocalDensity.current) { 38.dp.toPx() }
    val thin = with(LocalDensity.current) { 1.2.dp.toPx() }
    var scale by remember { mutableFloatStateOf(1f) }
    var cam by remember { mutableStateOf(Offset.Zero) }
    var sized by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var moved by remember { androidx.compose.runtime.mutableIntStateOf(0) } // bumped on every pan or zoom
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val vibe = remember { Vibe(context) }
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    }
    val world = session.world

    LaunchedEffect(session.boomSignal) {
        if (session.boomSignal > 0 && d.vibration) vibe.boom()
    }
    LaunchedEffect(session.buoySignal) {
        if (session.buoySignal > 0 && d.vibration) vibe.buoy()
    }

    fun tileAt(pos: Offset): Pair<Int, Int> {
        val s = base * scale
        return floor((pos.x + cam.x) / s).toInt() to floor((pos.y + cam.y) / s).toInt()
    }

    Box(modifier.clipToBounds()) {
    // a buoy needs a 0.5 s hold
    val base0 = androidx.compose.ui.platform.LocalViewConfiguration.current
    val buoyHold = remember(base0) { object : androidx.compose.ui.platform.ViewConfiguration by base0 { override val longPressTimeoutMillis = 500L } }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalViewConfiguration provides buoyHold) {
    Canvas(
        Modifier.fillMaxSize()
            .onSizeChanged {
                view = it
                if (!sized) {
                    sized = true
                    cam = Offset(CS / 2f * base - it.width / 2f, CS / 2f * base - it.height / 2f)
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val ns = (scale * zoom).coerceIn(0.075f, 1.3f)
                    val anchor = (centroid + cam) / scale
                    cam = anchor * ns - centroid - pan
                    scale = ns
                    moved++
                }
            }
            .pointerInput(d.longTapFlag, d.vibration) {
                detectTapGestures(
                    onTap = { val (x, y) = tileAt(it); session.tap(x, y) },
                    onLongPress = {
                        if (d.longTapFlag && session.booster == null) {
                            val (x, y) = tileAt(it)
                            session.secondary(x, y)

                        }
                    },
                )
            }
    ) {
        session.tick
        val s = base * scale
        val x0 = floor(cam.x / s).toInt()
        val y0 = floor(cam.y / s).toInt()
        val x1 = ceil((cam.x + size.width) / s).toInt()
        val y1 = ceil((cam.y + size.height) / s).toInt()
        val g = maxOf(1f, s * 0.05f)
        val corner = CornerRadius(s * 0.2f)
        val inner = Size(s - 2 * g, s - 2 * g)
        paint.textSize = s * 0.56f
        drawRect(p.bg)

        // zoomed far out: one merged map — open vs closed areas, their contour, vaults and locks
        if (s < FAR_TILE_PX) {
            drawOverview(world, cam, s, x0, y0, x1, y1, p, thin)
            session.fx?.let { f -> drawFx(f, session.fxT, Offset(f.chunk.x * CS * s - cam.x, f.chunk.y * CS * s - cam.y), CS * s, s, p) }
            return@Canvas
        }

        for (ty in y0..y1) for (tx in x0..x1) {
            val c = world.chunkAt(tx, ty)
            if (c.kind == Kind.WALL) continue
            val l = tx * s - cam.x
            val t = ty * s - cam.y
            val center = Offset(l + s / 2, t + s / 2)
            when (val st = world.state(tx, ty)) {
                HIDDEN, FLAG -> {
                    drawRoundRect(p.hidden, Offset(l + g, t + g), inner, corner)
                    drawRoundRect(p.hiddenHi.copy(alpha = 0.6f), Offset(l + g, t + g), Size(inner.width, inner.height * 0.42f), corner)
                    if (st == FLAG) drawFlag(d.flagSkin, center, s * 0.36f, p)
                    else if ((session.over || sonarShows(session, c, tx, ty)) && world.isMine(tx, ty)) drawMine(d.mineSkin, center, s * 0.32f, p.copyMineAlpha())
                }
                OPEN -> {
                    drawRect(if ((tx + ty) and 1 == 0) p.open else p.openAlt, Offset(l, t), Size(s, s))
                    val n = world.count(tx, ty)
                    if (n > 0 && s > 10f) drawIntoCanvas {
                        paint.color = nums[n - 1].toArgb()
                        it.nativeCanvas.drawText(n.toString(), center.x, center.y - (paint.descent() + paint.ascent()) / 2, paint)
                    }
                }
                BOOM -> {
                    drawRect(p.danger.copy(alpha = 0.5f), Offset(l, t), Size(s, s))
                    drawMine(d.mineSkin, center, s * 0.36f, p)
                }
            }
        }

        val cs = CS * s
        for (cy in Math.floorDiv(y0, CS)..Math.floorDiv(y1, CS)) for (cx in Math.floorDiv(x0, CS)..Math.floorDiv(x1, CS)) {
            val c = world.chunk(cx, cy)
            val tl = Offset(cx * cs - cam.x, cy * cs - cam.y)
            val mid = tl + Offset(cs / 2, cs / 2)
            if (c.kind == Kind.WALL) {
                drawRect(p.panel, tl, Size(cs, cs))
                for (i in 0 until CS * CS) {
                    drawCircle(p.line.copy(alpha = 0.25f), s * 0.07f, tl + Offset((i % CS + 0.5f) * s, (i / CS + 0.5f) * s))
                }
                continue
            }
            when {
                c.locked -> {
                    drawRect(p.danger.copy(alpha = 0.28f), tl, Size(cs, cs))
                    drawLock(mid, s * 0.9f, p.danger)
                    label(paint, "${world.solvedAround(c)}/8", mid + Offset(0f, s * 1.4f), s, p.text.toArgb())
                }
                !c.vaultOpen && session.fx?.let { it.kind == "lockpick" && it.chunk === c } == true ->
                    vaultCorners(tl, cs, s, p.vault, crack = session.fxT)
                !c.vaultOpen && session.aimingVault -> vaultCorners(tl, cs, s, p.vault)
                // closed vault: playable, but its bonus waits for 4 solved neighbours or the bathyscaphe
                !c.vaultOpen -> {
                    drawRect(p.vault.copy(alpha = 0.2f), tl, Size(cs, cs))
                    vaultCorners(tl, cs, s, p.vault)
                    vaultBadge(paint, tl, s, "🔒 ${world.solvedAround(c)}/8", p)
                }
                c.kind == Kind.VAULT && !c.vaultPaid -> vaultCorners(tl, cs, s, p.vault)
                c.solved -> drawRect(p.accent.copy(alpha = 0.07f), tl, Size(cs, cs))
            }
            val special = c.kind == Kind.SPECIAL
            drawRect(if (special) p.special else p.line.copy(alpha = 0.55f), tl, Size(cs, cs), style = Stroke(if (special) thin * 2.5f else thin))
            if (special && !c.solved) drawCrystal(tl + Offset(s * 0.35f, s * 0.35f), s * 0.22f, p.special)
        }

        drawLandmarks(world, cam, cs, Math.floorDiv(x0, CS), Math.floorDiv(y0, CS), Math.floorDiv(x1, CS), Math.floorDiv(y1, CS), thin, far = false)

        session.trawlFrom?.let { (fx0, fy0) ->
            val o = Offset(fx0 * s - cam.x, fy0 * s - cam.y)
            drawRect(p.accent.copy(alpha = 0.35f), o, Size(s, s))
            drawRect(p.accent, o, Size(s, s), style = Stroke(thin * 2))
            for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val c = o + Offset((dx + 0.5f) * s, (dy + 0.5f) * s)
                drawCircle(p.bg.copy(alpha = 0.8f), s * 0.42f, c)
                drawArrow(c, s * 0.3f, dx, dy, p.accent)
            }
        }

        session.fx?.let { f -> drawFx(f, session.fxT, Offset(f.chunk.x * cs - cam.x, f.chunk.y * cs - cam.y), cs, s, p) }
    }
    }

    SectorCoords(session, base * scale, cam, view, moved)
    Trackers(session, base * scale, cam, view, onJump = { target -> moved++; scope.launch {
        val from = cam
        androidx.compose.animation.core.animate(0f, 1f, animationSpec = androidx.compose.animation.core.tween(600)) { t, _ -> cam = from + (target - from) * t }
    } })
    }
}

/** Below this tile size the board switches to the merged overview map. */
private const val FAR_TILE_PX = 18f

/**
 * Far zoom: sectors merge into one map. Open (or solved) ground and closed ground get one
 * colour each, a contour line runs where they meet, vaults and locked sectors stand out.
 * A sector that is all open or all closed is drawn as a single rectangle.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOverview(
    world: com.skeorz.lab.deepsweep.game.World, cam: Offset, s: Float, x0: Int, y0: Int, x1: Int, y1: Int, p: Palette, thin: Float,
) {
    // Nothing here generates sectors: unvisited ones are closed ground, their kind comes from kindOf.
    val cs = CS * s // rects below are drawn 0.6px larger so merged sectors show no seams
    val cx0 = Math.floorDiv(x0, CS); val cx1 = Math.floorDiv(x1, CS)
    val cy0 = Math.floorDiv(y0, CS); val cy1 = Math.floorDiv(y1, CS)
    val line = p.accent.copy(alpha = 0.9f)
    val stroke = maxOf(thin * 1.6f, s * 0.22f)
    val full = Size(cs + 0.6f, cs + 0.6f)
    val tile = Size(s + 0.5f, s + 0.5f)

    fun existing(cx: Int, cy: Int) = world.chunks[com.skeorz.lab.deepsweep.game.packKey(cx, cy)]
    fun openAt(tx: Int, ty: Int): Boolean {
        val c = existing(Math.floorDiv(tx, CS), Math.floorDiv(ty, CS)) ?: return false
        return c.kind != Kind.WALL && (c.solved || c.state[Math.floorMod(ty, CS) * CS + Math.floorMod(tx, CS)] == OPEN)
    }

    drawRect(p.hidden)
    for (cy in cy0..cy1) for (cx in cx0..cx1) {
        val tl = Offset(cx * cs - cam.x, cy * cs - cam.y)
        val c = existing(cx, cy)
        if ((c?.kind ?: world.kindOf(cx, cy)) == Kind.WALL) { drawRect(p.panel, tl, full); continue }
        if (c == null) continue
        if (c.solved) { drawRect(p.open, tl, full); continue }
        for (i in 0 until com.skeorz.lab.deepsweep.game.CN) if (c.state[i] == OPEN) drawRect(p.open, tl + Offset((i % CS) * s, (i / CS) * s), tile)
    }

    // contour, from the open side: an edge wherever an open tile touches a closed one
    for (cy in cy0..cy1) for (cx in cx0..cx1) {
        val c = existing(cx, cy) ?: continue
        if (c.kind == Kind.WALL) continue
        for (i in 0 until com.skeorz.lab.deepsweep.game.CN) {
            if (!c.solved && c.state[i] != OPEN) continue
            val tx = cx * CS + i % CS
            val ty = cy * CS + i / CS
            val l = tx * s - cam.x
            val t = ty * s - cam.y
            if (!openAt(tx + 1, ty)) drawLine(line, Offset(l + s, t), Offset(l + s, t + s), stroke)
            if (!openAt(tx - 1, ty)) drawLine(line, Offset(l, t), Offset(l, t + s), stroke)
            if (!openAt(tx, ty + 1)) drawLine(line, Offset(l, t + s), Offset(l + s, t + s), stroke)
            if (!openAt(tx, ty - 1)) drawLine(line, Offset(l, t), Offset(l + s, t), stroke)
        }
    }

    drawLandmarks(world, cam, cs, cx0, cy0, cx1, cy1, thin, far = true)

    // the starting sector (0, 0): theme-coloured frame with a flag
    if (world.started) {
        val st = Offset(world.ox * cs - cam.x, world.oy * cs - cam.y)
        drawRect(p.accent.copy(alpha = 0.25f), st, Size(cs, cs))
        drawRect(p.accent, st, Size(cs, cs), style = Stroke(maxOf(thin * 2.5f, cs * 0.08f)))
        val pole = st + Offset(cs * 0.38f, cs * 0.2f)
        drawLine(p.text, pole, pole + Offset(0f, cs * 0.6f), maxOf(thin, cs * 0.05f))
        drawPath(androidx.compose.ui.graphics.Path().apply {
            moveTo(pole.x, pole.y); lineTo(pole.x + cs * 0.34f, pole.y + cs * 0.13f); lineTo(pole.x, pole.y + cs * 0.26f); close()
        }, p.accent)
    }

    for (cy in cy0..cy1) for (cx in cx0..cx1) {
        val c = existing(cx, cy)
        val tl = Offset(cx * cs - cam.x, cy * cs - cam.y)
        val mid = tl + Offset(cs / 2, cs / 2)
        when {
            c?.locked == true -> {
                drawRect(p.danger.copy(alpha = 0.45f), tl, full)
                drawLock(mid, cs * 0.22f, p.danger)
            }
            (c?.kind ?: world.kindOf(cx, cy)) == Kind.VAULT && c?.vaultPaid != true -> {
                drawRect(p.vault.copy(alpha = 0.45f), tl, full)
                drawRect(p.vault, tl, Size(cs, cs), style = Stroke(maxOf(thin * 2f, cs * 0.06f)))
                drawCrystal(mid, cs * 0.3f, if (c?.vaultOpen == true) p.accent else p.vault)
            }
        }
    }
}

private val LANDMARK_COLORS = mapOf(
    com.skeorz.lab.deepsweep.game.LandmarkType.SHELL to Color(0xFFFFB4A2),
    com.skeorz.lab.deepsweep.game.LandmarkType.REEF to Color(0xFFFF7F6E),
    com.skeorz.lab.deepsweep.game.LandmarkType.WRECK to Color(0xFFD4A373),
    com.skeorz.lab.deepsweep.game.LandmarkType.ANCHOR to Color(0xFFA8C5DA),
    com.skeorz.lab.deepsweep.game.LandmarkType.STAR to Color(0xFFFFA94D),
)

/**
 * Landmarks in the visible sectors: a picture over the whole shape and an outline round it.
 * In the normal view the picture is faint so the tiles stay readable; [far] = overview.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLandmarks(
    world: com.skeorz.lab.deepsweep.game.World, cam: Offset, cs: Float, cx0: Int, cy0: Int, cx1: Int, cy1: Int, thin: Float, far: Boolean,
) {
    val z = com.skeorz.lab.deepsweep.game.ZONE
    val rx0 = Math.floorDiv(cx0 - world.ox, z); val rx1 = Math.floorDiv(cx1 - world.ox, z)
    val ry0 = Math.floorDiv(cy0 - world.oy, z); val ry1 = Math.floorDiv(cy1 - world.oy, z)
    for (ry in ry0..ry1) for (rx in rx0..rx1) {
        val l = world.landmark(rx, ry) ?: continue
        val done = l.key in world.landmarksDone
        val color = LANDMARK_COLORS.getValue(l.type)
        val tl = Offset(l.x * cs - cam.x, l.y * cs - cam.y)
        if (far || done) for ((dx, dy) in l.type.shape)
            drawRect(color.copy(alpha = if (done) 0.22f else 0.12f), tl + Offset(dx * cs, dy * cs), Size(cs + 0.6f, cs + 0.6f))
        drawLandmarkArt(l.type, tl, cs, color.copy(alpha = when { far -> 0.85f; done -> 0.45f; else -> 0.22f }))
        // outline of the union of its sectors
        val w = maxOf(thin * 2.5f, cs * 0.05f)
        val edge = color.copy(alpha = if (far) 1f else 0.85f)
        for ((dx, dy) in l.type.shape) {
            val o = tl + Offset(dx * cs, dy * cs)
            if (!l.has(l.x + dx, l.y + dy - 1)) drawLine(edge, o, o + Offset(cs, 0f), w)
            if (!l.has(l.x + dx, l.y + dy + 1)) drawLine(edge, o + Offset(0f, cs), o + Offset(cs, cs), w)
            if (!l.has(l.x + dx - 1, l.y + dy)) drawLine(edge, o, o + Offset(0f, cs), w)
            if (!l.has(l.x + dx + 1, l.y + dy)) drawLine(edge, o + Offset(cs, 0f), o + Offset(cs, cs), w)
        }
        if (done) drawCrystal(tl + Offset(cs * 0.35f, cs * 0.35f), cs * 0.15f, color)
    }
}

/** The picture of a landmark, fitted to its shape's bounding box (top-left [tl], sector size [cs]). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLandmarkArt(
    type: com.skeorz.lab.deepsweep.game.LandmarkType, tl: Offset, cs: Float, color: Color,
) {
    val w = type.w * cs
    val h = type.h * cs
    val c = tl + Offset(w / 2, h / 2)
    val st = cs * 0.06f
    when (type) {
        com.skeorz.lab.deepsweep.game.LandmarkType.SHELL -> { // scallop: a fan with ribs
            val base = Offset(c.x, tl.y + h * 0.9f)
            val r = w * 0.46f
            drawArc(color, 200f, 140f, true, Offset(c.x - r, base.y - r * 1.55f), Size(r * 2, r * 2))
            for (k in 0..6) {
                val a = Math.toRadians(205.0 + k * 21.5)
                drawLine(color.copy(alpha = color.alpha * 0.6f), base,
                    Offset(c.x + (r * kotlin.math.cos(a)).toFloat(), base.y - r * 0.55f + (r * kotlin.math.sin(a)).toFloat()), st)
            }
            drawRect(color, Offset(c.x - w * 0.12f, base.y - h * 0.08f), Size(w * 0.24f, h * 0.1f))
        }
        com.skeorz.lab.deepsweep.game.LandmarkType.REEF -> { // coral branches rising from a rocky base
            drawRoundRect(color, Offset(tl.x + w * 0.05f, tl.y + h * 0.78f), Size(w * 0.9f, h * 0.17f),
                androidx.compose.ui.geometry.CornerRadius(h * 0.08f))
            val tips = listOf(0.12f to 0.25f, 0.28f to 0.1f, 0.44f to 0.32f, 0.58f to 0.15f, 0.74f to 0.4f, 0.88f to 0.22f)
            for ((fx, fy) in tips) {
                val bottom = Offset(tl.x + w * fx, tl.y + h * 0.8f)
                val top = Offset(tl.x + w * fx, tl.y + h * fy)
                drawLine(color, bottom, top, st * 2.2f)
                drawLine(color, Offset(top.x, (top.y + bottom.y) / 2), Offset(top.x + w * 0.06f, top.y + h * 0.1f), st * 1.6f)
                drawCircle(color, st * 2.4f, top)
            }
        }
        com.skeorz.lab.deepsweep.game.LandmarkType.WRECK -> { // sunken ship: hull, mast, torn sail, portholes
            val deck = tl.y + h * 0.62f
            drawPath(androidx.compose.ui.graphics.Path().apply {
                moveTo(tl.x + w * 0.04f, deck); lineTo(tl.x + w * 0.96f, deck - h * 0.06f)
                lineTo(tl.x + w * 0.82f, tl.y + h * 0.92f); lineTo(tl.x + w * 0.14f, tl.y + h * 0.92f); close()
            }, color)
            drawLine(color, Offset(c.x, deck), Offset(c.x, tl.y + h * 0.08f), st * 1.6f)
            drawPath(androidx.compose.ui.graphics.Path().apply {
                moveTo(c.x + st, tl.y + h * 0.12f); lineTo(c.x + w * 0.16f, tl.y + h * 0.3f); lineTo(c.x + st, tl.y + h * 0.52f); close()
            }, color.copy(alpha = color.alpha * 0.7f))
            for (k in 0..3) drawCircle(Color.Black.copy(alpha = color.alpha * 0.8f), st * 1.4f, Offset(tl.x + w * (0.28f + k * 0.15f), deck + h * 0.12f))
        }
        com.skeorz.lab.deepsweep.game.LandmarkType.ANCHOR -> {
            drawCircle(color, w * 0.09f, Offset(c.x, tl.y + h * 0.12f), style = Stroke(st * 1.6f))
            drawLine(color, Offset(c.x, tl.y + h * 0.2f), Offset(c.x, tl.y + h * 0.86f), st * 2.4f)
            drawLine(color, Offset(c.x - w * 0.2f, tl.y + h * 0.3f), Offset(c.x + w * 0.2f, tl.y + h * 0.3f), st * 2f)
            drawArc(color, 20f, 140f, false, Offset(c.x - w * 0.36f, tl.y + h * 0.42f), Size(w * 0.72f, h * 0.46f), style = Stroke(st * 2.4f))
            for (sx in listOf(-1f, 1f)) {
                val tip = Offset(c.x + sx * w * 0.33f, tl.y + h * 0.7f)
                drawLine(color, tip, tip + Offset(-sx * w * 0.02f, -h * 0.09f), st * 2f)
                drawLine(color, tip, tip + Offset(sx * w * 0.08f, -h * 0.02f), st * 2f)
            }
        }
        com.skeorz.lab.deepsweep.game.LandmarkType.STAR -> { // five-armed starfish
            val path = androidx.compose.ui.graphics.Path()
            for (k in 0 until 10) {
                val r = if (k % 2 == 0) w * 0.46f else w * 0.2f
                val a = -Math.PI / 2 + k * Math.PI / 5
                val pt = Offset(c.x + (r * kotlin.math.cos(a)).toFloat(), c.y + (r * kotlin.math.sin(a)).toFloat())
                if (k == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
            }
            path.close()
            drawPath(path, color)
            for (k in 0 until 5) {
                val a = -Math.PI / 2 + k * 2 * Math.PI / 5
                for (d in 1..3) drawCircle(Color.White.copy(alpha = color.alpha * 0.5f), st,
                    Offset(c.x + (w * 0.1f * d * kotlin.math.cos(a)).toFloat(), c.y + (w * 0.1f * d * kotlin.math.sin(a)).toFloat()))
            }
        }
    }
}

/** Sonar (id "flash"): a mine shows once the rotating beam has passed it. */
private fun sonarShows(session: Session, c: com.skeorz.lab.deepsweep.game.Chunk, tx: Int, ty: Int): Boolean {
    if (session.flash !== c) return false
    val f = session.fx ?: return true
    val sweep = (session.fxT / 0.3f).coerceAtMost(1f) * 360f
    val dx = (tx - c.x * CS) + 0.5f - CS / 2f
    val dy = (ty - c.y * CS) + 0.5f - CS / 2f
    val a = ((Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())) + 450) % 360).toFloat()
    return f.kind == "flash" && a <= sweep
}

/** Small "🔒 n/8" tag in a closed vault's top-left corner, off the tiles' numbers. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.vaultBadge(paint: Paint, tl: Offset, s: Float, text: String, p: Palette) {
    if (s < 8f) return
    drawIntoCanvas {
        val old = paint.textSize
        paint.textSize = s * 0.42f
        val w = paint.measureText(text) + s * 0.3f
        val box = tl + Offset(s * 0.4f, s * 0.4f)
        drawRoundRect(p.vault.copy(alpha = 0.85f), box, Size(w, s * 0.6f), CornerRadius(s * 0.2f))
        paint.color = p.bg.toArgb()
        it.nativeCanvas.drawText(text, box.x + w / 2, box.y + s * 0.44f, paint)
        paint.textSize = old
    }
}

/** Vault corner brackets; [crack] from 0 to 1 splits them apart and fades them out. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.vaultCorners(tl: Offset, cs: Float, s: Float, color: Color, crack: Float = -1f) {
    val len = s * 1.6f
    val w = s * 0.32f
    val open = if (crack < 0) 0f else crack
    val shift = s * 1.2f * open
    val alpha = if (crack < 0) 0.45f else 0.6f * (1f - open)
    val col = color.copy(alpha = alpha)
    for ((sx, sy) in listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)) {
        val ox = tl.x + sx * cs + (if (sx == 0) -shift else shift)
        val oy = tl.y + sy * cs + (if (sy == 0) -shift else shift)
        val hx = if (sx == 0) ox else ox - len
        val vy = if (sy == 0) oy else oy - len
        // a crack in the middle of each arm while it splits
        val gap = if (crack < 0) 0f else len * 0.12f * open
        drawRect(col, Offset(hx, if (sy == 0) oy else oy - w), Size(len / 2 - gap, w))
        drawRect(col, Offset(hx + len / 2 + gap, if (sy == 0) oy else oy - w), Size(len / 2 - gap, w))
        drawRect(col, Offset(if (sx == 0) ox else ox - w, vy), Size(w, len / 2 - gap))
        drawRect(col, Offset(if (sx == 0) ox else ox - w, vy + len / 2 + gap), Size(w, len / 2 - gap))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrow(c: Offset, r: Float, dx: Int, dy: Int, color: Color) {
    val tip = c + Offset(dx * r, dy * r)
    val back = c - Offset(dx * r, dy * r)
    val side = Offset(-dy * r * 0.6f, dx * r * 0.6f)
    drawLine(color, back, tip, strokeWidth = r * 0.3f)
    drawLine(color, tip, tip - Offset(dx * r * 0.6f, dy * r * 0.6f) + side, strokeWidth = r * 0.3f)
    drawLine(color, tip, tip - Offset(dx * r * 0.6f, dy * r * 0.6f) - side, strokeWidth = r * 0.3f)
}

/** Booster animations drawn over sector [tl] (size [cs]); [t] runs from 0 to 1. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFx(f: Fx, t: Float, tl: Offset, cs: Float, s: Float, p: Palette) {
    val mid = tl + Offset(cs / 2, cs / 2)
    when (f.kind) {
        "sonar" -> { // periscope: a see-through lens narrowing onto the target tile
            val target = tl + Offset((f.arg % CS + 0.5f) * s, (f.arg / CS + 0.5f) * s)
            val e = 1f - (1f - t) * (1f - t)
            val c = mid + (target - mid) * e
            val r = cs * 0.75f * (1f - e) + s * 0.55f * e
            drawCircle(p.accent.copy(alpha = 0.18f), r, c)
            drawCircle(p.accent.copy(alpha = 0.8f), r, c, style = Stroke(s * 0.12f))
            drawLine(p.accent.copy(alpha = 0.6f), c - Offset(r, 0f), c + Offset(r, 0f), strokeWidth = s * 0.05f)
            drawLine(p.accent.copy(alpha = 0.6f), c - Offset(0f, r), c + Offset(0f, r), strokeWidth = s * 0.05f)
        }
        "echo" -> { // rings spreading from the centre to the edges, then the found window lights up
            for (k in 0..2) {
                val tt = t * 1.3f - k * 0.18f
                if (tt !in 0f..1f) continue
                drawCircle(p.accent.copy(alpha = 0.7f * (1f - tt)), cs * 0.72f * tt, mid, style = Stroke(s * 0.18f))
            }
            if (t > 0.6f) drawRect(p.accent.copy(alpha = 0.35f * (t - 0.6f) / 0.4f),
                tl + Offset((f.arg % CS) * s, (f.arg / CS) * s), Size(s * 3, s * 3))
        }
        "solve" -> { // torpedo: a flash and a blast wave from the centre
            val r = cs * 0.8f * t
            drawCircle(Color(0xFFFFE08A).copy(alpha = (1f - t) * 0.9f), r * 0.55f, mid)
            drawCircle(Color(0xFFFF7A3D).copy(alpha = (1f - t) * 0.6f), r, mid)
            drawCircle(Color.White.copy(alpha = (1f - t)), r, mid, style = Stroke(s * 0.25f * (1f - t) + 1f))
        }
        "flash" -> { // sonar beam going round once, then the mines stay visible
            val sweep = (t / 0.3f).coerceAtMost(1f) * 360f
            if (sweep < 360f) {
                drawArc(p.accent.copy(alpha = 0.25f), sweep - 90f - 60f, 60f, true, mid - Offset(cs * 0.71f, cs * 0.71f), Size(cs * 1.42f, cs * 1.42f))
                val a = Math.toRadians((sweep - 90f).toDouble())
                drawLine(p.accent, mid, mid + Offset((cs * 0.71f * kotlin.math.cos(a)).toFloat(), (cs * 0.71f * kotlin.math.sin(a)).toFloat()), strokeWidth = s * 0.12f)
            }
            drawRect(p.accent.copy(alpha = 0.06f), tl, Size(cs, cs))
        }
    }
}

private fun Palette.copyMineAlpha() = Palette(bg, hidden, hiddenHi, open, openAlt, line, accent, text, panel,
    mine.copy(alpha = 0.6f), special, vault, danger)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.label(paint: Paint, text: String, at: Offset, s: Float, color: Int) {
    if (s < 8f) return
    drawIntoCanvas {
        val old = paint.textSize
        paint.textSize = s * 0.6f
        paint.color = color
        it.nativeCanvas.drawText(text, at.x, at.y, paint)
        paint.textSize = old
    }
}
