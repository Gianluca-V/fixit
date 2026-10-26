package com.vespelabs.fixit.core.domain.usecase

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.repository.AddressSearch
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.rules.Validators
import javax.inject.Inject

/**
 * Address autocomplete rules (address-search spec): the text is trimmed, a short text never reaches the service,
 * suggestions without address or with invalid coordinates are dropped and at most [MAX_SUGGESTIONS] are kept.
 */
class SearchAddressUseCase @Inject constructor(
    private val addressSearch: AddressSearch,
) {
    suspend operator fun invoke(query: String, near: GeoPoint?): AppResult<List<Place>> {
        val text = query.trim()
        if (text.length < MIN_QUERY_LENGTH) return AppResult.Success(emptyList())
        return when (val result = addressSearch.search(text, near)) {
            is AppResult.Error -> result
            is AppResult.Success -> AppResult.Success(
                result.data
                    .filter { it.address.isNotBlank() && Validators.coordinates(it.point) == null }
                    .take(MAX_SUGGESTIONS),
            )
        }
    }

    companion object {
        const val MIN_QUERY_LENGTH = 3
        const val MAX_SUGGESTIONS = 5
    }
}
