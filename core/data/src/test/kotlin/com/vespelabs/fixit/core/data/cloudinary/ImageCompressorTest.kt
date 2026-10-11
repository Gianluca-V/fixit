package com.vespelabs.fixit.core.data.cloudinary

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCompressorTest {

    @Test
    fun `given a landscape photo larger than 1600 px when targetSize runs then the longest side becomes 1600`() {
        assertEquals(1600 to 1200, targetSize(4000, 3000))
    }

    @Test
    fun `given a portrait photo larger than 1600 px when targetSize runs then the longest side becomes 1600`() {
        assertEquals(1200 to 1600, targetSize(3000, 4000))
    }

    @Test
    fun `given a photo within 1600 px when targetSize runs then the size is unchanged`() {
        assertEquals(800 to 600, targetSize(800, 600))
    }

    @Test
    fun `given an extreme aspect ratio when targetSize runs then no side becomes 0`() {
        assertEquals(1600 to 1, targetSize(10_000, 2))
        assertEquals(1 to 1600, targetSize(2, 10_000))
    }

    @Test
    fun `given a 4000x3000 photo when sampleSizeFor runs then it decodes at half size`() {
        assertEquals(2, sampleSizeFor(4000, 3000))
    }

    @Test
    fun `given a 12000x9000 photo when sampleSizeFor runs then it decodes at a quarter size`() {
        assertEquals(4, sampleSizeFor(12_000, 9000))
    }

    @Test
    fun `given a photo within 1600 px when sampleSizeFor runs then it decodes at full size`() {
        assertEquals(1, sampleSizeFor(800, 600))
    }

    @Test
    fun `given a 3200x2400 photo when sampleSizeFor runs then the decoded longest side stays at 1600`() {
        assertEquals(2, sampleSizeFor(3200, 2400))
    }

    @Test
    fun `given invalid bounds when sampleSizeFor runs then it returns 1`() {
        assertEquals(1, sampleSizeFor(0, 3000))
        assertEquals(1, sampleSizeFor(4000, 0))
        assertEquals(1, sampleSizeFor(-1, -1))
    }
}
