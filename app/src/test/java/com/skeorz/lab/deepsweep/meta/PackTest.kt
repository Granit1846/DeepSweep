package com.skeorz.lab.deepsweep.meta

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import kotlin.random.Random

class PackTest {
    @Test
    fun packOddsMatchWeights() {
        val store = Store(Files.createTempDirectory("ds").toFile())
        store.grantPack(20000, Random(7))
        val cards = store.d.packs.single()
        for ((id, weight) in PACK_WEIGHTS) {
            assertEquals("$id share", weight / 100.0, cards.count { it == id } / 20000.0, 0.015)
        }
    }
}
