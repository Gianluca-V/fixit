package com.vespelabs.fixit.core.domain.usecase

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.testing.TestAddressSearch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAddressUseCaseTest {
    private val service = TestAddressSearch()
    private val searchAddress = SearchAddressUseCase(service)

    private val center = GeoPoint(-34.8065, -58.2788)

    private fun place(index: Int) = Place(GeoPoint(-34.80 - index / 100.0, -58.27), "Calle $index, Florencio Varela")

    // [addr] Address search rules

    @Test
    fun `given a text of 2 characters after trimming when searching then the result is empty and the service is not called`() =
        runTest {
            val result = searchAddress("  Av ", center)

            assertEquals(AppResult.Success(emptyList<Place>()), result)
            assertTrue(service.calls.isEmpty())
        }

    @Test
    fun `given the service returns 8 valid results when searching then the first 5 are returned in order`() = runTest {
        val all = (1..8).map(::place)
        service.result = AppResult.Success(all)

        val result = searchAddress("San Martín", center)

        assertEquals(AppResult.Success(all.take(5)), result)
    }

    @Test
    fun `given a result without address and one with latitude 91 when searching then both are discarded`() = runTest {
        val valid = place(1)
        service.result = AppResult.Success(
            listOf(
                Place(GeoPoint(-34.7, -58.3), "   "),
                Place(GeoPoint(91.0, -58.3), "Calle Falsa 123, Quilmes"),
                valid,
            ),
        )

        val result = searchAddress("Calle", center)

        assertEquals(AppResult.Success(listOf(valid)), result)
    }

    @Test
    fun `given invalid results first when searching then the limit of 5 counts only valid suggestions`() = runTest {
        val invalid = List(3) { Place(GeoPoint(-34.7, 200.0), "Calle $it") }
        val valid = (1..6).map(::place)
        service.result = AppResult.Success(invalid + valid)

        val result = searchAddress("Calle", center)

        assertEquals(AppResult.Success(valid.take(5)), result)
    }

    @Test
    fun `given the service fails with network when searching then the result is the network error`() = runTest {
        service.result = AppResult.Error(DomainError.Network)

        val result = searchAddress("San Martín", center)

        assertEquals(AppResult.Error(DomainError.Network), result)
    }

    @Test
    fun `given a text with surrounding spaces when searching then the service receives it trimmed with the map center`() =
        runTest {
            searchAddress("  Calle 12  ", center)

            assertEquals(listOf("Calle 12" to center), service.calls)
        }
}
