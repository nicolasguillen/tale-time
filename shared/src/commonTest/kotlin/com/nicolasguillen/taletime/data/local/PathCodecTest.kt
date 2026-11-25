package com.nicolasguillen.taletime.data.local

import com.nicolasguillen.taletime.story.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PathCodecTest {
    @Test
    fun roundTrips() {
        val path = listOf(Step("p01-meadow"), Step("p03-tuck", 1), Step("p04b-garden"))
        assertEquals("p01-meadow,p03-tuck:1,p04b-garden", encodePath(path))
        assertEquals(path, decodePath(encodePath(path)))
    }

    @Test
    fun anEmptyOrDamagedPathIsNoPath() {
        assertNull(decodePath(""))
        assertNull(decodePath("p1,f1:x"))
    }
}
