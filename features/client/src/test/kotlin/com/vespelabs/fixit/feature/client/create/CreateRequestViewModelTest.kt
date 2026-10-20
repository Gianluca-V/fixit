package com.vespelabs.fixit.feature.client.create

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.model.SessionState
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.result.FormField
import com.vespelabs.fixit.core.domain.result.ValidationError
import com.vespelabs.fixit.core.domain.testing.MainDispatcherRule
import com.vespelabs.fixit.core.domain.testing.TestAuthRepository
import com.vespelabs.fixit.core.domain.testing.TestData
import com.vespelabs.fixit.core.domain.testing.TestLocation
import com.vespelabs.fixit.core.domain.testing.TestRepairRequestRepository
import com.vespelabs.fixit.core.domain.testing.TestUserRepository
import com.vespelabs.fixit.core.domain.usecase.CreateRequestUseCase
import com.vespelabs.fixit.core.domain.usecase.GetCurrentPlaceUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateRequestViewModelTest {
    // StandardTestDispatcher queues the coroutines, so "in progress" states are observable.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val client = TestData.client(id = "c1")
    private val auth = TestAuthRepository(SessionState.SignedIn(client))
    private val users = TestUserRepository(listOf(client))
    private val requests = TestRepairRequestRepository()
    private val location = TestLocation()
    // Lazy: the ViewModel launches its area read in init, so it must be built after MainDispatcherRule sets Main.
    private val viewModel by lazy { newViewModel() }

    private fun newViewModel() = CreateRequestViewModel(
        CreateRequestUseCase(auth, users, requests),
        GetCurrentPlaceUseCase(location, location),
        auth,
        users,
    )

    private val state get() = viewModel.uiState.value

    /** Content URIs as the Photo Picker or the camera FileProvider return them. */
    private val pickedPhotos = (1..4).map { "content://media/picker/0/com.android.providers.media.photopicker/media/$it" }

    private fun addPhotos(count: Int) = viewModel.onPhotosAdded(pickedPhotos.take(count))

    /** Category, description and two photos; the location is left to each test. */
    private fun fillFormWithoutLocation() {
        viewModel.onCategorySelect(ServiceCategory.PLUMBING)
        viewModel.onDescriptionChange("The kitchen tap is leaking since yesterday")
        addPhotos(2)
    }

    // [rr] Creation form

    @Test
    fun `given plumbing selected when the user taps gas then only gas is selected`() {
        viewModel.onCategorySelect(ServiceCategory.PLUMBING)

        viewModel.onCategorySelect(ServiceCategory.GAS)

        assertEquals(ServiceCategory.GAS, state.category)
    }

    @Test
    fun `given a form without photos when publishing then the photos error is shown and the form keeps its data`() =
        runTest {
            viewModel.onCategorySelect(ServiceCategory.PLUMBING)
            viewModel.onDescriptionChange("The kitchen tap is leaking since yesterday")
            viewModel.onUseLocation()
            advanceUntilIdle()

            viewModel.onSubmit()
            advanceUntilIdle()

            assertEquals(ValidationError.PHOTOS_REQUIRED, state.validationError)
            assertEquals(FormField.PHOTOS, state.validationError?.field)
            assertEquals(ServiceCategory.PLUMBING, state.category)
            assertEquals("The kitchen tap is leaking since yesterday", state.description)
            assertEquals(TestData.florencioVarela.address, state.address)
            assertNull(state.createdRequestId)
            assertTrue(requests.createCalls.isEmpty())
        }

    @Test
    fun `given a valid form when publishing then the request is created and its id is offered once`() = runTest {
        fillFormWithoutLocation()
        viewModel.onUseLocation()
        advanceUntilIdle()

        viewModel.onSubmit()
        assertTrue(state.isSubmitting)
        advanceUntilIdle()

        assertFalse(state.isSubmitting)
        assertNull(state.validationError)
        val created = requests.requestsSnapshot().single()
        assertEquals(created.id, state.createdRequestId)
        assertEquals(RequestStatus.OPEN, created.status)
        assertEquals("c1", created.clientId)
        assertEquals(pickedPhotos.take(2), created.photoUrls)

        viewModel.onNavigated()

        assertNull(state.createdRequestId)
    }

    @Test
    fun `given a submit in progress when publishing again then only one request is created`() = runTest {
        fillFormWithoutLocation()
        viewModel.onUseLocation()
        advanceUntilIdle()

        viewModel.onSubmit()
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(1, requests.createCalls.size)
    }

    @Test
    fun `given publishing fails with network when the result arrives then the message is shown and the form keeps its data`() =
        runTest {
            fillFormWithoutLocation()
            viewModel.onUseLocation()
            advanceUntilIdle()
            requests.createError = DomainError.Network

            viewModel.onSubmit()
            advanceUntilIdle()

            assertEquals(DomainError.Network, state.message)
            assertNull(state.validationError)
            assertNull(state.createdRequestId)
            assertEquals(2, state.photoUris.size)
            assertEquals(ServiceCategory.PLUMBING, state.category)

            viewModel.onMessageShown()

            assertNull(state.message)
        }

    // [media] Photos

    @Test
    fun `given three photos when more photos are picked then the list stays at three and adding is disabled`() {
        addPhotos(3)

        assertFalse(state.canAddPhoto)
        viewModel.onPhotosAdded(listOf(pickedPhotos[3]))
        assertEquals(pickedPhotos.take(3), state.photoUris)
    }

    @Test
    fun `given three photos when the user removes one then two remain and adding is enabled again`() {
        addPhotos(3)

        viewModel.onRemovePhoto(1)

        assertEquals(listOf(pickedPhotos[0], pickedPhotos[2]), state.photoUris)
        assertTrue(state.canAddPhoto)
    }

    @Test
    fun `given one photo when two photos are picked then they are appended in order`() {
        addPhotos(1)

        viewModel.onPhotosAdded(pickedPhotos.subList(1, 3))

        assertEquals(pickedPhotos.take(3), state.photoUris)
    }

    @Test
    fun `given two photos when more photos than missing are picked then only the missing ones are added`() {
        addPhotos(2)

        viewModel.onPhotosAdded(pickedPhotos.subList(2, 4))

        assertEquals(pickedPhotos.take(3), state.photoUris)
    }

    @Test
    fun `given one photo when the picker is cancelled then the list does not change`() {
        addPhotos(1)

        viewModel.onPhotosAdded(emptyList())

        assertEquals(pickedPhotos.take(1), state.photoUris)
    }

    // [loc] Location

    @Test
    fun `given the create screen when the user taps use my location then the simulated coordinates and address are filled`() =
        runTest {
            viewModel.onUseLocation()
            assertTrue(state.isLocating)
            advanceUntilIdle()

            assertFalse(state.isLocating)
            assertEquals(TestData.florencioVarela.point, state.point)
            assertEquals(TestData.florencioVarela.address, state.address)
        }

    @Test
    fun `given a location with coordinates P when the user edits the address and publishes then the request keeps P with the new address`() =
        runTest {
            val p = GeoPoint(-34.7200, -58.2500)
            location.point = AppResult.Success(p)
            fillFormWithoutLocation()
            viewModel.onUseLocation()
            advanceUntilIdle()

            viewModel.onAddressChange("Calle 123, Quilmes")
            viewModel.onSubmit()
            advanceUntilIdle()

            assertEquals(Place(p, "Calle 123, Quilmes"), requests.requestsSnapshot().single().place)
        }

    @Test
    fun `given the simulated location fails when the user taps use my location then the message is shown and publishing requires a location`() =
        runTest {
            location.point = AppResult.Error(DomainError.LocationUnavailable)
            fillFormWithoutLocation()

            viewModel.onUseLocation()
            advanceUntilIdle()

            assertEquals(DomainError.LocationUnavailable, state.message)
            assertFalse(state.isLocating)
            assertNull(state.point)

            viewModel.onAddressChange("Calle 123, Quilmes")
            assertEquals("Calle 123, Quilmes", state.address)

            viewModel.onSubmit()
            advanceUntilIdle()

            assertEquals(ValidationError.LOCATION_REQUIRED, state.validationError)
            assertTrue(requests.createCalls.isEmpty())
        }

    // [rr] Location preloaded with the client area / chosen on the map

    private val quilmes = Place(GeoPoint(-34.7206, -58.2546), "Calle 123, Quilmes")

    @Test
    fun `given a client with area Z when the creation form opens then the location is Z with its address`() = runTest {
        users.upsert(client.copy(area = quilmes))

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(quilmes.point, viewModel.uiState.value.point)
        assertEquals("Calle 123, Quilmes", viewModel.uiState.value.address)
    }

    @Test
    fun `given a client without area when the creation form opens then the location is empty`() = runTest {
        advanceUntilIdle()

        assertNull(state.point)
        assertEquals("", state.address)
    }

    @Test
    fun `given the user cannot be read when the creation form opens then there is no message and the location is empty`() =
        runTest {
            users.setUsers(emptyList())

            val viewModel = newViewModel()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.message)
            assertNull(viewModel.uiState.value.point)
        }

    @Test
    fun `given a location already obtained when the area arrives later then it does not overwrite it`() = runTest {
        users.upsert(client.copy(area = quilmes))
        val viewModel = newViewModel()

        viewModel.onPlacePicked(Place(GeoPoint(-34.7631, -58.2112), "Calle 12 450, Berazategui"))
        advanceUntilIdle()

        assertEquals("Calle 12 450, Berazategui", viewModel.uiState.value.address)
    }

    @Test
    fun `given a location from use my location when Q is confirmed on the map then point and address are both replaced`() =
        runTest {
            viewModel.onUseLocation()
            advanceUntilIdle()
            val q = Place(GeoPoint(-34.7631, -58.2112), "Calle 12 450, Berazategui")

            viewModel.onPlacePicked(q)

            assertEquals(q.point, state.point)
            assertEquals("Calle 12 450, Berazategui", state.address)
        }

    @Test
    fun `given a valid form with Q chosen on the map when publishing then the request has Q and its address`() = runTest {
        val q = Place(GeoPoint(-34.7631, -58.2112), "Calle 12 450, Berazategui")
        fillFormWithoutLocation()
        viewModel.onPlacePicked(q)

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(q, requests.requestsSnapshot().single().place)
    }
}
