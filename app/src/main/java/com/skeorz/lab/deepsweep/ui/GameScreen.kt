package com.skeorz.lab.deepsweep.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import com.skeorz.lab.deepsweep.meta.ACHIEVEMENTS
import com.skeorz.lab.deepsweep.meta.AVATARS
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skeorz.lab.deepsweep.game.Mode
import com.skeorz.lab.deepsweep.game.Session
import com.skeorz.lab.deepsweep.meta.BOOSTERS
import com.skeorz.lab.deepsweep.meta.INSTANT_BOOSTERS
import com.skeorz.lab.deepsweep.meta.Store
import kotlinx.coroutines.delay

private val TUTORIAL = listOf(
    "Нажмите на любую клетку — сонар безопасно откроет первый участок дна.",
    "Цифра — сколько мин среди 8 соседних клеток. Удерживайте палец на клетке, где точно мина, — встанет буй.",
    "Когда вокруг цифры стоит столько же буёв, нажмите на цифру — откроются остальные соседи.",
    "Откройте все безопасные клетки сектора 8×8, чтобы решить его.",
    "Сектор решён! За сектора дают очки, опыт и иногда кристаллы. Подрыв на мине блокирует сектор — его откроют решённые соседи или кристаллы.",
)

@Composable
fun GameScreen(session: Session, store: Store, onExit: () -> Unit, onRestart: () -> Unit, onOpenPack: () -> Unit, paused: Boolean = false) {
    store.rev
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf(false) }
    var shop by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var records by remember { mutableStateOf(false) }
    var wheel by remember { mutableStateOf(false) }

    // booster animation: run it, then apply its result
    LaunchedEffect(session.fx) {
        val f = session.fx ?: return@LaunchedEffect
        val start = withFrameMillis { it }
        do {
            val now = withFrameMillis { it }
            session.fxT = ((now - start).toFloat() / f.durationMs).coerceAtMost(1f)
        } while (session.fxT < 1f)
        session.fx = null
        f.done()
    }

    LaunchedEffect(session) { while (true) { delay(1000); if (!menu && !profile && !shop && !settings && !records && !paused) session.secondPassed() } }
    // leaving mid-animation still applies the booster that was already spent
    DisposableEffect(session) { onDispose { session.fx?.let { session.fx = null; it.done() }; session.save() } }
    BackHandler { menu = true }

    Box(Modifier.fillMaxSize().background(cs.background)) {
        Board(session, store, Modifier.fillMaxSize())

        Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(10.dp)) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surface.copy(alpha = 0.92f))
                    .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 8.dp)
            ) {
                val title = if (session.mode == Mode.ENDLESS) "${session.mode.title} · ${session.world.difficulty.title}" else session.mode.title
                Text(title, Modifier.padding(start = 52.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurface.copy(alpha = 0.7f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { menu = true }, Modifier.width(48.dp)) { Text("☰", fontSize = 22.sp) }
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable { profile = true }.padding(end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(store, 36)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Ур. ${store.d.level}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            LinearProgressIndicator(progress = { store.d.xp.toFloat() / store.xpNeed() }, Modifier.fillMaxWidth().height(6.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("${store.d.crystals} 💎", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.padding(start = 52.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val time = if (session.mode.timeLimit > 0) session.timeLeft else session.seconds
                    Text("⏱ ${clock(time)}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        color = if (session.mode.timeLimit > 0 && time < 30) cs.error else cs.onSurface)
                    if (session.mode != Mode.TUTORIAL) {
                        Text("   🔥 серия ${session.clean} (+${session.nextReward} 💎)", fontSize = 14.sp)
                        if (session.shield) Text("  🛡", fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        Text("${session.score} очк.", fontSize = 14.sp, color = cs.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (session.mode == Mode.TUTORIAL) {
                val step = session.tutorialStep.coerceAtMost(TUTORIAL.lastIndex)
                Column(
                    Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(cs.primary.copy(alpha = 0.92f)).padding(12.dp)
                ) {
                    Text("Шаг ${step + 1} из ${TUTORIAL.size}", color = cs.onPrimary, fontWeight = FontWeight.Bold)
                    Text(TUTORIAL[step], color = cs.onPrimary)
                    if (step == TUTORIAL.lastIndex) TextButton(onClick = {
                        if (!store.d.tutorialDone) { store.d.tutorialDone = true; store.earn(15, "обучение пройдено"); store.changed() }
                        session.abandon(); onExit()
                    }) { Text("Завершить обучение", color = cs.onPrimary, fontWeight = FontWeight.Bold) }
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val hint = when {
                session.trawlFrom != null -> "Нажмите на стрелку — направление трала (другое место — отмена)"
                else -> session.booster?.let { id ->
                    val target = when (id) { "trawl" -> "клетку"; "lockpick" -> "хранилище"; else -> "сектор" }
                    "Выберите $target для «${BOOSTERS.first { it.id == id }.name}» · веер — отмена"
                }
            }
            hint?.let {
                Text(it, Modifier.clip(RoundedCornerShape(10.dp)).background(cs.surface.copy(alpha = 0.92f)).padding(8.dp),
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.padding(4.dp))
            }
            Row(
                Modifier.clip(RoundedCornerShape(18.dp)).background(cs.surface.copy(alpha = 0.92f)).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Chip(if (session.flagMode) "🚩 Ставить буи" else "⛏ Открывать", session.flagMode) { session.flagMode = !session.flagMode }
                if (session.mode != Mode.TUTORIAL) FanButton(session.booster, wheel) {
                    if (session.booster != null) session.booster = null else wheel = !wheel
                }
                if (store.d.packs.isNotEmpty()) PackButton(store.d.packs.size, onOpenPack)
            }
        }

        if (wheel) BoosterWheel(store, bottomGap = 76.dp, onPick = { id ->
            wheel = false
            when {
                !store.hasBooster(id) -> store.toast("Нет бустера — его можно купить в магазине или найти в паке")
                id in INSTANT_BOOSTERS -> session.activate(id)
                else -> session.booster = id
            }
        }, onClose = { wheel = false })

        if (session.shield) ShieldOverlay()
        if (com.skeorz.lab.deepsweep.BuildConfig.CHEATS && store.anyCheat) CheatWatermark(0.1f)
        ShieldShatter(session.shieldBreak)
        Bubbles(session.bubbles)
    }

    session.lockDialog?.let { c ->
        val price = session.unlockPrice(c)
        AlertDialog(
            onDismissRequest = { session.lockDialog = null },
            title = { Text("Сектор заблокирован") },
            text = {
                Text("Здесь сработала мина. Сектор откроется сам, когда вы решите все 8 секторов вокруг него " +
                    "(сейчас ${session.world.solvedAround(c)}/8). Или разблокируйте его сразу.")
            },
            confirmButton = { TextButton(onClick = { session.buyUnlock(c) }) { Text("За $price 💎") } },
            dismissButton = {
                Row {
                    if (store.hasBooster("defuse")) TextButton(onClick = { session.defuseWithBooster(c) }) { Text("Бустером") }
                    TextButton(onClick = { session.lockDialog = null }) { Text("Позже") }
                }
            },
        )
    }

    if (session.over && session.mode != Mode.TUTORIAL) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(if (session.mode.timeLimit > 0) "Время вышло" else "Погружение окончено") },
            text = { Text("Очки: ${session.score}\nРекорд режима: ${store.best(session.mode)}") },
            confirmButton = { TextButton(onClick = onRestart) { Text("Ещё раз") } },
            dismissButton = { TextButton(onClick = onExit) { Text("В меню") } },
        )
    }

    if (menu) PauseMenu(
        session, store,
        onResume = { menu = false },
        onRestart = { menu = false; onRestart() },
        onSettings = { menu = false; settings = true },
        onRecords = { menu = false; records = true },
        onExit = { menu = false; session.save(); onExit() },
    )

    if (profile) ProfilePanel(store, onShop = { profile = false; shop = true }, onClose = { profile = false })
    if (shop) Box(Modifier.fillMaxSize().background(cs.background)) { ShopScreen(store) { shop = false } }
    if (settings) Box(Modifier.fillMaxSize().background(cs.background)) { SettingsScreen(store, allowReset = false) { settings = false } }
    if (records) Box(Modifier.fillMaxSize().background(cs.background)) {
        RecordsScreen(store, mapOf(session.mode to session.score), session.mode) { records = false }
    }
}

@Composable
private fun PauseMenu(session: Session, store: Store, onResume: () -> Unit, onRestart: () -> Unit, onSettings: () -> Unit, onRecords: () -> Unit, onExit: () -> Unit) {
    store.rev
    val cs = MaterialTheme.colorScheme
    val frame = RoundedCornerShape(24.dp)
    Dialog(onDismissRequest = onResume) {
        Column(
            Modifier.fillMaxWidth().clip(frame).background(cs.surface).border(2.dp, cs.primary, frame).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Пауза", fontSize = 24.sp, fontWeight = FontWeight.Black, color = cs.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(store, 52)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(store.d.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Ур. ${store.d.level} · ${store.d.title}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Активность", fontWeight = FontWeight.Bold)
            val time = if (session.mode.timeLimit > 0) session.timeLeft else session.seconds
            val tiles = buildList {
                add("⏱" to clock(time) to if (session.mode.timeLimit > 0) "осталось" else "в сессии")
                if (session.mode != Mode.TUTORIAL) {
                    add("🔥" to "${session.clean}" to "серия")
                    add("🏆" to "${session.score}" to "очки")
                }
                add("💎" to "+${session.sessionCrystals}" to "за сессию · всего ${store.d.crystals}")
            }
            for (row in tiles.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((head, label) in row) {
                    val (icon, value) = head
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).border(1.dp, cs.primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("$icon $value", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurface.copy(alpha = 0.7f))
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PauseButton("▶  Продолжить", primary = true, onClick = onResume)
                if (session.mode != Mode.TUTORIAL) PauseButton("↻  Новая карта", onClick = onRestart)
                if (session.mode != Mode.TUTORIAL) PauseButton("📊  Рекорды", onClick = onRecords)
                PauseButton("⚙  Настройки", onClick = onSettings)
                PauseButton("⌂  В меню", onClick = onExit)
            }
            Text("Прогресс карты сохраняется автоматически.", style = MaterialTheme.typography.labelSmall,
                color = cs.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun PauseButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Text(
        text,
        Modifier.fillMaxWidth().clip(shape).background(if (primary) cs.primary else cs.surface)
            .border(1.5.dp, cs.primary, shape).clickable(onClick = onClick).padding(vertical = 14.dp),
        textAlign = TextAlign.Center, fontSize = 17.sp, fontWeight = FontWeight.Bold,
        color = if (primary) cs.onPrimary else cs.onSurface,
    )
}

/** In-game profile: avatar, XP from → to, the five closest achievements and the shop. */
@Composable
private fun ProfilePanel(store: Store, onShop: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    store.rev
    val d = store.d
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onClose)) {
        Column(
            Modifier.align(Alignment.TopCenter).padding(10.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(cs.surface).clickable(enabled = false) {}.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(store, 56)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Уровень ${d.level} · ${d.title}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onClose) { Text("✕", fontSize = 20.sp) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${d.xp}", fontWeight = FontWeight.Bold, color = cs.primary)
                LinearProgressIndicator(progress = { d.xp.toFloat() / store.xpNeed() },
                    Modifier.weight(1f).padding(horizontal = 8.dp).height(8.dp))
                Text("${store.xpNeed()}", fontWeight = FontWeight.Bold)
            }
            Text("Аватар", fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((emoji, lvl) in AVATARS) {
                    val ok = d.level >= lvl
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(if (emoji == d.avatar) cs.primary else cs.surfaceVariant)
                            .clickable(enabled = ok) { d.avatar = emoji; store.changed() },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (ok) emoji else "$lvl", fontSize = if (ok) 22.sp else 12.sp) }
                }
            }
            Text("Ближайшие достижения", fontWeight = FontWeight.Bold)
            val next = ACHIEVEMENTS.filter { store.tier(it) < it.goals.size }
                .sortedByDescending { store.stat(it.stat).toFloat() / it.goals[store.tier(it)] }.take(5)
            for (a in next) {
                val tier = store.tier(a)
                val goal = a.goals[tier]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Medal(if (a.tiered) tier else 0, 28)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(a.desc(goal), style = MaterialTheme.typography.labelSmall)
                        LinearProgressIndicator(progress = { store.stat(a.stat).coerceAtMost(goal).toFloat() / goal },
                            Modifier.fillMaxWidth().padding(top = 3.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("${store.stat(a.stat).coerceAtMost(goal)}/$goal", style = MaterialTheme.typography.labelSmall)
                }
            }
            Button(onClick = onShop, Modifier.fillMaxWidth().height(48.dp)) { Text("🛒 Магазин", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Text(
        text,
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) cs.primary else cs.surfaceVariant)
            .border(1.dp, cs.primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        color = if (selected) cs.onPrimary else cs.onSurface,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun BoosterIcon(id: String, size: Dp, color: Color = MaterialTheme.colorScheme.primary, back: Color = MaterialTheme.colorScheme.surface) {
    Canvas(Modifier.size(size)) { drawBooster(id, center, this.size.minDimension * 0.42f, color, back) }
}

fun clock(sec: Long) = "%d:%02d".format(sec / 60, sec % 60)
