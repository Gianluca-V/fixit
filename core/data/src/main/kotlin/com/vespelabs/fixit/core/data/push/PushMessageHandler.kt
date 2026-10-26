package com.vespelabs.fixit.core.data.push

import com.vespelabs.fixit.core.domain.model.SessionState
import com.vespelabs.fixit.core.domain.repository.AuthRepository
import com.vespelabs.fixit.core.domain.repository.RepairRequestRepository
import com.vespelabs.fixit.core.domain.repository.UserRepository
import com.vespelabs.fixit.core.domain.result.AppResult
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Decides whether an incoming FCM data message becomes a visible notice (design 14b.5, D25). Uses only domain
 * interfaces; the professional's location never leaves the device.
 */
class PushMessageHandler @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val requestRepository: RepairRequestRepository,
) {
    /** The event to show, or null (unknown payload, no session, pre-filtered, failed read or no match). */
    suspend fun handle(data: Map<String, String>): PushEvent? {
        val event = parsePushEvent(data[PushKeys.TYPE], data[PushKeys.REQUEST_ID]) ?: return null
        val uid = authRepository.currentUserId() ?: return null
        if (event.type == PushEventType.NEW_REQUEST) {
            // The session profile is kept fresh by the profile listener: this pre-filter costs no reads.
            val viewer = (authRepository.session.first { it !is SessionState.Loading } as? SessionState.SignedIn)?.user
            if (!mayMatchCategory(data[PushKeys.CATEGORY], viewer)) return null
        }
        // Fresh reads (D19): a failed read never shows a notice and never throws.
        val user = (userRepository.get(uid) as? AppResult.Success)?.data ?: return null
        val request = (requestRepository.get(event.requestId) as? AppResult.Success)?.data ?: return null
        return event.takeIf { shouldNotify(it.type, request, user) }
    }
}
