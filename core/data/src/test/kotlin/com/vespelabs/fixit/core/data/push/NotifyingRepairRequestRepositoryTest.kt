package com.vespelabs.fixit.core.data.push

import com.vespelabs.fixit.core.data.fake.FakeDatabase
import com.vespelabs.fixit.core.data.fake.FakeRepairRequestRepository
import com.vespelabs.fixit.core.data.fake.SeedData
import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.repository.RepairRequestRepository
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.testing.TestClock
import com.vespelabs.fixit.core.domain.testing.TestData
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Records every request to the Worker. */
internal class RecordingWorkerClient : WorkerClient {
    val calls = mutableListOf<Pair<String, PushEvent>>()

    override suspend fun send(idToken: String, event: PushEvent) {
        calls += idToken to event
    }
}

internal class FixedIdTokenProvider(private val token: String?) : IdTokenProvider {
    override suspend fun idToken(): String? = token
}

/** Fails every request with [error] (an HTTP error surfacing as an exception, or a network failure). */
internal class ThrowingWorkerClient(private val error: Exception) : WorkerClient {
    var calls = 0

    override suspend fun send(idToken: String, event: PushEvent) {
        calls++
        throw error
    }
}

/** Never answers; records whether the request was dropped (cancelled). */
internal class HangingWorkerClient : WorkerClient {
    var calls = 0
    var dropped = false

    override suspend fun send(idToken: String, event: PushEvent) {
        calls++
        try {
            awaitCancellation()
        } finally {
            dropped = true
        }
    }
}

/** The real repository whose get() never answers (a slow pre-read). */
internal class HangingGetRepository(delegate: RepairRequestRepository) : RepairRequestRepository by delegate {
    override suspend fun get(requestId: String): AppResult<RepairRequest> = awaitCancellation()
}

/**
 * Design 14b.3: (a) which operations request a push and (b) best effort (never changes the result, bounded waits). The real repository is the Phase 1 Fake over a
 * FakeDatabase seeded with SeedData (r1 and r2 OPEN of c1, r4 ACCEPTED by p3 for c2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotifyingRepairRequestRepositoryTest {
    private val clock = TestClock(1_800_000_000_000L)
    private val db = FakeDatabase(clock, SeedData.initialState(clock.now()))
    private val real = FakeRepairRequestRepository(db)
    private val worker = RecordingWorkerClient()

    private fun TestScope.decorated(
        tokens: IdTokenProvider = FixedIdTokenProvider("id-token"),
        delegate: RepairRequestRepository = real,
    ) = NotifyingRepairRequestRepository(delegate, worker, tokens, backgroundScope)

    private fun AppResult<RepairRequest>.data() = (this as AppResult.Success).data

    @Test
    fun `Crear solicita el envio una vez - one NEW_REQUEST with the requestId and the ID token`() = runTest {
        val created = decorated().create("c1", TestData.newRequest()).data()
        runCurrent()

        assertEquals(listOf("id-token" to PushEvent(PushEventType.NEW_REQUEST, created.id)), worker.calls)
    }

    @Test
    fun `Creacion fallida no envia aviso - a create rejected by the real repository requests nothing`() = runTest {
        val result = decorated().create("no-such-client", TestData.newRequest())
        runCurrent()

        assertEquals(AppResult.Error(DomainError.NotFound), result)
        assertEquals(emptyList<Pair<String, PushEvent>>(), worker.calls)
    }

    @Test
    fun `Aceptar solicita el envio una vez - one REQUEST_ACCEPTED with the requestId and the ID token`() = runTest {
        val accepted = decorated().accept("r1", "p1").data()
        runCurrent()

        assertEquals(RequestStatus.ACCEPTED, accepted.status)
        assertEquals(listOf("id-token" to PushEvent(PushEventType.REQUEST_ACCEPTED, "r1")), worker.calls)
    }

    @Test
    fun `Aceptacion rechazada no envia aviso - AlreadyTaken requests nothing`() = runTest {
        val result = decorated().accept("r4", "p1") // r4 is already ACCEPTED by p3
        runCurrent()

        assertEquals(AppResult.Error(DomainError.AlreadyTaken), result)
        assertEquals(emptyList<Pair<String, PushEvent>>(), worker.calls)
    }

    @Test
    fun `Aceptacion repetida no duplica el aviso - an idempotent accept by the same professional requests nothing`() = runTest {
        val result = decorated().accept("r4", "p3")
        runCurrent()

        assertEquals(RequestStatus.ACCEPTED, result.data().status)
        assertEquals(emptyList<Pair<String, PushEvent>>(), worker.calls)
    }

    @Test
    fun `Cancelar un ACCEPTED solicita el envio - one REQUEST_CANCELLED for a request accepted by p1`() = runTest {
        real.accept("r1", "p1") // straight to the real repository: no push for the setup
        val cancelled = decorated().cancel("r1").data()
        runCurrent()

        assertEquals(RequestStatus.CANCELLED, cancelled.status)
        assertEquals("p1", cancelled.professionalId)
        assertEquals(listOf("id-token" to PushEvent(PushEventType.REQUEST_CANCELLED, "r1")), worker.calls)
    }

    @Test
    fun `Cancelar un OPEN no envia aviso - no professional assigned, no request`() = runTest {
        val cancelled = decorated().cancel("r2").data()
        runCurrent()

        assertEquals(RequestStatus.CANCELLED, cancelled.status)
        assertEquals(emptyList<Pair<String, PushEvent>>(), worker.calls)
    }

    @Test
    fun `Cancelacion rechazada no envia aviso - an Unauthorized cancel requests nothing`() = runTest {
        db.failNextWrite(DomainError.Unauthorized)

        val result = decorated().cancel("r4")
        runCurrent()

        assertEquals(AppResult.Error(DomainError.Unauthorized), result)
        assertEquals(emptyList<Pair<String, PushEvent>>(), worker.calls)
    }

    @Test
    fun `given failNextWrite when creating or accepting then the error is returned and nothing is requested`() = runTest {
        val repository = decorated()
        db.failNextWrite(DomainError.Network)
        val create = repository.create("c1", TestData.newRequest())
        db.failNextWrite(DomainError.Network)
        val accept = repository.accept("r1", "p1")
        runCurrent()

        assertEquals(AppResult.Error(DomainError.Network), create)
        assertEquals(AppResult.Error(DomainError.Network), accept)
        assertEquals(emptyList<Pair<String, PushEvent>>(), worker.calls)
    }

    @Test
    fun `given no ID token when an operation succeeds then nothing is requested and the result is unchanged`() = runTest {
        val repository = decorated(tokens = FixedIdTokenProvider(null))

        val accepted = repository.accept("r1", "p1")
        runCurrent()

        assertEquals(RequestStatus.ACCEPTED, accepted.data().status)
        assertEquals(emptyList<Pair<String, PushEvent>>(), worker.calls)
    }

    @Test
    fun `given get observe and completeWithReview when called then they delegate without requests`() = runTest {
        val repository = decorated()

        assertEquals(real.get("r1"), repository.get("r1"))
        assertEquals(real.observe("r1").first(), repository.observe("r1").first())
        assertEquals(real.observeByClient("c1").first(), repository.observeByClient("c1").first())
        assertEquals(real.observeByProfessional("p3").first(), repository.observeByProfessional("p3").first())
        val center = TestData.florencioVarela.point
        assertEquals(real.observeOpenNear(center, 10).first(), repository.observeOpenNear(center, 10).first())
        val review = (repository.completeWithReview("r4", 5, "Great") as AppResult.Success<*>).data
        assertEquals(AppResult.Success(review), repository.observeReview("r4").first())
        assertEquals(RequestStatus.COMPLETED, real.get("r4").data().status)
        runCurrent()

        assertEquals(emptyList<Pair<String, PushEvent>>(), worker.calls)
    }

    // region (b) best effort

    @Test
    fun `Lectura previa lenta no bloquea la aceptacion - Success after at most 2 virtual seconds and exactly 1 request`() = runTest {
        val repository = decorated(delegate = HangingGetRepository(real))
        val start = currentTime

        val result = repository.accept("r1", "p1")
        val waited = currentTime - start
        runCurrent()

        assertEquals(RequestStatus.ACCEPTED, result.data().status)
        assertTrue("waited $waited ms", waited <= PRE_READ_TIMEOUT_MS)
        assertEquals(listOf("id-token" to PushEvent(PushEventType.REQUEST_ACCEPTED, "r1")), worker.calls)
    }

    @Test
    fun `Falla del Worker al crear - a Worker error still returns Success with the created request`() = runTest {
        val failing = ThrowingWorkerClient(IllegalStateException("HTTP 500"))
        val repository = NotifyingRepairRequestRepository(real, failing, FixedIdTokenProvider("id-token"), backgroundScope)

        val created = repository.create("c1", TestData.newRequest())
        runCurrent()

        assertEquals(created, real.get(created.data().id))
        assertEquals(1, failing.calls)
    }

    @Test
    fun `Falla del Worker al aceptar - a network exception still returns Success and the request is ACCEPTED`() = runTest {
        val failing = ThrowingWorkerClient(IOException("network down"))
        val repository = NotifyingRepairRequestRepository(real, failing, FixedIdTokenProvider("id-token"), backgroundScope)

        val result = repository.accept("r1", "p1")
        runCurrent()

        assertEquals(RequestStatus.ACCEPTED, result.data().status)
        assertEquals(RequestStatus.ACCEPTED, real.get("r1").data().status)
        assertEquals(1, failing.calls)
    }

    @Test
    fun `Falla del Worker al cancelar - a Worker that never answers still returns Success and the request is CANCELLED`() = runTest {
        val hanging = HangingWorkerClient()
        val repository = NotifyingRepairRequestRepository(real, hanging, FixedIdTokenProvider("id-token"), backgroundScope)

        val result = repository.cancel("r4")
        runCurrent()

        assertEquals(RequestStatus.CANCELLED, result.data().status)
        assertEquals(RequestStatus.CANCELLED, real.get("r4").data().status)
        assertEquals(1, hanging.calls)
    }

    @Test
    fun `Tiempo de espera del Worker - the result was already Success and the request is dropped at 5 s without retries`() = runTest {
        val hanging = HangingWorkerClient()
        val repository = NotifyingRepairRequestRepository(real, hanging, FixedIdTokenProvider("id-token"), backgroundScope)
        val start = currentTime

        val result = repository.create("c1", TestData.newRequest())
        assertEquals(start, currentTime) // the caller did not wait for the Worker
        assertTrue(result is AppResult.Success)
        advanceTimeBy(PUSH_TIMEOUT_MS - 1)
        runCurrent()
        assertFalse(hanging.dropped)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(hanging.dropped)
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(1, hanging.calls)
    }

    @Test
    fun `Sin Worker configurado - a blank URL returns the same undecorated instance and requests nothing`() = runTest {
        val repository = withPush(real, workerUrl = " ", worker, FixedIdTokenProvider("id-token"), backgroundScope)

        assertSame(real, repository)
        val created = repository.create("c1", TestData.newRequest()).data()
        repository.accept(created.id, "p1")
        repository.cancel(created.id)
        runCurrent()
        assertEquals(RequestStatus.CANCELLED, real.get(created.id).data().status)
        assertEquals(emptyList<Pair<String, PushEvent>>(), worker.calls)
    }

    @Test
    fun `given a Worker URL when building the repository then it is the push decorator`() = runTest {
        val repository = withPush(real, "https://push.example.workers.dev", worker, FixedIdTokenProvider("t"), backgroundScope)

        assertTrue(repository is NotifyingRepairRequestRepository)
    }

    // endregion
}
