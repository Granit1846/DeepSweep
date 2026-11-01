package com.skeorz.lab.deepsweep.meta

import com.skeorz.lab.deepsweep.game.Mode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class ModeUnlockTest {
    @Test
    fun modesOpenByLevelOrPremiumOnly() {
        val store = Store(Files.createTempDirectory("ds").toFile())
        store.d.premium = false
        assertEquals(true, store.modeOpen(Mode.ENDLESS))
        assertEquals(false, store.modeOpen(Mode.BRUTAL))
        store.d.level = Mode.BRUTAL.unlockLevel
        assertEquals(true, store.modeOpen(Mode.BRUTAL))

        store.d.level = 1
        store.d.premium = true
        assertEquals(true, store.modeOpen(Mode.BRUTAL))
        store.d.lastMode = Mode.BRUTAL.name
        assertEquals(Mode.BRUTAL, store.lastPlayable())
        store.d.premium = false
        assertEquals(Mode.ENDLESS, store.lastPlayable()) // locked again → main button falls back
    }
}
