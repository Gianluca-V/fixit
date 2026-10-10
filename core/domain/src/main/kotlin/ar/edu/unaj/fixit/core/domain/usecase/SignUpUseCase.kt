package ar.edu.unaj.fixit.core.domain.usecase

import ar.edu.unaj.fixit.core.domain.model.ProfileForm
import ar.edu.unaj.fixit.core.domain.model.SignUpForm
import ar.edu.unaj.fixit.core.domain.model.User
import ar.edu.unaj.fixit.core.domain.model.UserRole
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
        return authRepository.signUp(form.normalized())
    }

    /** Persists the email normalized and the name and phone trimmed; clients carry no professional fields. */
    private fun SignUpForm.normalized(): SignUpForm {
        val name = profile.name.trim()
        val phone = profile.phone.trim()
        return copy(
            email = Validators.normalizeEmail(email),
            profile = if (role == UserRole.PROFESSIONAL) profile.copy(name = name, phone = phone) else ProfileForm(name, phone),
        )
    }
}
