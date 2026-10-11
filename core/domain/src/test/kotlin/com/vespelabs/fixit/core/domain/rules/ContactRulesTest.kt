package com.vespelabs.fixit.core.domain.rules

import com.vespelabs.fixit.core.domain.model.Contact
import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.model.ProfessionalInfo
import com.vespelabs.fixit.core.domain.model.RepairRequest
import com.vespelabs.fixit.core.domain.model.Reputation
import com.vespelabs.fixit.core.domain.model.RequestStatus
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.model.UserRole
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactRulesTest {
    private val place = Place(GeoPoint(-34.80, -58.27), "Somewhere")

    private val client = User(
        id = "c1", name = "Carla Client", email = "carla@example.com", phone = "11 5555-1234", role = UserRole.CLIENT,
    )
    private val professional = User(
        id = "p1", name = "Pablo Pro", email = "pablo@example.com", phone = "+54 9 221 555 1234",
        role = UserRole.PROFESSIONAL,
        professional = ProfessionalInfo(
            categories = setOf(ServiceCategory.PLUMBING),
            base = place,
            radiusKm = 10,
            reputation = Reputation(ratingSum = 13, reviewCount = 3),
        ),
    )

    private fun request(status: RequestStatus, professionalId: String? = "p1") = RepairRequest(
        id = "r1",
        clientId = "c1",
        category = ServiceCategory.PLUMBING,
        description = "Pipe leaking under the sink",
        photoUrls = emptyList(),
        place = place,
        status = status,
        professionalId = professionalId,
        createdAt = 0L,
    )

    private val professionalContact = Contact(
        name = "Pablo Pro",
        phone = "+54 9 221 555 1234",
        whatsAppUrl = "https://wa.me/5492215551234",
        reputation = Reputation(ratingSum = 13, reviewCount = 3),
    )
    private val clientContact = Contact(
        name = "Carla Client",
        phone = "11 5555-1234",
        whatsAppUrl = "https://wa.me/5491155551234",
        reputation = null,
    )

    private val unauthorized = AppResult.Error(DomainError.Unauthorized)

    @Test
    fun `given an accepted request when the owner client asks then it gets the professional contact`() {
        val result = ContactRules.contactFor(request(RequestStatus.ACCEPTED), viewerId = "c1", counterpart = professional)

        assertEquals(AppResult.Success(professionalContact), result)
    }

    @Test
    fun `given an accepted request when the assigned professional asks then it gets the client contact`() {
        val result = ContactRules.contactFor(request(RequestStatus.ACCEPTED), viewerId = "p1", counterpart = client)

        assertEquals(AppResult.Success(clientContact), result)
    }

    @Test
    fun `given a completed request when either participant asks then each gets the counterpart contact`() {
        val completed = request(RequestStatus.COMPLETED)

        assertEquals(
            AppResult.Success(professionalContact),
            ContactRules.contactFor(completed, viewerId = "c1", counterpart = professional),
        )
        assertEquals(
            AppResult.Success(clientContact),
            ContactRules.contactFor(completed, viewerId = "p1", counterpart = client),
        )
    }

    @Test
    fun `given an open request when anyone asks then the result is unauthorized`() {
        val open = request(RequestStatus.OPEN, professionalId = null)

        assertEquals(unauthorized, ContactRules.contactFor(open, viewerId = "c1", counterpart = professional))
        assertEquals(unauthorized, ContactRules.contactFor(open, viewerId = "p1", counterpart = client))
        assertEquals(unauthorized, ContactRules.contactFor(open, viewerId = "p2", counterpart = client))
    }

    @Test
    fun `given a request cancelled after being accepted when either participant asks then both are unauthorized`() {
        val cancelled = request(RequestStatus.CANCELLED, professionalId = "p1")

        assertEquals(unauthorized, ContactRules.contactFor(cancelled, viewerId = "c1", counterpart = professional))
        assertEquals(unauthorized, ContactRules.contactFor(cancelled, viewerId = "p1", counterpart = client))
    }

    @Test
    fun `given an accepted request when a third party asks then the result is unauthorized`() {
        val accepted = request(RequestStatus.ACCEPTED)

        assertEquals(unauthorized, ContactRules.contactFor(accepted, viewerId = "p2", counterpart = client))
        assertEquals(unauthorized, ContactRules.contactFor(accepted, viewerId = "c2", counterpart = professional))
    }

    @Test
    fun `given a completed request when a third party asks then the result is unauthorized`() {
        val completed = request(RequestStatus.COMPLETED)

        assertEquals(unauthorized, ContactRules.contactFor(completed, viewerId = "p2", counterpart = client))
        assertEquals(unauthorized, ContactRules.contactFor(completed, viewerId = "c2", counterpart = professional))
    }
}
