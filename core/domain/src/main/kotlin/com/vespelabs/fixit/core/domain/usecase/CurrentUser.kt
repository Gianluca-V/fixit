package com.vespelabs.fixit.core.domain.usecase

import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.repository.AuthRepository
import com.vespelabs.fixit.core.domain.repository.UserRepository
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError

/**
 * Fresh read of the signed-in user (D19): only the id comes from the session, everything else
 * (role, categories) is read again. No session means Unauthorized.
 */
internal suspend fun readCurrentUser(auth: AuthRepository, users: UserRepository): AppResult<User> {
    val userId = auth.currentUserId() ?: return AppResult.Error(DomainError.Unauthorized)
    return users.get(userId)
}
