package ar.edu.unaj.fixit.core.domain.repository

import ar.edu.unaj.fixit.core.domain.model.GeoPoint
import ar.edu.unaj.fixit.core.domain.result.AppResult

interface AddressResolver {
    suspend fun resolve(point: GeoPoint): AppResult<String>
}
