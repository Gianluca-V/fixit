package ar.edu.unaj.fixit.core.domain.testing

import ar.edu.unaj.fixit.core.domain.model.ProfessionalInfo
import ar.edu.unaj.fixit.core.domain.model.SessionState
import ar.edu.unaj.fixit.core.domain.model.SignUpForm
import ar.edu.unaj.fixit.core.domain.model.User
import ar.edu.unaj.fixit.core.domain.model.UserRole
import ar.edu.unaj.fixit.core.domain.repository.AuthRepository
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hand-written AuthRepository double. `session` is backed by a MutableStateFlow the test controls
 * (setSession). Every call is recorded; the error hooks fire once, on the NEXT call of their operation.
 */
class TestAuthRepository(initial: SessionState = SessionState.SignedOut) : AuthRepository {
    private val state = MutableStateFlow(initial)

    override val session: Flow<SessionState> = state

    /** One-shot hooks. */
    var signInError: DomainError? = null
    var signUpError: DomainError? = null

    /** User returned by a successful signIn; null means InvalidCredentials. */
    var signInUser: User? = null

    val signInCalls = mutableListOf<Pair<String, String>>()
    val signUpCalls = mutableListOf<SignUpForm>()
    var signOutCalls = 0
        private set

    fun setSession(session: SessionState) {
        state.value = session
    }

    fun signedInAs(user: User) = setSession(SessionState.SignedIn(user))

    override fun currentUserId(): String? = (state.value as? SessionState.SignedIn)?.user?.id

    override suspend fun signIn(email: String, password: String): AppResult<User> {
        signInCalls += email to password
        ::signInError.take()?.let { return AppResult.Error(it) }
        val user = signInUser ?: return AppResult.Error(DomainError.InvalidCredentials)
        state.value = SessionState.SignedIn(user)
        return AppResult.Success(user)
    }

    /** Builds the user from the form, the way a real repository would persist it. */
    override suspend fun signUp(form: SignUpForm): AppResult<User> {
        signUpCalls += form
        ::signUpError.take()?.let { return AppResult.Error(it) }
        val role = checkNotNull(form.role) { "TestAuthRepository.signUp needs a validated form" }
        val user = User(
            id = "new-user",
            name = form.profile.name,
            email = form.email,
            phone = form.profile.phone,
            role = role,
            professional = if (role == UserRole.PROFESSIONAL) {
                ProfessionalInfo(
                    categories = form.profile.categories,
                    base = checkNotNull(form.profile.base),
                    radiusKm = checkNotNull(form.profile.radiusKm),
                )
            } else {
                null
            },
        )
        state.value = SessionState.SignedIn(user)
        return AppResult.Success(user)
    }

    override fun signOut() {
        signOutCalls++
        state.value = SessionState.SignedOut
    }
}
