package ar.edu.unaj.fixit.core.domain.rules

import ar.edu.unaj.fixit.core.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HaversineTest {
    private val floroVarela = GeoPoint(-34.8065, -58.2788)
    private val quilmes = GeoPoint(-34.7206, -58.2546)

    @Test
    fun `given the same point twice when computing the distance then it is zero`() {
        assertEquals(0.0, Haversine.km(floroVarela, floroVarela), 0.0)
    }

    @Test
    fun `given Florencio Varela and Quilmes when computing the distance then it is about 9_8 km`() {
        assertEquals(9.8, Haversine.km(floroVarela, quilmes), 0.2)
    }

    @Test
    fun `given two different points when computing both directions then the distances are equal`() {
        assertEquals(Haversine.km(floroVarela, quilmes), Haversine.km(quilmes, floroVarela), 1e-9)
    }

    @Test
    fun `given points across the antimeridian when computing the distance then it takes the short way`() {
        assertEquals(22.2, Haversine.km(GeoPoint(0.0, 179.9), GeoPoint(0.0, -179.9)), 0.05)
    }

    @Test
    fun `given points one degree of latitude apart when computing the distance then it is 111_195 km`() {
        assertEquals(111.195, Haversine.km(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0)), 0.01)
    }

    @Test
    fun `given antipodal points when computing the distance then the result is half the circumference and not NaN`() {
        val halfCircumference = Math.PI * 6371.0088
        val pairs = listOf(
            GeoPoint(0.0, 0.0) to GeoPoint(0.0, 180.0),
            GeoPoint(90.0, 0.0) to GeoPoint(-90.0, 0.0),
            GeoPoint(-34.8065, -58.2788) to GeoPoint(34.8065, 121.7212),
        )
        pairs.forEach { (a, b) ->
            val km = Haversine.km(a, b)
            assertFalse("distance between $a and $b is NaN", km.isNaN())
            assertEquals(halfCircumference, km, 0.01)
        }
    }
}
