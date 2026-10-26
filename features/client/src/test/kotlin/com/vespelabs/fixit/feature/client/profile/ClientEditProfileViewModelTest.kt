package com.vespelabs.fixit.feature.client.profile

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.model.SessionState
import com.vespelabs.fixit.core.domain.model.UserRole
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
class ClientEditProfileViewModelTest {
    // StandardTestDispatcher queues the coroutines, so "in progress" states are observable.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val client = TestData.client(id = "c1", name = "Ana", email = "ana@fixit.test", phone = "1155551234")
    private val auth = TestAuthRepository(SessionState.SignedIn(client))
    private val users = TestUserRepository(listOf(client))

    private val location = TestLocation()

    private fun viewModel() =
        ClientEditProfileViewModel(auth, users, UpdateProfileUseCase(auth, users), GetCurrentPlaceUseCase(location, location))

    /** Waits for the first matching state; the virtual-time timeout makes a missing state fail fast. */
    private suspend fun ClientEditProfileViewModel.awaitState(
        predicate: (ClientEditProfileUiState) -> Boolean,
    ): ClientEditProfileUiState = withTimeout(5_000L) { uiState.first(predicate) }

    private suspend fun loadedViewModel(): ClientEditProfileViewModel =
        viewModel().also { it.awaitState { state -> !state.isLoading } }

    private fun ClientEditProfileViewModel.edit(name: String? = null, phone: String? = null) {
        val form = uiState.value.form
        onFormChange(form.copy(name = name ?: form.name, phone = phone ?: form.phone))
    }

    // [prof] Edit profile screen

    @Test
    fun `given a client named Ana when the edit screen opens then the form is preloaded with her values and shows email and role`() =
        runTest {
            val loaded = viewModel().awaitState { !it.isLoading }

            assertEquals("Ana", loaded.form.name)
            assertEquals("1155551234", loaded.form.phone)
            assertEquals("ana@fixit.test", loaded.email)
            assertEquals(UserRole.CLIENT, loaded.role)
            assertNull(loaded.loadError)
            assertTrue(loaded.canSave)
        }

    @Test
    fun `given a client named Ana when she saves Ana Perez and a new phone then the trimmed values are saved and email and role do not change`() =
        runTest {
            val viewModel = loadedViewModel()
            viewModel.edit(name = "  Ana Pérez ", phone = "11 5555-9999")

            viewModel.onSave()
            assertTrue(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.canSave)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.saved)
            assertFalse(state.isSaving)
            val saved = users.user("c1")!!
            assertEquals("Ana Pérez", saved.name)
            assertEquals("11 5555-9999", saved.phone)
            assertEquals("ana@fixit.test", saved.email)
            assertEquals(UserRole.CLIENT, saved.role)

            viewModel.onNavigated()
            assertFalse(viewModel.uiState.value.saved)
        }

    @Test
    fun `given a name of one character when the user saves then the error is invalid name and nothing is persisted`() =
        runTest {
            val viewModel = loadedViewModel()
            viewModel.edit(name = "A")

            viewModel.onSave()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(ValidationError.INVALID_NAME, state.validationError)
            assertNull(state.message)
            assertFalse(state.saved)
            assertEquals("A", state.form.name)
            assertTrue(users.updateProfileCalls.isEmpty())
            assertEquals(client, users.user("c1"))
        }

    @Test
    fun `given the phone 12ab when the user saves then the error is invalid phone and nothing is persisted`() = runTest {
        val viewModel = loadedViewModel()
        viewModel.edit(phone = "12ab")

        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ValidationError.INVALID_PHONE, state.validationError)
        assertFalse(state.saved)
        assertTrue(users.updateProfileCalls.isEmpty())
        assertEquals(client, users.user("c1"))

        // Typing again clears the error next to the field.
        viewModel.edit(phone = "1155559999")
        assertNull(viewModel.uiState.value.validationError)
    }

    @Test
    fun `given saving fails with network when the user saves then a message is shown the values are kept and retrying saves`() =
        runTest {
            val viewModel = loadedViewModel()
            users.updateProfileError = DomainError.Network
            viewModel.edit(name = "Ana Pérez")

            viewModel.onSave()
            advanceUntilIdle()

            var state = viewModel.uiState.value
            assertEquals(DomainError.Network, state.message)
            assertNull(state.validationError)
            assertFalse(state.saved)
            assertFalse(state.isSaving)
            assertTrue(state.canSave)
            assertEquals("Ana Pérez", state.form.name)
            assertEquals("Ana", users.user("c1")?.name)

            viewModel.onMessageShown()
            assertNull(viewModel.uiState.value.message)

            viewModel.onSave()
            advanceUntilIdle()

            state = viewModel.uiState.value
            assertTrue(state.saved)
            assertEquals("Ana Pérez", users.user("c1")?.name)
        }

    @Test
    fun `given the user cannot be loaded when the edit screen opens then the load error is shown and retry loads it`() =
        runTest {
            users.setUsers(emptyList())
            val viewModel = viewModel()

            val failed = viewModel.awaitState { it.loadError != null }
            assertEquals(DomainError.NotFound, failed.loadError)
            assertFalse(failed.canSave)

            users.setUsers(listOf(client))
            viewModel.onRetry()

            val loaded = viewModel.awaitState { !it.isLoading && it.loadError == null }
            assertEquals("Ana", loaded.form.name)
        }

    @Test
    fun `given there is no session when the edit screen opens then the load error is unauthorized`() = runTest {
        auth.setSession(SessionState.SignedOut)

        assertEquals(DomainError.Unauthorized, viewModel().awaitState { it.loadError != null }.loadError)
    }

    // [prof] Client area

    private val quilmes = Place(GeoPoint(-34.7206, -58.2546), "Calle 123, Quilmes")

    @Test
    fun `given a client with area Calle 123 Quilmes when the edit screen opens then the form holds that area`() = runTest {
        users.upsert(client.copy(area = quilmes))

        assertEquals(quilmes, viewModel().awaitState { !it.isLoading }.form.area)
    }

    @Test
    fun `given a client without area when she picks Q on the map and saves then her profile has the area Q`() = runTest {
        val viewModel = loadedViewModel()

        viewModel.onPlacePicked(quilmes)
        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.saved)
        assertEquals(quilmes, users.user("c1")!!.area)
    }

    @Test
    fun `given a client without area when she uses her location and saves then her profile has the current place`() = runTest {
        val viewModel = loadedViewModel()

        viewModel.onUseLocation()
        assertTrue(viewModel.uiState.value.isLocating)
        assertFalse(viewModel.uiState.value.canSave)
        advanceUntilIdle()
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(TestData.florencioVarela, users.user("c1")!!.area)
    }

    @Test
    fun `given a client with area when she clears it and saves then her profile has no area`() = runTest {
        users.upsert(client.copy(area = quilmes))
        val viewModel = loadedViewModel()

        viewModel.onFormChange(viewModel.uiState.value.form.copy(area = null)) // "Quitar zona"
        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.saved)
        assertNull(users.user("c1")!!.area)
    }

    @Test
    fun `given an area whose address was erased when saving then the error is location required and nothing is persisted`() =
        runTest {
            val viewModel = loadedViewModel()
            viewModel.onPlacePicked(quilmes)
            viewModel.onFormChange(viewModel.uiState.value.form.copy(area = quilmes.copy(address = "")))

            viewModel.onSave()
            advanceUntilIdle()

            assertEquals(ValidationError.LOCATION_REQUIRED, viewModel.uiState.value.validationError)
            assertTrue(users.updateProfileCalls.isEmpty())
        }

    @Test
    fun `given the location is unavailable when use my location is tapped then a message is shown and the area is kept`() =
        runTest {
            users.upsert(client.copy(area = quilmes))
            location.point = AppResult.Error(DomainError.LocationUnavailable)
            val viewModel = loadedViewModel()

            viewModel.onUseLocation()
            advanceUntilIdle()

            assertEquals(DomainError.LocationUnavailable, viewModel.uiState.value.message)
            assertEquals(quilmes, viewModel.uiState.value.form.area)
            assertFalse(viewModel.uiState.value.isLocating)
        }
}
