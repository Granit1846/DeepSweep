package com.skeorz.lab.deepsweep.meta

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.skeorz.lab.deepsweep.game.Difficulty
import com.skeorz.lab.deepsweep.BuildConfig
import com.skeorz.lab.deepsweep.game.Mode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import kotlin.math.pow
import kotlin.random.Random

@Serializable
data class MissionState(val id: String, val target: Long, val baseline: Long, val reward: Int, var claimed: Boolean = false)


@Serializable
data class RunRecord(val mode: String, val score: Long, val at: Long, val name: String)

@Serializable
data class Data(
    var crystals: Int = 25,
    var xp: Long = 0,
    var level: Int = 1,
    var name: String = "Акванавт",
    var avatar: String = "🐙",
    var frame: String = "frame_none",
    var title: String = "Новичок",
    val boosters: MutableMap<String, Int> = mutableMapOf("sonar" to 3, "defuse" to 1, "solve" to 1),
    val owned: MutableSet<String> = ITEMS.filter { it.price == 0 }.map { it.id }.toMutableSet(),
    var theme: String = "theme_deep",
    var mineSkin: String = "mine_naval",
    var flagSkin: String = "flag_buoy",
    var numbers: String = "num_sonar",
    val stats: MutableMap<String, Long> = mutableMapOf(),
    var tutorialDone: Boolean = false,
    var lastLogin: Long = -1,
    var streak: Int = 0,
    var missionDay: Long = -1,
    val missions: MutableList<MissionState> = mutableListOf(),
    var missionBonus: Boolean = false,
    val runs: MutableList<RunRecord> = mutableListOf(),
    var longTapFlag: Boolean = true,
    var vibration: Boolean = true,
    var lastMode: String = Mode.ENDLESS.name,
    var firstSectorDay: Long = -1,
    var endlessDifficulty: String = Difficulty.NORMAL.name,
    /** Active for everyone until a way to get it exists. */
    var premium: Boolean = true,
    /** Achievement id → reached tier (1 bronze … 4 platinum; 1 for one-time achievements). */
    val tiers: MutableMap<String, Int> = mutableMapOf(),
    /** Earned, not yet opened packs (each is its list of boosters); all of one kind, opened one at a time. */
    val packs: MutableList<List<String>> = mutableListOf(),
    /** Debug-only cheats; ignored in release builds, see [Store.debug]. */
    var cheatCrystals: Boolean = false,
    var cheatImmortal: Boolean = false,
    var cheatBoosters: Boolean = false,
    var cheatOxygen: Boolean = false,
)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** All persistent player progress. Crystals are only ever earned through play. */
class Store(private val dir: File) {
    private val file = File(dir, "progress.json")
    var d: Data = runCatching { json.decodeFromString<Data>(file.readText()) }.getOrElse { Data() }
        private set

    /** Bumped on every change so Compose re-reads [d]. */
    var rev by mutableIntStateOf(0)
        private set
    val toasts = mutableStateListOf<String>()

    fun save() {
        val tmp = File(dir, "progress.tmp")
        tmp.writeText(json.encodeToString(Data.serializer(), d))
        tmp.renameTo(file) || run { file.delete(); tmp.renameTo(file) }
    }

    fun changed() { rev++; save() }
    fun toast(s: String) { toasts += s }

    fun stat(k: String) = d.stats[k] ?: 0L

    fun add(k: String, n: Long = 1) {
        if (n == 0L) return
        d.stats[k] = stat(k) + n
        checkAchievements()
    }

    fun setMax(k: String, v: Long) {
        if (v > stat(k)) { d.stats[k] = v; checkAchievements() }
    }

    fun earn(base: Int, why: String) {
        if (base <= 0) return
        val n = if (premium) base * PREMIUM_CRYSTALS else base
        d.crystals += n
        add(S.CRYSTALS_EARNED, n.toLong())
        toast("+$n 💎  $why")
    }

    // --- debug cheats: only a debuggable build may use them, whatever the save says ---

    /** Set by the activity from the app's debuggable flag. */
    var debug = BuildConfig.CHEATS

    /** Premium in effect: only builds that ship it (test build) honour the saved switch. */
    val premium get() = BuildConfig.PREMIUM && d.premium
    val infiniteCrystals get() = debug && d.cheatCrystals
    val immortal get() = debug && d.cheatImmortal
    val infiniteBoosters get() = debug && d.cheatBoosters
    val endlessOxygen get() = debug && d.cheatOxygen
    val anyCheat get() = infiniteCrystals || immortal || infiniteBoosters || endlessOxygen

    fun spend(n: Int): Boolean {
        if (infiniteCrystals) return true
        if (d.crystals < n) { toast("Не хватает кристаллов: нужно $n 💎"); return false }
        d.crystals -= n
        add(S.CRYSTALS_SPENT, n.toLong())
        changed()
        return true
    }

    // --- level ---

    fun xpNeed(level: Int = d.level) = (60 * level.toDouble().pow(1.35)).toLong()

    fun addXp(base: Long) {
        d.xp += if (premium) base * PREMIUM_XP else base
        while (d.xp >= xpNeed()) {
            d.xp -= xpNeed()
            d.level++
            setMax(S.LEVEL, d.level.toLong())
            earn(5 + d.level * 2, "уровень ${d.level}")
            for (m in Mode.entries) if (m.unlockLevel == d.level && !premium) toast("Открыт режим «${m.title}»")
        }
    }

    // --- modes ---

    // modes open by level or with premium only; crystals cannot buy them
    fun modeOpen(m: Mode) = premium || d.level >= m.unlockLevel

    /** The mode the main button plays: the last one, unless it has locked again (e.g. premium ended). */
    fun lastPlayable() = runCatching { Mode.valueOf(d.lastMode) }.getOrDefault(Mode.ENDLESS).takeIf { modeOpen(it) } ?: Mode.ENDLESS

    // --- daily login ---

    /** Returns the reward granted today, or null if already collected. */
    fun dailyLogin(today: Long = LocalDate.now().toEpochDay()): Int? {
        if (d.lastLogin == today) return null
        d.streak = if (d.lastLogin == today - 1) d.streak + 1 else 1
        d.lastLogin = today
        setMax(S.STREAK, d.streak.toLong())
        val reward = LOGIN_REWARDS[(d.streak - 1) % LOGIN_REWARDS.size]
        earn(reward, "награда за ${d.streak}-й день")
        changed()
        return reward
    }

    // --- missions ---

    fun refreshMissions(today: Long = LocalDate.now().toEpochDay()) {
        if (d.missionDay == today && d.missions.size >= DAILY_MISSIONS) return
        if (d.missionDay != today) { d.missions.clear(); d.missionBonus = false }
        val r = Random(today)
        for (t in MISSIONS.shuffled(r)) {
            if (d.missions.size >= DAILY_MISSIONS) break
            if (d.missions.any { it.id == t.id }) continue
            val target = t.range.random(r).toLong()
            d.missions += MissionState(t.id, target, stat(t.stat), t.reward + (target - t.range.first).toInt() / 10)
        }
        d.missionDay = today
        changed()
    }

    fun template(m: MissionState) = MISSIONS.first { it.id == m.id }
    fun missionText(m: MissionState) = template(m).text(m.target)
    fun progress(m: MissionState) = (stat(template(m).stat) - m.baseline).coerceIn(0, m.target)

    fun claim(m: MissionState) {
        if (m.claimed || progress(m) < m.target) return
        m.claimed = true
        add(S.MISSIONS)
        earn(m.reward, "задание выполнено")
        if (!d.missionBonus && d.missions.all { it.claimed }) {
            d.missionBonus = true
            earn(MISSION_BONUS, "все задания дня")
        }
        changed()
    }

    // --- achievements ---

    fun checkAchievements() {
        var granted = false
        for (a in ACHIEVEMENTS) {
            // earn() re-enters this check, so re-read the tier on every step instead of caching it
            while (tier(a) < a.goals.size && stat(a.stat) >= a.goals[tier(a)]) {
                val tier = tier(a) + 1
                d.tiers[a.id] = tier
                granted = true
                val label = if (a.tiered) "${a.name} — ${TIER_NAMES[tier - 1]}" else a.name
                toast("🏆 $label")
                // before earn(): it re-enters this check, and packs must queue bronze → platinum
                if (a.packFor(tier, premium) > 0) grantPack(a.packFor(tier, premium))
                earn(a.rewardFor(tier), "достижение")
            }
        }
        // save right away: a tier lost to a killed app would be granted (and paid) again
        if (granted) changed()
    }

    /** Frames that come from reaching an achievement's last tier. */
    private fun earnedFrame(id: String) = ACHIEVEMENTS.any { it.frame == id && tier(it) == it.goals.size }

    fun tier(a: Achievement) = d.tiers[a.id] ?: 0

    // --- booster packs ---

    /**
     * Earned packs wait sealed in [Data.packs]; a pack's boosters reach the inventory only
     * when the player opens it.
     */
    fun grantPack(size: Int, random: Random = Random.Default) {
        val total = PACK_WEIGHTS.sumOf { it.second }
        d.packs += List(size) {
            var roll = random.nextInt(total)
            PACK_WEIGHTS.first { (_, w) -> (roll < w).also { roll -= w } }.first
        }
        toast("📦 Новый пак бустеров")
        changed()
    }

    /** Opens the oldest sealed pack: its boosters go to the inventory; returns its cards. */
    fun openPack(): List<String> {
        val cards = d.packs.removeFirstOrNull() ?: return emptyList()
        for (id in cards) d.boosters[id] = (d.boosters[id] ?: 0) + 1
        changed()
        return cards
    }

    /** Opens every sealed pack at once; returns all their cards together. */
    fun openAllPacks(): List<String> {
        val cards = d.packs.flatten()
        for (id in cards) d.boosters[id] = (d.boosters[id] ?: 0) + 1
        d.packs.clear()
        changed()
        return cards
    }

    fun titles() = listOf("Новичок") + ACHIEVEMENTS.filter { tier(it) > 0 }.map { it.name }

    // --- shop ---

    /** Premium opens every cosmetic item; boosters stay consumables bought with crystals. */
    fun owns(item: Item) = item.id in d.owned || earnedFrame(item.id) || (premium && item.cat != Cat.BOOSTER)

    fun buy(item: Item) {
        if (item.cat == Cat.BOOSTER) {
            if (spend(item.price)) { d.boosters[item.id] = (d.boosters[item.id] ?: 0) + 1; changed() }
        } else if (owns(item)) {
            select(item)
        } else if (item.price >= 0 && d.level >= item.level && spend(item.price)) {
            d.owned += item.id
            select(item)
        }
    }

    fun setPremium(on: Boolean) {
        d.premium = on
        val all = ITEMS.filter { owns(it) }.map { it.id }.toSet()
        // a skin used only through premium falls back to the free default when premium ends
        if (!on) {
            if (d.theme !in all) d.theme = "theme_deep"
            if (d.mineSkin !in all) d.mineSkin = "mine_naval"
            if (d.flagSkin !in all) d.flagSkin = "flag_buoy"
            if (d.numbers !in all) d.numbers = "num_sonar"
            if (d.frame !in all) d.frame = "frame_none"
        }
        changed()
    }

    fun select(item: Item) {
        if (!owns(item)) return
        when (item.cat) {
            Cat.THEME -> d.theme = item.id
            Cat.MINE -> d.mineSkin = item.id
            Cat.FLAG -> d.flagSkin = item.id
            Cat.NUMBERS -> d.numbers = item.id
            Cat.FRAME -> d.frame = item.id
            Cat.BOOSTER -> {}
        }
        changed()
    }

    /** Whether the booster can be used now (always, with the infinite-boosters cheat). */
    fun hasBooster(id: String) = infiniteBoosters || (d.boosters[id] ?: 0) > 0

    fun useBooster(id: String): Boolean {
        if (infiniteBoosters) { add(S.BOOSTERS); return true }
        val n = d.boosters[id] ?: 0
        if (n <= 0) return false
        d.boosters[id] = n - 1
        add(S.BOOSTERS)
        return true
    }

    // --- records ---

    fun addRun(mode: Mode, score: Long) {
        if (score <= 0 || mode == Mode.TUTORIAL) return
        earn((score / 250).toInt().coerceAtMost(40), "итог погружения: $score очк.")
        d.runs += RunRecord(mode.name, score, System.currentTimeMillis(), d.name)
        d.runs.sortByDescending { it.score }
        while (d.runs.size > 300) d.runs.removeAt(d.runs.lastIndex)
        when (mode) {
            Mode.HARDCORE -> setMax(S.BEST_HARDCORE, score)
            Mode.TIMED -> setMax(S.BEST_TIMED, score)
            else -> {}
        }
        changed()
    }

    fun best(mode: Mode) = d.runs.filter { it.mode == mode.name }.maxOfOrNull { it.score } ?: 0L

    fun reset() {
        d = Data()
        File(dir, "worlds").deleteRecursively()
        changed()
    }
}
