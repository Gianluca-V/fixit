package com.vespelabs.fixit.navigation

import androidx.navigation3.runtime.NavKey
import com.vespelabs.fixit.core.data.push.PushEventType
import com.vespelabs.fixit.core.domain.model.UserRole

/** Where a tapped notice leads: a tab and the detail opened on top of its root (design 14b.6, D28). */
data class DeepLinkDestination(val tab: TopLevelDestination, val key: NavKey)

/**
 * Pure function of (event type, session role, requestId), resolved when the notice is tapped (the destination never
 * travels in the Intent). null = no detail (wrong role or no session). The detail screen re-checks visibility.
 */
fun deepLinkDestination(type: PushEventType, role: UserRole?, requestId: String): DeepLinkDestination? = when {
    type == PushEventType.REQUEST_ACCEPTED && role == UserRole.CLIENT ->
        DeepLinkDestination(TopLevelDestination.REQUESTS, ClientRequestDetailKey(requestId))
    type == PushEventType.NEW_REQUEST && role == UserRole.PROFESSIONAL ->
        DeepLinkDestination(TopLevelDestination.FEED, ProRequestDetailKey(requestId))
    type == PushEventType.REQUEST_CANCELLED && role == UserRole.PROFESSIONAL ->
        DeepLinkDestination(TopLevelDestination.MY_JOBS, ProRequestDetailKey(requestId))
    else -> null
}
