package com.vespelabs.fixit.core.domain.usecase

import com.vespelabs.fixit.core.domain.model.NewRequest
import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.UserRole
import com.vespelabs.fixit.core.domain.repository.AuthRepository
import com.vespelabs.fixit.core.domain.repository.RepairRequestRepository
import com.vespelabs.fixit.core.domain.repository.UserRepository
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.rules.Validators
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
