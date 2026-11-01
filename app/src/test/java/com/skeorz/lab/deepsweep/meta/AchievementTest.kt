package com.skeorz.lab.deepsweep.meta

import com.skeorz.lab.deepsweep.game.Difficulty
import com.skeorz.lab.deepsweep.game.Mode
import com.skeorz.lab.deepsweep.game.World
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class AchievementTest {
    @Test
    fun tiersAreGrantedInOrderWithDoublingReward() {
        val store = Store(Files.createTempDirectory("ds").toFile())
        store.d.premium = false
        val before = store.d.crystals
        val boostersBefore = store.d.boosters.values.sum()
        store.add(S.SECTORS, 150) // passes 1 (one-time), 10 (bronze) and 100 (silver)
        val sectors = ACHIEVEMENTS.first { it.id == "sectors" }
        assertEquals(2, store.tier(sectors))
        assertEquals(1, store.tier(ACHIEVEMENTS.first { it.id == "first" }))
        assertEquals(before + 5 + sectors.reward + sectors.reward * 2, store.d.crystals)
        assertEquals(listOf(12), store.d.packs.map { it.size }) // silver pack only, still sealed
        assertEquals(boostersBefore, store.d.boosters.values.sum())
        store.add(S.SECTORS, 5000)
        assertEquals(4, store.tier(sectors))
        assertEquals(true, store.owns(ITEMS.first { it.id == "frame_gold" }))
        assertEquals(listOf(12, 24), store.d.packs.map { it.size }) // gold gives none, platinum 24
        assertEquals(12, store.openPack().size) // one at a time, oldest first
        assertEquals(24, store.openPack().size)
        assertEquals(0, store.d.packs.size)
        assertEquals(boostersBefore + 36, store.d.boosters.values.sum())
    }

    @Test
    fun grantedTiersAreOnDiskImmediately() {
        val dir = Files.createTempDirectory("ds").toFile()
        val store = Store(dir)
        store.d.premium = false
        store.add(S.SECTORS, 600) // up to gold: no pack, so nothing else would have saved it
        assertEquals(3, Store(dir).tier(ACHIEVEMENTS.first { it.id == "sectors" }))
        store.add(S.SECTORS, 5000)
        assertEquals(true, Store(dir).owns(ITEMS.first { it.id == "frame_gold" }))
    }

    @Test
    fun premiumGivesAPackForEveryTier() {
        val store = Store(Files.createTempDirectory("ds").toFile())
        store.add(S.SECTORS, 2100) // bronze…platinum of "Картограф"; the one-time "Первый контакт" gives none
        assertEquals(listOf(9, 18, 24, 36), store.d.packs.map { it.size })
    }

    @Test
    fun premiumMultipliesCrystalsAndXpAndOpensShop() {
        val store = Store(Files.createTempDirectory("ds").toFile())
        assertEquals(true, store.d.premium)
        val c = store.d.crystals
        store.earn(2, "тест")
        assertEquals(c + 2 * PREMIUM_CRYSTALS, store.d.crystals)
        store.addXp(10)
        assertEquals(10L * PREMIUM_XP, store.d.xp)
        val neon = ITEMS.first { it.id == "theme_neon" }
        assertEquals(true, store.owns(neon))
        store.setPremium(false)
        assertEquals(false, store.owns(neon))
        assertEquals(false, store.owns(ITEMS.first { it.id == "sonar" }))
    }

    @Test
    fun difficultyShiftsRings() {
        val easy = World(Mode.ENDLESS, 1, Difficulty.EASY)
        val extreme = World(Mode.ENDLESS, 1, Difficulty.EXTREME)
        assertEquals(3, easy.maxNumber(6, 0))
        assertEquals(5, extreme.maxNumber(5, 0))
        assertEquals(8, extreme.maxNumber(11, 0))
        assertEquals(7, easy.maxNumber(30, 0))
    }
}
