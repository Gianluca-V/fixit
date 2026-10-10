package ar.edu.unaj.fixit.feature.client.profile

import ar.edu.unaj.fixit.core.domain.model.SessionState
import ar.edu.unaj.fixit.core.domain.model.UserRole
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.result.ValidationError
import ar.edu.unaj.fixit.core.domain.testing.MainDispatcherRule
import ar.edu.unaj.fixit.core.domain.testing.TestAuthRepository
import ar.edu.unaj.fixit.core.domain.testing.TestData
import ar.edu.unaj.fixit.core.domain.testing.TestUserRepository
import ar.edu.unaj.fixit.core.domain.usecase.UpdateProfileUseCase
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

    private fun viewModel() = ClientEditProfileViewModel(auth, users, UpdateProfileUseCase(auth, users))

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
}
