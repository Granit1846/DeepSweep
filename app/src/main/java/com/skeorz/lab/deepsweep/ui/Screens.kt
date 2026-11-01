package com.skeorz.lab.deepsweep.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skeorz.lab.deepsweep.game.Difficulty
import com.skeorz.lab.deepsweep.game.Mode
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import com.skeorz.lab.deepsweep.meta.ACHIEVEMENTS
import com.skeorz.lab.deepsweep.meta.AVATARS
import com.skeorz.lab.deepsweep.meta.CHEST_EVERY
import com.skeorz.lab.deepsweep.meta.CHEST_REWARD
import com.skeorz.lab.deepsweep.meta.Cat
import com.skeorz.lab.deepsweep.meta.PREMIUM_CRYSTALS
import com.skeorz.lab.deepsweep.meta.PREMIUM_XP
import com.skeorz.lab.deepsweep.meta.S
import com.skeorz.lab.deepsweep.meta.TIER_NAMES
import com.skeorz.lab.deepsweep.meta.ITEMS
import com.skeorz.lab.deepsweep.meta.Item
import com.skeorz.lab.deepsweep.meta.LOGIN_REWARDS
import com.skeorz.lab.deepsweep.meta.MISSION_BONUS
import com.skeorz.lab.deepsweep.meta.STAT_NAMES
import com.skeorz.lab.deepsweep.meta.Store
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class Screen { MENU, MODES, DIFFICULTY, MISSIONS, ACHIEVEMENTS, SHOP, PROFILE, RECORDS, SETTINGS }

private val DIFFICULTY_COLORS = listOf(Color(0xFF7CFC9A), Color(0xFFFFE066), Color(0xFFFF9F43), Color(0xFFFF5E78))
private val DIFFICULTY_DESC = listOf(
    "Большие цифры появляются далеко от старта. Для спокойной игры.",
    "Сложность растёт плавно по мере удаления от старта.",
    "Поле быстро становится плотным, большие цифры — уже рядом.",
    "Большие цифры почти сразу. Только для мастеров сонара.",
)

/** Depth gauge: the number of lit bars grows with the difficulty. */
@Composable
fun DifficultyIcon(d: Difficulty, size: Int) {
    val off = MaterialTheme.colorScheme.surfaceVariant
    val on = DIFFICULTY_COLORS[d.ordinal]
    Canvas(Modifier.size(size.dp)) {
        val bw = this.size.width / 6.5f
        for (k in 0..3) {
            val h = this.size.height * (0.3f + 0.7f * (k + 1) / 4f)
            drawRoundRect(if (k <= d.ordinal) on else off, Offset(bw * 0.5f + k * bw * 1.5f, this.size.height - h),
                androidx.compose.ui.geometry.Size(bw, h), androidx.compose.ui.geometry.CornerRadius(bw / 3))
        }
    }
}

@Composable
fun DifficultyScreen(store: Store, onPick: (Difficulty) -> Unit, onBack: () -> Unit) = Page("Сложность", onBack, store) {
    item { Text("«${Mode.ENDLESS.title}» — новая карта", style = MaterialTheme.typography.bodyMedium) }
    items(Difficulty.entries) { df ->
        val cs = MaterialTheme.colorScheme
        val last = df.name == store.d.endlessDifficulty
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surface)
                .border(if (last) 2.dp else 1.dp, if (last) DIFFICULTY_COLORS[df.ordinal] else cs.primary.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                .clickable { onPick(df) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DifficultyIcon(df, 52)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(df.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DIFFICULTY_COLORS[df.ordinal])
                Text(DIFFICULTY_DESC[df.ordinal], style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surface)
            .border(1.dp, cs.primary.copy(alpha = 0.18f), RoundedCornerShape(18.dp)).padding(14.dp),
        content = content,
    )
}

@Composable
fun Page(title: String, onBack: () -> Unit, store: Store, content: LazyListScope.() -> Unit) {
    BackHandler(onBack = onBack)
    store.rev
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BackArrow(onBack)
            Spacer(Modifier.width(4.dp))
            Text(title, Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("${store.d.crystals} 💎", Modifier.padding(end = 12.dp), fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun BackArrow(onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(26.dp)) {
            val w = size.width
            val stroke = w * 0.12f
            drawLine(color, Offset(w * 0.15f, w / 2), Offset(w * 0.88f, w / 2), stroke, StrokeCap.Round)
            drawLine(color, Offset(w * 0.15f, w / 2), Offset(w * 0.48f, w * 0.17f), stroke, StrokeCap.Round)
            drawLine(color, Offset(w * 0.15f, w / 2), Offset(w * 0.48f, w * 0.83f), stroke, StrokeCap.Round)
        }
    }
}

fun frameColor(id: String) = when (id) {
    "frame_teal" -> Color(0xFF3FE0D0)
    "frame_coral" -> Color(0xFFFF7F6E)
    "frame_gold" -> Color(0xFFFFC857)
    "frame_violet" -> Color(0xFFB57BFF)
    "frame_neon" -> Color(0xFFFF2BD6)
    else -> Color.Transparent
}

@Composable
fun Avatar(store: Store, size: Int, frame: String = store.d.frame) {
    val ring = frameColor(frame)
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
            .border((size / 16).dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(store.d.avatar, fontSize = (size / 2).sp) }
}

@Composable
fun MenuScreen(store: Store, canContinue: Boolean, onPlay: () -> Unit, go: (Screen) -> Unit, onOpenPack: () -> Unit) {
    store.rev
    val d = store.d
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().clickable { go(Screen.PROFILE) }, verticalAlignment = Alignment.CenterVertically) {
            Avatar(store, 48)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, fontWeight = FontWeight.Bold)
                Text("Ур. ${d.level} · ${d.title}", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { d.xp.toFloat() / store.xpNeed() }, Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("${d.crystals} 💎", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(36.dp))
        Text("ГЛУБИННЫЙ", fontSize = 34.sp, fontWeight = FontWeight.Black, color = cs.primary, letterSpacing = 6.sp)
        Text("САПЁР", fontSize = 34.sp, fontWeight = FontWeight.Black, color = cs.onBackground, letterSpacing = 10.sp)
        Text("бесконечное дно · без рекламы", style = MaterialTheme.typography.bodySmall, color = cs.onBackground.copy(alpha = 0.6f))
        Spacer(Modifier.height(36.dp))
        val last = store.lastPlayable()
        Button(onClick = onPlay, Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(20.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (canContinue) "ПРОДОЛЖИТЬ" else "ПОГРУЗИТЬСЯ", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(last.title, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        val items = listOf(
            "🌊 Режимы" to Screen.MODES, "📋 Задания" to Screen.MISSIONS, "🏆 Достижения" to Screen.ACHIEVEMENTS,
            "🛒 Магазин" to Screen.SHOP, "📊 Рекорды" to Screen.RECORDS, "⚙ Настройки" to Screen.SETTINGS,
        )
        for (row in items.chunked(2)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((label, screen) in row) {
                    OutlinedButton(onClick = { go(screen) }, Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp)) {
                        Text(label, fontSize = 15.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (d.packs.isNotEmpty()) {
            Row(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpenPack).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                PackButton(d.packs.size, onOpenPack)
                Spacer(Modifier.width(10.dp))
                Text(if (d.packs.size == 1) "Пак бустеров ждёт вскрытия" else "Паков ждут вскрытия: ${d.packs.size}", color = cs.primary, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
        }
        val ready = d.missions.count { !it.claimed && store.progress(it) >= it.target }
        if (ready > 0) Text("Есть награды за задания: $ready", color = cs.primary, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { go(Screen.MISSIONS) })
    }
}

@Composable
fun ModesScreen(store: Store, hasSave: (Mode) -> Boolean, onStart: (Mode, Boolean) -> Unit, onBack: () -> Unit) =
    Page("Режимы", onBack, store) {
        items(Mode.entries) { m ->
            val open = store.modeOpen(m)
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.title, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (m != Mode.TUTORIAL) Text("рекорд ${store.best(m)}", style = MaterialTheme.typography.bodySmall)
                }
                Text(m.desc, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!open) {
                        Text(if (com.skeorz.lab.deepsweep.BuildConfig.PREMIUM) "🔒 Откроется на ${m.unlockLevel} уровне или с ★ Premium" else "🔒 Откроется на ${m.unlockLevel} уровне", Modifier.align(Alignment.CenterVertically).weight(1f),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                    } else {
                        if (hasSave(m)) {
                            Button(onClick = { onStart(m, false) }) { Text("Продолжить") }
                            OutlinedButton(onClick = { onStart(m, true) }) { Text("Новая карта") }
                        } else Button(onClick = { onStart(m, true) }) { Text("Играть") }
                    }
                }
            }
        }
    }

@Composable
fun MissionsScreen(store: Store, onBack: () -> Unit) = Page("Задания", onBack, store) {
    val d = store.d
    item {
        Card {
            Text("Ежедневная вахта", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Заходите каждый день — награда растёт. Пропуск сбрасывает серию.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val dayInCycle = (d.streak - 1).mod(LOGIN_REWARDS.size)
                LOGIN_REWARDS.forEachIndexed { i, r ->
                    val got = i <= dayInCycle
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (got) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val c = if (got) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        Text("Д${i + 1}", fontSize = 11.sp, color = c)
                        Text("$r", fontWeight = FontWeight.Bold, color = c)
                    }
                }
            }
        }
    }
    item { Text("Задания на сегодня", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
    items(d.missions) { m ->
        val prog = store.progress(m)
        Card {
            Text(store.missionText(m), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { prog.toFloat() / m.target }, Modifier.fillMaxWidth())
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$prog / ${m.target}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                when {
                    m.claimed -> Text("Получено ✓", color = MaterialTheme.colorScheme.primary)
                    prog >= m.target -> Button(onClick = { store.claim(m) }) { Text("Забрать ${m.reward} 💎") }
                    else -> Text("${m.reward} 💎")
                }
            }
        }
    }
    item {
        Text(if (d.missionBonus) "Бонус за все задания получен ✓" else "Выполните все задания дня: ещё +$MISSION_BONUS 💎",
            style = MaterialTheme.typography.bodySmall)
    }
    item {
        val solved = store.stat(S.SECTORS)
        val inChest = solved % CHEST_EVERY
        Card {
            Text("Сундук глубин", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Каждые $CHEST_EVERY решённых секторов — $CHEST_REWARD 💎", style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { inChest.toFloat() / CHEST_EVERY }, Modifier.fillMaxWidth().padding(top = 8.dp))
            Text("$inChest / $CHEST_EVERY", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun AchievementsScreen(store: Store, onBack: () -> Unit) = Page("Достижения", onBack, store) {
    val got = ACHIEVEMENTS.sumOf { store.tier(it) }
    item {
        Text("Получено уровней: $got из ${ACHIEVEMENTS.sumOf { it.goals.size }}. Награда удваивается с каждым уровнем.",
            style = MaterialTheme.typography.bodyMedium)
    }
    items(ACHIEVEMENTS.sortedBy { store.tier(it) == it.goals.size }) { a ->
        val tier = store.tier(a)
        val done = tier == a.goals.size
        val goal = a.goals[if (done) tier - 1 else tier]
        val v = store.stat(a.stat).coerceAtMost(goal)
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Medal(if (a.tiered) tier else if (done) 3 else 0, 40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, fontWeight = FontWeight.Bold)
                    Text(a.desc(goal), style = MaterialTheme.typography.bodySmall)
                    if (!done) LinearProgressIndicator(progress = { v.toFloat() / goal }, Modifier.fillMaxWidth().padding(top = 6.dp))
                    if (a.tiered) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (t in 1..a.goals.size) Medal(if (t <= tier) t else 0, 16)
                        Text(if (tier == 0) "нет уровня" else TIER_NAMES[tier - 1], Modifier.padding(start = 4.dp),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (done) "✓" else "${a.rewardFor(tier + 1)} 💎", fontWeight = FontWeight.SemiBold)
                    if (!done && a.packFor(tier + 1, store.premium) > 0) Text("+ пак ×${a.packFor(tier + 1, store.premium)}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private val MEDAL_COLORS = listOf(Color(0xFFCD7F32), Color(0xFFC9D3DC), Color(0xFFFFC857), Color(0xFF9FF3F0))

/** tier 0 = not reached, 1 bronze, 2 silver, 3 gold, 4 platinum. */
@Composable
fun Medal(tier: Int, size: Int) {
    val cs = MaterialTheme.colorScheme
    val c = if (tier == 0) cs.surfaceVariant else MEDAL_COLORS[tier - 1]
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(c)
            .border((size / 14 + 1).dp, if (tier == 0) cs.primary.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (size >= 24) Text(if (tier == 0) "?" else "★", fontSize = (size / 2).sp, color = if (tier == 0) cs.onSurface else Color(0xFF1A1A1A)) }
}

private val GOLD = Color(0xFFFFC857)

@Composable
private fun PremiumCard(store: Store) {
    val on = store.premium
    var activate by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF3A2A05), Color(0xFF1B1430))))
            .border(1.5.dp, GOLD, RoundedCornerShape(18.dp)).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("★ Premium", Modifier.weight(1f), color = GOLD, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(if (on) "Активен" else "Не активирован",
                Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) GOLD else Color.White.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = if (on) Color(0xFF1A1A1A) else Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        for (perk in listOf("Весь магазин открыт: темы, мины, буи, цифры, рамки", "Опыт ×$PREMIUM_XP в любом режиме и на любой сложности",
            "Все полученные кристаллы ×$PREMIUM_CRYSTALS")) {
            Text("✦  $perk", color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (on) {
            Text("Спасибо за поддержку! Premium привязан к этому устройству и работает офлайн.",
                color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Разовая покупка без подписки: после оплаты вы получите код активации, он проверяется на устройстве.",
                color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            if (com.skeorz.lab.deepsweep.BuildConfig.PURCHASE_URL.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(com.skeorz.lab.deepsweep.BuildConfig.PURCHASE_URL))
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Купить Premium") }
            }
            TextButton(onClick = { activate = true }, modifier = Modifier.fillMaxWidth()) {
                Text("У меня есть код", color = GOLD)
            }
        }
        // test build only: flip Premium without a licence
        if (com.skeorz.lab.deepsweep.BuildConfig.CHEATS) {
            TextButton(onClick = { store.setPremium(!store.d.premium) }) {
                Text(if (store.d.premium) "Выключить (для проверки)" else "Включить (для проверки)", color = GOLD)
            }
        }
    }
    if (activate) ActivateDialog(store) { activate = false }
}

@Composable
private fun ActivateDialog(store: Store, onClose: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Активация Premium") },
        text = {
            Column {
                Text("Вставьте код активации из письма или сообщения после покупки. Код проверяется на устройстве и не требует интернета.",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it; error = null },
                    label = { Text("Код активации") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (store.activate(code)) onClose()
                else error = "Код не подошёл. Проверьте, что он скопирован целиком."
            }) { Text("Активировать") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Отмена") } },
    )
}


@Composable
fun ShopScreen(store: Store, onBack: () -> Unit) {
    var cat by remember { mutableStateOf(Cat.BOOSTER) }
    Page("Магазин", onBack, store) {
        // Premium lives in the Pro and test builds; R8 drops the card from the free build
        if (com.skeorz.lab.deepsweep.BuildConfig.PREMIUM) item { PremiumCard(store) }
        item {
            Text("Всё покупается только за кристаллы, заработанные в игре: решайте особые сектора, вскрывайте хранилища, " +
                "выполняйте задания и достижения, заходите каждый день.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in Cat.entries) Chip(c.title, c == cat) { cat = c }
            }
        }
        items(ITEMS.filter { it.cat == cat }) { item ->
            val d = store.d
            val owned = store.owns(item)
            val selected = item.id in setOf(d.theme, d.mineSkin, d.flagSkin, d.numbers, d.frame)
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.cat != Cat.THEME) {
                        ItemPreview(item, store)
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.Bold)
                        if (item.desc.isNotEmpty()) Text(item.desc, style = MaterialTheme.typography.bodySmall)
                        if (item.cat == Cat.BOOSTER) Text("В запасе: ${d.boosters[item.id] ?: 0}", style = MaterialTheme.typography.bodySmall)
                        if (item.cat == Cat.THEME) ThemeSwatch(item.id)
                    }
                    Spacer(Modifier.width(8.dp))
                    when {
                        item.cat == Cat.BOOSTER -> Button(onClick = { store.buy(item) }) { Text("${item.price} 💎") }
                        selected -> Text("Выбрано ✓", color = MaterialTheme.colorScheme.primary)
                        owned -> OutlinedButton(onClick = { store.select(item) }) { Text("Выбрать") }
                        item.price < 0 -> Text("За достижение", style = MaterialTheme.typography.bodySmall)
                        d.level < item.level -> Text("С ${item.level} ур.", style = MaterialTheme.typography.bodySmall)
                        else -> Button(onClick = { store.buy(item) }) { Text("${item.price} 💎") }
                    }
                }
            }
        }
    }
}

/** What the item looks like, drawn in the current theme; shown whether or not it is unlocked yet. */
@Composable
private fun ItemPreview(item: Item, store: Store) {
    val p = palette(store.d.theme)
    val bg = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(if (item.cat == Cat.FLAG) p.hidden else bg),
        contentAlignment = Alignment.Center) {
        when (item.cat) {
            Cat.BOOSTER -> BoosterIcon(item.id, 38.dp, back = bg)
            Cat.MINE -> Canvas(Modifier.size(44.dp)) { drawMine(item.id, center, size.minDimension * 0.42f, p) }
            Cat.FLAG -> Canvas(Modifier.size(44.dp)) { drawFlag(item.id, center, size.minDimension * 0.42f, p) }
            Cat.NUMBERS -> {
                val nums = numberColors(item.id, p)
                Row(Modifier.clip(RoundedCornerShape(8.dp)).background(p.open).padding(horizontal = 4.dp, vertical = 2.dp)) {
                    for (k in 0..2) Text("${k + 1}", color = nums[k], fontWeight = FontWeight.Black, fontSize = 17.sp)
                }
            }
            Cat.FRAME -> Avatar(store, 44, frame = item.id)
            Cat.THEME -> ThemeSwatch(item.id)
        }
    }
}

@Composable
private fun ThemeSwatch(id: String) {
    val p = palette(id)
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (c in listOf(p.bg, p.hidden, p.open, p.line, p.accent)) Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(c))
    }
}

@Composable
fun ProfileScreen(store: Store, onBack: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    Page("Профиль", onBack, store) {
        val d = store.d
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(store, 80)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(d.title, color = MaterialTheme.colorScheme.primary)
                        Text("Уровень ${d.level} · ${d.xp}/${store.xpNeed()} опыта", style = MaterialTheme.typography.bodySmall)
                    }
                    EditButton { editing = true }
                }
            }
        }
        item { Text("Статистика", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        items(STAT_NAMES.entries.toList()) { (k, name) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(name, Modifier.weight(1f))
                Text("${store.stat(k)}", fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (editing) ProfileEditor(store) { editing = false }
}

@Composable
private fun EditButton(onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme.primary
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(c.copy(alpha = 0.14f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // pencil
        Canvas(Modifier.size(22.dp)) {
            val w = size.width
            val s = w * 0.13f
            drawLine(c, Offset(w * 0.22f, w * 0.78f), Offset(w * 0.78f, w * 0.22f), s * 1.6f, StrokeCap.Round)
            drawLine(c, Offset(w * 0.12f, w * 0.88f), Offset(w * 0.2f, w * 0.8f), s, StrokeCap.Round)
            drawLine(c, Offset(w * 0.1f, w * 0.95f), Offset(w * 0.9f, w * 0.95f), s * 0.6f, StrokeCap.Round)
        }
    }
}

/** Lays [items] out in [rows] rows that scroll sideways together. */
@Composable
private fun <T> RowsPicker(items: List<T>, rows: Int, cell: @Composable (T) -> Unit) {
    val cols = (items.size + rows - 1) / rows
    Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (r in 0 until rows) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in 0 until cols) items.getOrNull(r * cols + c)?.let { cell(it) }
        }
    }
}

/** Name, avatar, title and frame in one place, with a live preview; nothing changes until «Сохранить». */
@Composable
private fun ProfileEditor(store: Store, onClose: () -> Unit) {
    val d = store.d
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(d.name) }
    var avatar by remember { mutableStateOf(d.avatar) }
    var title by remember { mutableStateOf(d.title) }
    var frame by remember { mutableStateOf(d.frame) }
    val shape = RoundedCornerShape(24.dp)
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(0.94f).clip(shape).background(cs.surface).border(2.dp, cs.primary, shape).padding(18.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(cs.surfaceVariant).border(5.dp, frameColor(frame), CircleShape),
                    contentAlignment = Alignment.Center) { Text(avatar, fontSize = 36.sp) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(name.ifBlank { "Без имени" }, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(title, color = cs.primary)
                    Text("Уровень ${d.level}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(name, { name = it.take(20) }, Modifier.fillMaxWidth(), label = { Text("Имя") }, singleLine = true)

                Text("Аватар", fontWeight = FontWeight.Bold)
                RowsPicker(AVATARS, 2) { (emoji, lvl) ->
                    val ok = d.level >= lvl
                    Box(
                        Modifier.size(48.dp).clip(CircleShape)
                            .background(if (emoji == avatar) cs.primary else cs.surfaceVariant)
                            .clickable(enabled = ok) { avatar = emoji },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (ok) Text(emoji, fontSize = 24.sp)
                        else Text("$lvl ур.", fontSize = 11.sp, color = cs.onSurface.copy(alpha = 0.6f))
                    }
                }

                Text("Звание", fontWeight = FontWeight.Bold)
                RowsPicker(store.titles(), 3) { t -> Chip(t, t == title) { title = t } }
                Text("Новые звания дают достижения.", style = MaterialTheme.typography.labelSmall)

                Text("Рамка", fontWeight = FontWeight.Bold)
                RowsPicker(ITEMS.filter { it.cat == Cat.FRAME }, 2) { f ->
                    val ok = store.owns(f)
                    Column(
                        Modifier.width(76.dp).clip(RoundedCornerShape(12.dp))
                            .border(if (f.id == frame) 2.dp else 1.dp, if (f.id == frame) cs.primary else cs.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .clickable(enabled = ok) { frame = f.id }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(cs.surfaceVariant).border(4.dp, frameColor(f.id), CircleShape),
                            contentAlignment = Alignment.Center) { Text(if (ok) avatar else "🔒", fontSize = 18.sp) }
                        Text(f.name.substringBefore(" ("), fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center,
                            color = cs.onSurface.copy(alpha = if (ok) 1f else 0.5f))
                    }
                }
                Text("Другие рамки — в магазине и за платину достижений.", style = MaterialTheme.typography.labelSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onClose, Modifier.weight(1f).height(50.dp)) { Text("Отмена") }
                Button(
                    onClick = {
                        d.name = name.trim().ifBlank { d.name }
                        d.avatar = avatar
                        d.title = title
                        d.frame = frame
                        store.changed()
                        onClose()
                    },
                    Modifier.weight(1f).height(50.dp),
                ) { Text("Сохранить", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun RecordsScreen(store: Store, current: Map<Mode, Long> = emptyMap(), startMode: Mode = Mode.ENDLESS, onBack: () -> Unit) {
    var mode by remember { mutableStateOf(startMode.takeIf { it != Mode.TUTORIAL } ?: Mode.ENDLESS) }
    var scope by remember { mutableStateOf(0) }
    Page("Рекорды", onBack, store) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (m in Mode.entries.filter { it != Mode.TUTORIAL }) Chip(m.title, m == mode) { mode = m }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Сегодня", "Месяц", "Всё время").forEachIndexed { i, s -> Chip(s, i == scope) { scope = i } }
            }
        }
        val today = LocalDate.now()
        // the run in progress counts as today's, marked «текущий»
        val live = current[mode]?.takeIf { it > 0 }?.let { com.skeorz.lab.deepsweep.meta.RunRecord(mode.name, it, System.currentTimeMillis(), store.d.name) }
        val list = (store.d.runs + listOfNotNull(live)).sortedByDescending { it.score }.filter { r ->
            val day = Instant.ofEpochMilli(r.at).atZone(ZoneId.systemDefault()).toLocalDate()
            r.mode == mode.name && when (scope) {
                0 -> day == today
                1 -> day.year == today.year && day.month == today.month
                else -> true
            }
        }.take(10)
        if (list.isEmpty()) item { Text("Пока нет результатов. Рекорд записывается, когда погружение заканчивается или вы начинаете новую карту; идущий забег показан с пометкой «текущий».",
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
        items(list.withIndex().toList()) { (i, r) ->
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", Modifier.width(32.dp), fontSize = 20.sp, fontWeight = FontWeight.Black,
                        color = if (i < 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.name, fontWeight = FontWeight.SemiBold)
                            if (r === live) Text("текущий", Modifier.padding(start = 8.dp).clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary).padding(horizontal = 6.dp, vertical = 1.dp),
                                color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(Instant.ofEpochMilli(r.at).atZone(ZoneId.systemDefault()).toLocalDate().toString(), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${r.score}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(store: Store, allowReset: Boolean = true, onBack: () -> Unit) {
    var cheats by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Page("Настройки", onBack, store) {
        val d = store.d
        item {
            Card {
                SettingRow("Буй долгим нажатием", "Короткое нажатие открывает клетку, долгое ставит буй (и наоборот в режиме «Ставить буи»)",
                    d.longTapFlag) { d.longTapFlag = it; store.changed() }
                SettingRow("Вибрация", "Отклик при постановке буя и подрыве", d.vibration) { d.vibration = it; store.changed() }
            }
        }
        item {
            Card {
                Text("Как заработать кристаллы", fontWeight = FontWeight.Bold)
                Text("• каждый решённый сектор — от 1 до 5: чем длиннее серия без подрывов, тем больше\n" +
                    "• подарки — в каждом секторе под 1–3 пустыми клетками спрятано по 1\n• особые сектора (фиолетовая рамка) — +3\n" +
                    "• хранилища — +10…20, когда сектор решён и вскрыт (все 8 соседей или Батискаф)\n• цепная реакция (3+ сектора за ход)\n" +
                    "• достопримечательности (ракушки, рифы, корабли) — 5 за каждый их сектор\n" +
                    "• первый сектор дня — +5, новая дальность каждые 5 секторов — +5\n" +
                    "• сундук глубин каждые $CHEST_EVERY секторов — +$CHEST_REWARD\n• итог погружения — 1 за каждые 250 очков\n" +
                    "• новый уровень, ежедневная вахта, задания и достижения", style = MaterialTheme.typography.bodySmall)
            }
        }
        // reset from inside a running game would pull the map out from under it
        if (allowReset) item {
            OutlinedButton(onClick = { confirm = true }, Modifier.fillMaxWidth()) { Text("Сбросить весь прогресс", color = MaterialTheme.colorScheme.error) }
        }
        // test build only: R8 drops the item and the cheats window from release
        if (com.skeorz.lab.deepsweep.BuildConfig.CHEATS) item {
            OutlinedButton(onClick = { cheats = true }, Modifier.fillMaxWidth()) {
                Text("🛠 Читы (только debug)", color = Color(0xFFFF4D5E), fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Text(
                "Глубинный сапёр 1.0 · без рекламы и трекеров" +
                    if (com.skeorz.lab.deepsweep.BuildConfig.PREMIUM) "" else " и покупок",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (com.skeorz.lab.deepsweep.BuildConfig.CHEATS && cheats) CheatsWindow(store) { cheats = false }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Сбросить прогресс?") },
        text = { Text("Удалятся кристаллы, уровень, покупки, достижения, рекорды и все карты. Это нельзя отменить.") },
        confirmButton = { TextButton(onClick = { confirm = false; store.reset(); onBack() }) { Text("Сбросить", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
    )
}

/** Debug cheats behind watermarks; every switch is saved, but only a debug build honours it. */
@Composable
private fun CheatsWindow(store: Store, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    store.rev
    val d = store.d
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(cs.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackArrow(onClose)
                Spacer(Modifier.width(8.dp))
                Text("Читы", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF4D5E))
            }
            Text("Только для отладки: в релизной сборке этого окна нет, а включённые читы не действуют. " +
                "Пока включён хотя бы один чит, в игре видны водяные знаки.", style = MaterialTheme.typography.bodySmall)
            Card {
                SettingRow("Бесконечные кристаллы", "Покупки и разблокировка секторов не списывают кристаллы", d.cheatCrystals) {
                    d.cheatCrystals = it; store.changed()
                }
                SettingRow("Бессмертие", "Мина не заканчивает игру и не блокирует сектор — просто помечается буем", d.cheatImmortal) {
                    d.cheatImmortal = it; store.changed()
                }
                SettingRow("Бесконечные бустеры", "Бустеры не тратятся, даже если их нет в запасе", d.cheatBoosters) {
                    d.cheatBoosters = it; store.changed()
                }
                SettingRow("Кислородный баллон", "В «Погружении на время» время не заканчивается", d.cheatOxygen) {
                    d.cheatOxygen = it; store.changed()
                }
            }
        }
        CheatWatermark(0.35f)
    }
}

@Composable
private fun SettingRow(title: String, desc: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.bodySmall)
        }
        Switch(value, onChange)
    }
}
