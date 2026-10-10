package ar.edu.unaj.fixit.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/** Navigation spec flows, driven through the same functions AppNavDisplay wires to the callbacks. */
class NavigationFlowsTest {
    private val client = navigationStateFor(clientTabs)
    private val professional = navigationStateFor(professionalTabs)

    @Test
    fun `given My requests then New request when the request is created then its detail is shown and back goes to My requests`() {
        client.navigate(CreateRequestKey)

        client.onRequestCreated(requestId = "r9")

        assertEquals(ClientRequestDetailKey("r9"), client.currentKey)
        client.goBackFrom(ClientRequestDetailKey("r9"))
        assertEquals(MyRequestsKey, client.currentKey)
    }

    @Test
    fun `given an accepted request detail when opening Complete and rate and confirming then the detail is shown again`() {
        client.navigate(ClientRequestDetailKey("r4"))
        client.navigate(CompleteJobKey("r4"))

        client.goBackFrom(CompleteJobKey("r4"))

        assertEquals(ClientRequestDetailKey("r4"), client.currentKey)
    }

    @Test
    fun `given Complete and rate open when the user goes back without confirming then the detail is shown`() {
        client.navigate(ClientRequestDetailKey("r4"))
        client.navigate(CompleteJobKey("r4"))

        client.goBack()

        assertEquals(ClientRequestDetailKey("r4"), client.currentKey)
    }

    @Test
    fun `given the Feed with a detail open when the job is accepted then My jobs shows that detail and Feed is at its root`() {
        professional.navigate(ProRequestDetailKey("r1"))

        professional.onJobAccepted(detailKey = ProRequestDetailKey("r1"), requestId = "r1")

        assertEquals(TopLevelDestination.MY_JOBS, professional.currentTab)
        assertEquals(ProRequestDetailKey("r1"), professional.currentKey)
        // Back from that detail returns to My jobs, and Feed is at its root.
        professional.goBackFrom(ProRequestDetailKey("r1"))
        assertEquals(MyJobsKey, professional.currentKey)
        professional.selectTab(TopLevelDestination.FEED)
        assertEquals(FeedKey, professional.currentKey)
    }

    @Test
    fun `given My jobs with an old detail open when a job is accepted then My jobs restarts from its root`() {
        professional.selectTab(TopLevelDestination.MY_JOBS)
        professional.navigate(ProRequestDetailKey("r4"))
        professional.selectTab(TopLevelDestination.FEED)
        professional.navigate(ProRequestDetailKey("r1"))

        professional.onJobAccepted(detailKey = ProRequestDetailKey("r1"), requestId = "r1")
        professional.goBack()

        assertEquals(MyJobsKey, professional.currentKey)
    }

    @Test
    fun `given the Feed with a detail open when the race is lost and confirmed then the Feed is shown`() {
        professional.navigate(ProRequestDetailKey("r3"))

        professional.goBackFrom(ProRequestDetailKey("r3"))

        assertEquals(TopLevelDestination.FEED, professional.currentTab)
        assertEquals(FeedKey, professional.currentKey)
    }

    @Test
    fun `given My jobs when a job is tapped then its detail is shown and back returns to My jobs`() {
        professional.selectTab(TopLevelDestination.MY_JOBS)

        professional.navigate(ProRequestDetailKey("r4"))
        assertEquals(ProRequestDetailKey("r4"), professional.currentKey)
        professional.goBackFrom(ProRequestDetailKey("r4"))

        assertEquals(MyJobsKey, professional.currentKey)
    }

    @Test
    fun `given a detail opened from My requests when tapping the top bar back action then My requests is shown`() {
        client.navigate(ClientRequestDetailKey("r5"))

        client.goBackFrom(ClientRequestDetailKey("r5"))

        assertEquals(MyRequestsKey, client.currentKey)
    }

    @Test
    fun `given Edit profile when the changes are saved then the Profile is shown`() {
        client.selectTab(TopLevelDestination.CLIENT_PROFILE)
        client.navigate(ClientEditProfileKey)

        client.goBackFrom(ClientEditProfileKey)

        assertEquals(ClientProfileKey, client.currentKey)
    }

    @Test
    fun `given Edit profile with unsaved changes when going back then the Profile is shown`() {
        professional.selectTab(TopLevelDestination.PRO_PROFILE)
        professional.navigate(ProEditProfileKey)

        professional.goBack()

        assertEquals(ProProfileKey, professional.currentKey)
    }
}
