package com.vespelabs.fixit.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vespelabs.fixit.core.data.push.PushMessageHandler
import com.vespelabs.fixit.core.data.push.TopicSync
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Bound of the on-device decision (two fresh reads); FCM grants about 20 s to onMessageReceived. */
private const val HANDLE_TIMEOUT_MS = 10_000L

/**
 * Receives the data-only FCM messages (design 14b.5). FCM calls onMessageReceived off the main thread, so runBlocking
 * is acceptable here; WorkManager is not used (one dependency less, design 19). Checked by hand in 4.13.2 and 4.13.6.
 */
@AndroidEntryPoint
class FixItMessagingService : FirebaseMessagingService() {
    @Inject lateinit var handler: PushMessageHandler

    @Inject lateinit var topicSync: TopicSync

    override fun onMessageReceived(message: RemoteMessage) {
        val event = runBlocking { withTimeoutOrNull(HANDLE_TIMEOUT_MS) { handler.handle(message.data) } } ?: return
        Notifications.show(applicationContext, event)
    }

    /**
     * The new token has no subscriptions yet: TopicSync reconciles everything again. firebase-messaging 26 delivers a
     * new token through onRegistered and still through the deprecated onNewToken; both are handled because a second
     * resubscribe() only restarts an idempotent reconciliation.
     */
    override fun onRegistered(token: String) {
        topicSync.resubscribe()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        topicSync.resubscribe()
    }
}
