package com.vespelabs.fixit.core.domain.repository

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.result.AppResult

interface LocationProvider {
    suspend fun current(): AppResult<GeoPoint>
}
