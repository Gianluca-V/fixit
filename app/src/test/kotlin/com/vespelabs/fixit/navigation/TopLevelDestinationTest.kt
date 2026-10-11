package com.vespelabs.fixit.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {
    @Test
    fun `given a client when listing tabs then they are Requests and Profile with Requests first`() {
        assertEquals(
            listOf(TopLevelDestination.REQUESTS, TopLevelDestination.CLIENT_PROFILE),
            clientTabs,
        )
        assertEquals(listOf<Any>(MyRequestsKey, ClientProfileKey), clientTabs.map { it.startKey })
    }

    @Test
    fun `given a professional when listing tabs then they are Feed, My jobs and Profile with Feed first`() {
        assertEquals(
            listOf(TopLevelDestination.FEED, TopLevelDestination.MY_JOBS, TopLevelDestination.PRO_PROFILE),
            professionalTabs,
        )
        assertEquals(listOf<Any>(FeedKey, MyJobsKey, ProProfileKey), professionalTabs.map { it.startKey })
    }

    @Test
    fun `given the sign in experience when listing tabs then there is only the AUTH pseudo tab rooted at sign in`() {
        assertEquals(listOf(TopLevelDestination.AUTH), authTabs)
        assertEquals(SignInKey, TopLevelDestination.AUTH.startKey)
    }
}
