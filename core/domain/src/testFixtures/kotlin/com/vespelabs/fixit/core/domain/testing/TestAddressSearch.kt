package com.vespelabs.fixit.core.domain.testing

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.repository.AddressSearch
import com.vespelabs.fixit.core.domain.result.AppResult

/** Configurable AddressSearch double: answers [result] to every query and records each call. */
class TestAddressSearch(
    var result: AppResult<List<Place>> = AppResult.Success(emptyList()),
) : AddressSearch {
    val calls = mutableListOf<Pair<String, GeoPoint?>>()

    override suspend fun search(query: String, near: GeoPoint?): AppResult<List<Place>> {
        calls += query to near
        return result
    }
}
