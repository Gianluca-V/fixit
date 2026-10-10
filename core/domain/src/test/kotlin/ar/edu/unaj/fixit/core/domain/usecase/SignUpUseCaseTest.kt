package ar.edu.unaj.fixit.core.domain.usecase

import ar.edu.unaj.fixit.core.domain.model.GeoPoint
import ar.edu.unaj.fixit.core.domain.model.Place
import ar.edu.unaj.fixit.core.domain.model.ProfessionalInfo
import ar.edu.unaj.fixit.core.domain.model.ProfileForm
import ar.edu.unaj.fixit.core.domain.model.Reputation
import ar.edu.unaj.fixit.core.domain.model.ServiceCategory
import ar.edu.unaj.fixit.core.domain.model.SignUpForm
import ar.edu.unaj.fixit.core.domain.model.User
import ar.edu.unaj.fixit.core.domain.model.UserRole
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.result.ValidationError
import ar.edu.unaj.fixit.core.domain.testing.TestAuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignUpUseCaseTest {
    private val auth = TestAuthRepository()
    private val signUp = SignUpUseCase(auth)

    private val base = Place(GeoPoint(-34.7206, -58.2546), "Quilmes")

    private fun clientForm(
        email: String = "ana@fixit.test",
        password: String = "test1234",
        name: String = "Ana López",
        phone: String = "11 5555-0001",
        role: UserRole? = UserRole.CLIENT,
    ) = SignUpForm(email, password, role, ProfileForm(name, phone))

    private fun professionalForm(
        categories: Set<ServiceCategory> = setOf(ServiceCategory.PLUMBING, ServiceCategory.GAS),
        base: Place? = this.base,
        radiusKm: Int? = 10,
    ) = SignUpForm(
        email = "diana@fixit.test",
        password = "test1234",
        role = UserRole.PROFESSIONAL,
        profile = ProfileForm("Diana Ruiz", "11 5555-0102", categories, base, radiusKm),
    )

    private fun assertValidation(expected: ValidationError, result: AppResult<User>) {
        assertEquals(AppResult.Error(DomainError.Validation(expected)), result)
        assertTrue("no account may be created", auth.signUpCalls.isEmpty())
    }

    @Test
    fun `given valid client data when signing up then the account is created with role CLIENT`() = runTest {
        val result = signUp(clientForm())

        val user = (result as AppResult.Success).data
        assertEquals(UserRole.CLIENT, user.role)
        assertEquals("ana@fixit.test", user.email)
        assertEquals(null, user.professional)
    }

    @Test
    fun `given valid professional data when signing up then the account has that profile and no reviews`() = runTest {
        val result = signUp(professionalForm())

        val user = (result as AppResult.Success).data
        assertEquals(UserRole.PROFESSIONAL, user.role)
        assertEquals(
            ProfessionalInfo(setOf(ServiceCategory.PLUMBING, ServiceCategory.GAS), base, 10, Reputation(0, 0)),
            user.professional,
        )
    }

    @Test
    fun `given an email already registered when signing up then the result is EmailAlreadyInUse`() = runTest {
        auth.signUpError = DomainError.EmailAlreadyInUse

        val result = signUp(clientForm())

        assertEquals(AppResult.Error(DomainError.EmailAlreadyInUse), result)
    }

    @Test
    fun `given an email with capitals and spaces when signing up then the repository receives the normalized email`() = runTest {
        signUp(clientForm(email = " Ana@Fixit.Test "))

        assertEquals("ana@fixit.test", auth.signUpCalls.single().email)
    }

    @Test
    fun `given a chosen role when signing up then the role reaches the repository and the session`() = runTest {
        signUp(professionalForm())

        assertEquals(UserRole.PROFESSIONAL, auth.signUpCalls.single().role)
    }

    @Test
    fun `given padded name and phone when signing up then trimmed values are persisted and the phone keeps its format`() = runTest {
        signUp(clientForm(name = "  Ana López ", phone = " 11 5555-0001 "))

        val profile = auth.signUpCalls.single().profile
        assertEquals("Ana López", profile.name)
        assertEquals("11 5555-0001", profile.phone)
    }

    @Test
    fun `given professional fields on a client form when signing up then they are not persisted`() = runTest {
        val form = clientForm().let { it.copy(profile = it.profile.copy(categories = setOf(ServiceCategory.GAS), base = base, radiusKm = 5)) }

        signUp(form)

        assertEquals(ProfileForm("Ana López", "11 5555-0001"), auth.signUpCalls.single().profile)
    }

    @Test
    fun `given an invalid email and a short password when signing up then only the first error is reported`() = runTest {
        assertValidation(ValidationError.INVALID_EMAIL, signUp(clientForm(email = "nope", password = "12345")))
    }

    @Test
    fun `given a short password when signing up then the result is WeakPassword`() = runTest {
        assertValidation(ValidationError.WEAK_PASSWORD, signUp(clientForm(password = "12345")))
    }

    @Test
    fun `given a phone with letters when signing up then the result is InvalidPhone`() = runTest {
        assertValidation(ValidationError.INVALID_PHONE, signUp(clientForm(phone = "12ab")))
    }

    @Test
    fun `given no role when signing up then the result is RoleRequired`() = runTest {
        assertValidation(ValidationError.ROLE_REQUIRED, signUp(clientForm(role = null)))
    }

    @Test
    fun `given a professional without categories when signing up then the result is CategoriesRequired`() = runTest {
        assertValidation(ValidationError.CATEGORIES_REQUIRED, signUp(professionalForm(categories = emptySet())))
    }

    @Test
    fun `given a professional without base point when signing up then the result is BaseLocationRequired`() = runTest {
        assertValidation(ValidationError.BASE_LOCATION_REQUIRED, signUp(professionalForm(base = null)))
    }

    @Test
    fun `given a base point with latitude 91 when signing up as professional then the result is InvalidCoordinates`() = runTest {
        val outOfRange = Place(GeoPoint(91.0, 0.0), "Nowhere")

        assertValidation(ValidationError.INVALID_COORDINATES, signUp(professionalForm(base = outOfRange)))
    }

    @Test
    fun `given a radius of 51 km when signing up as professional then the result is InvalidRadius`() = runTest {
        assertValidation(ValidationError.INVALID_RADIUS, signUp(professionalForm(radiusKm = 51)))
    }

    @Test
    fun `given a base point with valid coordinates and an invalid radius when signing up then coordinates are checked first`() = runTest {
        val outOfRange = Place(GeoPoint(0.0, -181.0), "Nowhere")

        assertValidation(ValidationError.INVALID_COORDINATES, signUp(professionalForm(base = outOfRange, radiusKm = 0)))
    }
}
