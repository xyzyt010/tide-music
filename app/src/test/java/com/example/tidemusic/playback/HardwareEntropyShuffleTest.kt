package com.example.tidemusic.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareEntropyShuffleTest {

    @Test
    fun `buildPermutation puts fixedFirstIndex first and includes every index once`() {
        val count = 25
        val fixed = 7
        val perm = HardwareEntropyShuffle.buildPermutation(
            count = count,
            fixedFirstIndex = fixed,
            seed = 0x1234_5678_9ABCL,
        )
        assertEquals(count, perm.size)
        assertEquals(fixed, perm[0])
        assertEquals((0 until count).toSet(), perm.toSet())
        val isSequential = perm.indices.all { i -> perm[i] == (fixed + i) % count }
        assertTrue("Shuffle order should not remain sequential", !isSequential)
    }

    @Test
    fun `createShuffleOrder traverses non-sequentially from fixedFirstIndex`() {
        val count = 20
        val fixed = 4
        val order = HardwareEntropyShuffle.createShuffleOrder(
            count = count,
            fixedFirstIndex = fixed,
            seed = 987654321L,
        )
        assertEquals(count, order.length)
        assertEquals(fixed, order.firstIndex)
        val visited = mutableListOf<Int>()
        var cur = order.firstIndex
        while (cur != -1) {
            visited.add(cur)
            cur = order.getNextIndex(cur)
        }
        assertEquals(count, visited.size)
        assertEquals((0 until count).toSet(), visited.toSet())
        assertNotEquals((0 until count).toList(), visited)
    }

    @Test
    fun `buildPermutation avoids placing avoidFirstIndex first`() {
        val count = 12
        val avoid = 3
        for (seed in 1L..20L) {
            val perm = HardwareEntropyShuffle.buildPermutation(
                count = count,
                fixedFirstIndex = null,
                avoidFirstIndex = avoid,
                seed = seed,
            )
            assertNotEquals(avoid, perm[0])
            assertEquals((0 until count).toSet(), perm.toSet())
        }
    }
}
