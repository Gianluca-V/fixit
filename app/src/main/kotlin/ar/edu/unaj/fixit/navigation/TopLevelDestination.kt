package ar.edu.unaj.fixit.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import ar.edu.unaj.fixit.R

/**
 * The tabs of the bottom bar. Each has its own back stack that starts at [startKey].
 * [AUTH] is a pseudo tab: it gives the sign-in/sign-up stack the same NavigationState
 * (same double-tap guards) without showing a bar.
 */
enum class TopLevelDestination(
    val startKey: NavKey,
    val icon: ImageVector,
    @StringRes val label: Int,
) {
    REQUESTS(MyRequestsKey, Icons.Filled.List, R.string.tab_requests),
    CLIENT_PROFILE(ClientProfileKey, Icons.Filled.Person, R.string.tab_profile),
    FEED(FeedKey, Icons.Filled.Home, R.string.tab_feed),
    MY_JOBS(MyJobsKey, Icons.Filled.Build, R.string.tab_my_jobs),
    PRO_PROFILE(ProProfileKey, Icons.Filled.Person, R.string.tab_profile),
    AUTH(SignInKey, Icons.Filled.Person, R.string.tab_auth),
}

/** The first tab of each list is the start tab. */
val clientTabs: List<TopLevelDestination> = listOf(TopLevelDestination.REQUESTS, TopLevelDestination.CLIENT_PROFILE)
val professionalTabs: List<TopLevelDestination> =
    listOf(TopLevelDestination.FEED, TopLevelDestination.MY_JOBS, TopLevelDestination.PRO_PROFILE)
val authTabs: List<TopLevelDestination> = listOf(TopLevelDestination.AUTH)
