package ar.edu.unaj.fixit.core.data.fake

import ar.edu.unaj.fixit.core.domain.model.GeoPoint
import ar.edu.unaj.fixit.core.domain.model.Place
import ar.edu.unaj.fixit.core.domain.model.ProfessionalInfo
import ar.edu.unaj.fixit.core.domain.model.ProfileForm
import ar.edu.unaj.fixit.core.domain.model.Reputation
import ar.edu.unaj.fixit.core.domain.model.ServiceCategory
import ar.edu.unaj.fixit.core.domain.model.SessionState
import ar.edu.unaj.fixit.core.domain.model.SignUpForm
import ar.edu.unaj.fixit.core.domain.model.UserRole
import ar.edu.unaj.fixit.core.domain.result.AppResult
import ar.edu.unaj.fixit.core.domain.result.DomainError
import ar.edu.unaj.fixit.core.domain.testing.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeAuthRepositoryTest {
    private val clock = TestClock(1_800_000_000_000L)
    private val db = FakeDatabase(clock, SeedData.initialState(clock.now()))
    private val auth = FakeAuthRepository(db)

    private val quilmes = Place(GeoPoint(-34.7206, -58.2546), "Quilmes")

    private fun clientForm(email: String = "nuevo@fixit.test", password: String = "secret1") =
        SignUpForm(email, password, UserRole.CLIENT, ProfileForm("Nuevo Cliente", "11 5555-7777"))

    private fun professionalForm() = SignUpForm(
        email = "pro@fixit.test",
        password = "secret1",
        role = UserRole.PROFESSIONAL,
        profile = ProfileForm(
            name = "Nuevo Pro",
            phone = "11 5555-8888",
            categories = setOf(ServiceCategory.PLUMBING, ServiceCategory.GAS),
            base = quilmes,
            radiusKm = 10,
        ),
    )

    private suspend fun currentSession() = auth.session.first()

    @Test
    fun `given an unregistered email when a client signs up then the account has role CLIENT and the session starts with it`() = runTest {
        val result = auth.signUp(clientForm())

        val user = (result as AppResult.Success).data
        assertEquals(UserRole.CLIENT, user.role)
        assertEquals("nuevo@fixit.test", user.email)
        assertNull(user.professional)
        assertEquals(SessionState.SignedIn(user), currentSession())
        assertEquals(user.id, auth.currentUserId())
    }

    @Test
    fun `given valid professional data when a professional signs up then the profile is stored with zero reviews`() = runTest {
        val result = auth.signUp(professionalForm())

        val user = (result as AppResult.Success).data
        assertEquals(UserRole.PROFESSIONAL, user.role)
        assertEquals(
            ProfessionalInfo(setOf(ServiceCategory.PLUMBING, ServiceCategory.GAS), quilmes, 10, Reputation(0, 0)),
            user.professional,
        )
        assertEquals(user, db.state.value.users.last())
    }

    @Test
    fun `given an email already registered when signing up then the result is EmailAlreadyInUse and nothing changes`() = runTest {
        val before = db.state.value

        val result = auth.signUp(clientForm(email = "ana@fixit.test"))

        assertEquals(AppResult.Error(DomainError.EmailAlreadyInUse), result)
        assertEquals(before, db.state.value)
        assertEquals(SessionState.SignedOut, currentSession())
    }

    @Test
    fun `given an account for ana@fixit_test when signing up with capitals and spaces then the result is EmailAlreadyInUse`() = runTest {
        val result = auth.signUp(clientForm(email = " Ana@Fixit.Test "))

        assertEquals(AppResult.Error(DomainError.EmailAlreadyInUse), result)
    }

    @Test
    fun `given an account for ana@fixit_test when signing in with capitals and spaces then the existing account's session starts`() = runTest {
        val result = auth.signIn(" Ana@Fixit.Test ", SeedData.PASSWORD)

        assertEquals("c1", (result as AppResult.Success).data.id)
        assertEquals("c1", auth.currentUserId())
    }

    @Test
    fun `given an email with capitals and spaces when signing up then it is stored normalized and works for sign in`() = runTest {
        val user = (auth.signUp(clientForm(email = " New@Fixit.Test ")) as AppResult.Success).data
        auth.signOut()

        val result = auth.signIn("NEW@fixit.test", "secret1")

        assertEquals("new@fixit.test", user.email)
        assertEquals(AppResult.Success(user), result)
    }

    @Test
    fun `given a user registered as CLIENT when signing out and in again then the session exposes role CLIENT`() = runTest {
        auth.signUp(clientForm())
        auth.signOut()

        auth.signIn("nuevo@fixit.test", "secret1")

        val session = currentSession() as SessionState.SignedIn
        assertEquals(UserRole.CLIENT, session.user.role)
    }

    @Test
    fun `given a registered user when signing in with the right credentials then the session starts with that user and role`() = runTest {
        val result = auth.signIn("carlos@fixit.test", SeedData.PASSWORD)

        val user = (result as AppResult.Success).data
        assertEquals("p1", user.id)
        assertEquals(UserRole.PROFESSIONAL, user.role)
        assertEquals(SessionState.SignedIn(user), currentSession())
    }

    @Test
    fun `given a wrong password or an unknown email when signing in then the result is InvalidCredentials and no session starts`() = runTest {
        assertEquals(AppResult.Error(DomainError.InvalidCredentials), auth.signIn("ana@fixit.test", "wrong"))
        assertEquals(AppResult.Error(DomainError.InvalidCredentials), auth.signIn("ghost@fixit.test", SeedData.PASSWORD))
        assertEquals(SessionState.SignedOut, currentSession())
        assertNull(auth.currentUserId())
    }

    @Test
    fun `given a signed-in user when signing out then the session is SignedOut`() = runTest {
        auth.signIn("ana@fixit.test", SeedData.PASSWORD)

        auth.signOut()

        assertEquals(SessionState.SignedOut, currentSession())
        assertNull(auth.currentUserId())
    }

    @Test
    fun `given a restart of the app when starting then the session is SignedOut`() = runTest {
        auth.signIn("ana@fixit.test", SeedData.PASSWORD)

        val restarted = FakeAuthRepository(FakeDatabase(clock, SeedData.initialState(clock.now())))

        assertEquals(SessionState.SignedOut, restarted.session.first())
    }

    @Test
    fun `given the session flow when signing in and out repeatedly then it keeps emitting states without failing or repeating`() = runTest {
        val states = mutableListOf<SessionState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { auth.session.toList(states) }

        auth.signIn("ana@fixit.test", SeedData.PASSWORD)
        auth.signIn("ana@fixit.test", SeedData.PASSWORD)
        auth.signOut()

        assertEquals(listOf("SignedOut", "SignedIn", "SignedOut"), states.map { it::class.simpleName })
    }

    @Test
    fun `given a registered account when its profile changes then the session emits the updated user`() = runTest {
        val states = mutableListOf<SessionState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { auth.session.toList(states) }
        auth.signIn("ana@fixit.test", SeedData.PASSWORD)

        db.write { old, _ ->
            WriteDecision.Commit(
                old.copy(users = old.users.map { if (it.id == "c1") it.copy(name = "Ana Pérez") else it }),
                Unit,
            )
        }

        assertEquals("Ana Pérez", (states.last() as SessionState.SignedIn).user.name)
    }

    @Test
    fun `given failNextWrite when signing up then the result is that error and no account is created`() = runTest {
        db.failNextWrite(DomainError.Network)
        val before = db.state.value

        val result = auth.signUp(clientForm())

        assertEquals(AppResult.Error(DomainError.Network), result)
        assertEquals(before, db.state.value)
    }

    @Test
    fun `given failNextWrite when signing in then the sign in succeeds and the hook stays armed`() = runTest {
        db.failNextWrite(DomainError.Network)

        val signIn = auth.signIn("ana@fixit.test", SeedData.PASSWORD)
        val signUp = auth.signUp(clientForm())

        assertEquals("c1", (signIn as AppResult.Success).data.id)
        assertEquals(AppResult.Error(DomainError.Network), signUp)
    }
}
