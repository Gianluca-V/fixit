package ar.edu.unaj.fixit.navigation

import ar.edu.unaj.fixit.core.domain.model.SessionState
import ar.edu.unaj.fixit.core.domain.testing.MainDispatcherRule
import ar.edu.unaj.fixit.core.domain.testing.TestAuthRepository
import ar.edu.unaj.fixit.core.domain.testing.TestData
import ar.edu.unaj.fixit.core.domain.model.SignUpForm
import ar.edu.unaj.fixit.core.domain.model.User
import ar.edu.unaj.fixit.core.domain.repository.AuthRepository
import ar.edu.unaj.fixit.core.domain.result.AppResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `given the repository has not emitted yet when observing then the session is Loading`() = runTest {
        val viewModel = SessionViewModel(NeverEmittingAuth())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.session.collect {} }

        assertEquals(SessionState.Loading, viewModel.session.value)
    }

    @Test
    fun `given a previous professional session when observing then the session is SignedIn`() = runTest {
        val professional = TestData.professional()
        val viewModel = SessionViewModel(TestAuthRepository(SessionState.SignedIn(professional)))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.session.collect {} }

        assertEquals(SessionState.SignedIn(professional), viewModel.session.value)
        assertEquals(Experience.PROFESSIONAL, experienceFor(viewModel.session.value))
    }

    @Test
    fun `given no session when a client signs in then the session becomes SignedIn and on sign out SignedOut`() = runTest {
        val auth = TestAuthRepository(SessionState.SignedOut)
        val viewModel = SessionViewModel(auth)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.session.collect {} }
        assertEquals(Experience.AUTH, experienceFor(viewModel.session.value))

        val client = TestData.client()
        auth.signedInAs(client)
        assertEquals(SessionState.SignedIn(client), viewModel.session.value)
        assertEquals(Experience.CLIENT, experienceFor(viewModel.session.value))

        auth.signOut()
        assertEquals(Experience.AUTH, experienceFor(viewModel.session.value))
    }
}

/** An auth repository whose session flow never emits (the app is still determining the session). */
private class NeverEmittingAuth : AuthRepository {
    override val session: Flow<SessionState> = flow { awaitCancellation() }

    override fun currentUserId(): String? = null

    override suspend fun signIn(email: String, password: String): AppResult<User> = error("not used")

    override suspend fun signUp(form: SignUpForm): AppResult<User> = error("not used")

    override fun signOut() = Unit
}
