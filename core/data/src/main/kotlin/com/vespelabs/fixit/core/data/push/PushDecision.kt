package com.vespelabs.fixit.core.data.push

import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.rules.Matching

/** Wire values shared with the Worker (design 14b.2): the enum names travel as the `type` field. */
enum class PushEventType { NEW_REQUEST, REQUEST_ACCEPTED, REQUEST_CANCELLED }

data class PushEvent(val type: PushEventType, val requestId: String)

/** Keys of the FCM data message and of the notification Intent extras (design 14b.2, 14b.6). */
object PushKeys {
    const val TYPE = "type"
    const val REQUEST_ID = "requestId"
    const val CATEGORY = "category" // NEW_REQUEST only
}

/** Parses the wire pair; an unknown type or a missing/blank requestId is not an event. */
fun parsePushEvent(type: String?, requestId: String?): PushEvent? {
    val eventType = PushEventType.entries.firstOrNull { it.name == type } ?: return null
    if (requestId.isNullOrBlank()) return null
    return PushEvent(eventType, requestId)
}

/**
 * Cheap pre-filter on the payload `category` (NEW_REQUEST only): a viewer who does not offer it needs no Firestore
 * read. It never decides to show a notice. No category in the payload (the other types): do not filter.
 */
fun mayMatchCategory(category: String?, viewer: User?): Boolean =
    category == null || viewer?.professional?.categories?.any { it.name == category } == true

/** Final decision with fresh data (design 14b.5). Reuses the domain Matching: no domain change. */
fun shouldNotify(type: PushEventType, request: RepairRequest, viewer: User?): Boolean = when {
    viewer == null -> false
    // OPEN + offered category + inclusive isWithin on the unrounded distance.
    type == PushEventType.NEW_REQUEST -> viewer.professional?.let { Matching.distanceIfMatches(it, request) } != null
    type == PushEventType.REQUEST_ACCEPTED -> request.clientId == viewer.id && request.status == RequestStatus.ACCEPTED
    else -> request.professionalId == viewer.id && request.status == RequestStatus.CANCELLED // REQUEST_CANCELLED
}
