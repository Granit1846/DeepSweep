package com.skeorz.lab.deepsweep.meta

import org.junit.Assert.assertEquals
import org.junit.Test

class PluralTest {
    @Test
    fun russianPlurals() {
        val f = { n: Long -> plural(n, "сектор", "сектора", "секторов") }
        assertEquals("сектор", f(1)); assertEquals("сектор", f(21))
        assertEquals("сектора", f(3)); assertEquals("сектора", f(104))
        assertEquals("секторов", f(5)); assertEquals("секторов", f(11)); assertEquals("секторов", f(12)); assertEquals("секторов", f(111))
    }
}
