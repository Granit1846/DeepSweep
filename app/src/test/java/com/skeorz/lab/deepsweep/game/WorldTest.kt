package com.skeorz.lab.deepsweep.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldTest {
    @Test
    fun generationIsDeterministic() {
        val a = World(Mode.ENDLESS, 42)
        val b = World(Mode.ENDLESS, 42)
        for (x in -5..5) for (y in -5..5) assertEquals(a.chunk(x, y).mines, b.chunk(x, y).mines)
    }

    @Test
    fun firstRevealIsSafeAndFloods() {
        repeat(30) { seed ->
            val w = World(Mode.BRUTAL, seed.toLong())
            val out = w.reveal(3, 3)
            assertTrue(out.booms.isEmpty())
            assertEquals(0, w.count(3, 3))
            assertTrue(out.opened >= 9)
        }
    }

    @Test
    fun cascadeStaysBoundedOnEmptyField() {
        val w = World(Mode.ENDLESS, 3)
        w.started = true
        for (x in -3..3) for (y in -3..3) w.chunk(x, y).mines = 0L
        assertEquals(9 * CN, w.reveal(0, 0).opened)
    }

    @Test
    fun numbersRespectRingsAroundFirstOpening() {
        for (mode in listOf(Mode.ENDLESS, Mode.BRUTAL)) repeat(3) { seed ->
            val w = World(mode, seed.toLong())
            w.reveal(100, 100) // first opening in chunk (12, 12)
            assertEquals(12, w.ox)
            // generate in a scattered order, as camera movement would
            for (r in 0..22) for (x in -r..r) for (y in -r..r) w.chunk(12 + x, 12 + y)
            for (cx in -10..34) for (cy in -10..34) {
                val limit = w.maxNumber(cx, cy)
                for (i in 0 until CN) {
                    val wx = cx * CS + i % CS
                    val wy = cy * CS + i / CS
                    if (!w.isMine(wx, wy)) assertTrue("$mode ($wx,$wy) > $limit", w.count(wx, wy) <= limit)
                }
            }
        }
    }

    @Test
    fun everySectorHasOneToThreeGiftsOnSafeTiles() {
        for (mode in listOf(Mode.ENDLESS, Mode.BRUTAL)) {
            val w = World(mode, 11)
            w.reveal(3, 3)
            for (x in -8..8) for (y in -8..8) {
                val c = w.chunk(x, y)
                val solvedBefore = c.solved || c.state.any { it == OPEN }
                if (solvedBefore) continue
                val n = java.lang.Long.bitCount(c.gifts)
                assertTrue("$mode ($x,$y) has $n gifts", n in 1..3)
                assertEquals(0L, c.gifts and c.mines)
                val out = w.torpedo(c) ?: continue
                assertTrue(out.gifts >= n)
                assertEquals(0L, c.gifts)
            }
        }
        assertEquals(0L, World(Mode.TUTORIAL, 1).chunk(0, 0).gifts)
    }

    @Test
    fun countSeesMinesAcrossChunkBorders() {
        val w = World(Mode.ENDLESS, 1)
        w.chunk(0, 0).mines = 0L
        w.chunk(1, 0).mines = 1L // tile (8,0)
        assertEquals(1, w.count(7, 0))
        assertEquals(1, w.count(7, 1))
        assertEquals(0, w.count(6, 1))
    }

    @Test
    fun mineLocksSectorAndSolvedNeighboursUnlockIt() {
        val w = World(Mode.ENDLESS, 7)
        w.started = true
        val c = w.chunk(0, 0)
        c.mines = 1L
        w.reveal(0, 0)
        assertTrue(c.locked)
        assertFalse(w.toggleFlag(1, 1))
        for ((x, y) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) w.torpedo(w.chunk(x, y))
        assertTrue(c.locked) // the 4 sides are not enough any more
        for ((x, y) in listOf(1 to 1, -1 to 1, 1 to -1)) w.torpedo(w.chunk(x, y))
        assertTrue(c.locked)
        w.torpedo(w.chunk(-1, -1)) // the 8th neighbour
        assertFalse(c.locked)
        assertEquals(FLAG, c.state[0])
    }

    @Test
    fun unlockPriceIsOpenedTiles() {
        val w = World(Mode.ENDLESS, 5)
        val c = w.chunk(0, 0)
        c.mines = 0b1111L // 4 mines
        assertEquals(1, w.unlockPrice(c)) // nothing opened → minimum
        for (i in 10 until 30) c.state[i] = OPEN
        c.state[40] = FLAG // flag on a safe tile is still closed
        assertEquals(64 - (4 + 40), w.unlockPrice(c))
    }

    @Test
    fun saveRoundTrip() {
        val w = World(Mode.ENDLESS, 9)
        w.reveal(2, 2)
        w.toggleFlag(40, 40)
        val copy = World(Mode.ENDLESS, 9).apply { loadChunks(w.saveChunks()) }
        for (x in -10..50) for (y in -10..50) assertEquals(w.state(x, y), copy.state(x, y))
    }

    @Test
    fun vaultsAreAtLeastThreeSectorsApart() {
        for (seed in 1L..4L) {
            val w = World(Mode.ENDLESS, seed).apply { started = true }
            val vaults = (-40..40).flatMap { y -> (-40..40).filter { x -> w.kindOf(x, y) == Kind.VAULT }.map { it to y } }
            assertTrue("seed $seed has vaults", vaults.size > 5)
            for ((a, b) in vaults) for ((c, e) in vaults) {
                if (a == c && b == e) continue
                assertTrue("vaults at ($a,$b) and ($c,$e)", maxOf(kotlin.math.abs(a - c), kotlin.math.abs(b - e)) > VAULT_GAP)
            }
        }
    }
}
