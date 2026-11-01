package com.skeorz.lab.deepsweep.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.skeorz.lab.deepsweep.meta.CHEST_EVERY
import com.skeorz.lab.deepsweep.meta.CHEST_REWARD
import com.skeorz.lab.deepsweep.meta.S
import com.skeorz.lab.deepsweep.meta.Store
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.roundToLong
import kotlin.random.Random

@Serializable
class SessionSave(
    val seed: Long, val started: Boolean, val score: Long, val seconds: Long, val clean: Int,
    val tutorialStep: Int, val chunks: List<ChunkSave>, val ox: Int = 0, val oy: Int = 0, val difficulty: String = Difficulty.NORMAL.name,
    val shield: Boolean = false,
    val landmarks: List<Long> = emptyList(),
)

private val json = Json { ignoreUnknownKeys = true }

/** A booster animation on sector [chunk]; [arg] is its target (tile or 3×3 window, local index). */
class Fx(val kind: String, val chunk: Chunk, val arg: Int, val durationMs: Int, val done: () -> Unit)

/** Rules of one run on top of [World]: score, timer, rewards, boosters, tutorial. */
class Session(val mode: Mode, val world: World, private val store: Store, private val file: File) {
    var score by mutableLongStateOf(0L)
    var seconds by mutableLongStateOf(0L)
    var over by mutableStateOf(false)
    var tick by mutableIntStateOf(0)
    var flagMode by mutableStateOf(false)
    var booster by mutableStateOf<String?>(null)
    var lockDialog by mutableStateOf<Chunk?>(null)
    var tutorialStep by mutableIntStateOf(0)
    var boomSignal by mutableIntStateOf(0)
    var buoySignal by mutableIntStateOf(0)

    /** The next mine is absorbed (booster «Щит»). */
    var shield by mutableStateOf(false)

    /** Sector whose mines are shown (booster «Сонар», id "flash"); cleared when its effect ends. */
    var flash by mutableStateOf<Chunk?>(null)

    /** Booster animation on the board; [Fx.done] applies its result when the game screen finishes it. */
    var fx by mutableStateOf<Fx?>(null)
    var fxT by mutableFloatStateOf(0f)

    /** Trawl: the tile picked first; the next tap on a neighbour gives the direction. */
    var trawlFrom by mutableStateOf<Pair<Int, Int>?>(null)

    /** Bumped when the shield breaks / oxygen is used, for the screen-wide effects. */
    var shieldBreak by mutableIntStateOf(0)
    var bubbles by mutableIntStateOf(0)

    /** Vault aimed at by the bathyscaphe: its corners are drawn see-through while aiming. */
    val aimingVault get() = booster == "lockpick"

    /** Crystals earned since this session was opened (shown in the pause menu). */
    private val earnedAtStart = store.stat(S.CRYSTALS_EARNED)
    val sessionCrystals get() = store.stat(S.CRYSTALS_EARNED) - earnedAtStart

    /** Sectors solved in a row without hitting a mine. */
    var clean by mutableIntStateOf(0)
        private set

    /** Crystals for the next solved sector: +1 for every 5 sectors of the clean series, up to 5. */
    val nextReward get() = (1 + clean / 5).coerceAtMost(5)

    val timeLeft get() = (mode.timeLimit - seconds).coerceAtLeast(0)

    fun primary(wx: Int, wy: Int) = if (flagMode) flag(wx, wy) else open(wx, wy)
    fun secondary(wx: Int, wy: Int) = if (flagMode) open(wx, wy) else flag(wx, wy)

    fun tap(wx: Int, wy: Int) {
        if (over || fx != null) return
        trawlFrom?.let { (fx0, fy0) ->
            trawlFrom = null
            val dx = wx - fx0
            val dy = wy - fy0
            if (kotlin.math.abs(dx) + kotlin.math.abs(dy) != 1) { store.toast("Трал отменён"); tick++; return }
            if (!store.useBooster("trawl")) return
            apply(world.trawl(fx0, fy0, dx, dy))
            store.changed()
            return
        }
        val b = booster
        if (b != null) return useBooster(b, wx, wy)
        primary(wx, wy)
    }

    fun open(wx: Int, wy: Int) {
        if (over) return
        val c = world.chunkAt(wx, wy)
        if (c.locked) { lockDialog = c; return }
        if (world.state(wx, wy) == OPEN) {
            val o = world.chord(wx, wy)
            if (o.chorded) { store.add(S.CHORDS); if (tutorialStep == 2) tutorialStep = 3 }
            apply(o)
        } else {
            apply(world.reveal(wx, wy))
            if (tutorialStep == 0) tutorialStep = 1
        }
    }

    fun flag(wx: Int, wy: Int) {
        if (over) return
        val c = world.chunkAt(wx, wy)
        if (c.locked) { lockDialog = c; return }
        if (world.toggleFlag(wx, wy)) {
            if (tutorialStep == 1) tutorialStep = 2
            if (world.state(wx, wy) == FLAG) buoySignal++ // vibrate only when a buoy is placed
            tick++
        }
    }

    private fun useBooster(id: String, wx: Int, wy: Int) {
        val c = world.chunkAt(wx, wy)
        booster = null
        fun nope() = store.toast("Здесь этот бустер не сработает")
        fun play(f: Fx) { store.useBooster(id); store.changed(); fxT = 0f; fx = f }
        when (id) {
            "sonar" -> world.periscopeTarget(c)?.let { i -> play(Fx(id, c, i, 1300) { apply(world.revealLocal(c, i)) }) } ?: nope()
            "echo" -> world.echoTarget(c)?.let { tl -> play(Fx(id, c, tl, 1100) { apply(world.echo(c, tl)) }) } ?: nope()
            "solve" -> if (world.playable(c) && world.started) play(Fx(id, c, 0, 1000) { world.torpedo(c)?.let { apply(it) } }) else nope()
            "flash" -> if (world.playable(c)) { flash = c; play(Fx(id, c, 0, 2600) { flash = null; tick++ }) } else nope()
            "lockpick" -> if (c.kind == Kind.VAULT && !c.vaultOpen) play(Fx(id, c, 0, 900) { world.pick(c)?.let { apply(it) } }) else nope()
            "trawl" -> if (world.playable(c) && world.started) { trawlFrom = wx to wy; tick++ } else nope()
            "defuse" -> world.defuse(c)?.let { store.useBooster(id); apply(it); store.changed() } ?: nope()
            else -> nope()
        }
    }

    /** Shield and oxygen work at once; returns false if the booster can't be used right now. */
    fun activate(id: String): Boolean {
        when (id) {
            "shield" -> if (shield) { store.toast("Щит уже активен"); return false }
            "oxygen" -> if (mode.timeLimit == 0) { store.toast("Кислород работает только в «Погружении на время»"); return false }
        }
        if (!store.useBooster(id)) return false
        when (id) {
            "shield" -> { shield = true; store.toast("🛡 Щит активен до следующей мины") }
            "oxygen" -> { seconds -= 30; bubbles++; store.toast("🫧 +30 секунд") }
        }
        store.changed()
        save()
        return true
    }

    fun unlockPrice(c: Chunk) = world.unlockPrice(c)

    fun buyUnlock(c: Chunk) {
        lockDialog = null
        if (!store.spend(unlockPrice(c))) return
        world.defuse(c)?.let { apply(it) }
    }

    fun defuseWithBooster(c: Chunk) {
        lockDialog = null
        if (!store.useBooster("defuse")) { store.toast("Нет бустера «Разминирование»"); return }
        world.defuse(c)?.let { apply(it) }
        store.changed()
    }

    private var xpCarry = 0.0

    /**
     * 1 XP per press that opened safe tiles, however big the cascade, times the difficulty
     * (premium doubles it in addXp). Fractions such as 0.5 on «Лёгкий» carry over to the next press.
     */
    fun awardPressXp() {
        xpCarry += world.difficulty.xp
        val whole = (xpCarry + 1e-9).toLong() // so 1.8 × 5 is 9, not 8.999…
        if (whole > 0) { store.addXp(whole); xpCarry -= whole }
    }

    /** Crystal bonuses of one action, shown as a single toast. */
    private val bonus = linkedMapOf<String, Int>()
    private fun bonus(why: String, n: Int) { bonus[why] = (bonus[why] ?: 0) + n }

    private fun apply(o: Outcome) {
        // the immortality cheat works like an endless shield: a mine just becomes a buoy
        if (o.booms.isNotEmpty() && (shield || store.immortal)) {
            if (shield) {
                shield = false
                shieldBreak++
                store.toast("🛡 Щит принял удар — мина помечена буем")
            }
            val a = world.absorbBooms(o.booms.distinct())
            o.booms.clear()
            o.solved += a.solved
            o.vaults += a.vaults
            o.correctFlags += a.correctFlags
        }
        store.add(S.TILES, o.opened.toLong())
        if (mode != Mode.TUTORIAL && o.opened > 0) awardPressXp()
        store.add(S.FLAGS_OK, o.correctFlags.toLong())
        if (o.gifts > 0) {
            store.add(S.CRYSTAL_TILES, o.gifts.toLong())
            bonus(if (o.gifts > 1) "подарки ×${o.gifts}" else "подарок", o.gifts)
        }
        for (c in o.solved) onSolved(c)
        // a landmark pays once all of its sectors are solved
        for (c in o.solved) world.landmarkAt(c.x, c.y)?.let { l ->
            if (l.key !in world.landmarksDone && world.landmarkSolved(l)) {
                world.landmarksDone += l.key
                store.add(S.LANDMARKS)
                bonus(l.type.done.lowercase(), l.reward)
            }
        }
        // vault bonus needs the sector solved and the vault opened, in any order
        for (v in (o.solved + o.vaults).distinct()) if (v.kind == Kind.VAULT && v.solved && v.vaultOpen && !v.vaultPaid) {
            v.vaultPaid = true
            v.modified = true
            store.add(S.VAULTS)
            bonus("хранилище", 10 + Random(mix(world.seed + packKey(v.x, v.y))).nextInt(11))
        }
        if (o.solved.size >= 3 && mode != Mode.TUTORIAL) {
            store.add(S.CHAINS)
            bonus("цепная реакция ×${o.solved.size}", o.solved.size - 1)
        }
        if (bonus.isNotEmpty()) {
            store.earn(bonus.values.sum(), bonus.keys.joinToString(", "))
            bonus.clear()
        }
        if (o.unlocked.isNotEmpty()) store.toast("Сектор снова доступен")
        for (v in o.vaults) if (!v.solved) store.toast("🔓 Хранилище открыто — решите его ради кристаллов")
        for (v in o.solved) if (v.kind == Kind.VAULT && !v.vaultOpen)
            store.toast("🔒 Хранилище решено — бонус после 8 решённых соседей (${world.solvedAround(v)}/8) или Батискафа")
        if (o.booms.isNotEmpty()) onBoom()
        tick++
        if (o.solved.isNotEmpty() || o.booms.isNotEmpty() || o.vaults.isNotEmpty()) save()
    }

    private fun onSolved(c: Chunk) {
        var mul = mode.scoreMul
        if (c.kind == Kind.SPECIAL) mul *= 2
        if (c.kind == Kind.VAULT) mul *= 1.5
        val pts = ((10 + c.mineCount * 4) * mul).roundToLong()
        score += pts
        store.add(S.SCORE, pts)
        store.add(S.SECTORS)
        if (!c.flagsUsed) store.add(S.NO_FLAGS)
        if (mode.climb) store.setMax(S.CLIMB, -c.y.toLong())
        when (c.kind) {
            Kind.SPECIAL -> { store.add(S.SPECIALS); bonus("особый сектор", 3) }
            else -> {}
        }
        val reward = nextReward
        clean++
        store.setMax(S.CLEAN, clean.toLong())
        if (mode == Mode.TUTORIAL) { if (tutorialStep in 1..3) tutorialStep = 4; return }

        bonus(if (clean > 5) "сектор, серия $clean" else "сектор решён", reward)
        if (!c.flagsUsed && c.mineCount >= 8) bonus("сектор без буёв", 1)
        val record = store.stat(S.MAX_DIST)
        val dist = world.dist(c)
        for (k in record + 1..dist) if (k % 5 == 0L) bonus("новая дальность $k", 5)
        store.setMax(S.MAX_DIST, dist.toLong())
        val today = LocalDate.now().toEpochDay()
        if (store.d.firstSectorDay != today) { store.d.firstSectorDay = today; bonus("первый сектор дня", 5) }
        if (store.stat(S.SECTORS) % CHEST_EVERY == 0L) bonus("сундук глубин", CHEST_REWARD)
        store.addXp(((5 + c.mineCount) * world.difficulty.xp).roundToLong())
    }

    private fun onBoom() {
        boomSignal++
        store.add(S.MINES_HIT)
        clean = 0
        when {
            mode.hardcore -> end()
            mode.timeLimit > 0 && !store.endlessOxygen -> { seconds += 15; if (timeLeft == 0L) end() }
        }
    }

    /** Called once per second while the game screen is active. */
    fun secondPassed() {
        if (over || !world.started) return
        if (mode.timeLimit > 0 && store.endlessOxygen) return // the oxygen cheat stops the countdown
        seconds++
        if (mode.timeLimit > 0 && timeLeft == 0L) end()
    }

    fun end() {
        if (over) return
        over = true
        store.addRun(mode, score)
        file.delete()
        store.changed()
    }

    /** Abandoning an endless run still records its score. */
    fun abandon() {
        if (!over) store.addRun(mode, score)
        over = true
        file.delete()
    }

    fun save() {
        if (over || !world.started) return
        file.parentFile?.mkdirs()
        val s = SessionSave(world.seed, world.started, score, seconds, clean, tutorialStep, world.saveChunks(), world.ox, world.oy, world.difficulty.name, shield, world.landmarksDone.toList())
        file.writeText(json.encodeToString(SessionSave.serializer(), s))
        store.save()
    }

    companion object {
        fun fileFor(dir: File, mode: Mode) = File(dir, "worlds/${mode.name}.json")

        fun canContinue(dir: File, mode: Mode) = fileFor(dir, mode).exists()

        /** Score of the unfinished saved map of [mode], 0 if none. */
        fun savedScore(dir: File, mode: Mode): Long =
            runCatching { json.decodeFromString<SessionSave>(fileFor(dir, mode).readText()).score }.getOrDefault(0L)

        fun load(mode: Mode, store: Store, dir: File, fresh: Boolean, difficulty: Difficulty = Difficulty.NORMAL): Session {
            val f = fileFor(dir, mode)
            var saved = runCatching { json.decodeFromString<SessionSave>(f.readText()) }.getOrNull()
            if (fresh && saved != null) { store.addRun(mode, saved.score); saved = null }
            if (saved == null) {
                f.delete()
                store.add(S.GAMES)
                store.changed()
                return Session(mode, World(mode, Random.nextLong(), if (mode == Mode.ENDLESS) difficulty else Difficulty.NORMAL), store, f)
            }
            val diff = runCatching { Difficulty.valueOf(saved.difficulty) }.getOrDefault(Difficulty.NORMAL)
            val w = World(mode, saved.seed, diff).apply { started = saved.started; ox = saved.ox; oy = saved.oy; loadChunks(saved.chunks) }
            return Session(mode, w, store, f).apply {
                score = saved.score
                seconds = saved.seconds
                clean = saved.clean
                tutorialStep = saved.tutorialStep
                shield = saved.shield
                world.landmarksDone += saved.landmarks
            }
        }
    }
}
