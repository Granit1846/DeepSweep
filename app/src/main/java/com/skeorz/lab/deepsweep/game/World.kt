package com.skeorz.lab.deepsweep.game

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

const val CS = 8
const val CN = CS * CS

const val HIDDEN: Byte = 0
const val OPEN: Byte = 1
const val FLAG: Byte = 2
const val BOOM: Byte = 3

enum class Kind { NORMAL, SPECIAL, VAULT, WALL }



fun packKey(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)

/** splitmix64 finalizer: spreads chunk coordinates into independent seeds. */
fun mix(v: Long): Long {
    var z = v + -0x61c8864680b583ebL
    z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
    z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
    return z xor (z ushr 31)
}

class Chunk(val x: Int, val y: Int, val kind: Kind) {
    var mines = 0L
    val state = ByteArray(CN)
    /** Bit i = a 1-crystal gift under safe tile i, collected when the tile opens. */
    var gifts = 0L
    var solved = false
    var locked = false
    /** Vault bonus unlocked: by 4 solved neighbours or the bathyscaphe. Play itself is never blocked. */
    var vaultOpen = kind != Kind.VAULT
    /** The vault bonus has been paid (needs both the sector solved and [vaultOpen]). */
    var vaultPaid = false
    var flagsUsed = false
    var modified = false

    val mineCount get() = java.lang.Long.bitCount(mines)
    fun isMine(i: Int) = (mines ushr i) and 1L == 1L
}

/** What one player action changed; the session turns it into score, crystals and stats. */
class Outcome {
    var opened = 0
    var gifts = 0
    var correctFlags = 0
    val booms = mutableListOf<Chunk>()
    val solved = mutableListOf<Chunk>()
    val unlocked = mutableListOf<Chunk>()
    val vaults = mutableListOf<Chunk>()
    var chorded = false
}

@Serializable
class ChunkSave(
    val x: Int, val y: Int, val mines: Long, val state: String, val crystal: Int = -1,
    val solved: Boolean, val locked: Boolean, val vaultOpen: Boolean, val flagsUsed: Boolean,
    val vaultPaid: Boolean? = null,
    val gifts: Long = 0,
)

class World(val mode: Mode, val seed: Long, val difficulty: Difficulty = Difficulty.NORMAL) {
    val chunks = HashMap<Long, Chunk>()
    var started = false

    /** Chunk of the first opening: difficulty grows with the distance from it. */
    var ox = 0
    var oy = 0

    fun dist(c: Chunk) = dist(c.x, c.y)

    /** Player-facing sector coordinates: the first opening is (0, 0), X grows right, Y grows up. */
    fun coords(cx: Int, cy: Int) = (cx - ox) to (oy - cy)
    private fun dist(cx: Int, cy: Int) = max(abs(cx - ox), abs(cy - oy))

    /** Highest number a tile may show, by its chunk's ring around the first opening. */
    fun maxNumber(cx: Int, cy: Int): Int {
        val d = dist(cx, cy)
        val r = difficulty.rings
        return when {
            d <= r[0] -> 3
            d <= r[1] -> 4
            d <= r[2] -> 5
            d <= r[3] -> 7
            else -> 8
        }
    }

    fun chunk(cx: Int, cy: Int): Chunk = chunks.getOrPut(packKey(cx, cy)) { generate(cx, cy) }
    fun chunkAt(wx: Int, wy: Int) = chunk(Math.floorDiv(wx, CS), Math.floorDiv(wy, CS))
    private fun li(wx: Int, wy: Int) = Math.floorMod(wy, CS) * CS + Math.floorMod(wx, CS)

    fun isMine(wx: Int, wy: Int) = chunkAt(wx, wy).isMine(li(wx, wy))
    fun state(wx: Int, wy: Int) = chunkAt(wx, wy).state[li(wx, wy)]

    fun count(wx: Int, wy: Int): Int {
        var n = 0
        for (dy in -1..1) for (dx in -1..1) if ((dx != 0 || dy != 0) && isMine(wx + dx, wy + dy)) n++
        return n
    }

    fun playable(c: Chunk) = c.kind != Kind.WALL && !c.locked && !c.solved

    // --- landmarks: multi-sector structures (shell, reef, wreck…) placed one per 6×6 zone at most ---

    /** Paid landmarks (their [Landmark.key]). */
    val landmarksDone = HashSet<Long>()
    private val landmarkCache = HashMap<Long, Landmark?>()
    private val kindCache = HashMap<Long, Kind>()

    fun landmark(rx: Int, ry: Int): Landmark? = landmarkCache.getOrPut(packKey(rx, ry)) {
        if (mode.climb || mode == Mode.TUTORIAL || (rx == 0 && ry == 0)) return@getOrPut null
        val r = Random(mix(seed * 31 + packKey(rx, ry)))
        if (r.nextInt(100) >= 13) return@getOrPut null // ~13% of zones
        val type = LandmarkType.entries[r.nextInt(LandmarkType.entries.size)]
        val x = ox + rx * ZONE + 1 + r.nextInt(ZONE - type.w - 1)
        val y = oy + ry * ZONE + 1 + r.nextInt(ZONE - type.h - 1)
        Landmark(packKey(rx, ry), type, x, y)
    }

    fun landmarkAt(cx: Int, cy: Int): Landmark? =
        landmark(Math.floorDiv(cx - ox, ZONE), Math.floorDiv(cy - oy, ZONE))?.takeIf { packKey(cx, cy) in it.cells }

    fun landmarkSolved(l: Landmark) = l.cells.all { chunks[it]?.solved == true }

    /** Sector kind without generating its mines (the overview uses it for unvisited sectors). */
    fun kindOf(cx: Int, cy: Int): Kind = kindCache.getOrPut(packKey(cx, cy)) {
        val roll = Random(mix(seed xor mix(packKey(cx, cy)))).nextInt(1000)
        val d = dist(cx, cy)
        when {
            mode.climb && (cx - ox !in -1..1 || cy > oy) -> Kind.WALL
            mode == Mode.TUTORIAL -> Kind.NORMAL
            landmarkAt(cx, cy) != null -> Kind.NORMAL
            roll < 15 && d >= 3 -> if (vaultWins(cx, cy)) Kind.VAULT else Kind.NORMAL // ~1.5% candidates
            d >= 2 && roll < 53 -> Kind.SPECIAL // ~3.8%
            else -> Kind.NORMAL
        }
    }

    /** Priority of a vault candidate (lower wins), or null if the sector cannot be a vault. */
    private fun vaultPriority(cx: Int, cy: Int): Long? {
        val key = packKey(cx, cy)
        if (dist(cx, cy) < 3 || Random(mix(seed xor mix(key))).nextInt(1000) >= 15) return null
        if (landmarkAt(cx, cy) != null) return null
        return mix(seed + key * 7919)
    }

    /**
     * Vaults keep at least 3 sectors between them: a candidate becomes a vault only if no other
     * candidate within 3 sectors has a lower priority. Order-independent, so generation stays deterministic.
     */
    private fun vaultWins(cx: Int, cy: Int): Boolean {
        val mine = vaultPriority(cx, cy) ?: return false
        for (dy in -VAULT_GAP..VAULT_GAP) for (dx in -VAULT_GAP..VAULT_GAP) {
            if (dx == 0 && dy == 0) continue
            val other = vaultPriority(cx + dx, cy + dy) ?: continue
            if (other < mine) return false
        }
        return true
    }

    private fun generate(cx: Int, cy: Int): Chunk {
        val r = Random(mix(seed xor mix(packKey(cx, cy))))
        val d = dist(cx, cy)
        r.nextInt(1000) // the kind roll, see kindOf
        val kind = kindOf(cx, cy)
        val c = Chunk(cx, cy, kind)
        if (kind == Kind.WALL) return c
        val brutal = mode.brutalMines
        if (brutal != null) {
            var left = brutal.first + r.nextInt(brutal.last - brutal.first + 1)
            if (kind == Kind.SPECIAL) left += 3
            // Bounded: near the start the number limits may not leave room for every mine.
            repeat(CN * 6) {
                if (left == 0) return@repeat
                val i = r.nextInt(CN)
                if (!c.isMine(i) && canPlace(c, i)) { c.mines = c.mines or (1L shl i); left-- }
            }
        } else {
            val depth = if (mode.climb) abs(cy - oy) else d
            var p = min(mode.maxDensity, mode.baseDensity + mode.growth * depth)
            if (kind == Kind.SPECIAL) p *= 1.45
            for (i in 0 until CN) if (r.nextDouble() < p && canPlace(c, i)) c.mines = c.mines or (1L shl i)
        }
        if (mode.gifts) {
            val safe = (0 until CN).filter { !c.isMine(it) }.shuffled(r)
            for (i in safe.take(1 + r.nextInt(3))) c.gifts = c.gifts or (1L shl i)
        }
        return c
    }

    /**
     * A new mine must not push any neighbouring tile above its ring's [maxNumber].
     * Only chunks that already exist are counted; chunks generated later run the same check,
     * so the limit holds across borders. Before the first opening the field is a placeholder.
     */
    private fun canPlace(c: Chunk, i: Int): Boolean {
        if (!started) return true
        val wx = c.x * CS + i % CS
        val wy = c.y * CS + i / CS
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val tx = wx + dx
            val ty = wy + dy
            val limit = maxNumber(Math.floorDiv(tx, CS), Math.floorDiv(ty, CS))
            if (limit >= 8) continue
            var n = 0
            for (ny in -1..1) for (nx in -1..1) if ((nx != 0 || ny != 0) && existingMine(c, tx + nx, ty + ny)) n++
            if (n + 1 > limit) return false
        }
        return true
    }

    private fun existingMine(generating: Chunk, wx: Int, wy: Int): Boolean {
        val cx = Math.floorDiv(wx, CS)
        val cy = Math.floorDiv(wy, CS)
        val c = if (cx == generating.x && cy == generating.y) generating else chunks[packKey(cx, cy)] ?: return false
        return c.isMine(li(wx, wy))
    }

    /** First opening: centre the difficulty rings on it and regenerate the placeholder field. */
    private fun begin(wx: Int, wy: Int) {
        started = true
        ox = Math.floorDiv(wx, CS)
        oy = Math.floorDiv(wy, CS)
        chunks.clear()
        landmarkCache.clear() // zones and kinds are laid out around the first opening
        kindCache.clear()
        clearAround(wx, wy)
    }

    /** The first opening of a map is always on an empty 3×3 area. */
    private fun clearAround(wx: Int, wy: Int) {
        for (dy in -1..1) for (dx in -1..1) {
            val c = chunkAt(wx + dx, wy + dy)
            if (c.kind == Kind.WALL) continue
            c.mines = c.mines and (1L shl li(wx + dx, wy + dy)).inv()
            c.modified = true
        }
    }

    fun reveal(wx: Int, wy: Int, out: Outcome = Outcome()): Outcome {
        var c = chunkAt(wx, wy)
        val i = li(wx, wy)
        if (!playable(c) || c.state[i] != HIDDEN) return out
        if (!started) { begin(wx, wy); c = chunkAt(wx, wy) }
        if (c.isMine(i)) {
            c.state[i] = BOOM
            c.modified = true
            if (mode.lockOnMine) c.locked = true
            out.booms += c
            return out
        }
        val touched = HashSet<Chunk>()
        flood(wx, wy, out, touched)
        settle(touched, out)
        return out
    }

    private fun flood(wx: Int, wy: Int, out: Outcome, touched: MutableSet<Chunk>) {
        // On an infinite field zero areas can be unbounded: cascade only through the 3×3 chunks around the tap.
        val ocx = Math.floorDiv(wx, CS)
        val ocy = Math.floorDiv(wy, CS)
        val queue = ArrayDeque<Long>()
        queue.add(packKey(wx, wy))
        while (queue.isNotEmpty()) {
            val k = queue.removeFirst()
            val x = (k shr 32).toInt()
            val y = k.toInt()
            if (abs(Math.floorDiv(x, CS) - ocx) > 1 || abs(Math.floorDiv(y, CS) - ocy) > 1) continue
            val c = chunkAt(x, y)
            val i = li(x, y)
            if (!playable(c) || c.state[i] != HIDDEN || c.isMine(i)) continue
            c.state[i] = OPEN
            c.modified = true
            touched += c
            out.opened++
            if ((c.gifts ushr i) and 1L == 1L) {
                out.gifts++
                c.gifts = c.gifts and (1L shl i).inv()
            }
            if (count(x, y) == 0) {
                for (dy in -1..1) for (dx in -1..1) if (dx != 0 || dy != 0) queue.add(packKey(x + dx, y + dy))
            }
        }
    }

    private fun settle(touched: Collection<Chunk>, out: Outcome) {
        for (c in touched) {
            // Numbers on opened tiles depend on neighbour mines, and generation order affects them: keep neighbours saved.
            for (dy in -1..1) for (dx in -1..1) chunk(c.x + dx, c.y + dy).modified = true
            checkSolved(c, out)
        }
    }

    private fun checkSolved(c: Chunk, out: Outcome) {
        if (c.solved || c.locked || c.kind == Kind.WALL) return
        for (i in 0 until CN) if (!c.isMine(i) && c.state[i] != OPEN) return
        c.solved = true
        for (i in 0 until CN) if (c.isMine(i)) {
            if (c.state[i] == FLAG) out.correctFlags++
            if (c.state[i] == HIDDEN) c.state[i] = FLAG
        }
        out.solved += c
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val n = chunk(c.x + dx, c.y + dy)
            // vaults and locked sectors open once all 8 neighbours are solved
            if (n.kind == Kind.VAULT && !n.vaultOpen && solvedAround(n) == 8) { n.vaultOpen = true; n.modified = true; out.vaults += n }
            if (n.locked && solvedAround(n) == 8) { unlock(n); out.unlocked += n; checkSolved(n, out) }
        }
    }

    /** Solved (or wall) sectors among all 8 neighbours — what opens a vault. */
    fun solvedAround(c: Chunk): Int {
        var k = 0
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val n = chunk(c.x + dx, c.y + dy)
            if (n.solved || n.kind == Kind.WALL) k++
        }
        return k
    }

    /** 64 − (mines + closed safe tiles), i.e. the tiles already opened; at least 1. */
    fun unlockPrice(c: Chunk): Int {
        val closedSafe = (0 until CN).count { !c.isMine(it) && c.state[it] != OPEN }
        return (CN - (c.mineCount + closedSafe)).coerceAtLeast(1)
    }

    private fun unlock(c: Chunk) {
        c.locked = false
        c.modified = true
        for (i in 0 until CN) if (c.state[i] == BOOM) c.state[i] = FLAG
    }

    fun toggleFlag(wx: Int, wy: Int): Boolean {
        val c = chunkAt(wx, wy)
        val i = li(wx, wy)
        if (!playable(c)) return false
        when (c.state[i]) {
            HIDDEN -> { c.state[i] = FLAG; c.flagsUsed = true }
            FLAG -> c.state[i] = HIDDEN
            else -> return false
        }
        c.modified = true
        return true
    }

    /** Tap on an opened number whose mines are all marked opens the remaining neighbours. */
    fun chord(wx: Int, wy: Int): Outcome {
        val out = Outcome()
        if (state(wx, wy) != OPEN) return out
        var marked = 0
        for (dy in -1..1) for (dx in -1..1) {
            val s = state(wx + dx, wy + dy)
            if ((dx != 0 || dy != 0) && (s == FLAG || s == BOOM)) marked++
        }
        if (marked != count(wx, wy) || marked == 0) return out
        out.chorded = true
        for (dy in -1..1) for (dx in -1..1) if (dx != 0 || dy != 0) reveal(wx + dx, wy + dy, out)
        return out
    }

    // --- boosters: *Target() picks what the animation points at, the action is applied when it ends ---

    /** Periscope target: a random hidden safe tile, preferably next to an opened one; local index or null. */
    fun periscopeTarget(c: Chunk): Int? {
        if (!playable(c) || !started) return null
        val safe = (0 until CN).filter { !c.isMine(it) && c.state[it] == HIDDEN }
        if (safe.isEmpty()) return null
        val nearOpen = safe.filter { i ->
            val wx = c.x * CS + i % CS
            val wy = c.y * CS + i / CS
            (-1..1).any { dy -> (-1..1).any { dx -> state(wx + dx, wy + dy) == OPEN } }
        }
        return nearOpen.ifEmpty { safe }.random()
    }

    fun revealLocal(c: Chunk, i: Int) = reveal(c.x * CS + i % CS, c.y * CS + i / CS)

    /** Torpedo: removes every mine of the sector and opens it completely. */
    fun torpedo(c: Chunk): Outcome? {
        if (!playable(c) || !started) return null
        val out = Outcome()
        c.mines = 0L
        for (i in 0 until CN) if (c.state[i] != OPEN) {
            c.state[i] = OPEN
            out.opened++
            if ((c.gifts ushr i) and 1L == 1L) out.gifts++
        }
        c.gifts = 0L
        c.modified = true
        settle(listOf(c), out)
        return out
    }

    fun defuse(c: Chunk): Outcome? {
        if (!c.locked) return null
        val out = Outcome()
        unlock(c)
        out.unlocked += c
        checkSolved(c, out)
        return out
    }

    /** Shield: the mines that just went off become buoys and their sectors stay open. */
    fun absorbBooms(chunks: Collection<Chunk>): Outcome {
        val out = Outcome()
        for (c in chunks) {
            unlock(c)
            checkSolved(c, out)
        }
        return out
    }

    /** Echo target: top-left local index of a random 3×3 inside the sector with at least 3 hidden safe tiles. */
    fun echoTarget(c: Chunk): Int? {
        if (!playable(c) || !started) return null
        val windows = (0..CS - 3).flatMap { y -> (0..CS - 3).map { x -> y * CS + x } }.filter { tl ->
            (0..2).sumOf { dy -> (0..2).count { dx -> val i = tl + dy * CS + dx; !c.isMine(i) && c.state[i] == HIDDEN } } >= 3
        }
        return windows.randomOrNull()
    }

    /** Echo sounder: opens the hidden safe tiles of the 3×3 window found by [echoTarget]. */
    fun echo(c: Chunk, tl: Int): Outcome {
        val out = Outcome()
        val touched = HashSet<Chunk>()
        for (dy in 0..2) for (dx in 0..2) {
            val i = tl + dy * CS + dx
            if (!c.isMine(i) && c.state[i] == HIDDEN) flood(c.x * CS + i % CS, c.y * CS + i / CS, out, touched)
        }
        settle(touched, out)
        return out
    }

    /** Trawl: buoys every hidden mine on the picked tile and the 3 after it in direction (dx, dy). */
    fun trawl(wx: Int, wy: Int, dx: Int, dy: Int): Outcome {
        for (k in 0..3) {
            val x = wx + dx * k
            val y = wy + dy * k
            val c = chunkAt(x, y)
            val i = li(x, y)
            if (playable(c) && c.isMine(i) && c.state[i] == HIDDEN) { c.state[i] = FLAG; c.modified = true }
        }
        return Outcome()
    }

    /** Bathyscaphe (former lockpick): opens a closed vault without its four neighbours. */
    fun pick(c: Chunk): Outcome? {
        if (c.kind != Kind.VAULT || c.vaultOpen) return null
        c.vaultOpen = true
        c.modified = true
        return Outcome().also { it.vaults += c }
    }

    // --- persistence: only touched chunks are stored, the rest regenerate from the seed ---

    fun saveChunks() = chunks.values.filter { it.modified }.map { c ->
        ChunkSave(c.x, c.y, c.mines, String(CharArray(CN) { '0' + c.state[it].toInt() }),
            solved = c.solved, locked = c.locked, vaultOpen = c.vaultOpen, flagsUsed = c.flagsUsed, gifts = c.gifts, vaultPaid = c.vaultPaid)
    }

    fun loadChunks(list: List<ChunkSave>) {
        for (s in list) {
            val c = chunk(s.x, s.y)
            c.mines = s.mines
            for (i in 0 until CN) c.state[i] = (s.state[i] - '0').toByte()
            c.gifts = if (s.crystal >= 0) 1L shl s.crystal else s.gifts // saves from before gifts had one crystal tile
            c.solved = s.solved
            c.locked = s.locked
            c.vaultOpen = s.vaultOpen
            // older saves: a vault could only be solved once open, and solving paid the bonus
            c.vaultPaid = s.vaultPaid ?: (c.kind == Kind.VAULT && s.solved && s.vaultOpen)
            c.flagsUsed = s.flagsUsed
            c.modified = true
        }
    }
}

/** Vaults never stand closer than this many sectors to each other. */
const val VAULT_GAP = 3

/** Side of the square zone (in sectors) that holds at most one landmark. */
const val ZONE = 6

/** Landmark kinds and their shapes: sector offsets from the top-left of the shape. */
enum class LandmarkType(val title: String, val done: String, val shape: List<Pair<Int, Int>>) {
    SHELL("Гигантская ракушка", "Ракушка исследована", listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)),
    REEF("Коралловый риф", "Риф исследован", listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1, 0 to 1)),
    WRECK("Затонувший корабль", "Корабль исследован", listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1)),
    ANCHOR("Якорь", "Якорь исследован", listOf(1 to 0, 1 to 1, 0 to 2, 1 to 2, 2 to 2)),
    STAR("Морская звезда", "Звезда исследована", listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1, 1 to 2));

    val w get() = shape.maxOf { it.first } + 1
    val h get() = shape.maxOf { it.second } + 1
}

/** A landmark at top-left sector ([x], [y]); [key] identifies its zone. */
class Landmark(val key: Long, val type: LandmarkType, val x: Int, val y: Int) {
    val cells: Set<Long> = type.shape.map { (dx, dy) -> packKey(x + dx, y + dy) }.toSet()
    fun has(cx: Int, cy: Int) = packKey(cx, cy) in cells

    /** 5 crystals per sector of the shape. */
    val reward get() = cells.size * 5
}
