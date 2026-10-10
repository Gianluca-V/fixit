package ar.edu.unaj.fixit.core.domain.testing

import ar.edu.unaj.fixit.core.domain.model.ProfileForm
import ar.edu.unaj.fixit.core.domain.model.User
import ar.edu.unaj.fixit.core.domain.repository.UserRepository
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Hand-written UserRepository double over a configurable, observable list of users.
 * Error hooks are one-shot: each is consumed by the NEXT call of its operation only
 * (`observeError` by the next observe, whichever it is).
 */
class TestUserRepository(initial: List<User> = emptyList()) : UserRepository {
    private val users = MutableStateFlow(initial)

    var updateProfileError: DomainError? = null
    var observeError: DomainError? = null

    val updateProfileCalls = mutableListOf<Pair<String, ProfileForm>>()

    fun setUsers(list: List<User>) {
        users.value = list
    }

    /** Adds or replaces a user (by id); observers re-emit. */
    fun upsert(user: User) {
        users.value = users.value.filterNot { it.id == user.id } + user
    }

    fun user(userId: String): User? = users.value.firstOrNull { it.id == userId }

    override suspend fun get(userId: String): AppResult<User> = find(userId)

    override fun observe(userId: String): Flow<AppResult<User>> {
        ::observeError.take()?.let { return flowOf(AppResult.Error(it)) }
        return users.map { find(userId) }
    }

    /** Applies name and phone (and the professional fields when the user is a professional); never email, role or reputation. */
    override suspend fun updateProfile(userId: String, form: ProfileForm): AppResult<User> {
        updateProfileCalls += userId to form
        ::updateProfileError.take()?.let { return AppResult.Error(it) }
        val current = user(userId) ?: return AppResult.Error(DomainError.NotFound)
        val updated = current.copy(
            name = form.name,
            phone = form.phone,
            professional = current.professional?.copy(
                categories = form.categories,
                base = form.base ?: current.professional.base,
                radiusKm = form.radiusKm ?: current.professional.radiusKm,
            ),
        )
        upsert(updated)
        return AppResult.Success(updated)
    }

    private fun find(userId: String): AppResult<User> =
        user(userId)?.let { AppResult.Success(it) } ?: AppResult.Error(DomainError.NotFound)
}
