package com.vespelabs.fixit.core.data.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.util.Log
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.repository.LocationProvider
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Longest wait for a fix before giving up with LocationUnavailable (location spec, "GPS real"). */
const val LOCATION_TIMEOUT_MS = 10_000L

private const val LOG_TAG = "FusedLocationProvider"

/**
 * Runs [fetch] for at most [timeoutMs]. No fix (device location off -> null), the timeout and any failure of the
 * provider (missing permission -> SecurityException, Play services errors) all become LocationUnavailable; the
 * caller's cancellation still propagates.
 */
suspend fun locateWithin(timeoutMs: Long = LOCATION_TIMEOUT_MS, fetch: suspend () -> GeoPoint?): AppResult<GeoPoint> {
    val point = try {
        withTimeoutOrNull(timeoutMs) { fetch() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(LOG_TAG, "Location request failed", e)
        null
    }
    return point?.let { AppResult.Success(it) } ?: AppResult.Error(DomainError.LocationUnavailable)
}

/**
 * Real device location (Phase 3): one fresh [getCurrentLocation][com.google.android.gms.location.FusedLocationProviderClient.getCurrentLocation]
 * with balanced priority, so an approximate (coarse-only) grant is enough. The permission is requested by the UI;
 * without it, or with the device location off, the result is LocationUnavailable without calling the provider.
 * No JVM test: [locateWithin] is tested; the hardware behavior is a manual check (tasks 3.7).
 */
class FusedLocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocationProvider {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    @OptIn(ExperimentalCoroutinesApi::class)
    @SuppressLint("MissingPermission") // checked by hasLocationPermission()
    override suspend fun current(): AppResult<GeoPoint> {
        if (!hasLocationPermission() || !isLocationEnabled()) return AppResult.Error(DomainError.LocationUnavailable)
        return locateWithin {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setDurationMillis(LOCATION_TIMEOUT_MS)
                .build()
            // await(source) cancels the Play services request when the coroutine is cancelled (also on timeout).
            val source = CancellationTokenSource()
            client.getCurrentLocation(request, source.token).await(source)
                ?.let { GeoPoint(it.latitude, it.longitude) }
        }
    }

    private fun hasLocationPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            .any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) || manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }
    }
}
