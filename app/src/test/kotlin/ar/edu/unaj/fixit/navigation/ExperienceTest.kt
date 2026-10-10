package ar.edu.unaj.fixit.navigation

import ar.edu.unaj.fixit.core.domain.model.SessionState
import ar.edu.unaj.fixit.core.domain.testing.TestData
import org.junit.Test
import org.junit.Assert.assertEquals

class ExperienceTest {
    @Test
    fun `given the session is being determined when asking for the experience then it is LOADING`() {
        assertEquals(Experience.LOADING, experienceFor(SessionState.Loading))
    }

    @Test
    fun `given no session when asking for the experience then it is AUTH`() {
        assertEquals(Experience.AUTH, experienceFor(SessionState.SignedOut))
    }

    @Test
    fun `given a signed in client when asking for the experience then it is CLIENT`() {
        assertEquals(Experience.CLIENT, experienceFor(SessionState.SignedIn(TestData.client())))
    }

    @Test
    fun `given a signed in professional when asking for the experience then it is PROFESSIONAL`() {
        assertEquals(Experience.PROFESSIONAL, experienceFor(SessionState.SignedIn(TestData.professional())))
    }
}
