package ar.edu.unaj.fixit.core.domain.usecase

import ar.edu.unaj.fixit.core.domain.model.SignUpForm
import ar.edu.unaj.fixit.core.domain.model.User
import ar.edu.unaj.fixit.core.domain.repository.AuthRepository
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.rules.Validators
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
