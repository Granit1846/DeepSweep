package com.skeorz.lab.deepsweep.game

import com.skeorz.lab.deepsweep.meta.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BoosterTest {
    private fun world(): World = World(Mode.ENDLESS, 5).apply { started = true }

    @Test
    fun echoOpensAtLeastThreeSafeTilesInsideA3x3() {
        val w = world()
        val c = w.chunk(0, 0)
        c.mines = 0b111L shl (2 * CS + 2) // a row of mines
        val tl = w.echoTarget(c)!!
        val before = (0 until CN).count { c.state[it] == OPEN }
        val out = w.echo(c, tl)
        assertTrue(out.opened >= 3)
        for (dy in 0..2) for (dx in 0..2) {
            val i = tl + dy * CS + dx
            if (!c.isMine(i)) assertEquals(OPEN, c.state[i])
        }
        assertTrue((0 until CN).count { c.state[it] == OPEN } > before)
    }

    @Test
    fun trawlBuoysThePickedTileAndThreeMore() {
        val w = world()
        val c = w.chunk(0, 0)
        c.mines = (1L shl 0) or (1L shl 3) or (1L shl 4) // (0,0) (3,0) (4,0)
        w.trawl(0, 0, 1, 0)
        assertEquals(FLAG, w.state(0, 0))   // the picked tile counts as the first
        assertEquals(FLAG, w.state(3, 0))   // the 4th tile
        assertEquals(HIDDEN, w.state(4, 0)) // the 5th: out of reach
    }

    @Test
    fun torpedoClearsAndSolvesTheSector() {
        val w = world()
        val c = w.chunk(0, 0)
        c.mines = 0b1011L
        val out = w.torpedo(c)!!
        assertEquals(0L, c.mines)
        assertTrue(c.solved)
        assertEquals(listOf(c), out.solved)
        assertTrue((0 until CN).all { c.state[it] == OPEN })
    }

    @Test
    fun periscopeTargetsAHiddenSafeTile() {
        val w = world()
        val c = w.chunk(0, 0)
        c.mines = -1L xor (1L shl 9) // only tile 9 is safe
        assertEquals(9, w.periscopeTarget(c))
    }

    @Test
    fun lockpickOpensOnlyClosedVaults() {
        val w = World(Mode.ENDLESS, 1)
        val vault = (3..30).asSequence().flatMap { x -> (3..30).asSequence().map { y -> w.chunk(x, y) } }.first { it.kind == Kind.VAULT }
        assertFalse(vault.vaultOpen)
        assertEquals(listOf(vault), w.pick(vault)!!.vaults)
        assertTrue(vault.vaultOpen)
        assertNull(w.pick(vault))
        assertNull(w.pick(w.chunk(0, 0)))
    }

    private fun session(mode: Mode): Pair<Session, Store> {
        val dir = Files.createTempDirectory("ds").toFile()
        val store = Store(dir)
        store.d.boosters["shield"] = 1
        val w = World(mode, 5).apply { started = true; chunk(0, 0).mines = 1L }
        return Session(mode, w, store, File(dir, "w.json")) to store
    }

    @Test
    fun shieldAbsorbsOneMine() {
        val (s, store) = session(Mode.ENDLESS)
        assertTrue(s.activate("shield"))
        assertEquals(0, store.d.boosters["shield"])
        s.open(0, 0) // the mine
        val c = s.world.chunk(0, 0)
        assertFalse(c.locked)
        assertEquals(FLAG, c.state[0])
        assertFalse(s.shield)
    }

    @Test
    fun shieldSavesAHardcoreRun() {
        val (s, _) = session(Mode.HARDCORE)
        s.activate("shield")
        s.open(0, 0)
        assertFalse(s.over)
        s.open(0, 0) // a buoy now: nothing happens
        assertFalse(s.over)
    }

    @Test
    fun oxygenOnlyInTimedMode() {
        val (s, store) = session(Mode.ENDLESS)
        store.d.boosters["oxygen"] = 1
        assertFalse(s.activate("oxygen"))
        val (t, store2) = session(Mode.TIMED)
        store2.d.boosters["oxygen"] = 1
        assertTrue(t.activate("oxygen"))
        assertEquals(330L, t.timeLeft)
    }

    @Test
    fun trawlDirectionIsPickedOnTheBoard() {
        val (s, store) = session(Mode.ENDLESS)
        store.d.boosters["trawl"] = 1
        s.world.chunk(0, 0).mines = 1L shl 2 // (2,0)
        s.booster = "trawl"
        s.tap(1, 0)                 // pick the tile
        assertEquals(1 to 0, s.trawlFrom)
        assertEquals(1, store.d.boosters["trawl"]) // not spent yet
        s.tap(2, 0)                 // the neighbour to the right gives the direction
        assertEquals(FLAG, s.world.state(2, 0))
        assertEquals(0, store.d.boosters["trawl"])
    }

    private fun vaultSession(): Triple<Session, Store, Chunk> {
        val dir = Files.createTempDirectory("ds").toFile()
        val store = Store(dir).apply { d.premium = false }
        val w = World(Mode.ENDLESS, 1).apply { started = true }
        val v = (3..30).asSequence().flatMap { x -> (3..30).asSequence().map { y -> w.chunk(x, y) } }.first { it.kind == Kind.VAULT }
        v.mines = 1L // one mine, at local 0
        for (dy in -1..1) for (dx in -1..1) if (dx != 0 || dy != 0) w.chunk(v.x + dx, v.y + dy).mines = 0L
        // a line of mines along each shared border keeps the cascade inside the vault and the neighbours unsolved
        val col = (0 until CS).fold(0L) { m, r -> m or (1L shl (r * CS)) }
        w.chunk(v.x + 1, v.y).mines = col
        w.chunk(v.x - 1, v.y).mines = col shl (CS - 1)
        w.chunk(v.x, v.y + 1).mines = 0xFFL
        w.chunk(v.x, v.y - 1).mines = 0xFFL shl (CS * (CS - 1))
        return Triple(Session(Mode.ENDLESS, w, store, File(dir, "w.json")), store, v)
    }

    @Test
    fun vaultIsPlayableButPaysOnlyOnceOpened() {
        val (s, store, v) = vaultSession()
        assertTrue(s.world.playable(v))
        s.open(v.x * CS + 4, v.y * CS + 4) // floods the whole vault
        assertTrue(v.solved)
        assertEquals(0L, store.stat(com.skeorz.lab.deepsweep.meta.S.VAULTS)) // solved, but still closed
        store.d.boosters["lockpick"] = 1
        s.booster = "lockpick"
        s.tap(v.x * CS, v.y * CS)
        s.fx!!.done() // the animation would call this when it ends
        assertEquals(1L, store.stat(com.skeorz.lab.deepsweep.meta.S.VAULTS))
        assertTrue(v.vaultPaid)
    }

    @Test
    fun vaultOpenedFirstPaysWhenSolved() {
        val (s, store, v) = vaultSession()
        for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) s.world.torpedo(s.world.chunk(v.x + dx, v.y + dy))
        // neighbours solved outside the session: open the vault the way a neighbour's solve would
        s.world.pick(v)
        assertEquals(0L, store.stat(com.skeorz.lab.deepsweep.meta.S.VAULTS))
        s.open(v.x * CS + 4, v.y * CS + 4)
        assertEquals(1L, store.stat(com.skeorz.lab.deepsweep.meta.S.VAULTS))
        s.open(v.x * CS + 4, v.y * CS + 4) // nothing more to pay
        assertEquals(1L, store.stat(com.skeorz.lab.deepsweep.meta.S.VAULTS))
    }

    @Test
    fun vaultOpensOnlyWhenAllEightNeighboursAreSolved() {
        val w = World(Mode.ENDLESS, 1).apply { started = true }
        val v = (3..30).asSequence().flatMap { x -> (3..30).asSequence().map { y -> w.chunk(x, y) } }.first { it.kind == Kind.VAULT }
        for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) w.torpedo(w.chunk(v.x + dx, v.y + dy))
        assertFalse(v.vaultOpen) // 4 sides are no longer enough
        for ((dx, dy) in listOf(1 to 1, -1 to 1, 1 to -1)) w.torpedo(w.chunk(v.x + dx, v.y + dy))
        assertEquals(7, w.solvedAround(v))
        assertFalse(v.vaultOpen)
        assertEquals(listOf(v), w.torpedo(w.chunk(v.x - 1, v.y - 1))!!.vaults)
        assertTrue(v.vaultOpen)
    }
}
