package com.vespelabs.fixit.core.data.device

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.vespelabs.fixit.core.data.di.IoDispatcher
import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.repository.AddressResolver
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val LOG_TAG = "GeocoderAddressResolver"

/** The parts of an [Address] the readable address is built from, so the formatting is testable on the JVM. */
data class AddressParts(
    val thoroughfare: String?,
    val subThoroughfare: String?,
    val locality: String?,
    val addressLine: String?,
)

/**
 * "Street number, locality" (e.g. "Av. San Martín 2000, Florencio Varela"). Without a street it falls back to the
 * full address line, then to the locality alone; null when there is nothing usable.
 */
fun formatAddress(parts: AddressParts): String? {
    val street = parts.thoroughfare.usable()
    val locality = parts.locality.usable()
    if (street != null) {
        val streetAndNumber = listOfNotNull(street, parts.subThoroughfare.usable()).joinToString(" ")
        return listOfNotNull(streetAndNumber, locality).joinToString(", ")
    }
    return parts.addressLine.usable() ?: locality
}

/**
 * Formats the first usable geocoder result. No usable result -> LocationUnavailable; the geocoder service being
 * unreachable (IOException) -> Network; invalid coordinates -> LocationUnavailable. GetCurrentPlaceUseCase turns any
 * of these into an empty, editable address.
 */
suspend fun resolveAddress(fetch: suspend () -> List<AddressParts>): AppResult<String> = try {
    fetch().firstNotNullOfOrNull(::formatAddress)
        ?.let { AppResult.Success(it) }
        ?: AppResult.Error(DomainError.LocationUnavailable)
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Log.w(LOG_TAG, "Geocoder unavailable", e)
    AppResult.Error(DomainError.Network)
} catch (e: IllegalArgumentException) {
    Log.w(LOG_TAG, "Invalid coordinates", e)
    AppResult.Error(DomainError.LocationUnavailable)
}

private fun String?.usable(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Reverse geocoding with the framework [Geocoder] in Spanish (Argentina). API 33+ uses the asynchronous listener;
 * older versions block, so that call runs on the injected IO dispatcher (D13). A device without a geocoder backend
 * resolves to LocationUnavailable. No JVM test: [formatAddress] and [resolveAddress] are tested; the real
 * geocoder is a manual check (tasks 3.7).
 */
class GeocoderAddressResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
) : AddressResolver {
    private val geocoder by lazy { Geocoder(context, Locale.forLanguageTag("es-AR")) }

    override suspend fun resolve(point: GeoPoint): AppResult<String> {
        if (!Geocoder.isPresent()) return AppResult.Error(DomainError.LocationUnavailable)
        return resolveAddress { fromLocation(point).map { it.toParts() } }
    }

    private suspend fun fromLocation(point: GeoPoint): List<Address> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(
                    point.latitude,
                    point.longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = continuation.resume(addresses)

                        override fun onError(errorMessage: String?) =
                            continuation.resumeWithException(IOException(errorMessage ?: "Geocoder error"))
                    },
                )
            }
        } else {
            withContext(dispatcher) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(point.latitude, point.longitude, 1).orEmpty()
            }
        }

    private fun Address.toParts() = AddressParts(
        thoroughfare = thoroughfare,
        subThoroughfare = subThoroughfare,
        locality = locality ?: subAdminArea,
        addressLine = if (maxAddressLineIndex >= 0) getAddressLine(0) else null,
    )
}
