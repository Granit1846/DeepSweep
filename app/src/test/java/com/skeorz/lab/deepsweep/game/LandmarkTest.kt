package com.skeorz.lab.deepsweep.game

import com.skeorz.lab.deepsweep.meta.S
import com.skeorz.lab.deepsweep.meta.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LandmarkTest {
    private fun landmarks(w: World) = (-6..6).flatMap { ry -> (-6..6).mapNotNull { rx -> w.landmark(rx, ry) } }

    @Test
    fun landmarksArePlacedInsideTheirZoneAndOnlyNormalSectors() {
        val w = World(Mode.ENDLESS, 21).apply { started = true }
        val all = landmarks(w)
        assertTrue("some zones hold a landmark", all.size in 8..50) // ~13% of 169 zones
        assertNull(w.landmark(0, 0)) // never in the starting zone
        for (l in all) {
            val rx = Math.floorDiv(l.x - w.ox, ZONE)
            val ry = Math.floorDiv(l.y - w.oy, ZONE)
            for ((dx, dy) in l.type.shape) {
                assertEquals(rx, Math.floorDiv(l.x + dx - w.ox, ZONE))
                assertEquals(ry, Math.floorDiv(l.y + dy - w.oy, ZONE))
                assertEquals(Kind.NORMAL, w.chunk(l.x + dx, l.y + dy).kind)
                assertEquals(l, w.landmarkAt(l.x + dx, l.y + dy))
            }
        }
        assertEquals(all.map { it.x to it.y }, landmarks(World(Mode.ENDLESS, 21).apply { started = true }).map { it.x to it.y })
        assertTrue(landmarks(World(Mode.CLIMB, 21)).isEmpty())
    }

    @Test
    fun solvingTheWholeShapePaysOnce() {
        val dir = Files.createTempDirectory("ds").toFile()
        val store = Store(dir).apply { d.premium = false }
        val w = World(Mode.ENDLESS, 21).apply { started = true }
        val l = landmarks(w).first()
        val s = Session(Mode.ENDLESS, w, store, File(dir, "w.json"))
        store.d.boosters["solve"] = 50
        val cells = l.type.shape.map { (dx, dy) -> w.chunk(l.x + dx, l.y + dy) }
        for ((i, c) in cells.withIndex()) {
            s.booster = "solve"
            s.tap(c.x * CS, c.y * CS)
            val f = s.fx!!; s.fx = null; f.done() // as the game screen does when the animation ends
            assertEquals(if (i == cells.lastIndex) 1L else 0L, store.stat(S.LANDMARKS))
        }
        assertTrue(l.key in w.landmarksDone)
    }
}
