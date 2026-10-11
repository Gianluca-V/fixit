package com.vespelabs.fixit.core.domain.repository

import com.vespelabs.fixit.core.domain.model.SessionState
import com.vespelabs.fixit.core.domain.model.SignUpForm
import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Never fails: a data-layer error ends as SignedOut. */
    val session: Flow<SessionState>

    /** Only the id: use cases read fresh data with UserRepository.get. */
    fun currentUserId(): String?

    suspend fun signIn(email: String, password: String): AppResult<User>

    suspend fun signUp(form: SignUpForm): AppResult<User>

    /** Plain fun returning Unit (documented exception to the AppResult rule). */
    fun signOut()
}
