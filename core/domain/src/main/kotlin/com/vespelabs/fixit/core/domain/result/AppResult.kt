package com.vespelabs.fixit.core.domain.result

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>

    data class Error(val error: DomainError) : AppResult<Nothing>
}
