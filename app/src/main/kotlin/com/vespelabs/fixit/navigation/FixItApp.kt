package com.vespelabs.fixit.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vespelabs.fixit.core.domain.model.SessionState
import com.vespelabs.fixit.core.domain.repository.AuthRepository
import com.vespelabs.fixit.core.ui.components.LoadingContent
import com.vespelabs.fixit.ui.AppShell
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Exposes the session to the gate. Starts as [SessionState.Loading] until the repository emits. */
@HiltViewModel
class SessionViewModel @Inject constructor(authRepository: AuthRepository) : ViewModel() {
    val session: StateFlow<SessionState> = authRepository.session.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = SessionState.Loading,
    )
}

/**
 * The session gate: shows exactly one experience (loading, authentication, client or professional).
 *
 * Everything below `key(userId)` leaves the composition when the user changes or signs out, so the
 * NavigationState and every entry's ViewModelStore are discarded: another user never sees the
 * previous one's screens or data (design D20; runtime check in task 1.10.12).
 */
@Composable
fun FixItApp(
    modifier: Modifier = Modifier,
    sessionViewModel: SessionViewModel = hiltViewModel(),
) {
    val session by sessionViewModel.session.collectAsStateWithLifecycle()
    val userId = (session as? SessionState.SignedIn)?.user?.id
    key(userId) {
        when (experienceFor(session)) {
            Experience.LOADING -> LoadingContent(modifier)
            Experience.AUTH -> {
                val navigationState = rememberNavigationState(authTabs)
                AuthNavDisplay(
                    navigationState = navigationState,
                    modifier = modifier.windowInsetsPadding(WindowInsets.navigationBars),
                )
            }
            Experience.CLIENT -> {
                val navigationState = rememberNavigationState(clientTabs)
                AppShell(clientTabs, navigationState, modifier) { contentModifier ->
                    ClientNavDisplay(navigationState, contentModifier)
                }
            }
            Experience.PROFESSIONAL -> {
                val navigationState = rememberNavigationState(professionalTabs)
                AppShell(professionalTabs, navigationState, modifier) { contentModifier ->
                    ProfessionalNavDisplay(navigationState, contentModifier)
                }
            }
        }
    }
}
