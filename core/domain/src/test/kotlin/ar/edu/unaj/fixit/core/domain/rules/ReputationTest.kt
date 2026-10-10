package ar.edu.unaj.fixit.core.domain.rules

import ar.edu.unaj.fixit.core.domain.model.Reputation
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReputationTest {
    @Test
    fun `given a rating sum of 13 and 3 reviews when averaging then the result is 4_3`() {
        assertEquals(BigDecimal("4.3"), Reputation(ratingSum = 13, reviewCount = 3).average())
    }

    @Test
    fun `given a rating sum of 17 and 4 reviews when averaging then the midpoint 4_25 rounds half up to 4_3`() {
        assertEquals(BigDecimal("4.3"), Reputation(ratingSum = 17, reviewCount = 4).average())
    }

    @Test
    fun `given a perfect average when averaging then it keeps one decimal`() {
        assertEquals(BigDecimal("5.0"), Reputation(ratingSum = 10, reviewCount = 2).average())
    }

    @Test
    fun `given no reviews when averaging then the result is null without dividing by zero`() {
        assertNull(Reputation(ratingSum = 0, reviewCount = 0).average())
    }
}
