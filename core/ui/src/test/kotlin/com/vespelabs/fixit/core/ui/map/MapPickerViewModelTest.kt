package com.vespelabs.fixit.core.ui.map

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.testing.MainDispatcherRule
import com.vespelabs.fixit.core.domain.testing.TestAddressSearch
import com.vespelabs.fixit.core.domain.testing.TestLocation
import com.vespelabs.fixit.core.domain.usecase.SearchAddressUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapPickerViewModelTest {
    // StandardTestDispatcher queues the coroutines, so "in progress" states and the 300 ms wait are observable.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val search = TestAddressSearch()
    private val location = TestLocation(address = AppResult.Success("Av. Calchaquí 3950, Florencio Varela"))

    private val florencioVarela = GeoPoint(-34.8065, -58.2788)
    private val quilmes = Place(GeoPoint(-34.7206, -58.2546), "Calle 123, Quilmes")
    private val q = GeoPoint(-34.7745, -58.2669)
    private val berazategui = Place(GeoPoint(-34.7631, -58.2112), "Calle 12 450, Berazategui")

    private fun viewModel(initial: Place? = null) = MapPickerViewModel(initial, SearchAddressUseCase(search), location)

    private fun suggestions(vararg places: Place) = AppResult.Success(places.toList())

    // [addr] Map picker: opening and the map

    @Test
    fun `given a form without location when the picker opens then it starts at Florencio Varela without asking for the location`() =
        runTest {
            val state = viewModel().uiState.value

            assertEquals(florencioVarela, state.center)
            assertEquals("", state.address)
            assertEquals(0, location.currentCalls)
        }

    @Test
    fun `given a form with location P when the picker opens then it starts at P with its address`() = runTest {
        val viewModel = viewModel(quilmes)

        assertEquals(quilmes.point, viewModel.uiState.value.center)
        assertEquals("Calle 123, Quilmes", viewModel.uiState.value.address)
    }

    @Test
    fun `given a form with location P when the map first settles on P then its address is not resolved again`() = runTest {
        val viewModel = viewModel(quilmes)

        viewModel.onCameraIdle(quilmes.point)
        advanceUntilIdle()

        assertEquals("Calle 123, Quilmes", viewModel.uiState.value.address)
        assertTrue(location.resolvedPoints.isEmpty())
    }

    @Test
    fun `given the picker when the map stops at Q then Q is chosen and its address is resolved`() = runTest {
        location.resolveDelayMs = 100
        val viewModel = viewModel()

        viewModel.onCameraIdle(q)
        runCurrent()
        assertTrue(viewModel.uiState.value.isResolving)
        assertEquals(q, viewModel.uiState.value.center)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isResolving)
        assertEquals("Av. Calchaquí 3950, Florencio Varela", state.address)
        assertEquals(listOf(q), location.resolvedPoints)
    }

    @Test
    fun `given resolving fails with network when the map stops at Q then Q is chosen with an empty address`() = runTest {
        location.address = AppResult.Error(DomainError.Network)
        val viewModel = viewModel(quilmes)

        viewModel.onCameraIdle(q)
        advanceUntilIdle()

        assertEquals(q, viewModel.uiState.value.center)
        assertEquals("", viewModel.uiState.value.address)
        assertFalse(viewModel.uiState.value.isResolving)
    }

    @Test
    fun `given two stops in a row when the first resolution answers last then only the last stop counts`() = runTest {
        location.resolveDelayMs = 100
        val viewModel = viewModel()

        viewModel.onCameraIdle(florencioVarela)
        runCurrent()
        location.address = AppResult.Success("Av. Calchaquí 3950, Florencio Varela")
        viewModel.onCameraIdle(q)
        advanceUntilIdle()

        assertEquals(q, viewModel.uiState.value.center)
        assertEquals("Av. Calchaquí 3950, Florencio Varela", viewModel.uiState.value.address)
        assertFalse(viewModel.uiState.value.isResolving)
    }

    @Test
    fun `given a resolution in progress when the user edits the address then the typed text wins`() = runTest {
        location.resolveDelayMs = 100
        val viewModel = viewModel()

        viewModel.onCameraIdle(q)
        runCurrent()
        viewModel.onAddressChange("Casa de mi madre, Calle 5 100")
        advanceUntilIdle()

        assertEquals("Casa de mi madre, Calle 5 100", viewModel.uiState.value.address)
        assertFalse(viewModel.uiState.value.isResolving)
    }

    // [addr] Autocomplete while typing

    @Test
    fun `given the picker when typing Cal Call and Calle less than 300 ms apart then a single search with Calle is made`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onQueryChange("Cal")
            advanceTimeBy(100)
            viewModel.onQueryChange("Call")
            advanceTimeBy(100)
            viewModel.onQueryChange("Calle")
            advanceTimeBy(299)
            runCurrent()
            assertTrue(search.calls.isEmpty())
            advanceUntilIdle()

            assertEquals(listOf("Calle"), search.calls.map { it.first })
        }

    @Test
    fun `given a search of Calle 1 still pending when the text changes to Calle 12 then the suggestions are those of Calle 12`() =
        runTest {
            val calle1 = Place(GeoPoint(-34.70, -58.20), "Calle 1 100, Quilmes")
            search.resultFor = { if (it == "Calle 1") suggestions(calle1) else suggestions(berazategui) }
            search.delayFor = { if (it == "Calle 1") 1_000L else 10L }
            val viewModel = viewModel()

            viewModel.onQueryChange("Calle 1")
            advanceTimeBy(400) // the Calle 1 search has started and is waiting for its answer
            viewModel.onQueryChange("Calle 12")
            advanceUntilIdle()

            assertEquals(listOf(berazategui), viewModel.uiState.value.suggestions)
            assertEquals("Calle 12", viewModel.uiState.value.query)
        }

    @Test
    fun `given the search fails with network when typing San Martin then search is unavailable with no suggestions and confirm still works`() =
        runTest {
            search.result = AppResult.Error(DomainError.Network)
            val viewModel = viewModel(quilmes)

            viewModel.onQueryChange("San Martín")
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.searchUnavailable)
            assertTrue(viewModel.uiState.value.suggestions.isEmpty())
            assertEquals(quilmes, viewModel.confirmedPlace())
        }

    @Test
    fun `given suggestions are shown when the text is cleared then they are hidden at once`() = runTest {
        search.result = suggestions(berazategui)
        val viewModel = viewModel()
        viewModel.onQueryChange("Calle 12")
        advanceUntilIdle()

        viewModel.onQueryChange("")
        runCurrent()

        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
        assertFalse(viewModel.uiState.value.searchUnavailable)
    }

    @Test
    fun `given the map was moved to Q when searching then the search is biased to Q`() = runTest {
        val viewModel = viewModel()
        viewModel.onCameraIdle(q)
        advanceUntilIdle()

        viewModel.onQueryChange("Mitre")
        advanceUntilIdle()

        assertEquals(listOf("Mitre" to q), search.calls)
    }

    // [addr] Choosing a suggestion and confirming

    @Test
    fun `given the suggestion Calle 12 450 Berazategui when it is chosen then the map moves there and its address is used as is`() =
        runTest {
            search.result = suggestions(berazategui)
            val viewModel = viewModel()
            viewModel.onQueryChange("Calle 12")
            advanceUntilIdle()

            viewModel.onSuggestionClick(berazategui)
            runCurrent()

            var state = viewModel.uiState.value
            assertEquals(berazategui.point, state.center)
            assertEquals("Calle 12 450, Berazategui", state.address)
            assertEquals(berazategui.point, state.cameraTarget)
            assertTrue(state.suggestions.isEmpty())
            assertEquals("", state.query)

            viewModel.onCameraMoved()
            viewModel.onCameraIdle(GeoPoint(berazategui.point.latitude + 1e-7, berazategui.point.longitude))
            advanceUntilIdle()

            state = viewModel.uiState.value
            assertNull(state.cameraTarget)
            assertEquals("Calle 12 450, Berazategui", state.address)
            assertTrue(location.resolvedPoints.isEmpty())
        }

    @Test
    fun `given the point Q with an edited address when confirming then the place is Q with that address trimmed`() = runTest {
        val viewModel = viewModel()
        viewModel.onCameraIdle(q)
        advanceUntilIdle()

        viewModel.onAddressChange("  Casa de mi madre, Calle 5 100 ")

        assertEquals(Place(q, "Casa de mi madre, Calle 5 100"), viewModel.confirmedPlace())
    }
}
