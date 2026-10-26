package com.vespelabs.fixit.feature.professional.profile

import com.vespelabs.fixit.core.domain.model.Reputation
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.model.SessionState
import com.vespelabs.fixit.core.domain.model.ThemeMode
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.rules.average
import com.vespelabs.fixit.core.domain.testing.MainDispatcherRule
import com.vespelabs.fixit.core.domain.testing.TestAuthRepository
import com.vespelabs.fixit.core.domain.testing.TestData
import com.vespelabs.fixit.core.domain.testing.TestThemePreferences
import com.vespelabs.fixit.core.domain.testing.TestUserRepository
import java.math.BigDecimal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProProfileViewModelTest {
    // StandardTestDispatcher queues the coroutines, so the initial Loading state is observable.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    // 3 reviews of 5, 4 and 4 stars.
    private val electrician = TestData.professional(
        id = "p1",
        name = "Carlos Gómez",
        categories = setOf(ServiceCategory.ELECTRICITY),
        radiusKm = 15,
        reputation = Reputation(ratingSum = 13, reviewCount = 3),
    )
    private val auth = TestAuthRepository(SessionState.SignedIn(electrician))
    private val users = TestUserRepository(listOf(electrician))
    private val theme = TestThemePreferences()

    private fun viewModel() = ProProfileViewModel(auth, users, theme)

    /** Waits for the first matching state; the virtual-time timeout makes a missing state fail fast. */
    private suspend fun ProProfileViewModel.awaitState(
        predicate: (ProProfileUiState) -> Boolean,
    ): ProProfileUiState = withTimeout(5_000L) { uiState.first(predicate) }

    // [prof] Professional profile

    @Test
    fun `given an electrician with radius 15 and reviews of 5 4 and 4 when the profile tab opens then the state has the categories radius average and count`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(ProProfileUiState.Loading, viewModel.uiState.value)

            val loaded = viewModel.awaitState { it is ProProfileUiState.Success } as ProProfileUiState.Success
            val professional = loaded.user.professional!!

            assertEquals(setOf(ServiceCategory.ELECTRICITY), professional.categories)
            assertEquals(15, professional.radiusKm)
            assertEquals(BigDecimal("4.3"), professional.reputation.average())
            assertEquals(3, professional.reputation.reviewCount)
            assertEquals(electrician.email, loaded.user.email)
            assertEquals(electrician.phone, loaded.user.phone)
            assertEquals(electrician.professional!!.base, professional.base)
        }

    @Test
    fun `given the profile is shown when the work zone is saved then the profile shows the new values`() = runTest {
        val viewModel = viewModel()
        viewModel.awaitState { it is ProProfileUiState.Success }

        val edited = electrician.copy(professional = electrician.professional!!.copy(radiusKm = 25))
        users.upsert(edited)

        val updated = viewModel.awaitState { (it as? ProProfileUiState.Success)?.user?.professional?.radiusKm == 25 }
        assertEquals(edited, (updated as ProProfileUiState.Success).user)
    }

    @Test
    fun `given observing the user fails with network when the profile tab opens then it shows the network error`() =
        runTest {
            users.observeError = DomainError.Network

            val viewModel = viewModel()

            assertEquals(ProProfileUiState.Error(DomainError.Network), viewModel.awaitState { it is ProProfileUiState.Error })
        }

    @Test
    fun `given observing the user failed with network when retry is tapped then the profile is shown`() = runTest {
        users.observeError = DomainError.Network // fails only the first observe
        val viewModel = viewModel()
        viewModel.awaitState { it is ProProfileUiState.Error }

        viewModel.onRetry()

        assertEquals(ProProfileUiState.Success(electrician), viewModel.awaitState { it is ProProfileUiState.Success })
    }

    @Test
    fun `given there is no session when the profile tab opens then it shows unauthorized`() = runTest {
        auth.setSession(SessionState.SignedOut)

        val viewModel = viewModel()

        assertEquals(ProProfileUiState.Error(DomainError.Unauthorized), viewModel.awaitState { it is ProProfileUiState.Error })
    }

    // [auth] Sign out

    @Test
    fun `given a signed-in professional when sign out is tapped then the session is closed`() = runTest {
        val viewModel = viewModel()

        viewModel.onSignOutClick()

        assertEquals(1, auth.signOutCalls)
    }

    // [ui] Appearance

    @Test
    fun `given dark mode when the system theme is restored then the preference is written`() = runTest {
        val viewModel = viewModel()

        viewModel.onThemeModeChange(ThemeMode.DARK)
        viewModel.onThemeModeChange(ThemeMode.SYSTEM)

        assertEquals(listOf(ThemeMode.DARK, ThemeMode.SYSTEM), theme.writes)
        assertEquals(ThemeMode.SYSTEM, viewModel.themeMode.first())
    }
}
