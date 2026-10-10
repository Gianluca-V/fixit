package ar.edu.unaj.fixit.core.data.fake

import ar.edu.unaj.fixit.core.domain.model.GeoPoint
import ar.edu.unaj.fixit.core.domain.repository.AddressResolver
import ar.edu.unaj.fixit.core.domain.repository.LocationProvider
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1 simulated location: a fixed, configurable place (Florencio Varela by default) that never
 * touches the hardware. One @Singleton serves both [LocationProvider] and [AddressResolver] so the
 * configuration is shared; setting [failWith] makes [current] fail (e.g. LocationUnavailable).
 */
@Singleton
class FakeLocation @Inject constructor() : LocationProvider, AddressResolver {
    var point: GeoPoint = GeoPoint(-34.8065, -58.2788)
    var address: String = "Av. San Martín 2000, Florencio Varela"
    var failWith: DomainError? = null

    override suspend fun current(): AppResult<GeoPoint> =
        failWith?.let { AppResult.Error(it) } ?: AppResult.Success(point)

    override suspend fun resolve(point: GeoPoint): AppResult<String> = AppResult.Success(address)
}
