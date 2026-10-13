package com.vespelabs.fixit.feature.professional.profile

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.model.Reputation
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.model.SessionState
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.result.ValidationError
import com.vespelabs.fixit.core.domain.testing.MainDispatcherRule
import com.vespelabs.fixit.core.domain.testing.TestAuthRepository
import com.vespelabs.fixit.core.domain.testing.TestData
import com.vespelabs.fixit.core.domain.testing.TestLocation
import com.vespelabs.fixit.core.domain.testing.TestUserRepository
import com.vespelabs.fixit.core.domain.usecase.GetCurrentPlaceUseCase
import com.vespelabs.fixit.core.domain.usecase.UpdateProfileUseCase
import com.vespelabs.fixit.core.ui.components.ProfileFormState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProEditProfileViewModelTest {
    // StandardTestDispatcher queues the coroutines, so "in progress" states are observable.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val reputation = Reputation(ratingSum = 13, reviewCount = 3)
    private val luis = TestData.professional(
        id = "p1",
        name = "Luis",
        email = "luis@fixit.test",
        phone = "1155550101",
        categories = setOf(ServiceCategory.PLUMBING),
        radiusKm = 12,
        reputation = reputation,
    )
    private val auth = TestAuthRepository(SessionState.SignedIn(luis))
    private val users = TestUserRepository(listOf(luis))
    private val location = TestLocation(
        point = AppResult.Success(GeoPoint(-34.7000, -58.3000)),
        address = AppResult.Success("Calle Nueva 456, Quilmes"),
    )

    private fun viewModel() = ProEditProfileViewModel(
        auth,
        users,
        UpdateProfileUseCase(auth, users),
        GetCurrentPlaceUseCase(location, location),
    )

    /** Waits for the first matching state; the virtual-time timeout makes a missing state fail fast. */
    private suspend fun ProEditProfileViewModel.awaitState(
        predicate: (ProEditProfileUiState) -> Boolean,
    ): ProEditProfileUiState = withTimeout(5_000L) { uiState.first(predicate) }

    private suspend fun loadedViewModel(): ProEditProfileViewModel =
        viewModel().also { it.awaitState { state -> !state.isLoading } }

    private fun ProEditProfileViewModel.edit(transform: (ProfileFormState) -> ProfileFormState) =
        onFormChange(transform(uiState.value.form))

    private fun saved() = users.user("p1")!!

    // [prof] Edit profile screen

    @Test
    fun `given a professional named Luis with radius 12 when the edit screen opens then the form is preloaded`() = runTest {
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.isLoading)

        val state = viewModel.awaitState { !it.isLoading }

        assertEquals("Luis", state.form.name)
        assertEquals("12", state.form.radiusKm)
        assertEquals(setOf(ServiceCategory.PLUMBING), state.form.categories)
        assertEquals(luis.professional!!.base, state.form.base)
        assertEquals("luis@fixit.test", state.email)
    }

    @Test
    fun `given the professional changes name and phone when saving then the trimmed values are saved and email and reputation do not change`() =
        runTest {
            val viewModel = loadedViewModel()
            viewModel.edit { it.copy(name = "  Luis Pérez ", phone = " 11 5555-0199 ") }

            viewModel.onSave()
            assertTrue(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.canSave)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.saved)
            assertEquals("Luis Pérez", saved().name)
            assertEquals("11 5555-0199", saved().phone)
            assertEquals("luis@fixit.test", saved().email)
            assertEquals(reputation, saved().professional?.reputation)
        }

    @Test
    fun `given a plumber with radius 10 when changing to plumbing and gas with radius 25 then the work zone is saved and the reputation is kept`() =
        runTest {
            val viewModel = loadedViewModel()
            viewModel.edit { it.copy(categories = setOf(ServiceCategory.PLUMBING, ServiceCategory.GAS), radiusKm = "25") }

            viewModel.onSave()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.saved)
            assertEquals(setOf(ServiceCategory.PLUMBING, ServiceCategory.GAS), saved().professional?.categories)
            assertEquals(25, saved().professional?.radiusKm)
            assertEquals(3, saved().professional?.reputation?.reviewCount)
        }

    @Test
    fun `given no categories when saving then the error is categories required and nothing is persisted`() = runTest {
        val viewModel = loadedViewModel()
        viewModel.edit { it.copy(categories = emptySet()) }

        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(ValidationError.CATEGORIES_REQUIRED, viewModel.uiState.value.validationError)
        assertFalse(viewModel.uiState.value.saved)
        assertTrue(users.updateProfileCalls.isEmpty())
    }

    @Test
    fun `given a radius of 0 or 51 when saving then the error is invalid radius and nothing is persisted`() = runTest {
        val viewModel = loadedViewModel()

        for (radius in listOf("0", "51")) {
            viewModel.edit { it.copy(radiusKm = radius) }
            viewModel.onSave()
            advanceUntilIdle()

            assertEquals(ValidationError.INVALID_RADIUS, viewModel.uiState.value.validationError)
        }
        assertTrue(users.updateProfileCalls.isEmpty())
        assertEquals(12, saved().professional?.radiusKm)
    }

    @Test
    fun `given a validation error when the form changes then the error is cleared`() = runTest {
        val viewModel = loadedViewModel()
        viewModel.edit { it.copy(categories = emptySet()) }
        viewModel.onSave()
        advanceUntilIdle()

        viewModel.edit { it.copy(categories = setOf(ServiceCategory.GAS)) }

        assertNull(viewModel.uiState.value.validationError)
    }

    @Test
    fun `given the edit screen when use my location is tapped then the base is filled with the location and its address`() =
        runTest {
            val viewModel = loadedViewModel()

            viewModel.onUseLocation()
            assertTrue(viewModel.uiState.value.isLocating)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLocating)
            assertEquals(Place(GeoPoint(-34.7000, -58.3000), "Calle Nueva 456, Quilmes"), state.form.base)

            viewModel.onSave()
            advanceUntilIdle()
            assertEquals("Calle Nueva 456, Quilmes", saved().professional?.base?.address)
        }

    @Test
    fun `given the location is unavailable when use my location is tapped then a message is shown and the base is kept`() =
        runTest {
            location.point = AppResult.Error(DomainError.LocationUnavailable)
            val viewModel = loadedViewModel()

            viewModel.onUseLocation()
            advanceUntilIdle()

            assertEquals(DomainError.LocationUnavailable, viewModel.uiState.value.message)
            assertEquals(luis.professional!!.base, viewModel.uiState.value.form.base)
        }

    @Test
    fun `given saving fails with network when saving then a message is shown the values are kept and retrying saves`() =
        runTest {
            val viewModel = loadedViewModel()
            users.updateProfileError = DomainError.Network
            viewModel.edit { it.copy(radiusKm = "20") }

            viewModel.onSave()
            advanceUntilIdle()

            var state = viewModel.uiState.value
            assertEquals(DomainError.Network, state.message)
            assertFalse(state.saved)
            assertTrue(state.canSave)
            assertEquals("20", state.form.radiusKm)
            assertEquals(12, saved().professional?.radiusKm)

            viewModel.onMessageShown()
            assertNull(viewModel.uiState.value.message)

            viewModel.onSave()
            advanceUntilIdle()

            state = viewModel.uiState.value
            assertTrue(state.saved)
            assertEquals(20, saved().professional?.radiusKm)

            viewModel.onNavigated()
            assertFalse(viewModel.uiState.value.saved)
        }

    @Test
    fun `given the user cannot be loaded when the edit screen opens then the load error is shown and retry loads it`() =
        runTest {
            users.setUsers(emptyList())
            val viewModel = viewModel()

            assertEquals(DomainError.NotFound, viewModel.awaitState { !it.isLoading }.loadError)
            assertFalse(viewModel.uiState.value.canSave)

            users.setUsers(listOf(luis))
            viewModel.onRetry()

            val state = viewModel.awaitState { !it.isLoading }
            assertNull(state.loadError)
            assertEquals("Luis", state.form.name)
        }
}
