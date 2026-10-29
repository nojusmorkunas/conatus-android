package com.useconatus.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The fractional-indexing package's own test vectors, ported: the phone must place things where the server would.
class OrderingTest {
    @Test fun `matches the library`() = listOf(
        Triple(null, null, "a0"), Triple(null, "a0", "Zz"), Triple(null, "Zz", "Zy"), Triple("a0", null, "a1"),
        Triple("a1", null, "a2"), Triple("a0", "a1", "a0V"), Triple("a1", "a2", "a1V"), Triple("a0V", "a1", "a0l"),
        Triple("Zz", "a0", "ZzV"), Triple("Zz", "a1", "a0"), Triple(null, "Y00", "Xzzz"), Triple("bzz", null, "c000"),
        Triple("a0", "a0V", "a0G"), Triple("a0", "a0G", "a08"), Triple("b125", "b129", "b127"), Triple("a0", "a1V", "a1"),
        Triple("Zz", "a01", "a0"), Triple(null, "a0V", "a0"), Triple(null, "b999", "b99"),
    ).forEach { (a, b, expected) -> assertEquals("$a, $b", expected, generateKeyBetween(a, b)) }

    @Test fun `neighbours in either order`() = assertEquals("a0V", generateKeyBetween("a1", "a0"))

    @Test fun `keys sort between their neighbours over many inserts`() {
        var low = "a0"
        val high = "a1"
        repeat(50) {
            val key = generateKeyBetween(low, high)
            assertTrue("$low < $key < $high", key > low && key < high)
            low = key
        }
    }

    @Test fun `a blank key from before ordering counts as the end`() {
        assertEquals("a0", keyBetween("", null))
        assertEquals("a2", keyAfter(listOf("a0", "", "a1")))
        assertEquals("a0", keyAfter(emptyList()))
    }
}
