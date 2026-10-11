package com.vespelabs.fixit.core.data.cloudinary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ParseUploadResponseTest {

    private val url = "https://res.cloudinary.com/demo-cloud/image/upload/v1/abc123.jpg"

    @Test
    fun `given 200 with a secure_url when the response is parsed then it returns the url`() {
        val body = """{"public_id":"abc123","url":"http://insecure","secure_url":"$url","format":"jpg"}"""

        assertEquals(url, parseUploadResponse(200, body))
    }

    @Test
    fun `given 200 without a secure_url when the response is parsed then it throws CloudinaryUploadException 200`() {
        val error = assertThrows(CloudinaryUploadException::class.java) {
            parseUploadResponse(200, """{"public_id":"abc123"}""")
        }

        assertEquals(200, error.httpCode)
    }

    @Test
    fun `given 400 with an error body when the response is parsed then it throws CloudinaryUploadException 400`() {
        val error = assertThrows(CloudinaryUploadException::class.java) {
            parseUploadResponse(400, """{"error":{"message":"Upload preset must be whitelisted for unsigned uploads"}}""")
        }

        assertEquals(400, error.httpCode)
    }

    @Test
    fun `given an error code with a secure_url when the response is parsed then it still throws with that code`() {
        val error = assertThrows(CloudinaryUploadException::class.java) {
            parseUploadResponse(500, """{"secure_url":"$url"}""")
        }

        assertEquals(500, error.httpCode)
    }

    @Test
    fun `given 200 with a body that is not json when the response is parsed then it throws CloudinaryUploadException`() {
        val error = assertThrows(CloudinaryUploadException::class.java) {
            parseUploadResponse(200, "<html>Bad gateway</html>")
        }

        assertEquals(200, error.httpCode)
    }

    @Test
    fun `given 200 with a secure_url that is not a string when the response is parsed then it throws`() {
        assertThrows(CloudinaryUploadException::class.java) { parseUploadResponse(200, """{"secure_url":null}""") }
        assertThrows(CloudinaryUploadException::class.java) { parseUploadResponse(200, """{"secure_url":{}}""") }
    }
}
