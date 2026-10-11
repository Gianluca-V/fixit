package com.vespelabs.fixit.navigation

import androidx.navigation3.runtime.NavKey

// Multi-step flows from the navigation spec. AppNavDisplay wires these to feature callbacks,
// and NavigationFlowsTest drives the same functions, so the tested behavior is the real one.

/** After publishing: swap the form for the new request's detail, so "back" goes to My requests. */
fun NavigationState.onRequestCreated(requestId: String, createKey: NavKey = CreateRequestKey) {
    goBackFrom(createKey)
    navigate(ClientRequestDetailKey(requestId))
}

/**
 * After accepting a job: the Feed stays at its root, My jobs becomes the active tab restarted
 * from its root, and it shows the accepted job. "Back" from there returns to My jobs.
 */
fun NavigationState.onJobAccepted(detailKey: NavKey, requestId: String) {
    goBackFrom(detailKey)
    selectTab(TopLevelDestination.MY_JOBS)
    resetToRoot(TopLevelDestination.MY_JOBS)
    navigate(ProRequestDetailKey(requestId))
}
