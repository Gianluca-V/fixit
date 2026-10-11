package com.vespelabs.fixit.core.domain.repository

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.result.AppResult

interface AddressResolver {
    suspend fun resolve(point: GeoPoint): AppResult<String>
}
