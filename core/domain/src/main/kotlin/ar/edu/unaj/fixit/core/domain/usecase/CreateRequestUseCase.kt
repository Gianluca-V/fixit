package ar.edu.unaj.fixit.core.domain.usecase

import ar.edu.unaj.fixit.core.domain.model.NewRequest
import ar.edu.unaj.fixit.core.domain.model.RepairRequest
import ar.edu.unaj.fixit.core.domain.model.UserRole
import ar.edu.unaj.fixit.core.domain.repository.AuthRepository
import ar.edu.unaj.fixit.core.domain.repository.RepairRequestRepository
import ar.edu.unaj.fixit.core.domain.repository.UserRepository
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.rules.Validators
import javax.inject.Inject

class CreateRequestUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val requestRepository: RepairRequestRepository,
) {
    suspend operator fun invoke(input: NewRequest): AppResult<RepairRequest> {
        val client = when (val current = readCurrentUser(authRepository, userRepository)) {
            is AppResult.Error -> return current
            is AppResult.Success -> current.data
        }
        if (client.role != UserRole.CLIENT) return AppResult.Error(DomainError.Unauthorized)
        Validators.request(input)?.let { return AppResult.Error(DomainError.Validation(it)) }
        return requestRepository.create(client.id, input.copy(description = input.description.trim()))
    }
}
