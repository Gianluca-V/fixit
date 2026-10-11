package com.vespelabs.fixit.core.domain.usecase

import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.repository.AuthRepository
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.rules.Validators
import javax.inject.Inject

class SignInUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String): AppResult<User> {
        val normalizedEmail = Validators.normalizeEmail(email)
        Validators.signIn(normalizedEmail, password)?.let {
            return AppResult.Error(DomainError.Validation(it))
        }
        return authRepository.signIn(normalizedEmail, password)
    }
}
