package com.vespelabs.fixit.core.data.push

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Asks the push Worker to send one event (design 14b.3). Best effort: callers never wait for it. */
interface WorkerClient {
    suspend fun send(idToken: String, event: PushEvent)
}

/** The Worker base URL from `PUSH_WORKER_URL`; blank means "no Worker configured" (withPush returns the real repo). */
data class WorkerConfig(val baseUrl: String)

/** The Firebase ID token of the signed-in user, or null without a session. */
fun interface IdTokenProvider {
    suspend fun idToken(): String?
}

private val JSON = "application/json; charset=utf-8".toMediaType()

/** `POST {baseUrl}/notify`, `Authorization: Bearer <idToken>`, body exactly `{ "type", "requestId" }`. */
fun buildNotifyRequest(baseUrl: String, idToken: String, event: PushEvent): Request {
    val body = buildJsonObject {
        put(PushKeys.TYPE, event.type.name)
        put(PushKeys.REQUEST_ID, event.requestId)
    }
    return Request.Builder()
        .url("${baseUrl.trimEnd('/')}/notify")
        .header("Authorization", "Bearer $idToken")
        .post(body.toString().toRequestBody(JSON))
        .build()
}
