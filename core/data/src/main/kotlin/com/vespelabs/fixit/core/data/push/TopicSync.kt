package com.vespelabs.fixit.core.data.push

import com.vespelabs.fixit.core.data.di.ApplicationScope
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.model.SessionState
import com.vespelabs.fixit.core.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** FCM topic operations. They return false on failure or timeout and never throw, except on cancellation. */
interface TopicMessaging {
    suspend fun subscribe(topic: String): Boolean

    suspend fun unsubscribe(topic: String): Boolean
}

/** The only persisted topic state: the `user_*` topics this device may be subscribed to (design 14b.4, point 3). */
interface TopicStore {
    fun userTopics(): Set<String>

    /** Synchronous, BEFORE subscribing. */
    fun addUserTopic(topic: String)

    /** Synchronous, only AFTER a successful unsubscribe. */
    fun removeUserTopic(topic: String)
}

/** Removes every notice of the app from the system tray. */
fun interface NoticeCanceller {
    fun cancelAll()
}

const val RETRY_FIRST_MS = 30_000L
const val RETRY_MAX_MS = 600_000L

/**
 * Keeps the FCM topics of the device equal to [topicsFor] of the session, without history (design 14b.4, revised
 * 2026-10-26, D26): every reconciliation subscribes everything desired and unsubscribes every non-desired category
 * and registered user topic. One consumer (`collectLatest`, no Mutex): a new session key or token cancels the block
 * in progress, including its retry wait. Never part of signIn, signOut or updateProfile, so it cannot fail them.
 */
@Singleton
class TopicSync @Inject constructor(
    private val authRepository: AuthRepository,
    private val messaging: TopicMessaging,
    private val store: TopicStore,
    private val noticeCanceller: NoticeCanceller,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val tokenEpoch = MutableStateFlow(0)

    /** Called once from FixItApplication.onCreate. */
    fun start(): Job = scope.launch {
        // Only who is signed in and their categories matter: a name, reputation or radius change is no work.
        val keys = authRepository.session.map(::sessionKey).distinctUntilChanged()
        combine(keys, tokenEpoch, ::Pair).collectLatest { (key, _) ->
            when (key.kind) {
                Kind.LOADING -> return@collectLatest // never subscribes without knowing the user
                Kind.SIGNED_OUT -> noticeCanceller.cancelAll() // FIRST action, before any topic operation
                Kind.SIGNED_IN -> Unit
            }
            var wait = RETRY_FIRST_MS
            while (!reconcile(key.desiredTopics())) {
                delay(wait)
                wait = (wait * 2).coerceAtMost(RETRY_MAX_MS)
            }
        }
    }

    /** onNewToken: the new token has no subscriptions yet, so the next block subscribes everything again. */
    fun resubscribe() {
        tokenEpoch.update { it + 1 }
    }

    /** Applies the plan; every operation runs even if an earlier one failed. True only if all succeeded. */
    private suspend fun reconcile(desired: Set<String>): Boolean {
        val plan = topicPlan(desired, store.userTopics())
        var allSucceeded = true
        for (topic in plan.subscribe) {
            if (isUserTopic(topic)) store.addUserTopic(topic)
            if (!messaging.subscribe(topic)) allSucceeded = false
        }
        for (topic in plan.unsubscribe) {
            if (messaging.unsubscribe(topic)) {
                if (isUserTopic(topic)) store.removeUserTopic(topic)
            } else {
                allSucceeded = false
            }
        }
        return allSucceeded
    }

    private enum class Kind { LOADING, SIGNED_OUT, SIGNED_IN }

    private data class SessionKey(val kind: Kind, val uid: String? = null, val categories: Set<ServiceCategory> = emptySet()) {
        fun desiredTopics(): Set<String> = uid?.let { topicsFor(it, categories) } ?: emptySet()
    }

    private fun sessionKey(state: SessionState): SessionKey = when (state) {
        SessionState.Loading -> SessionKey(Kind.LOADING)
        SessionState.SignedOut -> SessionKey(Kind.SIGNED_OUT)
        is SessionState.SignedIn ->
            SessionKey(Kind.SIGNED_IN, state.user.id, state.user.professional?.categories.orEmpty())
    }
}
