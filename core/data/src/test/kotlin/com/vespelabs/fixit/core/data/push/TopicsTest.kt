package com.vespelabs.fixit.core.data.push

import com.vespelabs.fixit.core.domain.model.ServiceCategory
import com.vespelabs.fixit.core.domain.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Design 14b.4 (revised 2026-10-26): desired topics and the history-free reconciliation plan. */
class TopicsTest {
    private val allCategoryTopics = ServiceCategory.entries.map { "category_${it.name}" }.toSet()

    @Test
    fun `Temas de un cliente - a client c1 wants only user_c1`() {
        assertEquals(setOf("user_c1"), topicsFor(TestData.client(id = "c1")))
    }

    @Test
    fun `Temas de un profesional - p1 with PLUMBING and GAS wants its user topic and both category topics`() {
        val pro = TestData.professional(id = "p1", categories = setOf(ServiceCategory.PLUMBING, ServiceCategory.GAS))

        assertEquals(setOf("user_p1", "category_PLUMBING", "category_GAS"), topicsFor(pro))
    }

    @Test
    fun `Temas sin sesion - no user wants no topic`() {
        assertEquals(emptySet<String>(), topicsFor(null))
    }

    @Test
    fun `given desired topics when planning then every desired topic is subscribed`() {
        val desired = setOf("user_p1", "category_GAS")

        assertEquals(desired, topicPlan(desired, registeredUserTopics = emptySet()).subscribe)
    }

    @Test
    fun `given desired topics when planning then every non-desired category of the eight and every non-desired registered user topic is unsubscribed`() {
        val plan = topicPlan(setOf("user_p1", "category_GAS"), registeredUserTopics = setOf("user_c1", "user_p1"))

        assertEquals(allCategoryTopics - "category_GAS" + "user_c1", plan.unsubscribe)
    }

    @Test
    fun `given any plan then a desired topic is never unsubscribed`() {
        val desired = setOf("user_p1", "category_GAS", "category_PLUMBING")

        val plan = topicPlan(desired, registeredUserTopics = setOf("user_p1", "user_old"))

        assertTrue(plan.unsubscribe.intersect(desired).isEmpty())
    }

    @Test
    fun `given no session when planning then the eight categories and every registered user topic are unsubscribed`() {
        val plan = topicPlan(emptySet(), registeredUserTopics = setOf("user_c1", "user_p1"))

        assertEquals(emptySet<String>(), plan.subscribe)
        assertEquals(allCategoryTopics + "user_c1" + "user_p1", plan.unsubscribe)
    }
}
