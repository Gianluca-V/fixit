package com.vespelabs.fixit.core.data.fake

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.usecase.GetCurrentPlaceUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeLocationTest {
    private val location = FakeLocation()
    private val florencioVarela = GeoPoint(-34.8065, -58.2788)

    @Test
    fun `given the default simulation when asking for the current location then it is Florencio Varela`() = runTest {
        assertEquals(AppResult.Success(florencioVarela), location.current())
    }

    @Test
    fun `given the default simulation when resolving an address then it is Av San Martin 2000 Florencio Varela`() = runTest {
        assertEquals(
            AppResult.Success("Av. San Martín 2000, Florencio Varela"),
            location.resolve(florencioVarela),
        )
    }

    @Test
    fun `given the default simulation when the use case builds the place then coordinates and address come from the fake`() = runTest {
        val place = GetCurrentPlaceUseCase(location, location)()

        assertEquals(
            AppResult.Success(Place(florencioVarela, "Av. San Martín 2000, Florencio Varela")),
            place,
        )
    }

    @Test
    fun `given failWith LocationUnavailable when asking for the current location then the result is that error`() = runTest {
        location.failWith = DomainError.LocationUnavailable

        assertEquals(AppResult.Error(DomainError.LocationUnavailable), location.current())
    }

    @Test
    fun `given failWith cleared when asking again then the location is available again`() = runTest {
        location.failWith = DomainError.LocationUnavailable
        location.current()

        location.failWith = null

        assertEquals(AppResult.Success(florencioVarela), location.current())
    }

    @Test
    fun `given a configured point and address when asking then the configured values are returned`() = runTest {
        val quilmes = GeoPoint(-34.7206, -58.2546)
        location.point = quilmes
        location.address = "Quilmes centro"

        assertEquals(AppResult.Success(quilmes), location.current())
        assertEquals(AppResult.Success("Quilmes centro"), location.resolve(quilmes))
    }
}
