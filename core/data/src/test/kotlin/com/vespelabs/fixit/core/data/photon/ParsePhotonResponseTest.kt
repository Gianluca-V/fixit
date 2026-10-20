package com.vespelabs.fixit.core.data.photon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ParsePhotonResponseTest {
    private val twoFeatures = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","geometry":{"type":"Point","coordinates":[-58.2788,-34.8065]},
           "properties":{"street":"Avenida San Martín","housenumber":"2000","city":"Florencio Varela","countrycode":"AR"}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[-58.2546,-34.7206]},
           "properties":{"name":"Quilmes","county":"Partido de Quilmes"}}
        ]}
    """.trimIndent()

    @Test
    fun `given a feature collection with 2 results when parsing then latitude and longitude come from coordinates lon lat`() {
        val features = parsePhotonResponse(200, twoFeatures)

        assertEquals(
            listOf(
                PhotonFeature(
                    latitude = -34.8065,
                    longitude = -58.2788,
                    street = "Avenida San Martín",
                    houseNumber = "2000",
                    city = "Florencio Varela",
                ),
                PhotonFeature(latitude = -34.7206, longitude = -58.2546, name = "Quilmes", county = "Partido de Quilmes"),
            ),
            features,
        )
    }

    @Test
    fun `given a feature without geometry when parsing then it is skipped`() {
        val body = """{"features":[{"properties":{"name":"Nowhere"}}]}"""

        assertEquals(emptyList<PhotonFeature>(), parsePhotonResponse(200, body))
    }

    @Test
    fun `given an HTTP 503 when parsing then it throws PhotonSearchException with the code`() {
        val error = assertThrows(PhotonSearchException::class.java) { parsePhotonResponse(503, "") }

        assertEquals(503, error.httpCode)
    }

    @Test
    fun `given a body that is not JSON when parsing then it throws`() {
        assertThrows(Exception::class.java) { parsePhotonResponse(200, "<html>busy</html>") }
    }
}
