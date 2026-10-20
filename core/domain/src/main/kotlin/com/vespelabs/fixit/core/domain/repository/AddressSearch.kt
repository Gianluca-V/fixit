package com.vespelabs.fixit.core.domain.repository

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.result.AppResult

/** Address suggestions for a typed text (autocomplete). */
interface AddressSearch {
    /** Raw suggestions for [query], preferring those close to [near]; SearchAddressUseCase validates and trims them. */
    suspend fun search(query: String, near: GeoPoint?): AppResult<List<Place>>
}
