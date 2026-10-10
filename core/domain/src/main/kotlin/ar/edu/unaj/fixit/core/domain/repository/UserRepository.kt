package ar.edu.unaj.fixit.core.domain.repository

import ar.edu.unaj.fixit.core.domain.model.ProfileForm
import ar.edu.unaj.fixit.core.domain.model.User
import ar.edu.unaj.fixit.core.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    /** Fresh read for pre-checks; NotFound if missing. */
    suspend fun get(userId: String): AppResult<User>

    /** NotFound if missing. */
    fun observe(userId: String): Flow<AppResult<User>>

    /** Never touches email, role or reputation. */
    suspend fun updateProfile(userId: String, form: ProfileForm): AppResult<User>
}
