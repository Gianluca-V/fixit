package ar.edu.unaj.fixit.core.data.fake

import ar.edu.unaj.fixit.core.domain.model.ProfileForm
import ar.edu.unaj.fixit.core.domain.model.User
import ar.edu.unaj.fixit.core.domain.repository.UserRepository
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class FakeUserRepository @Inject constructor(
    private val db: FakeDatabase,
) : UserRepository {
    override suspend fun get(userId: String): AppResult<User> = db.state.value.findUser(userId)

    override fun observe(userId: String): Flow<AppResult<User>> = db.observe { it.findUser(userId) }

    /** Updates name, phone and, for a professional, categories, base and radius; never email, role or reputation. */
    override suspend fun updateProfile(userId: String, form: ProfileForm): AppResult<User> =
        db.write { old, _ ->
            val user = old.users.firstOrNull { it.id == userId }
                ?: return@write WriteDecision.Reject(DomainError.NotFound)
            val updated = user.copy(
                name = form.name,
                phone = form.phone,
                professional = user.professional?.let { info ->
                    info.copy(
                        categories = form.categories,
                        base = form.base ?: info.base,
                        radiusKm = form.radiusKm ?: info.radiusKm,
                    )
                },
            )
            WriteDecision.Commit(old.copy(users = old.users.map { if (it.id == userId) updated else it }), updated)
        }
}

internal fun DbState.findUser(userId: String): AppResult<User> =
    users.firstOrNull { it.id == userId }?.let { AppResult.Success(it) } ?: AppResult.Error(DomainError.NotFound)
