package ar.edu.unaj.fixit.core.domain.usecase

import ar.edu.unaj.fixit.core.domain.model.GeoPoint
import ar.edu.unaj.fixit.core.domain.model.NewRequest
import ar.edu.unaj.fixit.core.domain.model.Place
import ar.edu.unaj.fixit.core.domain.model.RepairRequest
import ar.edu.unaj.fixit.core.domain.model.RequestStatus
import ar.edu.unaj.fixit.core.domain.model.ServiceCategory
import ar.edu.unaj.fixit.core.domain.model.User
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.result.ValidationError
import ar.edu.unaj.fixit.core.domain.testing.TestAuthRepository
import ar.edu.unaj.fixit.core.domain.testing.TestData
import ar.edu.unaj.fixit.core.domain.testing.TestRepairRequestRepository
import ar.edu.unaj.fixit.core.domain.testing.TestUserRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateRequestUseCaseTest {
    private val auth = TestAuthRepository()
    private val users = TestUserRepository()
    private val requests = TestRepairRequestRepository()
    private val createRequest = CreateRequestUseCase(auth, users, requests)

    private val client = TestData.client()

    private fun signInAs(user: User) {
        users.upsert(user)
        auth.signedInAs(user)
    }

    private fun assertRejected(expected: ValidationError, result: AppResult<RepairRequest>) {
        assertEquals(AppResult.Error(DomainError.Validation(expected)), result)
        assertTrue("no request may be created", requests.createCalls.isEmpty())
    }

    @Test
    fun `given a client and valid data when publishing then an OPEN request without professional is created`() = runTest {
        signInAs(client)
        val input = TestData.newRequest(category = ServiceCategory.PLUMBING, description = "d".repeat(40))

        val result = createRequest(input)

        val created = (result as AppResult.Success).data
        assertEquals(RequestStatus.OPEN, created.status)
        assertEquals(client.id, created.clientId)
        assertNull(created.professionalId)
        assertEquals(created, requests.request(created.id))
    }

    @Test
    fun `given a professional when publishing then the result is Unauthorized and nothing is created`() = runTest {
        signInAs(TestData.professional())

        val result = createRequest(TestData.newRequest())

        assertEquals(AppResult.Error(DomainError.Unauthorized), result)
        assertTrue(requests.createCalls.isEmpty())
    }

    @Test
    fun `given a professional and an invalid request when publishing then authorization wins over validation`() = runTest {
        signInAs(TestData.professional())

        val result = createRequest(TestData.newRequest(category = null))

        assertEquals(AppResult.Error(DomainError.Unauthorized), result)
    }

    @Test
    fun `given no session when publishing then the result is Unauthorized`() = runTest {
        val result = createRequest(TestData.newRequest())

        assertEquals(AppResult.Error(DomainError.Unauthorized), result)
        assertTrue(requests.createCalls.isEmpty())
    }

    @Test
    fun `given no professional nearby when publishing then the request is still created`() = runTest {
        signInAs(client)

        val result = createRequest(TestData.newRequest())

        assertTrue(result is AppResult.Success)
        assertEquals(RequestStatus.OPEN, requests.requestsSnapshot().single().status)
    }

    @Test
    fun `given five OPEN requests when publishing a sixth then it is created`() = runTest {
        signInAs(client)
        requests.setRequests(List(5) { TestData.request(id = "r$it", clientId = client.id) })

        val result = createRequest(TestData.newRequest())

        assertTrue(result is AppResult.Success)
        assertEquals(6, requests.requestsSnapshot().size)
    }

    @Test
    fun `given a padded description when publishing then the trimmed description is persisted`() = runTest {
        signInAs(client)

        createRequest(TestData.newRequest(description = "  Leaking tap in the kitchen \n"))

        assertEquals("Leaking tap in the kitchen", requests.createCalls.single().second.description)
    }

    @Test
    fun `given a description of exactly 10 characters after trimming when publishing then it is accepted`() = runTest {
        signInAs(client)

        val result = createRequest(TestData.newRequest(description = "  0123456789  "))

        assertTrue(result is AppResult.Success)
    }

    @Test
    fun `given no category and an empty description when publishing then only CategoryRequired is reported`() = runTest {
        signInAs(client)

        assertRejected(
            ValidationError.CATEGORY_REQUIRED,
            createRequest(TestData.newRequest(category = null, description = "   ")),
        )
    }

    @Test
    fun `given an empty description when publishing then the result is EmptyDescription`() = runTest {
        signInAs(client)

        assertRejected(ValidationError.EMPTY_DESCRIPTION, createRequest(TestData.newRequest(description = "   ")))
    }

    @Test
    fun `given descriptions of 9 and 501 characters when publishing then the results are TooShort and TooLong`() = runTest {
        signInAs(client)

        assertRejected(ValidationError.DESCRIPTION_TOO_SHORT, createRequest(TestData.newRequest(description = "x".repeat(9))))
        assertRejected(ValidationError.DESCRIPTION_TOO_LONG, createRequest(TestData.newRequest(description = "x".repeat(501))))
    }

    @Test
    fun `given 0 and 4 photos when publishing then the results are PhotosRequired and TooManyPhotos`() = runTest {
        signInAs(client)

        assertRejected(ValidationError.PHOTOS_REQUIRED, createRequest(TestData.newRequest(photoUris = emptyList())))
        assertRejected(
            ValidationError.TOO_MANY_PHOTOS,
            createRequest(TestData.newRequest(photoUris = listOf("a", "b", "c", "d"))),
        )
    }

    @Test
    fun `given no place or an empty address when publishing then the result is LocationRequired`() = runTest {
        signInAs(client)

        assertRejected(ValidationError.LOCATION_REQUIRED, createRequest(TestData.newRequest(place = null)))
        assertRejected(
            ValidationError.LOCATION_REQUIRED,
            createRequest(TestData.newRequest(place = Place(GeoPoint(-34.8, -58.2), ""))),
        )
    }

    @Test
    fun `given a latitude of 91 when publishing then the result is InvalidCoordinates`() = runTest {
        signInAs(client)

        assertRejected(
            ValidationError.INVALID_COORDINATES,
            createRequest(TestData.newRequest(place = Place(GeoPoint(91.0, 0.0), "Nowhere"))),
        )
    }

    @Test
    fun `given a repository failure when publishing then the error is returned unchanged`() = runTest {
        signInAs(client)
        requests.createError = DomainError.Network

        val result = createRequest(TestData.newRequest())

        assertEquals(AppResult.Error(DomainError.Network), result)
        assertTrue(requests.requestsSnapshot().isEmpty())
    }
}
