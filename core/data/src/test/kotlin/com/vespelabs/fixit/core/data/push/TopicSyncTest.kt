package com.vespelabs.fixit.core.data.push

import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.model.ServiceCategory.ELECTRICITY
import com.vespelabs.fixit.core.domain.model.ServiceCategory.GAS
import com.vespelabs.fixit.core.domain.model.ServiceCategory.PLUMBING
import com.vespelabs.fixit.core.domain.model.SessionState
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.testing.TestAuthRepository
import com.vespelabs.fixit.core.domain.testing.TestData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Models the FCM side: the set of topics the device is really subscribed to. Every completed operation is logged in
 * [log] (shared with the store and the canceller, so the order of effects can be asserted). Per topic it can fail,
 * hang until released ([hold]) or land late and report a timeout ([timingOut], like the 15 s bound in production).
 */
private class FakeTopicMessaging(private val log: MutableList<String>) : TopicMessaging {
    val subscribed = mutableSetOf<String>()
    var offline = false
    val failing = mutableSetOf<String>()
    val timingOut = mutableSetOf<String>()

    /** Operations that were started, completed or not (a cancelled one shows here but not in the log). */
    val started = mutableListOf<String>()
    private val held = MutableStateFlow<Set<String>>(emptySet())

    fun hold(topic: String) = held.update { it + topic }

    fun release(topic: String) = held.update { it - topic }

    override suspend fun subscribe(topic: String): Boolean {
        started += "subscribe $topic"
        held.first { topic !in it }
        if (topic in timingOut) {
            delay(15_000)
            subscribed += topic // it reached FCM, but too late: reported as failed
            log += "subscribe $topic timeout"
            return false
        }
        val ok = !offline && topic !in failing
        if (ok) subscribed += topic
        log += "subscribe $topic ${if (ok) "ok" else "failed"}"
        return ok
    }

    override suspend fun unsubscribe(topic: String): Boolean {
        started += "unsubscribe $topic"
        val ok = !offline && topic !in failing
        if (ok) subscribed -= topic
        log += "unsubscribe $topic ${if (ok) "ok" else "failed"}"
        return ok
    }
}

private class InMemoryTopicStore(private val log: MutableList<String>) : TopicStore {
    private val topics = linkedSetOf<String>()

    override fun userTopics(): Set<String> = topics.toSet()

    override fun addUserTopic(topic: String) {
        topics += topic
        log += "store add $topic"
    }

    override fun removeUserTopic(topic: String) {
        topics -= topic
        log += "store remove $topic"
    }
}

private class RecordingNoticeCanceller(private val log: MutableList<String>) : NoticeCanceller {
    var calls = 0

    override fun cancelAll() {
        calls++
        log += "cancelAll"
    }
}

/** Design 14b.4 (revised 2026-10-26), spec "Ciclo de vida de las suscripciones". Virtual time only. */
@OptIn(ExperimentalCoroutinesApi::class)
class TopicSyncTest {
    private val log = mutableListOf<String>()
    private val messaging = FakeTopicMessaging(log)
    private val store = InMemoryTopicStore(log)
    private val canceller = RecordingNoticeCanceller(log)
    private val allCategoryTopics = ServiceCategory.entries.map { "category_${it.name}" }.toSet()

    private fun pro(vararg categories: ServiceCategory, radiusKm: Int = 10, name: String = "Carlos Gómez") =
        TestData.professional(id = "p1", categories = categories.toSet(), radiusKm = radiusKm, name = name)

    /** Starts a TopicSync in [scope] (the test's backgroundScope by default) and runs what is due now. */
    private fun TestScope.startSync(
        auth: TestAuthRepository,
        scope: CoroutineScope = backgroundScope,
    ): TopicSync = TopicSync(auth, messaging, store, canceller, scope).also {
        it.start()
        runCurrent()
    }

    private fun logSince(mark: Int) = log.drop(mark)

    @Test
    fun `Suscripcion al iniciar sesion - p1 with PLUMBING ends subscribed to user_p1 and category_PLUMBING`() = runTest {
        val auth = TestAuthRepository(SessionState.SignedOut)
        startSync(auth)

        auth.signedInAs(pro(PLUMBING))
        runCurrent()

        assertEquals(setOf("user_p1", "category_PLUMBING"), messaging.subscribed)
        assertEquals(setOf("user_p1"), store.userTopics())
    }

    @Test
    fun `Edicion de categorias actualiza los temas - PLUMBING GAS to GAS ELECTRICITY`() = runTest {
        val auth = TestAuthRepository(SessionState.SignedIn(pro(PLUMBING, GAS)))
        startSync(auth)
        assertEquals(setOf("user_p1", "category_PLUMBING", "category_GAS"), messaging.subscribed)
        val mark = log.size

        auth.signedInAs(pro(GAS, ELECTRICITY))
        runCurrent()

        assertTrue(logSince(mark).contains("subscribe category_ELECTRICITY ok"))
        assertTrue(logSince(mark).contains("unsubscribe category_PLUMBING ok"))
        assertEquals(setOf("user_p1", "category_GAS", "category_ELECTRICITY"), messaging.subscribed)
        assertEquals(setOf("user_p1"), store.userTopics())
    }

    @Test
    fun `Edicion que no cambia categorias - editing only the radius performs no operation`() = runTest {
        val auth = TestAuthRepository(SessionState.SignedIn(pro(GAS)))
        startSync(auth)
        val started = messaging.started.size

        auth.signedInAs(pro(GAS, radiusKm = 25))
        runCurrent()

        assertEquals(started, messaging.started.size)
        assertEquals(setOf("user_p1", "category_GAS"), messaging.subscribed)
    }

    @Test
    fun `Cerrar sesion desuscribe todo - signing out leaves no topic and an empty store`() = runTest {
        val auth = TestAuthRepository(SessionState.SignedIn(pro(PLUMBING, GAS)))
        startSync(auth)

        auth.signOut()
        runCurrent()

        assertEquals(emptySet<String>(), messaging.subscribed)
        assertEquals(emptySet<String>(), store.userTopics())
    }

    @Test
    fun `Falla de suscripcion no bloquea la operacion - signing in and saving categories succeed while FCM fails`() = runTest {
        messaging.offline = true
        val auth = TestAuthRepository(SessionState.SignedOut)
        startSync(auth)
        auth.signInUser = pro(GAS)

        val signIn = auth.signIn("carlos@fixit.test", "secret")
        runCurrent()
        auth.signedInAs(pro(GAS, PLUMBING)) // the profile listener re-emits after a save
        runCurrent()

        assertTrue(signIn is AppResult.Success)
        assertEquals(SessionState.SignedIn(pro(GAS, PLUMBING)), auth.session.first())
        assertEquals(emptySet<String>(), messaging.subscribed)
    }

    @Test
    fun `Operacion fallida se reintenta con la app abierta - after 30 virtual seconds without a new emission`() = runTest {
        messaging.failing += "category_GAS"
        val auth = TestAuthRepository(SessionState.SignedOut)
        startSync(auth)
        auth.signedInAs(pro(GAS, PLUMBING))
        runCurrent()
        assertFalse("category_GAS" in messaging.subscribed)

        messaging.failing.clear()
        advanceTimeBy(29_999)
        runCurrent()
        assertFalse("category_GAS" in messaging.subscribed)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(setOf("user_p1", "category_GAS", "category_PLUMBING"), messaging.subscribed)
    }

    @Test
    fun `given FCM keeps failing when retrying then the wait doubles from 30 s up to 10 min`() = runTest {
        messaging.offline = true
        startSync(TestAuthRepository(SessionState.SignedIn(pro(GAS))))
        fun attempts() = messaging.started.count { it == "subscribe user_p1" }
        // Attempts at 0, 30, 90, 210, 450 and 930 s, then every 600 s (the cap): 1530, 2130.
        val expected = listOf(
            30_000L to 2, 90_000L to 3, 210_000L to 4, 450_000L to 5, 930_000L to 6, 1_530_000L to 7, 2_130_000L to 8,
        )
        assertEquals(1, attempts())

        var now = 0L
        for ((at, count) in expected) {
            advanceTimeBy(at - now - 1)
            runCurrent()
            assertEquals("just before $at ms", count - 1, attempts())
            advanceTimeBy(1)
            runCurrent()
            assertEquals("at $at ms", count, attempts())
            now = at
        }
    }

    @Test
    fun `Desuscripcion fallida de una cuenta anterior se repara con la cuenta siguiente`() = runTest {
        val auth = TestAuthRepository(SessionState.SignedIn(TestData.client(id = "c1")))
        startSync(auth)
        messaging.offline = true
        auth.signOut()
        runCurrent()
        assertEquals(setOf("user_c1"), messaging.subscribed)
        assertEquals(setOf("user_c1"), store.userTopics())

        messaging.offline = false
        auth.signedInAs(pro(PLUMBING))
        runCurrent()

        assertEquals(setOf("user_p1", "category_PLUMBING"), messaging.subscribed)
        assertEquals(setOf("user_p1"), store.userTopics())
    }

    @Test
    fun `Desuscripcion fallida al cerrar sesion se repara al arrancar - a new TopicSync with the same store and no session`() = runTest {
        val process = Job()
        val auth = TestAuthRepository(SessionState.SignedIn(pro(GAS)))
        startSync(auth, backgroundScope + process)
        messaging.offline = true
        auth.signOut()
        runCurrent()
        assertEquals(setOf("user_p1", "category_GAS"), messaging.subscribed)
        process.cancel() // the process dies with the failed sign-out still pending

        messaging.offline = false
        startSync(TestAuthRepository(SessionState.SignedOut))

        assertEquals(emptySet<String>(), messaging.subscribed)
        assertEquals(emptySet<String>(), store.userTopics())
    }

    @Test
    fun `Cerrar sesion retira los avisos de la bandeja - cancelAll is the first action of the signed-out block`() = runTest {
        val auth = TestAuthRepository(SessionState.SignedIn(pro(GAS)))
        startSync(auth)
        assertEquals(0, canceller.calls)
        val mark = log.size

        auth.signOut()
        runCurrent()

        assertEquals(1, canceller.calls)
        assertEquals("cancelAll", logSince(mark).first())
    }

    @Test
    fun `Token nuevo con la sesion en determinacion - nothing while Loading, everything once SignedIn again`() = runTest {
        val auth = TestAuthRepository(SessionState.SignedIn(pro(GAS)))
        val sync = startSync(auth)
        assertEquals(setOf("user_p1", "category_GAS"), messaging.subscribed)
        auth.setSession(SessionState.Loading)
        runCurrent()
        val mark = log.size

        sync.resubscribe()
        runCurrent()
        assertEquals(emptyList<String>(), logSince(mark))

        auth.signedInAs(pro(GAS))
        runCurrent()

        assertTrue(logSince(mark).containsAll(listOf("subscribe user_p1 ok", "subscribe category_GAS ok")))
    }

    @Test
    fun `Cerrar sesion con operaciones pendientes cancela la bandeja de inmediato - then unsubscribes everything`() = runTest {
        messaging.hold("user_p1") // never completes
        val auth = TestAuthRepository(SessionState.SignedIn(pro(GAS)))
        startSync(auth)
        assertEquals(0, canceller.calls)

        auth.signOut()
        runCurrent()

        assertEquals(1, canceller.calls)
        assertTrue(log.containsAll((allCategoryTopics + "user_p1").map { "unsubscribe $it ok" }))
        assertEquals(emptySet<String>(), messaging.subscribed)
        assertEquals(emptySet<String>(), store.userTopics())
    }

    @Test
    fun `Suscripcion con tiempo agotado se desuscribe al cerrar sesion - and the next operation still ran`() = runTest {
        messaging.timingOut += "user_p1"
        val auth = TestAuthRepository(SessionState.SignedIn(pro(GAS)))
        startSync(auth)
        advanceTimeBy(15_000)
        runCurrent()
        val timeout = log.indexOf("subscribe user_p1 timeout")
        assertTrue(timeout >= 0)
        assertEquals("the operation after the timeout was applied", "subscribe category_GAS ok", log[timeout + 1])
        val mark = log.size

        auth.signOut()
        runCurrent()

        assertTrue(logSince(mark).containsAll(listOf("unsubscribe category_GAS ok", "unsubscribe user_p1 ok")))
        assertEquals(emptySet<String>(), messaging.subscribed)
    }

    @Test
    fun `given a Loading session when starting then no operation is performed`() = runTest {
        startSync(TestAuthRepository(SessionState.Loading))

        assertEquals(emptyList<String>(), messaging.started)
        assertEquals(0, canceller.calls)
        assertEquals(emptySet<String>(), store.userTopics())
    }

    @Test
    fun `given a re-emission that only changes the name then no operation starts and the running one is not cancelled`() = runTest {
        messaging.hold("category_GAS")
        val auth = TestAuthRepository(SessionState.SignedIn(pro(GAS)))
        startSync(auth)
        val startedBefore = messaging.started.size

        auth.signedInAs(pro(GAS, name = "Carlos G."))
        runCurrent()
        assertEquals(startedBefore, messaging.started.size)
        messaging.release("category_GAS")
        runCurrent()

        assertEquals(1, messaging.started.count { it == "subscribe category_GAS" })
        assertTrue(log.contains("subscribe category_GAS ok"))
        assertEquals(setOf("user_p1", "category_GAS"), messaging.subscribed)
    }

    @Test
    fun `given a user topic then it is stored before subscribing and removed only after a successful unsubscribe`() = runTest {
        val auth = TestAuthRepository(SessionState.SignedIn(pro(GAS)))
        startSync(auth)
        assertTrue(log.indexOf("store add user_p1") in 0 until log.indexOf("subscribe user_p1 ok"))

        messaging.failing += "user_p1"
        auth.signOut()
        runCurrent()
        assertEquals(setOf("user_p1"), store.userTopics())
        assertFalse(log.contains("store remove user_p1"))

        messaging.failing.clear()
        advanceTimeBy(30_000)
        runCurrent()

        assertTrue(log.indexOf("unsubscribe user_p1 ok") in 0 until log.indexOf("store remove user_p1"))
        assertEquals(emptySet<String>(), store.userTopics())
    }

    @Test
    fun `given a signed-in session when a new token arrives then every desired topic is subscribed again`() = runTest {
        val auth = TestAuthRepository(SessionState.SignedIn(pro(GAS)))
        val sync = startSync(auth)
        messaging.subscribed.clear() // the new token has no subscriptions yet
        val mark = log.size

        sync.resubscribe()
        runCurrent()

        assertTrue(logSince(mark).containsAll(listOf("subscribe user_p1 ok", "subscribe category_GAS ok")))
        assertEquals(0, canceller.calls)
        assertEquals(setOf("user_p1", "category_GAS"), messaging.subscribed)
    }
}
