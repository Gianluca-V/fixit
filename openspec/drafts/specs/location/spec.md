# Especificación: Ubicación (`location`)

## Propósito

Obtener la ubicación del dispositivo para (a) la ubicación de una solicitud y (b) la ubicación base
del profesional, calcular distancias con Haversine en dominio y resolver una dirección legible.
Define el comportamiento por fase: simulado en Fase 1, geohash en Fase 2, GPS real con permisos
en Fase 3.

Módulos: `:core:domain` (`GeoPoint`, `HaversineDistance`, `LocationProvider` (interfaz),
`GetCurrentLocationUseCase`), `:core:data` (`FakeLocationProvider`, `FusedLocationProvider`,
`GeocoderAddressResolver`), `:features:*` (UI de permisos), `:app` (manifest).

## Requisitos

### Requirement: Modelo de coordenadas [Fase 1]

`GeoPoint(latitude: Double, longitude: Double)` DEBE validar en su construcción
`latitude ∈ [-90, 90]` y `longitude ∈ [-180, 180]` (fábrica `GeoPoint.of(...)` que devuelve
`AppResult`, o `require` con test que verifique la excepción). `Location(point: GeoPoint, address: String)`
agrupa coordenadas y dirección.

#### Escenario: Coordenadas inválidas
- DADO latitud 91
- ENTONCES la creación falla con `Validation.InvalidCoordinates`

### Requirement: Distancia Haversine en dominio [Fase 1]

`HaversineDistance.km(a: GeoPoint, b: GeoPoint): Double` DEBE implementarse en Kotlin puro con
radio terrestre `6371.0088` km. DEBE ser simétrica y devolver 0 para puntos iguales.

#### Escenario: Mismo punto
- DADO a == b
- ENTONCES la distancia es `0.0`

#### Escenario: Distancia conocida
- DADO Florencio Varela centro (-34.8065, -58.2788) y Quilmes centro (-34.7206, -58.2546)
- ENTONCES la distancia es ≈ 9.8 km (tolerancia ±0.2 km)

#### Escenario: Simetría
- ENTONCES `km(a, b) == km(b, a)` (tolerancia 1e-9)

#### Escenario: Antimeridiano
- DADO (0, 179.9) y (0, -179.9)
- ENTONCES la distancia es ≈ 22.2 km

### Requirement: Proveedor de ubicación abstracto [Fase 1]

El dominio DEBE definir `interface LocationProvider { suspend fun currentLocation(): AppResult<GeoPoint> }`
y `interface AddressResolver { suspend fun resolve(point: GeoPoint): AppResult<String> }`.
Las features obtienen la ubicación solo a través de `GetCurrentLocationUseCase`, que combina ambos
y devuelve `Location`. Si no hay dirección resoluble, DEBE devolver la ubicación con `address`
vacía para que el usuario la complete manualmente.

#### Escenario: Dirección no resoluble
- DADO `AddressResolver` que devuelve `Error(Network)`
- CUANDO se ejecuta `GetCurrentLocationUseCase`
- ENTONCES el resultado es `Success(Location(point, address = ""))`

### Requirement: Ubicación simulada [Fase 1]

En Fase 1, `FakeLocationProvider` DEBE devolver una ubicación fija configurable (por defecto
Florencio Varela, -34.8065, -58.2788) y `FakeAddressResolver` una dirección mock
("Av. San Martín 2000, Florencio Varela"). PUEDE ofrecer en modo debug un selector de ubicaciones
mock (Florencio Varela, Quilmes, Berazategui) para probar el matching.

#### Escenario: Botón "Usar mi ubicación" en Fase 1
- DADO la pantalla de creación de solicitud
- CUANDO el usuario toca "Usar mi ubicación"
- ENTONCES se completa la ubicación con el punto y dirección mock

#### Escenario: Simular falla
- DADO `FakeLocationProvider` configurado con `Error(LocationUnavailable)`
- ENTONCES el ViewModel muestra el mensaje de ubicación no disponible y permite ingresar la dirección a mano

### Requirement: Dirección editable [Fase 1]

El usuario DEBE poder editar el texto de la dirección. Las coordenadas NO DEBEN poder editarse
manualmente; provienen siempre del proveedor de ubicación.

#### Escenario: Editar dirección
- DADO una ubicación obtenida
- CUANDO el usuario cambia la dirección a "Calle 123, Quilmes"
- ENTONCES la solicitud se crea con esa dirección y las coordenadas obtenidas

### Requirement: Geohash para consultas [Fase 2]

En Fase 2, al persistir solicitudes DEBE calcularse `geohash` con
`GeoFireUtils.getGeoHashForLocation(GeoLocation(lat, lng))` (dependencia
`com.firebase:geofire-android-common`), y la consulta del feed DEBE usar
`getGeoHashQueryBounds`. El filtro exacto por distancia DEBE seguir siendo `HaversineDistance`
en dominio. El geohash es un detalle de `:core:data` y NO DEBE aparecer en el modelo de dominio.

#### Escenario: Geohash fuera del dominio
- DADO el modelo `RepairRequest` de dominio
- ENTONCES no contiene ningún campo `geohash`; solo `RequestDto` lo tiene

### Requirement: Permisos de ubicación en tiempo de ejecución [Fase 3]

El manifest DEBE declarar `ACCESS_COARSE_LOCATION` y `ACCESS_FINE_LOCATION`. La UI DEBE pedir el
permiso con `rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions())`
solo cuando el usuario toca "Usar mi ubicación", mostrar una justificación si
`shouldShowRequestPermissionRationale` es verdadero, y aceptar ubicación aproximada.

#### Escenario: Permiso concedido
- DADO el permiso no otorgado
- CUANDO el usuario toca "Usar mi ubicación" y concede
- ENTONCES se obtiene la ubicación real

#### Escenario: Permiso denegado
- CUANDO el usuario deniega
- ENTONCES se muestra un mensaje explicativo, el campo de dirección queda editable y la publicación requiere reintentar la ubicación

#### Escenario: Denegado permanentemente
- DADO que el usuario marcó "No volver a preguntar"
- ENTONCES se ofrece un botón que abre la configuración de la app (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`)

### Requirement: GPS real [Fase 3]

`FusedLocationProvider` DEBE usar `FusedLocationProviderClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationToken)`
con timeout de 10 s; si devuelve `null` o vence el tiempo, DEBE devolver
`Error(LocationUnavailable)`. `GeocoderAddressResolver` DEBE usar `Geocoder` (API asíncrona en
API 33+, síncrona en un dispatcher de IO en versiones previas). El intercambio Fake → real se hace
solo con bindings de Hilt.

#### Escenario: GPS desactivado
- DADO la ubicación del sistema desactivada
- CUANDO se solicita la ubicación
- ENTONCES el resultado es `Error(LocationUnavailable)`

#### Escenario: Ubicación base del profesional
- DADO el registro de profesional en Fase 3
- CUANDO toca "Usar mi ubicación como base"
- ENTONCES `baseLocation` se completa con la ubicación real
