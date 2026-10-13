package com.vespelabs.fixit.core.data.device

import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressFormattingTest {

    private val full = AddressParts(
        thoroughfare = "Av. San Martín",
        subThoroughfare = "2000",
        locality = "Florencio Varela",
        addressLine = "Av. San Martín 2000, B1888 Florencio Varela, Provincia de Buenos Aires, Argentina",
    )

    @Test
    fun `given street, number and locality when formatting then it is street number, locality`() {
        assertEquals("Av. San Martín 2000, Florencio Varela", formatAddress(full))
    }

    @Test
    fun `given no street number when formatting then the street is shown alone`() {
        assertEquals("Av. San Martín, Florencio Varela", formatAddress(full.copy(subThoroughfare = null)))
    }

    @Test
    fun `given no street when formatting then the full address line is used`() {
        assertEquals(full.addressLine, formatAddress(full.copy(thoroughfare = " ", subThoroughfare = null)))
    }

    @Test
    fun `given only the locality when formatting then the locality is used`() {
        assertEquals("Florencio Varela", formatAddress(AddressParts(null, null, "Florencio Varela", null)))
    }

    @Test
    fun `given no usable part when formatting then there is no address`() {
        assertNull(formatAddress(AddressParts(null, "2000", " ", "")))
    }

    @Test
    fun `given geocoder results when resolving then the first one is formatted`() = runTest {
        val other = AddressParts("Calle 10", "123", "Quilmes", null)
        assertEquals(AppResult.Success("Av. San Martín 2000, Florencio Varela"), resolveAddress { listOf(full, other) })
    }

    @Test
    fun `given no results when resolving then the error is LocationUnavailable`() = runTest {
        assertEquals(AppResult.Error(DomainError.LocationUnavailable), resolveAddress { emptyList() })
    }

    @Test
    fun `given only unusable results when resolving then the error is LocationUnavailable`() = runTest {
        assertEquals(AppResult.Error(DomainError.LocationUnavailable), resolveAddress { listOf(AddressParts(null, null, null, null)) })
    }

    @Test
    fun `given the geocoder service fails (IOException) when resolving then the error is Network`() = runTest {
        assertEquals(AppResult.Error(DomainError.Network), resolveAddress { throw IOException("grpc failed") })
    }

    @Test
    fun `given invalid coordinates when resolving then the error is LocationUnavailable`() = runTest {
        assertEquals(AppResult.Error(DomainError.LocationUnavailable), resolveAddress { throw IllegalArgumentException("lat") })
    }
}
