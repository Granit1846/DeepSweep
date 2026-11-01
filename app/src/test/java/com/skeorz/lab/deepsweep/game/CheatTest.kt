package com.skeorz.lab.deepsweep.game

import com.skeorz.lab.deepsweep.meta.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CheatTest {
    private fun setup(mode: Mode, debug: Boolean): Pair<Session, Store> {
        val dir = Files.createTempDirectory("ds").toFile()
        val store = Store(dir).apply {
            this.debug = debug
            d.cheatCrystals = true; d.cheatImmortal = true; d.cheatBoosters = true; d.cheatOxygen = true
        }
        val w = World(mode, 5).apply { started = true; chunk(0, 0).mines = 1L }
        return Session(mode, w, store, File(dir, "w.json")) to store
    }

    @Test
    fun releaseBuildIgnoresSavedCheats() {
        val (s, store) = setup(Mode.HARDCORE, debug = false)
        assertFalse(store.anyCheat)
        store.d.crystals = 5
        assertFalse(store.spend(10))
        s.open(0, 0)
        assertTrue(s.over) // no immortality in release
    }

    @Test
    fun debugCheatsWork() {
        val (s, store) = setup(Mode.HARDCORE, debug = true)
        store.d.crystals = 5
        assertTrue(store.spend(10))
        assertEquals(5, store.d.crystals)              // infinite crystals
        store.d.boosters["shield"] = 0
        assertTrue(s.activate("shield"))               // infinite boosters
        assertEquals(0, store.d.boosters["shield"])
        s.shield = false
        s.open(0, 0)                                    // the mine
        assertFalse(s.over)                             // immortal
        assertEquals(FLAG, s.world.state(0, 0))
    }

    @Test
    fun oxygenCheatStopsTheClock() {
        val (t, _) = setup(Mode.TIMED, debug = true)
        repeat(400) { t.secondPassed() }
        assertFalse(t.over)
        assertEquals(300L, t.timeLeft)
    }

    @Test
    fun openAllPacksTakesEverything() {
        val (_, store) = setup(Mode.ENDLESS, debug = false)
        store.grantPack(9); store.grantPack(18)
        val before = store.d.boosters.values.sum()
        assertEquals(27, store.openAllPacks().size)
        assertEquals(0, store.d.packs.size)
        assertEquals(before + 27, store.d.boosters.values.sum())
    }
}
