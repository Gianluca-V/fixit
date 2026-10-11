package com.vespelabs.fixit.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/**
 * All navigation state of one experience: the active tab and one back stack per tab.
 * Adapted from the course repository (mauromarod/unaj-am). With Navigation 3 there is no
 * NavController: navigating is mutating a list.
 *
 * The stacks are plain [MutableList]s of keys (a `NavBackStack` is one), so the logic runs in JVM tests.
 * Differences from the course version, on purpose:
 *  - [navigate] ignores a key that is already on top (double tap guard).
 *  - [goBackFrom] only removes [key] when it is on top of a stack with more than one entry. It never
 *    pops a root nor switches tab (the course version delegates to [goBack]); screens call it for
 *    their own non-root key, so a stale tap can never change the active tab.
 *  - [resetToRoot] exists to trim a stack before accepting a job and before sign out.
 */
class NavigationState(
    private val startTab: TopLevelDestination,
    currentTabState: MutableState<TopLevelDestination>,
    val backStacks: Map<TopLevelDestination, MutableList<NavKey>>,
) {
    var currentTab: TopLevelDestination by currentTabState
        private set

    private val currentStack: MutableList<NavKey>
        get() = backStacks.getValue(currentTab)

    /** The screen on top of the active tab. */
    val currentKey: NavKey
        get() = currentStack.last()

    /** True on the root screen of the active tab. */
    val isOnTabRoot: Boolean
        get() = currentStack.size == 1

    /** Stacks to display: the start tab stays underneath, so "back" returns to it before closing the app. */
    val visibleTabs: List<TopLevelDestination>
        get() = listOf(startTab, currentTab).distinct()

    fun navigate(key: NavKey) {
        if (currentStack.lastOrNull() != key) currentStack.add(key)
    }

    /** Returns false when there is nothing left to go back to, so the system should close the app. */
    fun goBack(): Boolean = when {
        currentStack.size > 1 -> {
            currentStack.removeAt(currentStack.lastIndex)
            true
        }
        currentTab != startTab -> {
            currentTab = startTab
            true
        }
        else -> false
    }

    /** "Back" requested by a specific screen: acts only while that screen is still on top. */
    fun goBackFrom(key: NavKey) {
        if (currentStack.size > 1 && currentStack.last() == key) currentStack.removeAt(currentStack.lastIndex)
    }

    fun selectTab(tab: TopLevelDestination) {
        if (tab == currentTab) resetToRoot(tab) else currentTab = tab
    }

    fun resetToRoot(tab: TopLevelDestination) {
        val stack = backStacks.getValue(tab)
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}

/** The first tab of [tabs] is the start tab. State survives rotation and process death. */
@Composable
fun rememberNavigationState(tabs: List<TopLevelDestination>): NavigationState {
    val startTab = tabs.first()
    val currentTab = rememberSaveable { mutableStateOf(startTab) }
    val backStacks = tabs.associateWith { tab -> rememberNavBackStack(tab.startKey) }
    return remember(backStacks) { NavigationState(startTab, currentTab, backStacks) }
}
