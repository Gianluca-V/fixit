package com.vespelabs.fixit.core.domain.testing

import com.vespelabs.fixit.core.domain.result.DomainError
import kotlin.reflect.KMutableProperty0

/** Reads a one-shot error hook and clears it, so only the NEXT call of its operation fails. */
internal fun KMutableProperty0<DomainError?>.take(): DomainError? = get().also { set(null) }
