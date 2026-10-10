package ar.edu.unaj.fixit.feature.client.profile

import ar.edu.unaj.fixit.core.domain.model.SessionState
import ar.edu.unaj.fixit.core.domain.model.UserRole
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.testing.MainDispatcherRule
import ar.edu.unaj.fixit.core.domain.testing.TestAuthRepository
import ar.edu.unaj.fixit.core.domain.testing.TestData
import ar.edu.unaj.fixit.core.domain.testing.TestUserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClientProfileViewModelTest {
    // StandardTestDispatcher queues the coroutines, so the initial Loading state is observable.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val client = TestData.client(id = "c1", name = "Ana López", email = "ana@fixit.test", phone = "11 5555-0001")
    private val auth = TestAuthRepository(SessionState.SignedIn(client))
    private val users = TestUserRepository(listOf(client))

    private fun viewModel() = ClientProfileViewModel(auth, users)

    /** Waits for the first matching state; the virtual-time timeout makes a missing state fail fast. */
    private suspend fun ClientProfileViewModel.awaitState(
        predicate: (ClientProfileUiState) -> Boolean,
    ): ClientProfileUiState = withTimeout(5_000L) { uiState.first(predicate) }

    // [prof] Client profile

    @Test
    fun `given a signed-in client when the profile tab opens then it is loading and then shows name email phone and role`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(ClientProfileUiState.Loading, viewModel.uiState.value)

            val loaded = viewModel.awaitState { it is ClientProfileUiState.Success } as ClientProfileUiState.Success

            assertEquals("Ana López", loaded.user.name)
            assertEquals("ana@fixit.test", loaded.user.email)
            assertEquals("11 5555-0001", loaded.user.phone)
            assertEquals(UserRole.CLIENT, loaded.user.role)
        }

    @Test
    fun `given the profile is shown when the user is updated then the profile shows the new values`() = runTest {
        val viewModel = viewModel()
        viewModel.awaitState { it is ClientProfileUiState.Success }

        users.upsert(client.copy(name = "Ana Pérez"))

        val updated = viewModel.awaitState { (it as? ClientProfileUiState.Success)?.user?.name == "Ana Pérez" }
        assertEquals(client.copy(name = "Ana Pérez"), (updated as ClientProfileUiState.Success).user)
    }

    @Test
    fun `given observing the user fails with network when the profile tab opens then it shows the network error`() =
        runTest {
            users.observeError = DomainError.Network

            val viewModel = viewModel()

            assertEquals(ClientProfileUiState.Error(DomainError.Network), viewModel.awaitState { it is ClientProfileUiState.Error })
        }

    @Test
    fun `given there is no session when the profile tab opens then it shows unauthorized`() = runTest {
        auth.setSession(SessionState.SignedOut)

        val viewModel = viewModel()

        assertEquals(ClientProfileUiState.Error(DomainError.Unauthorized), viewModel.awaitState { it is ClientProfileUiState.Error })
    }

    // [auth] Sign out

    @Test
    fun `given a signed-in client when the user taps sign out then the session becomes signed out`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSignOutClick()

        assertEquals(1, auth.signOutCalls)
        assertEquals(SessionState.SignedOut, auth.session.first())
    }
}
