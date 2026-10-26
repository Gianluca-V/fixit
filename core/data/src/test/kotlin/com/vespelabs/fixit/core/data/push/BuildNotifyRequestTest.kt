package com.vespelabs.fixit.core.data.push

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

/** Design 14b.3: the request the app sends to the Worker. */
class BuildNotifyRequestTest {
    private val event = PushEvent(PushEventType.REQUEST_ACCEPTED, "r1")

    private fun bodyOf(event: PushEvent) = Buffer()
        .also { buildNotifyRequest("https://push.example.workers.dev", "id-token", event).body!!.writeTo(it) }
        .readUtf8()
        .let { Json.parseToJsonElement(it).jsonObject }

    @Test
    fun `given a base url when the request is built then it is a POST to base notify`() {
        val request = buildNotifyRequest("https://push.example.workers.dev", "id-token", event)

        assertEquals("POST", request.method)
        assertEquals("https://push.example.workers.dev/notify", request.url.toString())
    }

    @Test
    fun `given a base url with a trailing slash when the request is built then the path is still notify`() {
        val request = buildNotifyRequest("https://push.example.workers.dev/", "id-token", event)

        assertEquals("https://push.example.workers.dev/notify", request.url.toString())
    }

    @Test
    fun `given an id token when the request is built then it travels as a Bearer authorization header`() {
        val request = buildNotifyRequest("https://push.example.workers.dev", "id-token", event)

        assertEquals("Bearer id-token", request.header("Authorization"))
    }

    @Test
    fun `given an event when the request is built then the JSON body has exactly type and requestId`() {
        val body = bodyOf(event)

        assertEquals(setOf("type", "requestId"), body.keys)
        assertEquals("REQUEST_ACCEPTED", body.getValue("type").jsonPrimitive.content)
        assertEquals("r1", body.getValue("requestId").jsonPrimitive.content)
    }

    @Test
    fun `given each event type when the request is built then type carries the wire name`() {
        for (type in PushEventType.entries) {
            assertEquals(type.name, bodyOf(PushEvent(type, "r1")).getValue("type").jsonPrimitive.content)
        }
    }

    @Test
    fun `given an event when the request is built then the body is application json`() {
        val request = buildNotifyRequest("https://push.example.workers.dev", "id-token", event)

        assertEquals("application/json", request.body!!.contentType()!!.let { "${it.type}/${it.subtype}" })
    }
}
