package com.vespelabs.fixit.core.data.push

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Bound of every topic operation: a timeout returns false and never aborts the rest of the reconciliation. */
const val TOPIC_OPERATION_TIMEOUT_MS = 15_000L

/** FCM topic operations (design 14b.4, point 7). No JVM test: checked by hand in 4.13.3 and 4.13.6. */
class FirebaseTopicMessaging @Inject constructor(
    private val messaging: FirebaseMessaging,
) : TopicMessaging {
    override suspend fun subscribe(topic: String): Boolean = run("subscribe", topic) { messaging.subscribeToTopic(topic) }

    override suspend fun unsubscribe(topic: String): Boolean =
        run("unsubscribe", topic) { messaging.unsubscribeFromTopic(topic) }

    private suspend fun run(operation: String, topic: String, task: () -> Task<Void>): Boolean = try {
        // Task<Void>.await() yields null on success, so success is the explicit `true`; null means timed out.
        val done = withTimeoutOrNull(TOPIC_OPERATION_TIMEOUT_MS) {
            task().await()
            true
        } ?: false
        if (!done) Log.w(TAG, "$operation $topic timed out")
        done
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$operation $topic failed", e)
        false
    }

    private companion object {
        const val TAG = "TopicSync"
    }
}
