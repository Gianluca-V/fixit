package com.vespelabs.fixit.core.ui.components

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileFormStateTest {
    private val quilmes = Place(GeoPoint(-34.7206, -58.2546), "Calle 123, Quilmes")

    // [prof] Client area preloaded when editing

    @Test
    fun `given a client with area when the form is preloaded then it holds the area`() {
        assertEquals(quilmes, ProfileFormState.from(TestData.client(area = quilmes)).area)
    }

    @Test
    fun `given a client without area when the form is preloaded then the area is empty`() {
        assertNull(ProfileFormState.from(TestData.client()).area)
    }

    @Test
    fun `given a form with area when converting it for the use case then the area is passed along`() {
        val form = ProfileFormState(name = "Ana", phone = "1155551234", area = quilmes).toProfileForm()

        assertEquals(quilmes, form.area)
    }
}
