package ar.edu.unaj.fixit.core.domain.model

data class GeoPoint(val latitude: Double, val longitude: Double)

/** Not named "Location" to avoid confusion with android.location.Location. */
data class Place(val point: GeoPoint, val address: String)
