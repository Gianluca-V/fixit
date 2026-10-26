package com.vespelabs.fixit.navigation

import com.vespelabs.fixit.core.data.push.PushEventType
import com.vespelabs.fixit.core.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Design 14b.6: the destination of a tapped notice is a pure function of (type, role, requestId). */
class DeepLinkTest {
    @Test
    fun `Aviso de aceptacion abre el detalle del cliente - REQUESTS and ClientRequestDetailKey r1`() {
        assertEquals(
            DeepLinkDestination(TopLevelDestination.REQUESTS, ClientRequestDetailKey("r1")),
            deepLinkDestination(PushEventType.REQUEST_ACCEPTED, UserRole.CLIENT, "r1"),
        )
    }

    @Test
    fun `Aviso de nueva solicitud abre el detalle del profesional - FEED and ProRequestDetailKey r1`() {
        assertEquals(
            DeepLinkDestination(TopLevelDestination.FEED, ProRequestDetailKey("r1")),
            deepLinkDestination(PushEventType.NEW_REQUEST, UserRole.PROFESSIONAL, "r1"),
        )
    }

    @Test
    fun `Aviso de cancelacion abre el detalle del profesional - MY_JOBS and ProRequestDetailKey r1`() {
        assertEquals(
            DeepLinkDestination(TopLevelDestination.MY_JOBS, ProRequestDetailKey("r1")),
            deepLinkDestination(PushEventType.REQUEST_CANCELLED, UserRole.PROFESSIONAL, "r1"),
        )
    }

    @Test
    fun `Rol que no corresponde al evento - no destination`() {
        assertNull(deepLinkDestination(PushEventType.REQUEST_ACCEPTED, UserRole.PROFESSIONAL, "r1"))
        assertNull(deepLinkDestination(PushEventType.NEW_REQUEST, UserRole.CLIENT, "r1"))
        assertNull(deepLinkDestination(PushEventType.REQUEST_CANCELLED, UserRole.CLIENT, "r1"))
    }

    @Test
    fun `Sin sesion - no role means no destination`() {
        for (type in PushEventType.entries) assertNull(deepLinkDestination(type, null, "r1"))
    }
}
