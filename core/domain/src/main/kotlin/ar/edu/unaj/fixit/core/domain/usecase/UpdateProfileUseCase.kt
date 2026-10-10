package ar.edu.unaj.fixit.core.domain.usecase

import ar.edu.unaj.fixit.core.domain.model.ProfileForm
import ar.edu.unaj.fixit.core.domain.model.User
import ar.edu.unaj.fixit.core.domain.repository.AuthRepository
import ar.edu.unaj.fixit.core.domain.repository.UserRepository
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.rules.Validators
import javax.inject.Inject

class UpdateProfileUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(form: ProfileForm): AppResult<User> {
        val user = when (val current = readCurrentUser(authRepository, userRepository)) {
            is AppResult.Error -> return current
            is AppResult.Success -> current.data
        }
        Validators.profile(user.role, form)?.let { return AppResult.Error(DomainError.Validation(it)) }
        return userRepository.updateProfile(user.id, form.forPersistence(user.role))
    }
}
