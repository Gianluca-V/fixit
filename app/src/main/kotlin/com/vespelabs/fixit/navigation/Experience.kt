package com.vespelabs.fixit.navigation

import com.vespelabs.fixit.core.domain.model.SessionState
import com.vespelabs.fixit.core.domain.model.UserRole

/** Which part of the app is shown. Exactly one at a time (navigation spec: session and role gate). */
enum class Experience { AUTH, CLIENT, PROFESSIONAL, LOADING }

fun experienceFor(state: SessionState): Experience = when (state) {
    SessionState.Loading -> Experience.LOADING
    SessionState.SignedOut -> Experience.AUTH
    is SessionState.SignedIn -> when (state.user.role) {
        UserRole.CLIENT -> Experience.CLIENT
        UserRole.PROFESSIONAL -> Experience.PROFESSIONAL
    }
}
