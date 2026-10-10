package ar.edu.unaj.fixit.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Builds a NavigationState over plain lists: no composition needed (spike 1.2.4). */
internal fun navigationStateFor(tabs: List<TopLevelDestination>): NavigationState =
    NavigationState(
        startTab = tabs.first(),
        currentTabState = mutableStateOf(tabs.first()),
        backStacks = tabs.associateWith { mutableListOf<NavKey>(it.startKey) },
    )

class NavigationStateTest {
    private val professional = navigationStateFor(professionalTabs)

    @Test
    fun `given a detail open in Feed when switching to Profile and back then Feed keeps its detail`() {
        professional.navigate(ProRequestDetailKey("r1"))

        professional.selectTab(TopLevelDestination.PRO_PROFILE)
        assertEquals(ProProfileKey, professional.currentKey)
        professional.selectTab(TopLevelDestination.FEED)

        assertEquals(ProRequestDetailKey("r1"), professional.currentKey)
    }

    @Test
    fun `given a detail open in the active tab when selecting that tab again then it returns to its root`() {
        professional.navigate(ProRequestDetailKey("r1"))

        professional.selectTab(TopLevelDestination.FEED)

        assertEquals(FeedKey, professional.currentKey)
        assertTrue(professional.isOnTabRoot)
    }

    @Test
    fun `given the Profile tab at its root when going back then the start tab is selected`() {
        professional.selectTab(TopLevelDestination.PRO_PROFILE)

        val handled = professional.goBack()

        assertTrue(handled)
        assertEquals(TopLevelDestination.FEED, professional.currentTab)
    }

    @Test
    fun `given the start tab at its root when going back then the app is left to close`() {
        assertFalse(professional.goBack())
        assertEquals(TopLevelDestination.FEED, professional.currentTab)
    }

    @Test
    fun `given a detail on top when going back then the detail is removed`() {
        professional.navigate(ProRequestDetailKey("r1"))

        assertTrue(professional.goBack())

        assertEquals(FeedKey, professional.currentKey)
    }

    @Test
    fun `given a key that is not on top when going back from it then nothing changes`() {
        professional.navigate(ProRequestDetailKey("r1"))

        professional.goBackFrom(ProRequestDetailKey("other"))

        assertEquals(ProRequestDetailKey("r1"), professional.currentKey)
    }

    @Test
    fun `given a double tap on back when going back from the same key twice then only one screen is removed`() {
        professional.navigate(ProRequestDetailKey("r1"))
        professional.navigate(ProRequestDetailKey("r2"))

        professional.goBackFrom(ProRequestDetailKey("r2"))
        professional.goBackFrom(ProRequestDetailKey("r2"))

        assertEquals(ProRequestDetailKey("r1"), professional.currentKey)
    }

    @Test
    fun `given a root key when going back from it then neither the stack nor the tab change`() {
        professional.selectTab(TopLevelDestination.PRO_PROFILE)

        professional.goBackFrom(ProProfileKey)

        assertEquals(TopLevelDestination.PRO_PROFILE, professional.currentTab)
        assertEquals(ProProfileKey, professional.currentKey)
    }

    @Test
    fun `given the key already on top when navigating to it again then it is not pushed twice`() {
        professional.navigate(ProRequestDetailKey("r1"))
        professional.navigate(ProRequestDetailKey("r1"))

        professional.goBack()

        assertEquals(FeedKey, professional.currentKey)
    }

    @Test
    fun `given a stack in another tab when resetting it to root then only that stack is trimmed`() {
        professional.selectTab(TopLevelDestination.MY_JOBS)
        professional.navigate(ProRequestDetailKey("r4"))
        professional.selectTab(TopLevelDestination.FEED)
        professional.navigate(ProRequestDetailKey("r1"))

        professional.resetToRoot(TopLevelDestination.MY_JOBS)

        assertEquals(ProRequestDetailKey("r1"), professional.currentKey)
        professional.selectTab(TopLevelDestination.MY_JOBS)
        assertEquals(MyJobsKey, professional.currentKey)
    }

    @Test
    fun `given the AUTH stack when going back from sign in then the app is left to close`() {
        val auth = navigationStateFor(authTabs)

        assertFalse(auth.goBack())
        assertEquals(SignInKey, auth.currentKey)
    }

    @Test
    fun `given the sign up screen when going back from it then the AUTH stack returns to sign in`() {
        val auth = navigationStateFor(authTabs)
        auth.navigate(SignUpKey)

        auth.goBackFrom(SignUpKey)

        assertEquals(SignInKey, auth.currentKey)
    }

    @Test
    fun `given the start tab and another tab when listing visible tabs then both are shown without duplicates`() {
        assertEquals(listOf(TopLevelDestination.FEED), professional.visibleTabs)

        professional.selectTab(TopLevelDestination.MY_JOBS)

        assertEquals(listOf(TopLevelDestination.FEED, TopLevelDestination.MY_JOBS), professional.visibleTabs)
    }
}
