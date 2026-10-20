package com.vespelabs.fixit.core.domain.testing

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.repository.AddressSearch
import com.vespelabs.fixit.core.domain.result.AppResult
import kotlinx.coroutines.delay

/**
 * Configurable AddressSearch double that records each call. Answers [resultFor] of the query (by default [result])
 * after [delayFor] of the query in virtual time, so tests can make an older search answer after a newer one.
 */
class TestAddressSearch(
    var result: AppResult<List<Place>> = AppResult.Success(emptyList()),
) : AddressSearch {
    val calls = mutableListOf<Pair<String, GeoPoint?>>()
    var resultFor: (String) -> AppResult<List<Place>> = { result }
    var delayFor: (String) -> Long = { 0L }

    override suspend fun search(query: String, near: GeoPoint?): AppResult<List<Place>> {
        calls += query to near
        delay(delayFor(query))
        return resultFor(query)
    }
}
