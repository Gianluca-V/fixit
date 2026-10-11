package com.vespelabs.fixit.core.domain.usecase

import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.result.ValidationError
import com.vespelabs.fixit.core.domain.testing.TestAuthRepository
import com.vespelabs.fixit.core.domain.testing.TestData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInUseCaseTest {
    private val auth = TestAuthRepository()
    private val signIn = SignInUseCase(auth)

    @Test
    fun `given a registered user when signing in with the right credentials then the result is that user`() = runTest {
        val ana = TestData.client()
        auth.signInUser = ana

        val result = signIn("ana@fixit.test", "test1234")

        assertEquals(AppResult.Success(ana), result)
    }

    @Test
    fun `given wrong credentials when signing in then the result is InvalidCredentials`() = runTest {
        auth.signInUser = null

        val result = signIn("ana@fixit.test", "wrong-password")

        assertEquals(AppResult.Error(DomainError.InvalidCredentials), result)
    }

    @Test
    fun `given an empty email when signing in then the result is InvalidEmail and the account is not queried`() = runTest {
        val result = signIn("", "test1234")

        assertEquals(AppResult.Error(DomainError.Validation(ValidationError.INVALID_EMAIL)), result)
        assertTrue(auth.signInCalls.isEmpty())
    }

    @Test
    fun `given a valid email and an empty password when signing in then the result is EmptyPassword and the account is not queried`() = runTest {
        val result = signIn("ana@fixit.test", "")

        assertEquals(AppResult.Error(DomainError.Validation(ValidationError.EMPTY_PASSWORD)), result)
        assertTrue(auth.signInCalls.isEmpty())
    }

    @Test
    fun `given an email with capitals and spaces when signing in then the repository receives the normalized email`() = runTest {
        auth.signInUser = TestData.client()

        signIn(" Ana@Fixit.Test ", "test1234")

        assertEquals(listOf("ana@fixit.test" to "test1234"), auth.signInCalls)
    }

    @Test
    fun `given a repository failure when signing in then the error is returned unchanged`() = runTest {
        auth.signInError = DomainError.Network

        val result = signIn("ana@fixit.test", "test1234")

        assertEquals(AppResult.Error(DomainError.Network), result)
    }
}
