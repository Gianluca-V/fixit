package com.vespelabs.fixit.core.domain.usecase

import com.vespelabs.fixit.core.domain.model.SignUpForm
import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.repository.AuthRepository
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.rules.Validators
import javax.inject.Inject

class SignUpUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(form: SignUpForm): AppResult<User> {
        Validators.signUp(form)?.let { return AppResult.Error(DomainError.Validation(it)) }
        // Validators.signUp guarantees the role is present.
        val role = checkNotNull(form.role)
        return authRepository.signUp(
            form.copy(email = Validators.normalizeEmail(form.email), profile = form.profile.forPersistence(role)),
        )
    }
}
