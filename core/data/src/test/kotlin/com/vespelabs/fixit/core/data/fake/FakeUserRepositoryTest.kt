package com.vespelabs.fixit.core.data.fake

import com.vespelabs.fixit.core.domain.model.GeoPoint
import com.vespelabs.fixit.core.domain.model.Place
import com.vespelabs.fixit.core.domain.model.ProfileForm
import com.vespelabs.fixit.core.domain.model.Reputation
import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.model.User
import com.vespelabs.fixit.core.domain.result.AppResult
import com.vespelabs.fixit.core.domain.result.DomainError
import com.vespelabs.fixit.core.domain.testing.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeUserRepositoryTest {
    private val clock = TestClock(1_800_000_000_000L)
    private val db = FakeDatabase(clock, SeedData.initialState(clock.now()))
    private val users = FakeUserRepository(db)

    private suspend fun user(id: String): User = (users.get(id) as AppResult.Success).data

    @Test
    fun `given a seeded user when getting it then the stored user is returned`() = runTest {
        assertEquals(AppResult.Success(db.state.value.users.first { it.id == "c1" }), users.get("c1"))
    }

    @Test
    fun `given a missing user when getting it or observing it then the result is NotFound`() = runTest {
        assertEquals(AppResult.Error(DomainError.NotFound), users.get("ghost"))
        assertEquals(AppResult.Error(DomainError.NotFound), users.observe("ghost").first())
    }

    @Test
    fun `given a client when changing name and phone then they are updated and email and role stay`() = runTest {
        val before = user("c1")

        val result = users.updateProfile("c1", ProfileForm("Ana Pérez", "11 5555-9999"))

        val updated = (result as AppResult.Success).data
        assertEquals("Ana Pérez", updated.name)
        assertEquals("11 5555-9999", updated.phone)
        assertEquals(before.email, updated.email)
        assertEquals(before.role, updated.role)
        assertEquals(updated, user("c1"))
    }

    @Test
    fun `given a professional with 2 reviews when changing the work zone then categories and radius change and the reputation stays`() = runTest {
        val form = ProfileForm(
            name = "Carlos Gómez",
            phone = "11 5555-0101",
            categories = setOf(ServiceCategory.ELECTRICITY, ServiceCategory.GAS, ServiceCategory.PLUMBING),
            base = user("p1").professional!!.base,
            radiusKm = 25,
        )

        val updated = (users.updateProfile("p1", form) as AppResult.Success).data

        assertEquals(form.categories, updated.professional!!.categories)
        assertEquals(25, updated.professional!!.radiusKm)
        assertEquals(Reputation(9, 2), updated.professional!!.reputation)
        assertEquals("carlos@fixit.test", updated.email)
    }

    @Test
    fun `given a professional when updating the base point then the new base is stored`() = runTest {
        val quilmes = Place(GeoPoint(-34.7206, -58.2546), "Quilmes")
        val current = user("p1").professional!!

        val updated = (
            users.updateProfile("p1", ProfileForm("Carlos Gómez", "11 5555-0101", current.categories, quilmes, current.radiusKm))
                as AppResult.Success
            ).data

        assertEquals(quilmes, updated.professional!!.base)
    }

    @Test
    fun `given failNextWrite when saving the profile then the result is that error and the profile keeps its values`() = runTest {
        val before = user("c1")
        db.failNextWrite(DomainError.Network)

        val result = users.updateProfile("c1", ProfileForm("Ana Pérez", "11 5555-9999"))

        assertEquals(AppResult.Error(DomainError.Network), result)
        assertEquals(before, user("c1"))
    }

    @Test
    fun `given a professional with an ACCEPTED job when editing the work zone then the requests do not change`() = runTest {
        val requestsBefore = db.state.value.requests
        val current = user("p3").professional!!

        users.updateProfile(
            "p3",
            ProfileForm("Esteban Paz", "11 5555-0103", setOf(ServiceCategory.OTHER), current.base, 5),
        )

        assertEquals(requestsBefore, db.state.value.requests)
        assertEquals("p3", db.state.value.requests.first { it.id == "r4" }.professionalId)
    }

    @Test
    fun `given a client who changes the phone when reading the profile then the current phone is returned`() = runTest {
        users.updateProfile("c2", ProfileForm("Bruno Díaz", "11 5555-4444"))

        assertEquals("11 5555-4444", user("c2").phone)
    }

    @Test
    fun `given a missing user when updating the profile then the result is NotFound`() = runTest {
        assertEquals(
            AppResult.Error(DomainError.NotFound),
            users.updateProfile("ghost", ProfileForm("Nobody", "11 5555-0000")),
        )
    }

    @Test
    fun `given an observed user when the profile is edited then the new profile is emitted`() = runTest {
        val emissions = mutableListOf<AppResult<User>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { users.observe("c1").toList(emissions) }

        users.updateProfile("c1", ProfileForm("Ana Pérez", "11 5555-9999"))

        assertEquals(2, emissions.size)
        assertEquals("Ana Pérez", (emissions.last() as AppResult.Success).data.name)
    }

    @Test
    fun `given an observed user when something unrelated changes then nothing is emitted again`() = runTest {
        val emissions = mutableListOf<AppResult<User>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { users.observe("c1").toList(emissions) }

        db.signIn("c2")
        users.updateProfile("c2", ProfileForm("Bruno Díaz", "11 5555-4444"))
        db.signOut()

        assertEquals(1, emissions.size)
    }

    // [prof] Client area

    @Test
    fun `given a client when saving an area and then clearing it then the stored area follows`() = runTest {
        val quilmes = Place(GeoPoint(-34.7206, -58.2546), "Calle 123, Quilmes")

        users.updateProfile("c2", ProfileForm("Bruno Díaz", "11 5555-0002", area = quilmes))
        assertEquals(quilmes, user("c2").area)

        users.updateProfile("c2", ProfileForm("Bruno Díaz", "11 5555-0002", area = null))
        assertEquals(null, user("c2").area)
    }

    @Test
    fun `given a professional form with an area when saving then the professional keeps no area`() = runTest {
        val before = user("p1")
        val form = ProfileForm(
            name = before.name,
            phone = before.phone,
            categories = before.professional!!.categories,
            area = Place(GeoPoint(-34.7206, -58.2546), "Calle 123, Quilmes"),
        )

        users.updateProfile("p1", form)

        assertEquals(null, user("p1").area)
    }
}
