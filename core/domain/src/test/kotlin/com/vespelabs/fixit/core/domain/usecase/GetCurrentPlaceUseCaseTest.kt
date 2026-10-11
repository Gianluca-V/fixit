package com.vespelabs.fixit.core.domain.usecase

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.testing.TestLocation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetCurrentPlaceUseCaseTest {
    private val location = TestLocation()
    private val getCurrentPlace = GetCurrentPlaceUseCase(location, location)

    private val florencioVarela = GeoPoint(-34.8065, -58.2788)

    @Test
    fun `given the simulated location when getting the current place then coordinates and address are combined`() = runTest {
        val result = getCurrentPlace()

        assertEquals(
            AppResult.Success(Place(florencioVarela, "Av. San Martín 2000, Florencio Varela")),
            result,
        )
        assertEquals(listOf(florencioVarela), location.resolvedPoints)
    }

    @Test
    fun `given an address that cannot be resolved when getting the current place then the address is empty`() = runTest {
        location.address = AppResult.Error(DomainError.Network)

        val result = getCurrentPlace()

        assertEquals(AppResult.Success(Place(florencioVarela, "")), result)
    }

    @Test
    fun `given a location failure when getting the current place then the result is LocationUnavailable`() = runTest {
        location.point = AppResult.Error(DomainError.LocationUnavailable)

        val result = getCurrentPlace()

        assertEquals(AppResult.Error(DomainError.LocationUnavailable), result)
        assertTrue("the address is not resolved without coordinates", location.resolvedPoints.isEmpty())
    }
}
