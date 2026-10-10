package ar.edu.unaj.fixit.core.data.fake

import ar.edu.unaj.fixit.core.domain.model.RepairRequest
import ar.edu.unaj.fixit.core.domain.model.Review
import ar.edu.unaj.fixit.core.domain.model.User
import ar.edu.unaj.fixit.core.domain.repository.Clock
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The whole Phase 1 database: data, test passwords (by user id) and the session, in ONE immutable value. */
data class DbState(
    val users: List<User>,
    val requests: List<RepairRequest>,
    val reviews: List<Review>,
    val passwords: Map<String, String>,
    val currentUserId: String?,
) {
    companion object {
        val EMPTY = DbState(emptyList(), emptyList(), emptyList(), emptyMap(), null)
    }
}

/** What a write decided, computed by a PURE function of the old state. */
sealed interface WriteDecision<out T> {
    data class Commit<T>(val state: DbState, val result: T) : WriteDecision<T>

    data class Reject(val error: DomainError) : WriteDecision<Nothing>
}

/**
 * In-memory database of the Phase 1 Fakes (D10): a single [MutableStateFlow] of [DbState].
 *
 * Every DATA write (sign up, update profile, create, accept, cancel, complete) goes through [write]:
 * one update of the whole state under a [Mutex], so request, review and reputation change together
 * or not at all. Signing in and out only touch `currentUserId` through an atomic `update` and take
 * no lock. Dates come from the injected [Clock].
 */
@Singleton
class FakeDatabase(private val clock: Clock, initial: DbState) {
    /** The app database: seeded sample data relative to the current time. */
    @Inject
    constructor(clock: Clock) : this(clock, SeedData.initialState(clock.now()))

    private val mutableState = MutableStateFlow(initial)
    private val mutex = Mutex()
    private val idCounter = AtomicLong()

    @Volatile
    private var failNext: DomainError? = null

    val state: StateFlow<DbState> = mutableState.asStateFlow()

    /**
     * Test hook: the next write that passes validation returns [error] without changing the state,
     * and the hook is consumed. Sign in, sign out and writes rejected by validation or
     * authorization do not consume it.
     */
    fun failNextWrite(error: DomainError) {
        failNext = error
    }

    /** Unique id; the underscore keeps generated ids apart from the seeded ones (c1, p1, r1...). */
    fun newId(prefix: String): String = "${prefix}_${idCounter.incrementAndGet()}"

    fun signIn(userId: String) {
        mutableState.update { it.copy(currentUserId = userId) }
    }

    fun signOut() {
        mutableState.update { it.copy(currentUserId = null) }
    }

    /**
     * Runs [decide] (a PURE function: `update` may retry it) under the mutex and applies its decision.
     * The hook is read before and consumed after deciding that the write proceeds.
     */
    suspend fun <T> write(decide: (old: DbState, now: Long) -> WriteDecision<T>): AppResult<T> =
        mutex.withLock {
            val now = clock.now()
            val armedError = failNext
            var outcome: AppResult<T>? = null
            var passedValidation = false
            mutableState.update { old ->
                when (val decision = decide(old, now)) {
                    is WriteDecision.Reject -> {
                        outcome = AppResult.Error(decision.error)
                        passedValidation = false
                        old
                    }
                    is WriteDecision.Commit -> {
                        passedValidation = true
                        if (armedError != null) {
                            outcome = AppResult.Error(armedError)
                            old
                        } else {
                            outcome = AppResult.Success(decision.result)
                            decision.state
                        }
                    }
                }
            }
            if (passedValidation && armedError != null) failNext = null
            checkNotNull(outcome)
        }
}
