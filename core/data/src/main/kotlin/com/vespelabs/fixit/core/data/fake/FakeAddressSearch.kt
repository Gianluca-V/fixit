package com.vespelabs.fixit.core.data.fake

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.repository.AddressSearch
import com.vespelabs.fixit.core.domain.result.AppResult
import java.text.Normalizer
import javax.inject.Inject

/**
 * Simulated autocomplete over a fixed catalog of the southern Greater Buenos Aires (design D3): matches ignore case
 * and accents, and [near] is ignored. To use it instead of Photon, bind it to AddressSearch in NetworkModule.
 */
class FakeAddressSearch @Inject constructor() : AddressSearch {
    override suspend fun search(query: String, near: GeoPoint?): AppResult<List<Place>> {
        val needle = query.normalized()
        return AppResult.Success(CATALOG.filter { needle in it.address.normalized() })
    }

    private fun String.normalized(): String =
        Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

    private companion object {
        val DIACRITICS = Regex("\\p{Mn}+")

        val CATALOG = listOf(
            Place(GeoPoint(-34.8065, -58.2788), "Av. San Martín 2000, Florencio Varela"),
            Place(GeoPoint(-34.7745, -58.2669), "Av. Calchaquí 3950, Florencio Varela"),
            Place(GeoPoint(-34.7754, -58.2655), "Av. Calchaquí 6200, Florencio Varela"),
            Place(GeoPoint(-34.7967, -58.2765), "Universidad Nacional Arturo Jauretche, Florencio Varela"),
            Place(GeoPoint(-34.7206, -58.2546), "Rivadavia 300, Quilmes"),
            Place(GeoPoint(-34.7240, -58.2620), "Av. Mitre 500, Quilmes"),
            Place(GeoPoint(-34.7300, -58.2700), "Calle 123, Quilmes"),
            Place(GeoPoint(-34.7631, -58.2112), "Calle 12 450, Berazategui"),
            Place(GeoPoint(-34.7650, -58.2050), "Av. Mitre 1200, Berazategui"),
        )
    }
}
