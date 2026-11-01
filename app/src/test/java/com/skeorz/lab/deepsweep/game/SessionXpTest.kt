package com.skeorz.lab.deepsweep.game

import com.skeorz.lab.deepsweep.meta.Store
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SessionXpTest {
    private fun xpAfterPresses(difficulty: Difficulty, presses: Int, premium: Boolean): Long {
        val dir = Files.createTempDirectory("ds").toFile()
        val store = Store(dir).apply { d.premium = premium }
        val s = Session(Mode.ENDLESS, World(Mode.ENDLESS, 1, difficulty), store, File(dir, "w.json"))
        repeat(presses) { s.awardPressXp() }
        return store.d.xp
    }

    @Test
    fun pressXpFollowsDifficultyAndPremium() {
        assertEquals(1, xpAfterPresses(Difficulty.EASY, 2, false)) // 0.5 + 0.5
        assertEquals(0, xpAfterPresses(Difficulty.EASY, 1, false)) // half carried over
        assertEquals(4, xpAfterPresses(Difficulty.NORMAL, 4, false))
        assertEquals(3, xpAfterPresses(Difficulty.HARD, 2, false)) // 1 + 2
        assertEquals(6, xpAfterPresses(Difficulty.HARD, 2, true))
        assertEquals(9, xpAfterPresses(Difficulty.EXTREME, 5, false)) // 1.8 × 5
    }
}
