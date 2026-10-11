package com.vespelabs.fixit.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.vespelabs.fixit.feature.auth.signin.SignInRoute
import com.vespelabs.fixit.feature.auth.signup.SignUpRoute
import com.vespelabs.fixit.feature.client.complete.CompleteJobRoute
import com.vespelabs.fixit.feature.client.create.CreateRequestRoute
import com.vespelabs.fixit.feature.client.detail.ClientRequestDetailRoute
import com.vespelabs.fixit.feature.client.myrequests.MyRequestsRoute
import com.vespelabs.fixit.feature.client.profile.ClientEditProfileRoute
import com.vespelabs.fixit.feature.client.profile.ClientProfileRoute
import com.vespelabs.fixit.feature.professional.detail.ProRequestDetailRoute
import com.vespelabs.fixit.feature.professional.feed.FeedRoute
import com.vespelabs.fixit.feature.professional.myjobs.MyJobsRoute
import com.vespelabs.fixit.feature.professional.profile.ProEditProfileRoute
import com.vespelabs.fixit.feature.professional.profile.ProProfileRoute

// Navigation lives here. Features never know each other: they only expose callbacks
// (onRequestClick, onBack, ...) and this file decides what each one does.
//
// MERGE HOTSPOT: each experience has its own entry provider below. When you replace a
// placeholder screen, keep the Route name and callbacks; only touch the entry if the callbacks change.

/** Sign in / sign up. The AUTH pseudo tab gives it the same NavigationState (and guards) as the shells. */
@Composable
fun AuthNavDisplay(navigationState: NavigationState, modifier: Modifier = Modifier) {
    val entryProvider = entryProvider<NavKey> {
        entry<SignInKey> {
            SignInRoute(onSignUpClick = { navigationState.navigate(SignUpKey) })
        }
        entry<SignUpKey> { key ->
            SignUpRoute(onBack = { navigationState.goBackFrom(key) })
        }
    }
    AppNavDisplay(navigationState, entryProvider, modifier)
}

@Composable
fun ClientNavDisplay(navigationState: NavigationState, modifier: Modifier = Modifier) {
    val entryProvider = entryProvider<NavKey> {
        entry<MyRequestsKey> {
            MyRequestsRoute(
                onCreateClick = { navigationState.navigate(CreateRequestKey) },
                onRequestClick = { id -> navigationState.navigate(ClientRequestDetailKey(id)) },
            )
        }
        entry<CreateRequestKey> { key ->
            CreateRequestRoute(
                onBack = { navigationState.goBackFrom(key) },
                onCreated = { id -> navigationState.onRequestCreated(requestId = id, createKey = key) },
            )
        }
        entry<ClientRequestDetailKey> { key ->
            ClientRequestDetailRoute(
                requestId = key.requestId,
                onBack = { navigationState.goBackFrom(key) },
                onCompleteClick = { id -> navigationState.navigate(CompleteJobKey(id)) },
            )
        }
        entry<CompleteJobKey> { key ->
            CompleteJobRoute(
                requestId = key.requestId,
                onBack = { navigationState.goBackFrom(key) },
                onCompleted = { navigationState.goBackFrom(key) },
            )
        }
        entry<ClientProfileKey> {
            ClientProfileRoute(onEditClick = { navigationState.navigate(ClientEditProfileKey) })
        }
        entry<ClientEditProfileKey> { key ->
            ClientEditProfileRoute(
                onBack = { navigationState.goBackFrom(key) },
                onSaved = { navigationState.goBackFrom(key) },
            )
        }
    }
    AppNavDisplay(navigationState, entryProvider, modifier)
}

@Composable
fun ProfessionalNavDisplay(navigationState: NavigationState, modifier: Modifier = Modifier) {
    val entryProvider = entryProvider<NavKey> {
        entry<FeedKey> {
            FeedRoute(onRequestClick = { id -> navigationState.navigate(ProRequestDetailKey(id)) })
        }
        entry<ProRequestDetailKey> { key ->
            ProRequestDetailRoute(
                requestId = key.requestId,
                // Also used after the "lost the race" dialog: back to where the detail was opened (the Feed).
                onBack = { navigationState.goBackFrom(key) },
                onAccepted = { id -> navigationState.onJobAccepted(detailKey = key, requestId = id) },
            )
        }
        entry<MyJobsKey> {
            MyJobsRoute(onJobClick = { id -> navigationState.navigate(ProRequestDetailKey(id)) })
        }
        entry<ProProfileKey> {
            ProProfileRoute(onEditClick = { navigationState.navigate(ProEditProfileKey) })
        }
        entry<ProEditProfileKey> { key ->
            ProEditProfileRoute(
                onBack = { navigationState.goBackFrom(key) },
                onSaved = { navigationState.goBackFrom(key) },
            )
        }
    }
    AppNavDisplay(navigationState, entryProvider, modifier)
}

@Composable
private fun AppNavDisplay(
    navigationState: NavigationState,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        entries = navigationState.toEntries(entryProvider),
        // With only the start tab at its root there is a single entry, so NavDisplay does not
        // intercept "back" and the system closes the app.
        onBack = { navigationState.goBack() },
        modifier = modifier,
    )
}

/**
 * Decorates the back stack of EVERY tab, visible or not, so that switching tabs and coming back
 * keeps each screen's ViewModel and saveable state (design 19, same approach as the course repo).
 * Only the stacks of [NavigationState.visibleTabs] are displayed.
 */
@Composable
private fun NavigationState.toEntries(
    entryProvider: (NavKey) -> NavEntry<NavKey>,
): List<NavEntry<NavKey>> {
    val entriesByTab = backStacks.mapValues { (_, backStack) ->
        rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(), // saves rememberSaveable per screen
                rememberViewModelStoreNavEntryDecorator(), // one ViewModelStore per screen
            ),
            entryProvider = entryProvider,
        )
    }
    return visibleTabs.flatMap { tab -> entriesByTab.getValue(tab) }
}
