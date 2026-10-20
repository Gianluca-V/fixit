package com.vespelabs.fixit.core.domain.testing

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.repository.AddressResolver
import com.vespelabs.fixit.core.domain.repository.LocationProvider
import com.vespelabs.fixit.core.domain.result.AppResult
import kotlinx.coroutines.delay

/** Configurable LocationProvider + AddressResolver double; [resolveDelayMs] delays [resolve] in virtual time. */
class TestLocation(
    var point: AppResult<GeoPoint> = AppResult.Success(GeoPoint(-34.8065, -58.2788)),
    var address: AppResult<String> = AppResult.Success("Av. San Martín 2000, Florencio Varela"),
) : LocationProvider, AddressResolver {
    val resolvedPoints = mutableListOf<GeoPoint>()
    var currentCalls = 0
    var resolveDelayMs = 0L

    override suspend fun current(): AppResult<GeoPoint> {
        currentCalls++
        return point
    }

    override suspend fun resolve(point: GeoPoint): AppResult<String> {
        resolvedPoints += point
        delay(resolveDelayMs)
        return address
    }
}
