package com.vespelabs.fixit.core.data.photon

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatPhotonAddressTest {
    private fun feature(
        name: String? = null,
        street: String? = null,
        houseNumber: String? = null,
        city: String? = null,
        district: String? = null,
        locality: String? = null,
        county: String? = null,
    ) = PhotonFeature(-34.8, -58.3, name, street, houseNumber, city, district, locality, county)

    // [addr] Readable address of a suggestion

    @Test
    fun `given street number and locality when formatting then it is street number comma locality`() {
        assertEquals(
            "Avenida San Martín 2000, Florencio Varela",
            formatPhotonAddress(feature(street = "Avenida San Martín", houseNumber = "2000", city = "Florencio Varela")),
        )
    }

    @Test
    fun `given a named place without street when formatting then it is name comma locality`() {
        assertEquals(
            "Universidad Nacional Arturo Jauretche, Florencio Varela",
            formatPhotonAddress(feature(name = "Universidad Nacional Arturo Jauretche", city = "Florencio Varela")),
        )
    }

    @Test
    fun `given a street without number and without locality when formatting then it is only the street`() {
        assertEquals("Calle 10", formatPhotonAddress(feature(street = "Calle 10")))
    }

    @Test
    fun `given no city when formatting then the locality falls back to district then locality then county`() {
        assertEquals("Calle 10, Bosques", formatPhotonAddress(feature(street = "Calle 10", district = "Bosques", county = "X")))
        assertEquals("Calle 10, Zeballos", formatPhotonAddress(feature(street = "Calle 10", locality = "Zeballos", county = "X")))
        assertEquals("Calle 10, Partido de Quilmes", formatPhotonAddress(feature(street = "Calle 10", county = "Partido de Quilmes")))
    }

    @Test
    fun `given a place whose name is its locality when formatting then the locality is not repeated`() {
        assertEquals("Quilmes", formatPhotonAddress(feature(name = "Quilmes", city = "Quilmes")))
    }

    @Test
    fun `given only a locality when formatting then it is the locality`() {
        assertEquals("Berazategui", formatPhotonAddress(feature(city = "Berazategui")))
    }

    @Test
    fun `given blank parts when formatting then they are omitted without loose commas`() {
        assertEquals("Calle 10", formatPhotonAddress(feature(street = " Calle 10 ", houseNumber = " ", city = "")))
    }

    @Test
    fun `given nothing usable when formatting then it is null`() {
        assertNull(formatPhotonAddress(feature(name = "  ")))
    }

    @Test
    fun `given a usable feature when converting to a place then it has its coordinates and address`() {
        assertEquals(
            Place(GeoPoint(-34.8, -58.3), "Berazategui"),
            feature(city = "Berazategui").toPlace(),
        )
        assertNull(feature().toPlace())
    }
}
