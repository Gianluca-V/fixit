package ar.edu.unaj.fixit.core.data.fake

import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.testing.TestClock
import ar.edu.unaj.fixit.core.domain.testing.TestData
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeDatabaseTest {
    private val clock = TestClock(1_000L)
    private val ana = TestData.client(id = "c1")
    private val seeded = DbState.EMPTY.copy(users = listOf(ana))
    private val db = FakeDatabase(clock, seeded)

    private fun addClient(id: String): (DbState, Long) -> WriteDecision<String> = { old, _ ->
        WriteDecision.Commit(old.copy(users = old.users + TestData.client(id = id)), id)
    }

    private val rejectUnauthorized: (DbState, Long) -> WriteDecision<Nothing> = { _, _ ->
        WriteDecision.Reject(DomainError.Unauthorized)
    }

    @Test
    fun `given a committing write when writing then the single state holds the new state and the result is Success`() = runTest {
        val result = db.write(addClient("c2"))

        assertEquals(AppResult.Success("c2"), result)
        assertEquals(listOf("c1", "c2"), db.state.value.users.map { it.id })
    }

    @Test
    fun `given a committing write when observing the state then exactly one new value is emitted per write`() = runTest {
        val emissions = mutableListOf<DbState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { db.state.toList(emissions) }

        db.write(addClient("c2"))
        db.write(addClient("c3"))

        assertEquals(3, emissions.size)
    }

    @Test
    fun `given a rejecting write when writing then the error is returned and the state does not change`() = runTest {
        val result = db.write(rejectUnauthorized)

        assertEquals(AppResult.Error(DomainError.Unauthorized), result)
        assertEquals(seeded, db.state.value)
    }

    @Test
    fun `given a write when it runs then it receives the clock time of that moment`() = runTest {
        clock.time = 5_000L
        var seen = -1L

        db.write { old, now ->
            seen = now
            WriteDecision.Commit(old, Unit)
        }

        assertEquals(5_000L, seen)
    }

    @Test
    fun `given sign in and sign out when called then they only change the current user id`() {
        db.signIn("c1")
        assertEquals(seeded.copy(currentUserId = "c1"), db.state.value)

        db.signOut()
        assertEquals(seeded, db.state.value)
        assertNull(db.state.value.currentUserId)
    }

    @Test
    fun `given concurrent writes and concurrent sign ins when they finish then no write and no session change is lost`() = runTest {
        withContext(Dispatchers.Default) {
            val writes = List(200) { i -> async { db.write(addClient("u$i")) } }
            val toggles = launch {
                repeat(2_000) {
                    db.signIn("c1")
                    db.signOut()
                }
            }
            writes.awaitAll()
            toggles.join()
        }

        assertEquals(201, db.state.value.users.size)
        assertEquals(201, db.state.value.users.map { it.id }.toSet().size)
        assertNull(db.state.value.currentUserId)
    }

    @Test
    fun `given many concurrent writes when they run then their decisions never overlap`() = runTest {
        val running = AtomicInteger(0)
        val maxRunning = AtomicInteger(0)

        withContext(Dispatchers.Default) {
            List(50) { i ->
                async {
                    db.write { old, _ ->
                        maxRunning.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                        Thread.sleep(1)
                        running.decrementAndGet()
                        WriteDecision.Commit(old.copy(users = old.users + TestData.client(id = "w$i")), Unit)
                    }
                }
            }.awaitAll()
        }

        assertEquals(1, maxRunning.get())
        assertEquals(51, db.state.value.users.size)
    }

    @Test
    fun `given generated ids when asking concurrently then they are unique and keep the prefix`() = runTest {
        val ids = withContext(Dispatchers.Default) { List(100) { async { db.newId("r") } }.awaitAll() }

        assertEquals(100, ids.toSet().size)
        assertTrue(ids.all { it.startsWith("r") })
    }

    @Test
    fun `given failNextWrite when the next write passes validation then it returns that error and the state does not change`() = runTest {
        db.failNextWrite(DomainError.Network)

        val result = db.write(addClient("c2"))

        assertEquals(AppResult.Error(DomainError.Network), result)
        assertEquals(seeded, db.state.value)
    }

    @Test
    fun `given failNextWrite when a write has failed then the following write succeeds because the hook was consumed`() = runTest {
        db.failNextWrite(DomainError.Network)
        db.write(addClient("c2"))

        val second = db.write(addClient("c2"))

        assertEquals(AppResult.Success("c2"), second)
        assertEquals(2, db.state.value.users.size)
    }

    @Test
    fun `given failNextWrite when signing in and out and a write is rejected then the hook is not consumed`() = runTest {
        db.failNextWrite(DomainError.Network)

        db.signIn("c1")
        db.signOut()
        val rejected = db.write(rejectUnauthorized)
        val firstCommitting = db.write(addClient("c2"))

        assertEquals(AppResult.Error(DomainError.Unauthorized), rejected)
        assertEquals(AppResult.Error(DomainError.Network), firstCommitting)
    }
}
