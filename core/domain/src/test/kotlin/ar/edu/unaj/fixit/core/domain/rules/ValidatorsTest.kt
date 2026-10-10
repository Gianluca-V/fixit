package ar.edu.unaj.fixit.core.domain.rules

import ar.edu.unaj.fixit.core.domain.model.GeoPoint
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
}
