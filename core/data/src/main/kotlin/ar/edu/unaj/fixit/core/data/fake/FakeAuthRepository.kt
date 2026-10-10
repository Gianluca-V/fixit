package ar.edu.unaj.fixit.core.data.fake

import ar.edu.unaj.fixit.core.domain.model.ProfessionalInfo
import ar.edu.unaj.fixit.core.domain.model.SessionState
import ar.edu.unaj.fixit.core.domain.model.SignUpForm
import ar.edu.unaj.fixit.core.domain.model.User
import ar.edu.unaj.fixit.core.domain.model.UserRole
import ar.edu.unaj.fixit.core.domain.repository.AuthRepository
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.result.ValidationError
import ar.edu.unaj.fixit.core.domain.rules.Validators
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Phase 1 authentication over [FakeDatabase]. Emails are normalized when stored and when compared
 * (like Firebase Auth, which ignores case), so EmailAlreadyInUse behaves the same in every phase.
 * The session lives in the process only: a new database starts signed out.
 */
class FakeAuthRepository @Inject constructor(
    private val db: FakeDatabase,
) : AuthRepository {
    /** Never fails and never emits Loading in Phase 1. */
    override val session: Flow<SessionState> = db.observe { state ->
        state.users.firstOrNull { it.id == state.currentUserId }
            ?.let<User, SessionState> { SessionState.SignedIn(it) }
            ?: SessionState.SignedOut
    }

    override fun currentUserId(): String? = db.state.value.currentUserId

    override suspend fun signIn(email: String, password: String): AppResult<User> {
        val state = db.state.value
        val normalized = Validators.normalizeEmail(email)
        val user = state.users.firstOrNull { Validators.normalizeEmail(it.email) == normalized }
        if (user == null || state.passwords[user.id] != password) {
            return AppResult.Error(DomainError.InvalidCredentials)
        }
        db.signIn(user.id)
        return AppResult.Success(user)
    }

    override suspend fun signUp(form: SignUpForm): AppResult<User> {
        val role = form.role ?: return validationError(ValidationError.ROLE_REQUIRED)
        val professional = if (role == UserRole.PROFESSIONAL) {
            ProfessionalInfo(
                categories = form.profile.categories,
                base = form.profile.base ?: return validationError(ValidationError.BASE_LOCATION_REQUIRED),
                radiusKm = form.profile.radiusKm ?: return validationError(ValidationError.INVALID_RADIUS),
            )
        } else {
            null
        }
        val user = User(
            id = db.newId("u"),
            name = form.profile.name,
            email = Validators.normalizeEmail(form.email),
            phone = form.profile.phone,
            role = role,
            professional = professional,
        )
        return db.write { old, _ ->
            if (old.users.any { Validators.normalizeEmail(it.email) == user.email }) {
                WriteDecision.Reject(DomainError.EmailAlreadyInUse)
            } else {
                WriteDecision.Commit(
                    old.copy(
                        users = old.users + user,
                        passwords = old.passwords + (user.id to form.password),
                        currentUserId = user.id,
                    ),
                    user,
                )
            }
        }
    }

    override fun signOut() = db.signOut()

    private fun validationError(error: ValidationError) = AppResult.Error(DomainError.Validation(error))
}
