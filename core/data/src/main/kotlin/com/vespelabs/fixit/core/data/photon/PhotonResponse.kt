package com.vespelabs.fixit.core.data.photon

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/** Any non-2xx answer of Photon; maps to `DomainError.Unknown` (design D2). */
class PhotonSearchException(val httpCode: Int) : Exception("Photon search failed: HTTP $httpCode")

/** The parts of a Photon GeoJSON feature the app uses (design D2). */
data class PhotonFeature(
    val latitude: Double,
    val longitude: Double,
    val name: String? = null,
    val street: String? = null,
    val houseNumber: String? = null,
    val city: String? = null,
    val district: String? = null,
    val locality: String? = null,
    val county: String? = null,
)

private const val PHOTON_URL = "https://photon.komoot.io/api/"

/** Asks for more than the 5 suggestions shown, because SearchAddressUseCase drops the unusable ones first. */
private const val PHOTON_LIMIT = 8

/** Argentina (minLon, minLat, maxLon, maxLat): results outside it are not useful to FixIt. */
private const val ARGENTINA_BBOX = "-73.6,-55.1,-53.6,-21.8"

/** `GET /api/?q=..&limit=8&bbox=..[&lat=..&lon=..]`; the center only biases the order, it does not filter. */
fun buildPhotonRequest(query: String, near: GeoPoint?, userAgent: String): Request {
    val url = PHOTON_URL.toHttpUrl().newBuilder()
        .addQueryParameter("q", query)
        .addQueryParameter("limit", PHOTON_LIMIT.toString())
        .addQueryParameter("bbox", ARGENTINA_BBOX)
        .apply {
            if (near != null) {
                addQueryParameter("lat", near.latitude.toString())
                addQueryParameter("lon", near.longitude.toString())
            }
        }
        .build()
    return Request.Builder().url(url).header("User-Agent", userAgent).get().build()
}

/**
 * 2xx GeoJSON FeatureCollection -> its features; features without a point are skipped. A non-2xx code throws
 * [PhotonSearchException]; a body that is not that JSON throws the parser's exception (both end as Unknown).
 */
fun parsePhotonResponse(code: Int, body: String): List<PhotonFeature> {
    if (code !in 200..299) throw PhotonSearchException(code)
    // kotlinx-serialization JsonElement API, not org.json (a throwing stub in Android JVM tests).
    val features = Json.parseToJsonElement(body).jsonObject.getValue("features").jsonArray
    return features.mapNotNull { element ->
        val feature = element.jsonObject
        val coordinates = (feature["geometry"] as? JsonObject)?.get("coordinates")?.jsonArray ?: return@mapNotNull null
        val longitude = (coordinates.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
        val latitude = (coordinates.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
        val properties = feature["properties"] as? JsonObject
        fun text(key: String): String? = (properties?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
        PhotonFeature(
            latitude = latitude,
            longitude = longitude,
            name = text("name"),
            street = text("street"),
            houseNumber = text("housenumber"),
            city = text("city"),
            district = text("district"),
            locality = text("locality"),
            county = text("county"),
        )
    }
}

/**
 * "Street number, locality"; without a street, "name, locality"; otherwise the locality alone (address-search spec).
 * The locality is the city, else the district, the locality or the county. Blank parts are skipped; null when
 * nothing is usable.
 */
fun formatPhotonAddress(feature: PhotonFeature): String? {
    val locality = listOf(feature.city, feature.district, feature.locality, feature.county).firstNotNullOfOrNull { it.usable() }
    val street = feature.street.usable()
    val head = if (street != null) {
        listOfNotNull(street, feature.houseNumber.usable()).joinToString(" ")
    } else {
        feature.name.usable()?.takeUnless { it == locality }
    }
    return listOfNotNull(head, locality).joinToString(", ").ifEmpty { null }
}

/** A suggestion for the domain, or null when it has no usable address. */
fun PhotonFeature.toPlace(): Place? = formatPhotonAddress(this)?.let { Place(GeoPoint(latitude, longitude), it) }

private fun String?.usable(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
