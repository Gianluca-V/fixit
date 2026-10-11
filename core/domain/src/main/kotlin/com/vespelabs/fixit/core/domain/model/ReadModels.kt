package com.vespelabs.fixit.core.domain.model

data class FeedItem(val request: RepairRequest, val distanceKm: Double)

/** whatsAppUrl = PhoneFormatter.whatsAppUrl(phone). */
data class Contact(
    val name: String,
    val phone: String,
    val whatsAppUrl: String,
    val reputation: Reputation?,
)

data class RequestDetail(
    val request: RepairRequest,
    val contact: Contact?, // only ACCEPTED/COMPLETED and viewer is a participant (ContactRules)
    val review: Review?,
    val distanceKm: Double?, // only for a professional viewer, rounded to 0.1 km
    val viewerIsAssignee: Boolean, // professional viewer == request.professionalId
)
