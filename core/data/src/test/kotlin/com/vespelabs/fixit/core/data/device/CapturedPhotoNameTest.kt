package com.vespelabs.fixit.core.data.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CapturedPhotoNameTest {

    private val authority = "com.vespelabs.fixit.fileprovider"

    private fun name(uri: String) = capturedPhotoName(uri, authority)

    @Test
    fun `given a camera capture from our FileProvider when resolving then it is the file name`() {
        assertEquals("capture_123.jpg", name("content://$authority/photos/capture_123.jpg"))
    }

    @Test
    fun `given a percent-encoded name when resolving then it is decoded`() {
        assertEquals("capture 1.jpg", name("content://$authority/photos/capture%201.jpg"))
    }

    @Test
    fun `given a system Photo Picker uri when resolving then it is null`() {
        assertNull(name("content://media/picker/0/com.android.providers.media.photopicker/media/1000"))
    }

    @Test
    fun `given another app FileProvider when resolving then it is null`() {
        assertNull(name("content://com.other.app.fileprovider/photos/capture_123.jpg"))
    }

    @Test
    fun `given another path of our FileProvider when resolving then it is null`() {
        assertNull(name("content://$authority/images/capture_123.jpg"))
        assertNull(name("content://$authority/photos/sub/capture_123.jpg"))
        assertNull(name("content://$authority/photos/"))
    }

    @Test
    fun `given a path traversal when resolving then it is null`() {
        assertNull(name("content://$authority/photos/.."))
        assertNull(name("content://$authority/photos/..%2F..%2Fshared_prefs%2Fx.xml"))
        assertNull(name("content://$authority/photos/a%2Fb.jpg"))
        assertNull(name("content://$authority/photos/a%5Cb.jpg"))
    }

    @Test
    fun `given a file or malformed uri when resolving then it is null`() {
        assertNull(name("file:///data/user/0/com.vespelabs.fixit/cache/photos/capture_123.jpg"))
        assertNull(name("not a uri"))
        assertNull(name(""))
    }
}
