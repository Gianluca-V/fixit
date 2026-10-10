package ar.edu.unaj.fixit.feature.client.create

import ar.edu.unaj.fixit.core.domain.model.GeoPoint
import ar.edu.unaj.fixit.core.domain.model.Place
import ar.edu.unaj.fixit.core.domain.model.RequestStatus
import ar.edu.unaj.fixit.core.domain.model.ServiceCategory
import ar.edu.unaj.fixit.core.domain.model.SessionState
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.result.FormField
import ar.edu.unaj.fixit.core.domain.result.ValidationError
import ar.edu.unaj.fixit.core.domain.testing.MainDispatcherRule
import ar.edu.unaj.fixit.core.domain.testing.TestAuthRepository
import ar.edu.unaj.fixit.core.domain.testing.TestData
import ar.edu.unaj.fixit.core.domain.testing.TestLocation
import ar.edu.unaj.fixit.core.domain.testing.TestRepairRequestRepository
import ar.edu.unaj.fixit.core.domain.testing.TestUserRepository
import ar.edu.unaj.fixit.core.domain.usecase.CreateRequestUseCase
import ar.edu.unaj.fixit.core.domain.usecase.GetCurrentPlaceUseCase
import ar.edu.unaj.fixit.core.ui.components.SamplePhotos
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
    private val viewModel = CreateRequestViewModel(
        CreateRequestUseCase(auth, users, requests),
        GetCurrentPlaceUseCase(location, location),
    )

    private val state get() = viewModel.uiState.value

    private fun addSamplePhotos(count: Int) = repeat(count) { viewModel.onAddSamplePhoto() }

    /** Category, description and two photos; the location is left to each test. */
    private fun fillFormWithoutLocation() {
        viewModel.onCategorySelect(ServiceCategory.PLUMBING)
        viewModel.onDescriptionChange("The kitchen tap is leaking since yesterday")
        addSamplePhotos(2)
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
        assertEquals(SamplePhotos.take(2), created.photoUrls)

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
    fun `given three photos when the photo section is shown then adding photos is disabled`() {
        addSamplePhotos(3)

        assertFalse(state.canAddPhoto)
        viewModel.onAddSamplePhoto()
        assertEquals(3, state.photoUris.size)
    }

    @Test
    fun `given three photos when the user removes one then two remain and adding is enabled again`() {
        addSamplePhotos(3)

        viewModel.onRemovePhoto(1)

        assertEquals(listOf(SamplePhotos[0], SamplePhotos[2]), state.photoUris)
        assertTrue(state.canAddPhoto)
    }

    @Test
    fun `given no photos when the user taps add sample photo then one sample photo is added`() {
        viewModel.onAddSamplePhoto()

        assertEquals(1, state.photoUris.size)
        assertTrue(state.photoUris.single() in SamplePhotos)
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
}
