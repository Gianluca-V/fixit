package com.vespelabs.fixit.core.data.firebase

import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.result.AppResult

// Pure pieces of the feed query (design 13.1): which geohash ranges to listen to and how their results merge.

/** One `orderBy("geohash").startAt(start).endAt(end)` listener of the feed. */
internal data class GeohashRange(val start: String, val end: String)

/** Geohash ranges that together cover every point within [radiusKm] of [center] (plus some false positives). */
internal fun feedRanges(center: GeoPoint, radiusKm: Int): List<GeohashRange> =
    GeoFireUtils.getGeoHashQueryBounds(GeoLocation(center.latitude, center.longitude), radiusKm * 1000.0)
        .map { GeohashRange(it.startHash, it.endHash) }

/**
 * Combines the latest result of every range: any Error becomes the feed's Error; otherwise the candidates without
 * duplicates (overlapping ranges may return the same request). The exact distance and category filter (`Matching`)
 * stays in the domain, so false positives are expected here.
 */
internal fun mergeCandidates(results: List<AppResult<List<RepairRequest>>>): AppResult<List<RepairRequest>> {
    results.firstOrNull { it is AppResult.Error }?.let { return it }
    return AppResult.Success(
        results.flatMap { (it as AppResult.Success).data }.distinctBy { it.id },
    )
}
