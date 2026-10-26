package com.vespelabs.fixit.core.data.fake

import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.result.AppResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAddressSearchTest {
    private val search = FakeAddressSearch()

    private suspend fun addresses(query: String): List<String> =
        (search.search(query, near = null) as AppResult.Success<List<Place>>).data.map { it.address }

    @Test
    fun `given the text quilmes in lowercase when searching then only places of Quilmes are returned`() = runTest {
        val result = addresses("quilmes")

        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.endsWith("Quilmes") })
    }

    @Test
    fun `given the text calchaqui without accent when searching then Av Calchaqui is found`() = runTest {
        assertTrue(addresses("calchaqui").any { it.startsWith("Av. Calchaquí") })
    }

    @Test
    fun `given a text without matches when searching then the result is empty`() = runTest {
        assertEquals(emptyList<String>(), addresses("Rosario"))
    }
}
