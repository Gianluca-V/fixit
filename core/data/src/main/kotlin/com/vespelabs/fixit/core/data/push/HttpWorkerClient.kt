package com.vespelabs.fixit.core.data.push

import android.util.Log
import javax.inject.Inject
import okhttp3.OkHttpClient
import okhttp3.coroutines.executeAsync

/**
 * Sends the event over the shared Phase 2 [OkHttpClient]. `executeAsync()` cancels the Call with the coroutine, so the
 * decorator's 5 s bound really stops it. The response is not interpreted (logged when not 2xx) and its body is
 * never read. No JVM test: the request is covered by BuildNotifyRequestTest; checked by hand in 4.13.1 and 4.13.5.
 */
class HttpWorkerClient @Inject constructor(
    private val client: OkHttpClient,
    private val config: WorkerConfig,
) : WorkerClient {
    override suspend fun send(idToken: String, event: PushEvent) {
        client.newCall(buildNotifyRequest(config.baseUrl, idToken, event)).executeAsync().use { response ->
            if (!response.isSuccessful) Log.w("WorkerClient", "notify ${event.type} answered ${response.code}")
        }
    }
}
