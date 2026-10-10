package ar.edu.unaj.fixit.core.domain.model

sealed interface SessionState {
    data object Loading : SessionState

    data object SignedOut : SessionState

    data class SignedIn(val user: User) : SessionState
}
