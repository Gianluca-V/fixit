package com.vespelabs.fixit.core.data.firebase

import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoQueriesTest {

    private val center = TestData.florencioVarela.point

    private fun geohashOf(point: GeoPoint) = GeoFireUtils.getGeoHashForLocation(GeoLocation(point.latitude, point.longitude))

    private fun List<GeohashRange>.covers(point: GeoPoint): Boolean {
        val hash = geohashOf(point)
        return any { hash >= it.start && hash <= it.end }
    }

    @Test
    fun `ranges cover a request just inside the radius in kilometers`() {
        val ranges = feedRanges(center, radiusKm = 10)

        assertTrue(ranges.isNotEmpty())
        assertTrue(ranges.covers(center))
        assertTrue(ranges.covers(TestData.northOf(center, 9.9)))
        assertTrue(ranges.covers(TestData.northOf(center, 10.0)))
    }

    @Test
    fun `ranges do not reach a request far outside the radius`() {
        assertFalse(feedRanges(center, radiusKm = 10).covers(TestData.northOf(center, 200.0)))
    }

    @Test
    fun `merge removes the duplicates returned by overlapping ranges`() {
        val r1 = TestData.request(id = "r1")
        val r2 = TestData.request(id = "r2")
        val r3 = TestData.request(id = "r3")

        val merged = mergeCandidates(
            listOf(AppResult.Success(listOf(r1, r2)), AppResult.Success(listOf(r2, r3)), AppResult.Success(emptyList())),
        )

        assertEquals(AppResult.Success(listOf(r1, r2, r3)), merged)
    }

    @Test
    fun `merge emits the error when any range fails`() {
        val merged = mergeCandidates(
            listOf(AppResult.Success(listOf(TestData.request())), AppResult.Error(DomainError.Network)),
        )

        assertEquals(AppResult.Error(DomainError.Network), merged)
    }

    @Test
    fun `merge of no ranges is an empty list`() {
        assertEquals(AppResult.Success(emptyList<Any>()), mergeCandidates(emptyList()))
    }
}
