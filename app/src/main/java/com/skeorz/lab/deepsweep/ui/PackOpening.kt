package com.skeorz.lab.deepsweep.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material3.TextButton
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import com.skeorz.lab.deepsweep.meta.PACK_COLUMNS
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.skeorz.lab.deepsweep.meta.BOOSTERS
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

private const val CARD_RATIO = 1.35f

/** Sealed packs counter next to the booster fan; opens the oldest pack. */
@Composable
fun PackButton(count: Int, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(width = 58.dp, height = 46.dp)) {
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(cs.surfaceVariant)
                .border(1.dp, Color(0xFFFFC857).copy(alpha = 0.7f), RoundedCornerShape(12.dp)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Canvas(Modifier.size(width = 26.dp, height = 34.dp)) { drawBag(Offset.Zero, size, cs.primary, tornAt = -1f) } }
        Text(
            "$count", Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).clip(CircleShape)
                .background(Color(0xFFFF4D5E)).padding(horizontal = 6.dp, vertical = 1.dp),
            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Foil bag of size [s] at [tl], with crimped edges and the booster-fan label. [tornAt] in 0..1
 * is how far the top strip is torn (left to right, along a zigzag); -1 draws it sealed.
 * [stripLift] 0..1 swings the torn strip away.
 */
private fun DrawScope.drawBag(tl: Offset, s: Size, accent: Color, tornAt: Float, stripLift: Float = 0f) {
    val w = s.width
    val h = s.height
    val tooth = w / 14
    val tearY = tl.y + h * 0.15f
    val foil = Brush.linearGradient(
        listOf(Color(0xFF1B5E85), Color(0xFF7FD6E8), Color(0xFF1B4F72), Color(0xFF9EE7F2), Color(0xFF174563)),
        start = tl, end = tl + Offset(w, h),
    )

    // tear edge: zigzag where already torn, straight seal further right
    fun Path.tearEdge(forward: Boolean) {
        val split = tl.x + w * tornAt.coerceIn(0f, 1f)
        val xs = generateSequence(tl.x) { it + tooth }.takeWhile { it <= tl.x + w }.toList() + (tl.x + w)
        val pts = xs.mapIndexed { k, x -> Offset(x, if (tornAt >= 0f && x <= split && k % 2 == 1) tearY + tooth / 2 else tearY) }
        for (p in if (forward) pts else pts.reversed()) lineTo(p.x, p.y)
    }

    val body = Path().apply {
        moveTo(tl.x, tearY)
        tearEdge(forward = true)
        lineTo(tl.x + w, tl.y + h)
        var x = tl.x + w
        var k = 0
        while (x > tl.x) { x = maxOf(tl.x, x - tooth); lineTo(x, tl.y + h - if (k % 2 == 0) tooth / 2 else 0f); k++ }
        close()
    }
    drawPath(body, foil)
    for (k in 0..3) {
        val y = tl.y + h * (0.9f + k * 0.022f)
        drawLine(Color.White.copy(alpha = 0.25f), Offset(tl.x, y), Offset(tl.x + w, y), strokeWidth = w * 0.006f)
    }
    drawRect(Color.White.copy(alpha = 0.18f), tl + Offset(w * 0.12f, h * 0.2f), Size(w * 0.08f, h * 0.65f))
    val c = tl + Offset(w / 2, h * 0.54f)
    drawCircle(Color(0xFF061423).copy(alpha = 0.85f), w * 0.3f, c)
    drawCircle(accent, w * 0.3f, c, style = Stroke(w * 0.025f))
    val cw = w * 0.17f
    val ch = w * 0.25f
    for (deg in listOf(-24f, 0f, 24f)) rotate(deg, c + Offset(0f, ch * 0.55f)) {
        drawRoundRect(Color(0xFF061423), c + Offset(-cw / 2, -ch * 0.45f), Size(cw, ch), CornerRadius(cw * 0.2f))
        drawRoundRect(accent, c + Offset(-cw / 2, -ch * 0.45f), Size(cw, ch), CornerRadius(cw * 0.2f), style = Stroke(w * 0.02f))
    }

    if (stripLift >= 1f) return
    val strip = Path().apply {
        moveTo(tl.x, tearY)
        var x = tl.x
        var k = 0
        while (x < tl.x + w) { x = minOf(tl.x + w, x + tooth); lineTo(x, tl.y + if (k % 2 == 0) tooth / 2 else 0f); k++ }
        lineTo(tl.x + w, tearY)
        tearEdge(forward = false)
        close()
    }
    val pivot = Offset(tl.x + w, tearY)
    val angle = if (tornAt < 0f) 0f else -12f * tornAt - 70f * stripLift
    rotate(angle, pivot) {
        translate(w * 0.5f * stripLift, -h * 0.35f * stripLift) {
            drawPath(strip, foil, alpha = 1f - stripLift)
            if (tornAt < 0f) drawLine(Color.White.copy(alpha = 0.4f), Offset(tl.x + w * 0.05f, tearY), Offset(tl.x + w * 0.95f, tearY),
                strokeWidth = w * 0.008f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.03f, w * 0.02f)))
        }
    }
}

/**
 * Pack opening: a sealed foil bag the player tears open like a bag of chips; then the bag
 * drops away and the cards leave the deck ([PackReveal]). [onTear] runs when the bag is torn
 * (that is when its boosters reach the inventory); «Позже» keeps the pack sealed.
 */
@Composable
fun PackOpening(
    cards: List<String>, onTear: () -> Unit, onDone: () -> Unit,
    autoTear: Boolean = false, more: Int = 0, onMore: () -> Unit = {}, onAll: () -> Unit = {},
) {
    var stage by remember { mutableStateOf(if (autoTear) 1 else 0) } // 0 sealed, 1 tearing, 2 cards
    val tear = remember { Animatable(0f) }
    val cs = MaterialTheme.colorScheme
    LaunchedEffect(stage) {
        if (stage == 1) {
            onTear()
            tear.animateTo(1f, tween(1200, easing = LinearEasing))
            stage = 2
        }
    }
    if (stage == 2) { PackReveal(cards, onDone, more, onMore, onAll); return }
    BackHandler { if (stage == 0) onDone() }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xE6030A12)).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        val bagW = minOf(maxWidth * 0.62f, 260.dp)
        val bagH = bagW * 1.45f
        val t = tear.value
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Пак бустеров", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("внутри ${cards.size} ${com.skeorz.lab.deepsweep.meta.plural(cards.size.toLong(), "бустер", "бустера", "бустеров")}",
                color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))
            Canvas(
                Modifier.size(bagW, bagH).graphicsLayer {
                    val drop = ((t - 0.65f) / 0.35f).coerceIn(0f, 1f) // the body falls away once the strip is off
                    translationY = drop * size.height * 0.7f
                    alpha = 1f - drop
                }.pointerInput(Unit) { detectTapGestures { if (stage == 0) stage = 1 } }
            ) {
                val tornAt = if (stage == 0) -1f else (t / 0.45f).coerceAtMost(1f)
                drawBag(Offset.Zero, size, cs.primary, tornAt, ((t - 0.45f) / 0.3f).coerceIn(0f, 1f))
            }
            Spacer(Modifier.height(28.dp))
            if (stage == 0) {
                Button(onClick = { stage = 1 }, Modifier.fillMaxWidth(0.6f).height(52.dp)) {
                    Text("Вскрыть", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDone) { Text("Позже", color = Color.White.copy(alpha = 0.7f)) }
            } else Spacer(Modifier.height(100.dp))
        }
    }
}

/**
 * Booster pack reveal: cards leave the deck one by one into a 3-column grid,
 * then flip in a wave to show what was inside. Cards keep their size; the grid scrolls
 * and follows the falling cards.
 */
@Composable
private fun PackReveal(cards: List<String>, onDone: () -> Unit, more: Int, onMore: () -> Unit, onAll: () -> Unit) {
    val n = cards.size
    val cols = PACK_COLUMNS
    val fall = remember { List(n) { Animatable(0f) } }
    val flip = remember { List(n) { Animatable(0f) } }
    var finished by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        coroutineScope {
            cards.indices.map { i ->
                launch { delay(i * 90L); fall[i].animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
            }.joinAll()
        }
        delay(300)
        coroutineScope {
            cards.indices.map { i -> launch { delay(i * 45L); flip[i].animateTo(180f, tween(450)) } }.joinAll()
        }
        finished = true
    }
    BackHandler { if (finished) onDone() }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xE6030A12)).pointerInput(Unit) { detectTapGestures { } }) {
        val side = 16.dp
        val gap = if (cols == 6) 6.dp else 10.dp
        val cardW = min((maxWidth - side * 2 - gap * (cols - 1)) / cols, 110.dp)
        val cardH = cardW * CARD_RATIO
        val rows = (n + cols - 1) / cols
        val header = 56.dp
        val deckH = 110.dp
        val deckW = deckH / CARD_RATIO
        val deckX = (maxWidth - deckW) / 2
        val gridTop = header + deckH + 24.dp
        val gridLeft = (maxWidth - (cardW * cols + gap * (cols - 1))) / 2
        val footer = 150.dp
        val viewport = maxHeight

        // keep the row the cards are landing in on screen
        LaunchedEffect(Unit) {
            snapshotFlow { fall.count { it.value > 0f } }.collect { k ->
                if (k == 0) return@collect
                val rowBottom = gridTop + (cardH + gap) * ((k - 1) / cols + 1)
                val target = with(density) { (rowBottom - viewport + footer).roundToPx() }
                if (target > scroll.value) scroll.animateScrollTo(target)
            }
        }

        Box(Modifier.fillMaxWidth().verticalScroll(scroll).height(gridTop + (cardH + gap) * rows + footer)) {
            Text("Пак бустеров · $n", Modifier.fillMaxWidth().padding(top = 16.dp), textAlign = TextAlign.Center,
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)

            val inDeck = fall.count { it.value == 0f }
            for (k in (minOf(inDeck, 4) - 1) downTo 0) {
                CardBack(Modifier.offset(deckX + (k * 3).dp, header + (k * 3).dp).size(deckW, deckH))
            }

            cards.forEachIndexed { i, id ->
                val p = fall[i].value
                if (p == 0f) return@forEachIndexed
                val x = lerp(deckX, gridLeft + (cardW + gap) * (i % cols), p)
                val y = lerp(header, gridTop + (cardH + gap) * (i / cols), p)
                val w = lerp(deckW, cardW, p)
                val angle = flip[i].value
                Box(
                    Modifier.offset(x, y).size(w, w * CARD_RATIO).graphicsLayer {
                        rotationY = angle
                        cameraDistance = 14 * this.density
                    }
                ) {
                    if (angle < 90f) CardBack(Modifier.fillMaxSize())
                    else CardFace(id, w, Modifier.fillMaxSize().graphicsLayer { rotationY = 180f })
                }
            }
        }

        if (finished) Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF2030A12), Color(0xFF030A12))))
                .padding(start = side, end = side, top = 28.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val summary = BOOSTERS.mapNotNull { b -> cards.count { it == b.id }.takeIf { it > 0 }?.let { "${b.name} ×$it" } }
            Text(summary.joinToString("   "), color = Color.White, textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (more > 0) androidx.compose.material3.OutlinedButton(onClick = onMore, Modifier.weight(1f).height(52.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) { Text("Открыть ещё", fontSize = 14.sp, color = Color.White) }
                Button(onClick = onDone, Modifier.weight(1f).height(52.dp)) { Text("Забрать", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                if (more > 0) androidx.compose.material3.OutlinedButton(onClick = onAll, Modifier.weight(1f).height(52.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) { Text("Открыть все ($more)", fontSize = 14.sp, color = Color.White) }
            }
        }
    }
}

@Composable
private fun CardBack(modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0D3B5C), Color(0xFF061423))))
            .border(1.5.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize(0.6f)) {
            val r = size.minDimension / 2
            for (k in 1..3) drawCircle(accent.copy(alpha = 0.25f * k), r * k / 3f, style = Stroke(r * 0.07f))
            drawCrystal(center, r * 0.45f, accent)
        }
    }
}

@Composable
private fun CardFace(id: String, width: Dp, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(cs.surface).border(1.5.dp, Color(0xFFFFC857), RoundedCornerShape(10.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BoosterIcon(id, width * 0.55f)
        Spacer(Modifier.height(4.dp))
        // narrow cards (small phones) use a short label; one font size for every card of the pack
        val narrow = width < 80.dp
        val name = BOOSTERS.first { it.id == id }.name.let { if (narrow && it.length > 9) it.take(8) + "." else it }
        val longest = if (narrow) 9 else BOOSTERS.maxOf { it.name.length }
        Text(name, color = cs.onSurface, textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
            fontSize = ((width.value - 10f) / (longest * 0.72f)).coerceIn(7f, 14f).sp, fontWeight = FontWeight.SemiBold)
    }
}
