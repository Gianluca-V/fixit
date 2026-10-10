package ar.edu.unaj.fixit.core.domain.usecase

import ar.edu.unaj.fixit.core.domain.model.GeoPoint
import ar.edu.unaj.fixit.core.domain.model.Place
import ar.edu.unaj.fixit.core.domain.model.ProfileForm
import ar.edu.unaj.fixit.core.domain.model.Reputation
import ar.edu.unaj.fixit.core.domain.model.ServiceCategory
import ar.edu.unaj.fixit.core.domain.model.User
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.result.ValidationError
import ar.edu.unaj.fixit.core.domain.testing.TestAuthRepository
import ar.edu.unaj.fixit.core.domain.testing.TestData
import ar.edu.unaj.fixit.core.domain.testing.TestUserRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateProfileUseCaseTest {
    private val auth = TestAuthRepository()
    private val users = TestUserRepository()
    private val updateProfile = UpdateProfileUseCase(auth, users)

    private val ana = TestData.client(name = "Ana", phone = "1155551234")
    private val plumber = TestData.professional(
        categories = setOf(ServiceCategory.PLUMBING),
        radiusKm = 10,
        reputation = Reputation(ratingSum = 13, reviewCount = 3),
    )

    private fun signInAs(user: User) {
        users.upsert(user)
        auth.signedInAs(user)
    }

    private fun proForm(
        categories: Set<ServiceCategory> = setOf(ServiceCategory.PLUMBING, ServiceCategory.GAS),
        base: Place? = TestData.florencioVarela,
        radiusKm: Int? = 25,
    ) = ProfileForm("Carlos Gómez", "11 5555-0101", categories, base, radiusKm)

    private fun assertRejected(expected: ValidationError, result: AppResult<User>) {
        assertEquals(AppResult.Error(DomainError.Validation(expected)), result)
        assertTrue("nothing may be persisted", users.updateProfileCalls.isEmpty())
    }

    @Test
    fun `given a client when changing name and phone then they are updated and email and role stay`() = runTest {
        signInAs(ana)

        val result = updateProfile(ProfileForm("Ana Pérez", "11 5555-9999"))

        val updated = (result as AppResult.Success).data
        assertEquals("Ana Pérez", updated.name)
        assertEquals("11 5555-9999", updated.phone)
        assertEquals(ana.email, updated.email)
        assertEquals(ana.role, updated.role)
    }

    @Test
    fun `given padded values when updating the profile then trimmed values are persisted`() = runTest {
        signInAs(ana)

        updateProfile(ProfileForm("  Ana Pérez ", " 11 5555-9999 "))

        assertEquals(ProfileForm("Ana Pérez", "11 5555-9999"), users.updateProfileCalls.single().second)
    }

    @Test
    fun `given a name of one character when updating the profile then the result is InvalidName`() = runTest {
        signInAs(ana)

        assertRejected(ValidationError.INVALID_NAME, updateProfile(ProfileForm("A", "11 5555-9999")))
    }

    @Test
    fun `given a phone with letters when updating the profile then the result is InvalidPhone`() = runTest {
        signInAs(ana)

        assertRejected(ValidationError.INVALID_PHONE, updateProfile(ProfileForm("Ana Pérez", "12ab")))
    }

    @Test
    fun `given a professional when changing categories and radius then the work zone changes and the reputation stays`() = runTest {
        signInAs(plumber)

        val result = updateProfile(proForm())

        val info = (result as AppResult.Success).data.professional!!
        assertEquals(setOf(ServiceCategory.PLUMBING, ServiceCategory.GAS), info.categories)
        assertEquals(25, info.radiusKm)
        assertEquals(Reputation(13, 3), info.reputation)
    }

    @Test
    fun `given a professional without categories when updating then the result is CategoriesRequired`() = runTest {
        signInAs(plumber)

        assertRejected(ValidationError.CATEGORIES_REQUIRED, updateProfile(proForm(categories = emptySet())))
    }

    @Test
    fun `given a professional without base point when updating then the result is BaseLocationRequired`() = runTest {
        signInAs(plumber)

        assertRejected(ValidationError.BASE_LOCATION_REQUIRED, updateProfile(proForm(base = null)))
    }

    @Test
    fun `given a base point out of range when updating then the result is InvalidCoordinates`() = runTest {
        signInAs(plumber)

        val outOfRange = Place(GeoPoint(91.0, 0.0), "Nowhere")

        assertRejected(ValidationError.INVALID_COORDINATES, updateProfile(proForm(base = outOfRange)))
    }

    @Test
    fun `given a radius of 0 or 51 when updating then the result is InvalidRadius`() = runTest {
        signInAs(plumber)

        assertRejected(ValidationError.INVALID_RADIUS, updateProfile(proForm(radiusKm = 0)))
        assertRejected(ValidationError.INVALID_RADIUS, updateProfile(proForm(radiusKm = 51)))
    }

    @Test
    fun `given a new base point from the device location when updating then it is persisted`() = runTest {
        signInAs(plumber)
        val quilmes = Place(GeoPoint(-34.7206, -58.2546), "Quilmes")

        val result = updateProfile(proForm(base = quilmes))

        assertEquals(quilmes, (result as AppResult.Success).data.professional!!.base)
    }

    @Test
    fun `given professional fields on a client form when updating then they are not persisted`() = runTest {
        signInAs(ana)

        updateProfile(proForm())

        assertEquals(ProfileForm("Carlos Gómez", "11 5555-0101"), users.updateProfileCalls.single().second)
    }

    @Test
    fun `given no session when updating the profile then the result is Unauthorized`() = runTest {
        val result = updateProfile(ProfileForm("Ana Pérez", "11 5555-9999"))

        assertEquals(AppResult.Error(DomainError.Unauthorized), result)
        assertTrue(users.updateProfileCalls.isEmpty())
    }

    @Test
    fun `given a save failure when updating the profile then the error is returned unchanged`() = runTest {
        signInAs(ana)
        users.updateProfileError = DomainError.Network

        val result = updateProfile(ProfileForm("Ana Pérez", "11 5555-9999"))

        assertEquals(AppResult.Error(DomainError.Network), result)
    }
}
