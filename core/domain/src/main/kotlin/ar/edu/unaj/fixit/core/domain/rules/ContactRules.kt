package ar.edu.unaj.fixit.core.domain.rules

import ar.edu.unaj.fixit.core.domain.model.Contact
import ar.edu.unaj.fixit.core.domain.model.RepairRequest
import ar.edu.unaj.fixit.core.domain.model.RequestStatus
import ar.edu.unaj.fixit.core.domain.model.User
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError

object ContactRules {
    /**
     * The counterpart's contact is only available while the request is ACCEPTED or COMPLETED and
     * the viewer is the owner client or the assigned professional; otherwise Unauthorized.
     */
    fun contactFor(request: RepairRequest, viewerId: String, counterpart: User): AppResult<Contact> {
        val contactable = request.status == RequestStatus.ACCEPTED || request.status == RequestStatus.COMPLETED
        val isParticipant = viewerId == request.clientId || viewerId == request.professionalId
        if (!contactable || !isParticipant) return AppResult.Error(DomainError.Unauthorized)
        return AppResult.Success(
            Contact(
                name = counterpart.name,
                phone = counterpart.phone,
                whatsAppUrl = PhoneFormatter.whatsAppUrl(counterpart.phone),
                reputation = counterpart.professional?.reputation,
            ),
        )
    }
}
