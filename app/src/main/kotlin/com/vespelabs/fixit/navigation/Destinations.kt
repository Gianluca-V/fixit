package com.vespelabs.fixit.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Every screen is a typed key; a back stack is a list of keys. Keys carry only ids
// (never objects) and are @Serializable so the stacks survive rotation and process death.
// Keys live in :app because features never navigate: they receive callbacks from AppNavDisplay.

// Authentication
@Serializable data object SignInKey : NavKey
@Serializable data object SignUpKey : NavKey

// Client
@Serializable data object MyRequestsKey : NavKey
@Serializable data object CreateRequestKey : NavKey
@Serializable data class ClientRequestDetailKey(val requestId: String) : NavKey
@Serializable data class CompleteJobKey(val requestId: String) : NavKey
@Serializable data object ClientProfileKey : NavKey
@Serializable data object ClientEditProfileKey : NavKey

// Professional
@Serializable data object FeedKey : NavKey
@Serializable data class ProRequestDetailKey(val requestId: String) : NavKey
@Serializable data object MyJobsKey : NavKey
@Serializable data object ProProfileKey : NavKey
@Serializable data object ProEditProfileKey : NavKey
