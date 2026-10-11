package com.vespelabs.fixit.core.data.firebase

import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.firebase.firestore.FieldValue
import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.model.ProfessionalInfo
import com.vespelabs.fixit.core.domain.model.ProfileForm
import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.Reputation
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.Review
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtosTest {

    private val base = Place(GeoPoint(-34.6037, -58.3816), "Av. Corrientes 1234")

    private val client = User(
        id = "u1",
        name = "Ana",
        email = "ana@mail.com",
        phone = "1155550000",
        role = UserRole.CLIENT,
    )

    private val professional = User(
        id = "u2",
        name = "Beto",
        email = "beto@mail.com",
        phone = "1155551111",
        role = UserRole.PROFESSIONAL,
        professional = ProfessionalInfo(
            categories = setOf(ServiceCategory.GAS, ServiceCategory.PLUMBING),
            base = base,
            radiusKm = 10,
            reputation = Reputation(ratingSum = 9, reviewCount = 2),
        ),
    )

    @Test
    fun `given any role when a user is created then ratingSum and reviewCount start at zero`() {
        for (user in listOf(client, professional)) {
            val fields = user.toCreateFields()
            assertEquals(0, fields[UserFields.RATING_SUM])
            assertEquals(0, fields[UserFields.REVIEW_COUNT])
            assertTrue(fields[UserFields.CREATED_AT] is FieldValue)
        }
    }

    @Test
    fun `given a professional when it is created then its profile fields are written`() {
        val fields = professional.toCreateFields()

        assertEquals("Beto", fields[UserFields.NAME])
        assertEquals("beto@mail.com", fields[UserFields.EMAIL])
        assertEquals("1155551111", fields[UserFields.PHONE])
        assertEquals("PROFESSIONAL", fields[UserFields.ROLE])
        assertEquals(setOf("GAS", "PLUMBING"), (fields[UserFields.CATEGORIES] as List<*>).toSet())
        assertEquals(-34.6037, fields[UserFields.BASE_LAT])
        assertEquals(-58.3816, fields[UserFields.BASE_LNG])
        assertEquals("Av. Corrientes 1234", fields[UserFields.BASE_ADDRESS])
        assertEquals(10, fields[UserFields.RADIUS_KM])
    }

    @Test
    fun `given a client when it is created then no professional fields are written`() {
        val fields = client.toCreateFields()

        assertEquals("CLIENT", fields[UserFields.ROLE])
        for (key in listOf(UserFields.CATEGORIES, UserFields.BASE_LAT, UserFields.BASE_LNG, UserFields.BASE_ADDRESS, UserFields.RADIUS_KM)) {
            assertFalse(key, fields.containsKey(key))
        }
    }

    @Test
    fun `given stored fields when a user dto is mapped then the domain user is rebuilt`() {
        val dto = UserDto(
            id = "u2",
            name = "Beto",
            email = "beto@mail.com",
            phone = "1155551111",
            role = "PROFESSIONAL",
            categories = listOf("GAS", "PLUMBING"),
            baseLat = -34.6037,
            baseLng = -58.3816,
            baseAddress = "Av. Corrientes 1234",
            radiusKm = 10L,
            ratingSum = 9L,
            reviewCount = 2L,
        )

        assertEquals(professional, dto.toDomain())
        assertEquals(
            client,
            UserDto(id = "u1", name = "Ana", email = "ana@mail.com", phone = "1155550000", role = "CLIENT").toDomain(),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `given an unknown role when a user dto is mapped then it fails`() {
        UserDto(id = "u1", name = "Ana", email = "a@b.co", phone = "1", role = "ADMIN").toDomain()
    }

    @Test(expected = IllegalStateException::class)
    fun `given a professional without base when a user dto is mapped then it fails`() {
        UserDto(id = "u2", name = "Beto", email = "b@b.co", phone = "1", role = "PROFESSIONAL", radiusKm = 5L).toDomain()
    }

    @Test
    fun `given a professional when the profile is updated then only editable fields are written`() {
        val newBase = Place(GeoPoint(-34.0, -58.0), "Calle 1")
        val fields = profileUpdateFields(
            UserRole.PROFESSIONAL,
            ProfileForm(name = "B", phone = "2", categories = setOf(ServiceCategory.GAS), base = newBase, radiusKm = 20),
        )

        assertEquals(
            mapOf(
                UserFields.NAME to "B",
                UserFields.PHONE to "2",
                UserFields.CATEGORIES to listOf("GAS"),
                UserFields.BASE_LAT to -34.0,
                UserFields.BASE_LNG to -58.0,
                UserFields.BASE_ADDRESS to "Calle 1",
                UserFields.RADIUS_KM to 20,
            ),
            fields,
        )
        assertTrue(UserFields.EDITABLE.containsAll(fields.keys))
    }

    @Test
    fun `given a professional form without base or radius when the profile is updated then they are kept`() {
        val fields = profileUpdateFields(UserRole.PROFESSIONAL, ProfileForm(name = "B", phone = "2"))

        assertEquals(mapOf(UserFields.NAME to "B", UserFields.PHONE to "2", UserFields.CATEGORIES to emptyList<String>()), fields)
    }

    @Test
    fun `given a client when the profile is updated then professional fields are ignored`() {
        val fields = profileUpdateFields(
            UserRole.CLIENT,
            ProfileForm(name = "A", phone = "1", categories = setOf(ServiceCategory.GAS), base = base, radiusKm = 5),
        )

        assertEquals(mapOf(UserFields.NAME to "A", UserFields.PHONE to "1"), fields)
    }

    @Test
    fun `given a new request when its fields are built then they hold the geohash, photo urls and only createdAt`() {
        val photos = listOf("https://res.cloudinary.com/demo/image/upload/a.jpg")
        val fields = newRequestFields("u1", ServiceCategory.GAS, "Pierde gas la cocina", photos, base)

        assertEquals("u1", fields[RequestFields.CLIENT_ID])
        assertEquals("GAS", fields[RequestFields.CATEGORY])
        assertEquals("Pierde gas la cocina", fields[RequestFields.DESCRIPTION])
        assertEquals(photos, fields[RequestFields.PHOTO_URLS])
        assertEquals(-34.6037, fields[RequestFields.LAT])
        assertEquals(-58.3816, fields[RequestFields.LNG])
        assertEquals("Av. Corrientes 1234", fields[RequestFields.ADDRESS])
        assertEquals(GeoFireUtils.getGeoHashForLocation(GeoLocation(-34.6037, -58.3816)), fields[RequestFields.GEOHASH])
        assertEquals("OPEN", fields[RequestFields.STATUS])
        assertTrue(fields[RequestFields.CREATED_AT] is FieldValue)
        for (key in listOf(RequestFields.PROFESSIONAL_ID, RequestFields.ACCEPTED_AT, RequestFields.CANCELLED_AT, RequestFields.COMPLETED_AT)) {
            assertFalse(key, fields.containsKey(key))
        }
    }

    @Test
    fun `given stored fields when a request dto is mapped then the domain request is rebuilt`() {
        val dto = RequestDto(
            id = "r1",
            clientId = "u1",
            category = "GAS",
            description = "Pierde gas la cocina",
            photoUrls = listOf("https://x/a.jpg"),
            lat = -34.6037,
            lng = -58.3816,
            address = "Av. Corrientes 1234",
            status = "ACCEPTED",
            professionalId = "u2",
            createdAt = 1_000L,
            acceptedAt = 2_000L,
        )

        assertEquals(
            RepairRequest(
                id = "r1",
                clientId = "u1",
                category = ServiceCategory.GAS,
                description = "Pierde gas la cocina",
                photoUrls = listOf("https://x/a.jpg"),
                place = base,
                status = RequestStatus.ACCEPTED,
                professionalId = "u2",
                createdAt = 1_000L,
                acceptedAt = 2_000L,
            ),
            dto.toDomain(),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `given a request without createdAt when it is mapped then it fails`() {
        RequestDto(
            id = "r1", clientId = "u1", category = "GAS", description = "d", photoUrls = emptyList(),
            lat = 0.0, lng = 0.0, address = "a", status = "OPEN", createdAt = null,
        ).toDomain()
    }

    @Test
    fun `given a review when its fields are built and read back then they round trip`() {
        val fields = newReviewFields(professionalId = "u2", clientId = "u1", rating = 4, comment = null)

        assertEquals("u2", fields[ReviewFields.PROFESSIONAL_ID])
        assertEquals("u1", fields[ReviewFields.CLIENT_ID])
        assertEquals(4, fields[ReviewFields.RATING])
        assertNull(fields[ReviewFields.COMMENT])
        assertTrue(fields[ReviewFields.CREATED_AT] is FieldValue)
        assertEquals(
            Review("r1", "u2", "u1", 4, "Bien", 5_000L),
            ReviewDto("r1", "u2", "u1", 4L, "Bien", 5_000L).toDomain(),
        )
    }
}
