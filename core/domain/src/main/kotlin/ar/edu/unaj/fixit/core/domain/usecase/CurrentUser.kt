package ar.edu.unaj.fixit.core.domain.usecase

import ar.edu.unaj.fixit.core.domain.model.User
import ar.edu.unaj.fixit.core.domain.repository.AuthRepository
import ar.edu.unaj.fixit.core.domain.repository.UserRepository
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError

/**
 * Fresh read of the signed-in user (D19): only the id comes from the session, everything else
 * (role, categories) is read again. No session means Unauthorized.
 */
internal suspend fun readCurrentUser(auth: AuthRepository, users: UserRepository): AppResult<User> {
    val userId = auth.currentUserId() ?: return AppResult.Error(DomainError.Unauthorized)
    return users.get(userId)
}
