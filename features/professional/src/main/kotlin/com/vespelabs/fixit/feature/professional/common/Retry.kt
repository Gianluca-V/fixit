package com.vespelabs.fixit.feature.professional.common

import com.vespelabs.fixit.core.domain.result.DomainError

/** Retrying cannot fix a missing document or a denied access, so those screens offer no "Reintentar". */
internal fun DomainError.isRetryable(): Boolean = this != DomainError.NotFound && this != DomainError.Unauthorized
