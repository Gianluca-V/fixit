package com.vespelabs.fixit.core.data.firebase

import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.model.ProfessionalInfo
import com.vespelabs.fixit.core.domain.model.ProfileForm
import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.Reputation
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.Review
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.model.UserRole

// Firestore documents of design 13.1. The DTOs hold plain stored values (numbers arrive as Long, dates as epoch millis)
// so the domain mapping can be unit tested without a DocumentSnapshot. A malformed document fails with
// IllegalStateException, which the callers map to DomainError.Unknown (design 13.4).

internal const val USERS = "users"
internal const val REQUESTS = "requests"
internal const val REVIEWS = "reviews"

internal object UserFields {
    const val NAME = "name"
    const val EMAIL = "email"
    const val PHONE = "phone"
    const val ROLE = "role"
    const val CATEGORIES = "categories"
    const val BASE_LAT = "baseLat"
    const val BASE_LNG = "baseLng"
    const val BASE_ADDRESS = "baseAddress"
    const val RADIUS_KM = "radiusKm"
    const val RATING_SUM = "ratingSum"
    const val REVIEW_COUNT = "reviewCount"
    const val CREATED_AT = "createdAt"
    const val AREA_LAT = "areaLat"
    const val AREA_LNG = "areaLng"
    const val AREA_ADDRESS = "areaAddress"

    /** The only fields a profile edit may change (rules 13.3): never email, role or reputation. */
    val EDITABLE = setOf(NAME, PHONE, CATEGORIES, BASE_LAT, BASE_LNG, BASE_ADDRESS, RADIUS_KM, AREA_LAT, AREA_LNG, AREA_ADDRESS)
}

internal object RequestFields {
    const val CLIENT_ID = "clientId"
    const val CATEGORY = "category"
    const val DESCRIPTION = "description"
    const val PHOTO_URLS = "photoUrls"
    const val LAT = "lat"
    const val LNG = "lng"
    const val ADDRESS = "address"
    const val GEOHASH = "geohash"
    const val STATUS = "status"
    const val PROFESSIONAL_ID = "professionalId"
    const val CREATED_AT = "createdAt"
    const val ACCEPTED_AT = "acceptedAt"
    const val CANCELLED_AT = "cancelledAt"
    const val COMPLETED_AT = "completedAt"
}

internal object ReviewFields {
    const val PROFESSIONAL_ID = "professionalId"
    const val CLIENT_ID = "clientId"
    const val RATING = "rating"
    const val COMMENT = "comment"
    const val CREATED_AT = "createdAt"
}

// ---------- users/{uid} ----------

internal data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val role: String,
    val categories: List<String> = emptyList(),
    val baseLat: Double? = null,
    val baseLng: Double? = null,
    val baseAddress: String? = null,
    val radiusKm: Long? = null,
    val ratingSum: Long = 0,
    val reviewCount: Long = 0,
    val areaLat: Double? = null,
    val areaLng: Double? = null,
    val areaAddress: String? = null,
)

internal fun UserDto.toDomain(): User {
    val userRole = enumOrFail<UserRole>(role, "role")
    val professional = if (userRole == UserRole.PROFESSIONAL) {
        ProfessionalInfo(
            categories = categories.mapNotNull { name -> ServiceCategory.entries.firstOrNull { it.name == name } }.toSet(),
            base = Place(
                GeoPoint(checkNotNull(baseLat) { "baseLat missing" }, checkNotNull(baseLng) { "baseLng missing" }),
                checkNotNull(baseAddress) { "baseAddress missing" },
            ),
            radiusKm = checkNotNull(radiusKm) { "radiusKm missing" }.toInt(),
            reputation = Reputation(ratingSum = ratingSum.toInt(), reviewCount = reviewCount.toInt()),
        )
    } else {
        null
    }
    // The client area is optional (icon-map-address D5): a document written before it, or a partial one, has none.
    val area = if (userRole == UserRole.CLIENT && areaLat != null && areaLng != null && areaAddress != null) {
        Place(GeoPoint(areaLat, areaLng), areaAddress)
    } else {
        null
    }
    return User(id = id, name = name, email = email, phone = phone, role = userRole, professional = professional, area = area)
}

/** Fields of a new `users/{uid}`: ratingSum = 0 and reviewCount = 0 for every role, as the create rule demands (13.1). */
internal fun User.toCreateFields(): Map<String, Any> = buildMap {
    put(UserFields.NAME, name)
    put(UserFields.EMAIL, email)
    put(UserFields.PHONE, phone)
    put(UserFields.ROLE, role.name)
    professional?.let { info ->
        put(UserFields.CATEGORIES, info.categories.map { it.name })
        putBase(info.base)
        put(UserFields.RADIUS_KM, info.radiusKm)
    }
    put(UserFields.RATING_SUM, 0)
    put(UserFields.REVIEW_COUNT, 0)
    put(UserFields.CREATED_AT, FieldValue.serverTimestamp())
}

/**
 * Editable fields only (rules 13.3), mirroring FakeUserRepository: name and phone always; for a professional also
 * categories, and base and radius only when the form carries them. Professional fields are ignored for a client.
 * A client always sends its whole area, so a null area deletes the three area fields (icon-map-address D5).
 */
internal fun profileUpdateFields(role: UserRole, form: ProfileForm): Map<String, Any> = buildMap {
    put(UserFields.NAME, form.name)
    put(UserFields.PHONE, form.phone)
    if (role == UserRole.PROFESSIONAL) {
        put(UserFields.CATEGORIES, form.categories.map { it.name })
        form.base?.let { putBase(it) }
        form.radiusKm?.let { put(UserFields.RADIUS_KM, it) }
    } else {
        val area = form.area
        put(UserFields.AREA_LAT, area?.point?.latitude ?: FieldValue.delete())
        put(UserFields.AREA_LNG, area?.point?.longitude ?: FieldValue.delete())
        put(UserFields.AREA_ADDRESS, area?.address ?: FieldValue.delete())
    }
}

/** The domain user after applying [profileUpdateFields] locally (same rules as FakeUserRepository). */
internal fun User.withProfile(form: ProfileForm): User = copy(
    name = form.name,
    phone = form.phone,
    professional = professional?.let { info ->
        info.copy(categories = form.categories, base = form.base ?: info.base, radiusKm = form.radiusKm ?: info.radiusKm)
    },
    area = if (role == UserRole.CLIENT) form.area else null,
)

private fun MutableMap<String, Any>.putBase(base: Place) {
    put(UserFields.BASE_LAT, base.point.latitude)
    put(UserFields.BASE_LNG, base.point.longitude)
    put(UserFields.BASE_ADDRESS, base.address)
}

internal fun DocumentSnapshot.toUserDto(): UserDto = UserDto(
    id = id,
    name = requireString(UserFields.NAME),
    email = requireString(UserFields.EMAIL),
    phone = requireString(UserFields.PHONE),
    role = requireString(UserFields.ROLE),
    categories = stringList(UserFields.CATEGORIES),
    baseLat = getDouble(UserFields.BASE_LAT),
    baseLng = getDouble(UserFields.BASE_LNG),
    baseAddress = getString(UserFields.BASE_ADDRESS),
    radiusKm = getLong(UserFields.RADIUS_KM),
    ratingSum = getLong(UserFields.RATING_SUM) ?: 0,
    reviewCount = getLong(UserFields.REVIEW_COUNT) ?: 0,
    areaLat = getDouble(UserFields.AREA_LAT),
    areaLng = getDouble(UserFields.AREA_LNG),
    areaAddress = getString(UserFields.AREA_ADDRESS),
)

internal fun DocumentSnapshot.toUser(): User = toUserDto().toDomain()

// ---------- requests/{id} ----------

internal data class RequestDto(
    val id: String,
    val clientId: String,
    val category: String,
    val description: String,
    val photoUrls: List<String>,
    val lat: Double,
    val lng: Double,
    val address: String,
    val status: String,
    val professionalId: String? = null,
    val createdAt: Long?,
    val acceptedAt: Long? = null,
    val cancelledAt: Long? = null,
    val completedAt: Long? = null,
)

/** The geohash is never read back: it only exists in the stored document (13.1). */
internal fun RequestDto.toDomain(): RepairRequest = RepairRequest(
    id = id,
    clientId = clientId,
    category = enumOrFail<ServiceCategory>(category, "category"),
    description = description,
    photoUrls = photoUrls,
    place = Place(GeoPoint(lat, lng), address),
    status = enumOrFail<RequestStatus>(status, "status"),
    professionalId = professionalId,
    createdAt = checkNotNull(createdAt) { "createdAt missing" },
    acceptedAt = acceptedAt,
    cancelledAt = cancelledAt,
    completedAt = completedAt,
)

/**
 * Fields of a new `requests/{id}` (13.1): status OPEN, the geohash for the feed queries and only `createdAt` as a
 * server timestamp; professionalId and the other dates stay absent until their transaction writes them.
 */
internal fun newRequestFields(
    clientId: String,
    category: ServiceCategory,
    description: String,
    photoUrls: List<String>,
    place: Place,
): Map<String, Any> = mapOf(
    RequestFields.CLIENT_ID to clientId,
    RequestFields.CATEGORY to category.name,
    RequestFields.DESCRIPTION to description,
    RequestFields.PHOTO_URLS to photoUrls,
    RequestFields.LAT to place.point.latitude,
    RequestFields.LNG to place.point.longitude,
    RequestFields.ADDRESS to place.address,
    RequestFields.GEOHASH to GeoFireUtils.getGeoHashForLocation(GeoLocation(place.point.latitude, place.point.longitude)),
    RequestFields.STATUS to RequestStatus.OPEN.name,
    RequestFields.CREATED_AT to FieldValue.serverTimestamp(),
)

internal fun DocumentSnapshot.toRequestDto(): RequestDto = RequestDto(
    id = id,
    clientId = requireString(RequestFields.CLIENT_ID),
    category = requireString(RequestFields.CATEGORY),
    description = requireString(RequestFields.DESCRIPTION),
    photoUrls = stringList(RequestFields.PHOTO_URLS),
    lat = checkNotNull(getDouble(RequestFields.LAT)) { "lat missing" },
    lng = checkNotNull(getDouble(RequestFields.LNG)) { "lng missing" },
    address = requireString(RequestFields.ADDRESS),
    status = requireString(RequestFields.STATUS),
    professionalId = getString(RequestFields.PROFESSIONAL_ID),
    createdAt = millis(RequestFields.CREATED_AT),
    acceptedAt = millis(RequestFields.ACCEPTED_AT),
    cancelledAt = millis(RequestFields.CANCELLED_AT),
    completedAt = millis(RequestFields.COMPLETED_AT),
)

internal fun DocumentSnapshot.toRepairRequest(): RepairRequest = toRequestDto().toDomain()

// ---------- reviews/{requestId} ----------

internal data class ReviewDto(
    val requestId: String,
    val professionalId: String,
    val clientId: String,
    val rating: Long,
    val comment: String?,
    val createdAt: Long?,
)

internal fun ReviewDto.toDomain(): Review = Review(
    requestId = requestId,
    professionalId = professionalId,
    clientId = clientId,
    rating = rating.toInt(),
    comment = comment,
    createdAt = checkNotNull(createdAt) { "createdAt missing" },
)

/** Fields of a new `reviews/{requestId}`; `rating` is written as an integer, as the rule demands (13.3). */
internal fun newReviewFields(professionalId: String, clientId: String, rating: Int, comment: String?): Map<String, Any?> =
    mapOf(
        ReviewFields.PROFESSIONAL_ID to professionalId,
        ReviewFields.CLIENT_ID to clientId,
        ReviewFields.RATING to rating,
        ReviewFields.COMMENT to comment,
        ReviewFields.CREATED_AT to FieldValue.serverTimestamp(),
    )

internal fun DocumentSnapshot.toReviewDto(): ReviewDto = ReviewDto(
    requestId = id,
    professionalId = requireString(ReviewFields.PROFESSIONAL_ID),
    clientId = requireString(ReviewFields.CLIENT_ID),
    rating = checkNotNull(getLong(ReviewFields.RATING)) { "rating missing" },
    comment = getString(ReviewFields.COMMENT),
    createdAt = millis(ReviewFields.CREATED_AT),
)

internal fun DocumentSnapshot.toReview(): Review = toReviewDto().toDomain()

// ---------- snapshot helpers ----------

/** A server timestamp as epoch millis; ESTIMATE gives a local estimate while the write is pending; absent -> null. */
internal fun DocumentSnapshot.millis(field: String): Long? =
    getTimestamp(field, ServerTimestampBehavior.ESTIMATE)?.toDate()?.time

private fun DocumentSnapshot.requireString(field: String): String = checkNotNull(getString(field)) { "$field missing" }

private fun DocumentSnapshot.stringList(field: String): List<String> =
    (get(field) as? List<*>)?.filterIsInstance<String>().orEmpty()

private inline fun <reified E : Enum<E>> enumOrFail(value: String, field: String): E =
    enumValues<E>().firstOrNull { it.name == value } ?: error("Unknown $field: $value")
