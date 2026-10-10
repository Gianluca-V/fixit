package ar.edu.unaj.fixit.feature.auth.signin

import ar.edu.unaj.fixit.core.domain.model.SessionState
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.result.ValidationError
import ar.edu.unaj.fixit.core.domain.testing.MainDispatcherRule
import ar.edu.unaj.fixit.core.domain.testing.TestAuthRepository
import ar.edu.unaj.fixit.core.domain.testing.TestData
import ar.edu.unaj.fixit.core.domain.usecase.SignInUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class SignInViewModelTest {
    // StandardTestDispatcher queues the sign-in coroutine, so the "in progress" state is observable.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val auth = TestAuthRepository()
    private val viewModel = SignInViewModel(SignInUseCase(auth))

    private fun fillForm(email: String = "ana@fixit.test", password: String = "test1234") {
        viewModel.onEmailChange(email)
        viewModel.onPasswordChange(password)
    }

    @Test
    fun `given a valid form when submitting then the submit is in progress until the result arrives`() = runTest {
        auth.signInUser = TestData.client()
        fillForm()

        viewModel.onSubmit()
        assertTrue(viewModel.uiState.value.isSubmitting)

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `given a submit in progress when submitting again then the account is queried only once`() = runTest {
        auth.signInUser = TestData.client()
        fillForm()

        viewModel.onSubmit()
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(1, auth.signInCalls.size)
    }

    @Test
    fun `given the right credentials when submitting then the session starts without a message`() = runTest {
        val ana = TestData.client()
        auth.signInUser = ana
        fillForm()

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(SessionState.SignedIn(ana), auth.session.first())
        assertNull(viewModel.uiState.value.message)
        assertNull(viewModel.uiState.value.validationError)
    }

    @Test
    fun `given an empty email when submitting then the error is next to the email field and the input is kept`() = runTest {
        fillForm(email = "", password = "secret")

        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ValidationError.INVALID_EMAIL, state.validationError)
        assertNull(state.message)
        assertEquals("", state.email)
        assertEquals("secret", state.password)
        assertFalse(state.isSubmitting)
        assertTrue(auth.signInCalls.isEmpty())
    }

    @Test
    fun `given an empty password when submitting then the error is next to the password field`() = runTest {
        fillForm(password = "")

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(ValidationError.EMPTY_PASSWORD, viewModel.uiState.value.validationError)
        assertEquals("ana@fixit.test", viewModel.uiState.value.email)
        assertTrue(auth.signInCalls.isEmpty())
    }

    @Test
    fun `given a validation error when submitting a corrected form then the field error is cleared`() = runTest {
        auth.signInUser = TestData.client()
        fillForm(password = "")
        viewModel.onSubmit()
        advanceUntilIdle()

        viewModel.onPasswordChange("test1234")
        viewModel.onSubmit()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.validationError)
    }

    @Test
    fun `given wrong credentials when submitting then the message is InvalidCredentials and not a field error`() = runTest {
        auth.signInUser = null
        fillForm(password = "wrong-password")

        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DomainError.InvalidCredentials, state.message)
        assertNull(state.validationError)
        assertEquals("ana@fixit.test", state.email)
        assertEquals(SessionState.SignedOut, auth.session.first())
    }

    @Test
    fun `given an InvalidCredentials message when it is shown then it is not shown again`() = runTest {
        auth.signInUser = null
        fillForm(password = "wrong-password")
        viewModel.onSubmit()
        advanceUntilIdle()

        viewModel.onMessageShown()

        assertNull(viewModel.uiState.value.message)
    }
}
