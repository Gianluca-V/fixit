package com.vespelabs.fixit.core.data.photon

import com.vespelabs.fixit.core.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuildPhotonRequestTest {
    @Test
    fun `given a query and a center when building the request then the url has q limit bbox and the center`() {
        val request = buildPhotonRequest("Calle 12 & 5", GeoPoint(-34.8065, -58.2788), "FixIt/1.0")
        val url = request.url

        assertEquals("GET", request.method)
        assertEquals("photon.komoot.io", url.host)
        assertEquals("/api/", url.encodedPath)
        assertEquals("Calle 12 & 5", url.queryParameter("q"))
        assertEquals("8", url.queryParameter("limit"))
        assertEquals("-73.6,-55.1,-53.6,-21.8", url.queryParameter("bbox"))
        assertEquals("-34.8065", url.queryParameter("lat"))
        assertEquals("-58.2788", url.queryParameter("lon"))
        assertEquals("FixIt/1.0", request.header("User-Agent"))
    }

    @Test
    fun `given no center when building the request then lat and lon are not sent`() {
        val url = buildPhotonRequest("Calle 12", near = null, userAgent = "FixIt/1.0").url

        assertNull(url.queryParameter("lat"))
        assertNull(url.queryParameter("lon"))
    }
}
