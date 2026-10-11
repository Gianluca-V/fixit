package com.vespelabs.fixit.feature.auth.signup

import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.model.SessionState
import com.vespelabs.fixit.core.domain.model.UserRole
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.result.ValidationError
import com.vespelabs.fixit.core.domain.testing.MainDispatcherRule
import com.vespelabs.fixit.core.domain.testing.TestAuthRepository
import com.vespelabs.fixit.core.domain.testing.TestData
import com.vespelabs.fixit.core.domain.testing.TestLocation
import com.vespelabs.fixit.core.domain.usecase.GetCurrentPlaceUseCase
import com.vespelabs.fixit.core.domain.usecase.SignUpUseCase
import com.vespelabs.fixit.core.ui.components.ProfileFormState
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
class SignUpViewModelTest {
    // StandardTestDispatcher queues the sign-up coroutine, so the "in progress" state is observable.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val auth = TestAuthRepository()
    private val location = TestLocation()
    private val viewModel = SignUpViewModel(SignUpUseCase(auth), GetCurrentPlaceUseCase(location, location))

    private fun fillClientForm(phone: String = "11 5555-0001") {
        viewModel.onEmailChange("ana@fixit.test")
        viewModel.onPasswordChange("test1234")
        viewModel.onRoleSelect(UserRole.CLIENT)
        viewModel.onProfileChange(ProfileFormState(name = "Ana López", phone = phone))
    }

    private fun fillProfessionalForm() {
        viewModel.onEmailChange("diana@fixit.test")
        viewModel.onPasswordChange("test1234")
        viewModel.onRoleSelect(UserRole.PROFESSIONAL)
        viewModel.onProfileChange(
            ProfileFormState(
                name = "Diana Ruiz",
                phone = "11 5555-0102",
                categories = setOf(ServiceCategory.PLUMBING),
                base = TestData.florencioVarela,
                radiusKm = "8",
            ),
        )
    }

    @Test
    fun `given a valid form when submitting then the submit is in progress until the result arrives`() = runTest {
        fillClientForm()

        viewModel.onSubmit()
        assertTrue(viewModel.uiState.value.isSubmitting)

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `given a submit in progress when submitting again then only one account is created`() = runTest {
        fillClientForm()

        viewModel.onSubmit()
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(1, auth.signUpCalls.size)
    }

    @Test
    fun `given a valid client form when submitting then the session starts as CLIENT without a message`() = runTest {
        fillClientForm()

        viewModel.onSubmit()
        advanceUntilIdle()

        val session = auth.session.first() as SessionState.SignedIn
        assertEquals(UserRole.CLIENT, session.user.role)
        assertNull(viewModel.uiState.value.message)
        assertNull(viewModel.uiState.value.validationError)
    }

    @Test
    fun `given an invalid phone when submitting then the error is next to the phone field and the input is kept`() = runTest {
        fillClientForm(phone = "12ab")

        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ValidationError.INVALID_PHONE, state.validationError)
        assertNull(state.message)
        assertEquals("ana@fixit.test", state.email)
        assertEquals("test1234", state.password)
        assertEquals(UserRole.CLIENT, state.role)
        assertEquals(ProfileFormState(name = "Ana López", phone = "12ab"), state.profile)
        assertFalse(state.isSubmitting)
        assertTrue(auth.signUpCalls.isEmpty())
    }

    @Test
    fun `given no role when submitting then the error is next to the role and no account is created`() = runTest {
        viewModel.onEmailChange("ana@fixit.test")
        viewModel.onPasswordChange("test1234")
        viewModel.onProfileChange(ProfileFormState(name = "Ana López", phone = "11 5555-0001"))

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(ValidationError.ROLE_REQUIRED, viewModel.uiState.value.validationError)
        assertTrue(auth.signUpCalls.isEmpty())
    }

    @Test
    fun `given a new form when it opens then no role is preselected`() {
        assertNull(viewModel.uiState.value.role)
    }

    @Test
    fun `given an email already registered when submitting then the message is EmailAlreadyInUse and the session does not change`() = runTest {
        auth.signUpError = DomainError.EmailAlreadyInUse
        fillClientForm()

        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DomainError.EmailAlreadyInUse, state.message)
        assertNull(state.validationError)
        assertEquals("ana@fixit.test", state.email)
        assertEquals(SessionState.SignedOut, auth.session.first())
    }

    @Test
    fun `given an EmailAlreadyInUse message when it is shown then it is not shown again`() = runTest {
        auth.signUpError = DomainError.EmailAlreadyInUse
        fillClientForm()
        viewModel.onSubmit()
        advanceUntilIdle()

        viewModel.onMessageShown()

        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `given each role when it is selected then professional fields are shown only for PROFESSIONAL`() {
        assertFalse(viewModel.uiState.value.showProfessionalFields)

        viewModel.onRoleSelect(UserRole.PROFESSIONAL)
        assertTrue(viewModel.uiState.value.showProfessionalFields)

        viewModel.onRoleSelect(UserRole.CLIENT)
        assertFalse(viewModel.uiState.value.showProfessionalFields)
    }

    @Test
    fun `given professional fields filled when signing up as CLIENT then they are not sent`() = runTest {
        fillProfessionalForm()
        viewModel.onRoleSelect(UserRole.CLIENT)

        viewModel.onSubmit()
        advanceUntilIdle()

        val sent = auth.signUpCalls.single()
        assertEquals(UserRole.CLIENT, sent.role)
        assertTrue(sent.profile.categories.isEmpty())
        assertNull(sent.profile.base)
        assertNull(sent.profile.radiusKm)
    }

    @Test
    fun `given a valid professional form when submitting then the account is created with its professional fields`() = runTest {
        fillProfessionalForm()

        viewModel.onSubmit()
        advanceUntilIdle()

        val sent = auth.signUpCalls.single()
        assertEquals(UserRole.PROFESSIONAL, sent.role)
        assertEquals(setOf(ServiceCategory.PLUMBING), sent.profile.categories)
        assertEquals(TestData.florencioVarela, sent.profile.base)
        assertEquals(8, sent.profile.radiusKm)
    }

    @Test
    fun `given a professional without categories when submitting then the error is next to the categories`() = runTest {
        fillProfessionalForm()
        viewModel.onProfileChange(viewModel.uiState.value.profile.copy(categories = emptySet()))

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(ValidationError.CATEGORIES_REQUIRED, viewModel.uiState.value.validationError)
        assertTrue(auth.signUpCalls.isEmpty())
    }

    @Test
    fun `given a location available when using my location then it becomes the base point`() = runTest {
        viewModel.onRoleSelect(UserRole.PROFESSIONAL)

        viewModel.onUseLocation()
        advanceUntilIdle()

        assertEquals(TestData.florencioVarela, viewModel.uiState.value.profile.base)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `given the location fails when using my location then the message is LocationUnavailable and the base is unchanged`() = runTest {
        location.point = AppResult.Error(DomainError.LocationUnavailable)
        viewModel.onRoleSelect(UserRole.PROFESSIONAL)

        viewModel.onUseLocation()
        advanceUntilIdle()

        assertEquals(DomainError.LocationUnavailable, viewModel.uiState.value.message)
        assertNull(viewModel.uiState.value.profile.base)
    }
}
