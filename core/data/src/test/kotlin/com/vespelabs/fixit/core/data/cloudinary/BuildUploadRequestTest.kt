package com.vespelabs.fixit.core.data.cloudinary

import okhttp3.MultipartBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildUploadRequestTest {

    private val config = CloudinaryConfig(cloudName = "demo-cloud", uploadPreset = "fixit_unsigned")
    private val bytes = byteArrayOf(1, 2, 3, 4, 5)

    private fun multipart(): MultipartBody = buildUploadRequest(config, bytes).body as MultipartBody

    private fun MultipartBody.Part.disposition(): String = headers?.get("Content-Disposition").orEmpty()

    private fun MultipartBody.Part.content(): ByteArray = Buffer().also { body.writeTo(it) }.readByteArray()

    private fun MultipartBody.part(name: String): MultipartBody.Part =
        parts.single { it.disposition().contains("name=\"$name\"") }

    @Test
    fun `given a config when the request is built then it is a POST to the unsigned upload endpoint of the cloud`() {
        val request = buildUploadRequest(config, bytes)

        assertEquals("POST", request.method)
        assertEquals("https://api.cloudinary.com/v1_1/demo-cloud/image/upload", request.url.toString())
    }

    @Test
    fun `given a config when the request is built then the body is multipart form data`() {
        assertEquals(MultipartBody.FORM, multipart().type)
    }

    @Test
    fun `given photo bytes when the request is built then the file part is a jpeg named photo jpg with those bytes`() {
        val file = multipart().part("file")

        assertTrue(file.disposition().contains("filename=\"photo.jpg\""))
        assertEquals("image/jpeg", file.body.contentType()?.toString())
        assertArrayEquals(bytes, file.content())
    }

    @Test
    fun `given a config when the request is built then the upload_preset part carries the preset`() {
        val preset = multipart().part("upload_preset")

        assertFalse(preset.disposition().contains("filename="))
        assertEquals("fixit_unsigned", preset.content().decodeToString())
    }

    @Test
    fun `given a config when the request is built then there are exactly the file and upload_preset parts and no secret`() {
        val body = multipart()
        val names = body.parts.map { Regex("name=\"([^\"]+)\"").find(it.disposition())?.groupValues?.get(1) }

        assertEquals(listOf("file", "upload_preset"), names)
        val request = buildUploadRequest(config, bytes)
        listOf("api_key", "api_secret", "signature", "timestamp").forEach { forbidden ->
            assertFalse(names.contains(forbidden))
            assertFalse(request.url.toString().contains(forbidden))
        }
        assertEquals(0, request.headers.size)
    }
}
