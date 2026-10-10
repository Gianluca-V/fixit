package ar.edu.unaj.fixit.core.domain.rules

import ar.edu.unaj.fixit.core.domain.model.GeoPoint
import ar.edu.unaj.fixit.core.domain.model.Place
import ar.edu.unaj.fixit.core.domain.model.ProfileForm
import ar.edu.unaj.fixit.core.domain.model.ServiceCategory
import ar.edu.unaj.fixit.core.domain.model.SignUpForm
import ar.edu.unaj.fixit.core.domain.model.UserRole
import ar.edu.unaj.fixit.core.domain.result.ValidationError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {
    // region (a) normalizeEmail, signIn, coordinates

    @Test
    fun `given an email with spaces and capitals when normalizing then it is trimmed and lowercased`() {
        assertEquals("user@example.com", Validators.normalizeEmail("  User@Example.COM "))
    }

    @Test
    fun `given a valid email and a password when validating sign in then there is no error`() {
        assertNull(Validators.signIn("user@example.com", "x"))
    }

    @Test
    fun `given an email with capitals and spaces when validating sign in then it is accepted after normalization`() {
        assertNull(Validators.signIn("  User@Example.COM ", "secret"))
    }

    @Test
    fun `given emails with a valid shape when validating sign in then all are accepted`() {
        listOf("user@example.com", "first.last+tag@sub.example.co.ar", "a_b%c-d@x-y.io").forEach { email ->
            assertNull("$email must be valid", Validators.signIn(email, "secret"))
        }
    }

    @Test
    fun `given malformed emails when validating sign in then the result is invalid email`() {
        listOf("", "   ", "user", "user@", "@example.com", "user@example", "user@example.c", "user@@example.com", "us er@example.com")
            .forEach { email ->
                assertEquals(
                    "$email must be invalid",
                    ValidationError.INVALID_EMAIL,
                    Validators.signIn(email, "secret"),
                )
            }
    }

    @Test
    fun `given an empty email when validating sign in then the result is invalid email`() {
        assertEquals(ValidationError.INVALID_EMAIL, Validators.signIn("", "secret"))
    }

    @Test
    fun `given a valid email and an empty password when validating sign in then the result is empty password`() {
        assertEquals(ValidationError.EMPTY_PASSWORD, Validators.signIn("user@example.com", ""))
    }

    @Test
    fun `given an invalid email and an empty password when validating sign in then only the email error is reported`() {
        assertEquals(ValidationError.INVALID_EMAIL, Validators.signIn("nope", ""))
    }

    @Test
    fun `given a latitude of 91 when validating coordinates then they are invalid`() {
        assertEquals(ValidationError.INVALID_COORDINATES, Validators.coordinates(GeoPoint(91.0, 0.0)))
    }

    @Test
    fun `given values just outside the ranges when validating coordinates then they are invalid`() {
        listOf(GeoPoint(-91.0, 0.0), GeoPoint(0.0, 181.0), GeoPoint(0.0, -181.0)).forEach { point ->
            assertEquals("$point must be invalid", ValidationError.INVALID_COORDINATES, Validators.coordinates(point))
        }
    }

    @Test
    fun `given the exact limits when validating coordinates then both corners are accepted`() {
        assertNull(Validators.coordinates(GeoPoint(-90.0, -180.0)))
        assertNull(Validators.coordinates(GeoPoint(90.0, 180.0)))
    }

    @Test
    fun `given NaN or infinite values when validating coordinates then they are invalid`() {
        listOf(
            GeoPoint(Double.NaN, 0.0),
            GeoPoint(0.0, Double.NaN),
            GeoPoint(Double.POSITIVE_INFINITY, 0.0),
            GeoPoint(0.0, Double.NEGATIVE_INFINITY),
        ).forEach { point ->
            assertEquals("$point must be invalid", ValidationError.INVALID_COORDINATES, Validators.coordinates(point))
        }
    }

    // endregion

    // region (b) signUp and profile

    private val validBase = Place(GeoPoint(-34.8065, -58.2788), "Av. San Martin 2000, Florencio Varela")

    private val clientProfile = ProfileForm(name = "Ana Perez", phone = "11 5555-1234")

    private val professionalProfile = ProfileForm(
        name = "Pablo Gomez",
        phone = "+54 9 221 555 1234",
        categories = setOf(ServiceCategory.PLUMBING, ServiceCategory.GAS),
        base = validBase,
        radiusKm = 10,
    )

    private fun signUpForm(
        email: String = "user@example.com",
        password: String = "secret",
        role: UserRole? = UserRole.CLIENT,
        profile: ProfileForm = clientProfile,
    ) = SignUpForm(email = email, password = password, role = role, profile = profile)

    private fun professionalSignUp(profile: ProfileForm) = signUpForm(role = UserRole.PROFESSIONAL, profile = profile)

    @Test
    fun `given valid client data when validating sign up then there is no error`() {
        assertNull(Validators.signUp(signUpForm()))
    }

    @Test
    fun `given valid professional data when validating sign up then there is no error`() {
        assertNull(Validators.signUp(professionalSignUp(professionalProfile)))
    }

    @Test
    fun `given professional fields on a client when validating sign up then they are ignored`() {
        val ignored = clientProfile.copy(categories = emptySet(), base = null, radiusKm = 0)

        assertNull(Validators.signUp(signUpForm(profile = ignored)))
    }

    @Test
    fun `given the phone 12ab when validating sign up then the result is invalid phone`() {
        val form = signUpForm(profile = clientProfile.copy(phone = "12ab"))

        assertEquals(ValidationError.INVALID_PHONE, Validators.signUp(form))
    }

    @Test
    fun `given a phone with spaces parentheses and hyphens when validating sign up then it is accepted`() {
        val form = signUpForm(profile = clientProfile.copy(phone = " +54 (11) 5555-1234 "))

        assertNull(Validators.signUp(form))
    }

    @Test
    fun `given phones with 8 and 15 digits when validating sign up then both are accepted`() {
        listOf("12345678", "+123456789012345", "123456789012345").forEach { phone ->
            assertNull("$phone must be valid", Validators.signUp(signUpForm(profile = clientProfile.copy(phone = phone))))
        }
    }

    @Test
    fun `given phones with 7 and 16 digits or a misplaced plus when validating sign up then they are invalid`() {
        listOf("1234567", "1234567890123456", "11+55551234", "++5491155551234", "").forEach { phone ->
            assertEquals(
                "$phone must be invalid",
                ValidationError.INVALID_PHONE,
                Validators.signUp(signUpForm(profile = clientProfile.copy(phone = phone))),
            )
        }
    }

    @Test
    fun `given a password of 5 characters when validating sign up then the result is weak password`() {
        assertEquals(ValidationError.WEAK_PASSWORD, Validators.signUp(signUpForm(password = "12345")))
    }

    @Test
    fun `given a password of exactly 6 characters when validating sign up then it is accepted`() {
        assertNull(Validators.signUp(signUpForm(password = "123456")))
    }

    @Test
    fun `given names of 1 and 61 characters when validating sign up then the result is invalid name`() {
        listOf("A", "A".repeat(61), "  A  ", "   ").forEach { name ->
            assertEquals(
                "'$name' must be invalid",
                ValidationError.INVALID_NAME,
                Validators.signUp(signUpForm(profile = clientProfile.copy(name = name))),
            )
        }
    }

    @Test
    fun `given names of exactly 2 and 60 characters when validating sign up then both are accepted`() {
        listOf("Al", "A".repeat(60), "  Al  ").forEach { name ->
            assertNull("'$name' must be valid", Validators.signUp(signUpForm(profile = clientProfile.copy(name = name))))
        }
    }

    @Test
    fun `given no role when validating sign up then the result is role required`() {
        assertEquals(ValidationError.ROLE_REQUIRED, Validators.signUp(signUpForm(role = null)))
    }

    @Test
    fun `given a professional without categories when validating sign up then the result is categories required`() {
        val form = professionalSignUp(professionalProfile.copy(categories = emptySet()))

        assertEquals(ValidationError.CATEGORIES_REQUIRED, Validators.signUp(form))
    }

    @Test
    fun `given a professional without base point when validating sign up then the result is base location required`() {
        val form = professionalSignUp(professionalProfile.copy(base = null))

        assertEquals(ValidationError.BASE_LOCATION_REQUIRED, Validators.signUp(form))
    }

    @Test
    fun `given a base point with latitude 91 or longitude -181 when validating sign up then the result is invalid coordinates`() {
        listOf(GeoPoint(91.0, 0.0), GeoPoint(0.0, -181.0)).forEach { point ->
            val form = professionalSignUp(professionalProfile.copy(base = Place(point, "Somewhere")))
            assertEquals("$point must be invalid", ValidationError.INVALID_COORDINATES, Validators.signUp(form))
        }
    }

    @Test
    fun `given a radius of 0 or 51 or none when validating sign up then the result is invalid radius`() {
        listOf(0, 51, -5, null).forEach { radius ->
            val form = professionalSignUp(professionalProfile.copy(radiusKm = radius))
            assertEquals("$radius must be invalid", ValidationError.INVALID_RADIUS, Validators.signUp(form))
        }
    }

    @Test
    fun `given radii of exactly 1 and 50 when validating sign up then both are accepted`() {
        listOf(1, 50).forEach { radius ->
            assertNull("$radius must be valid", Validators.signUp(professionalSignUp(professionalProfile.copy(radiusKm = radius))))
        }
    }

    @Test
    fun `given an invalid email and a 5 character password when validating sign up then only the email error is reported`() {
        assertEquals(ValidationError.INVALID_EMAIL, Validators.signUp(signUpForm(email = "nope", password = "12345")))
    }

    @Test
    fun `given a form that is wrong everywhere when fixing one field at a time then errors follow the documented order`() {
        val wrong = ProfileForm(name = "A", phone = "12ab", categories = emptySet(), base = null, radiusKm = 0)
        var form = SignUpForm(email = "nope", password = "123", role = null, profile = wrong)
        val order = mutableListOf<ValidationError?>()

        order += Validators.signUp(form)
        form = form.copy(email = "user@example.com")
        order += Validators.signUp(form)
        form = form.copy(password = "secret")
        order += Validators.signUp(form)
        form = form.copy(profile = form.profile.copy(name = "Pablo"))
        order += Validators.signUp(form)
        form = form.copy(profile = form.profile.copy(phone = "1155551234"))
        order += Validators.signUp(form)
        form = form.copy(role = UserRole.PROFESSIONAL)
        order += Validators.signUp(form)
        form = form.copy(profile = form.profile.copy(categories = setOf(ServiceCategory.GAS)))
        order += Validators.signUp(form)
        form = form.copy(profile = form.profile.copy(base = Place(GeoPoint(91.0, 0.0), "x")))
        order += Validators.signUp(form)
        form = form.copy(profile = form.profile.copy(base = validBase))
        order += Validators.signUp(form)
        form = form.copy(profile = form.profile.copy(radiusKm = 10))
        order += Validators.signUp(form)

        assertEquals(
            listOf(
                ValidationError.INVALID_EMAIL,
                ValidationError.WEAK_PASSWORD,
                ValidationError.INVALID_NAME,
                ValidationError.INVALID_PHONE,
                ValidationError.ROLE_REQUIRED,
                ValidationError.CATEGORIES_REQUIRED,
                ValidationError.BASE_LOCATION_REQUIRED,
                ValidationError.INVALID_COORDINATES,
                ValidationError.INVALID_RADIUS,
                null,
            ),
            order,
        )
    }

    @Test
    fun `given a name of 1 character when validating a profile then the result is invalid name`() {
        assertEquals(
            ValidationError.INVALID_NAME,
            Validators.profile(UserRole.CLIENT, clientProfile.copy(name = "A")),
        )
    }

    @Test
    fun `given the phone 12ab when validating a profile then the result is invalid phone`() {
        assertEquals(
            ValidationError.INVALID_PHONE,
            Validators.profile(UserRole.CLIENT, clientProfile.copy(phone = "12ab")),
        )
    }

    @Test
    fun `given an invalid name and an invalid phone when validating a profile then only the name error is reported`() {
        assertEquals(
            ValidationError.INVALID_NAME,
            Validators.profile(UserRole.CLIENT, clientProfile.copy(name = "A", phone = "12ab")),
        )
    }

    @Test
    fun `given valid data when validating a client profile then professional fields are ignored`() {
        assertNull(Validators.profile(UserRole.CLIENT, clientProfile.copy(categories = emptySet(), base = null, radiusKm = 0)))
    }

    @Test
    fun `given valid data when validating a professional profile then there is no error`() {
        assertNull(Validators.profile(UserRole.PROFESSIONAL, professionalProfile))
    }

    @Test
    fun `given no categories when validating a professional profile then the result is categories required`() {
        assertEquals(
            ValidationError.CATEGORIES_REQUIRED,
            Validators.profile(UserRole.PROFESSIONAL, professionalProfile.copy(categories = emptySet())),
        )
    }

    @Test
    fun `given no base point when validating a professional profile then the result is base location required`() {
        assertEquals(
            ValidationError.BASE_LOCATION_REQUIRED,
            Validators.profile(UserRole.PROFESSIONAL, professionalProfile.copy(base = null)),
        )
    }

    @Test
    fun `given a base point out of range when validating a professional profile then the result is invalid coordinates`() {
        val profile = professionalProfile.copy(base = Place(GeoPoint(0.0, 181.0), "Somewhere"))

        assertEquals(ValidationError.INVALID_COORDINATES, Validators.profile(UserRole.PROFESSIONAL, profile))
    }

    @Test
    fun `given a radius of 0 or 51 when validating a professional profile then the result is invalid radius`() {
        listOf(0, 51).forEach { radius ->
            assertEquals(
                "$radius must be invalid",
                ValidationError.INVALID_RADIUS,
                Validators.profile(UserRole.PROFESSIONAL, professionalProfile.copy(radiusKm = radius)),
            )
        }
    }

    @Test
    fun `given a professional profile wrong everywhere when fixing one field at a time then errors follow the documented order`() {
        var form = ProfileForm(name = "A", phone = "12ab", categories = emptySet(), base = null, radiusKm = 0)
        val order = mutableListOf<ValidationError?>()

        order += Validators.profile(UserRole.PROFESSIONAL, form)
        form = form.copy(name = "Pablo")
        order += Validators.profile(UserRole.PROFESSIONAL, form)
        form = form.copy(phone = "1155551234")
        order += Validators.profile(UserRole.PROFESSIONAL, form)
        form = form.copy(categories = setOf(ServiceCategory.GAS))
        order += Validators.profile(UserRole.PROFESSIONAL, form)
        form = form.copy(base = Place(GeoPoint(91.0, 0.0), "x"))
        order += Validators.profile(UserRole.PROFESSIONAL, form)
        form = form.copy(base = validBase)
        order += Validators.profile(UserRole.PROFESSIONAL, form)
        form = form.copy(radiusKm = 25)
        order += Validators.profile(UserRole.PROFESSIONAL, form)

        assertEquals(
            listOf(
                ValidationError.INVALID_NAME,
                ValidationError.INVALID_PHONE,
                ValidationError.CATEGORIES_REQUIRED,
                ValidationError.BASE_LOCATION_REQUIRED,
                ValidationError.INVALID_COORDINATES,
                ValidationError.INVALID_RADIUS,
                null,
            ),
            order,
        )
    }

    // endregion
}
