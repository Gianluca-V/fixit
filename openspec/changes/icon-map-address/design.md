# Diseño: ícono de la app, mapa y autocompletado de dirección (`icon-map-address`)

> La motivación está en `proposal.md`. Los requisitos están en `specs/`. Este documento explica cómo implementarlo respetando la arquitectura de `fixit-mvp` (`openspec/changes/fixit-mvp/design.md`).

## Context

- **Ubicación hoy**:
  - "Usar mi ubicación" llama a `GetCurrentPlaceUseCase`, que combina `LocationProvider` (Fused, Fase 3) con `AddressResolver` (`GeocoderAddressResolver`, Fase 3).
  - Lo usan `CreateRequestViewModel` (campos `point` + `address`), `SignUpViewModel` y `ProEditProfileViewModel` (`ProfileFormState.base`).
  - El permiso lo pide `LocationPermission` (`:core:ui/permission`) recién cuando se toca el botón.
- **Formulario de perfil**: `ProfileFormFields` (`:core:ui`) lo comparten el registro y las dos ediciones. La sección del punto base solo se muestra con `showProfessionalFields`.
- **Usuario**:
  - `User` no tiene ubicación para el cliente. `ProfessionalInfo.base` es la del profesional.
  - `UserDto` guarda `baseLat`, `baseLng` y `baseAddress`.
  - En `firestore.rules`, `users/{uid}` limita la edición a `name`, `phone`, `categories`, `baseLat`, `baseLng`, `baseAddress` y `radiusKm`.
- **Red**: `NetworkModule` provee un único `OkHttpClient` con `okhttp-coroutines` (`executeAsync()`), y `kotlinx-serialization-json` se usa solo con la API de `JsonElement`. `:core:data` ya tiene tests JVM para el parseo de respuestas (`ParseUploadResponseTest`).
- **Reglas de dependencias**:
  - `:features:*` no dependen entre sí ni de `:core:data`.
  - La navegación se resuelve en `:app`.
  - `:core:ui` hoy no tiene Hilt ni tests.
- **El tema** usa colores dinámicos (Android 12 o superior), así que el ícono no puede depender del color primario en tiempo de ejecución: lleva colores fijos.

## Goals / Non-Goals

**Goals**
- Un único selector de ubicación reutilizable en las tres pantallas, sin duplicar lógica en tres ViewModels y sin violar las reglas de dependencias.
- Que la lógica del selector (espera entre teclas, descarte de resultados viejos, dirección al detenerse el mapa, sugerencia elegida) se pruebe en la JVM con dobles escritos a mano.
- Cambiar de servicio de búsqueda o de mapa tocando solo bindings o un archivo de configuración.

**Non-Goals**
- Mostrar solicitudes como pines o el mapa en el detalle.
- Teselas propias o mapas sin conexión.
- Reemplazar el `Geocoder` del dispositivo para la dirección inversa.
- Cambiar `SignUpScreen`: se le pasa `onPickOnMapClick = null`, así que no se ve el botón.

## Decisions

### D1. Biblioteca de mapa: MapLibre Native Android con estilo de OpenFreeMap

Se usa `org.maplibre.gl:android-sdk`, embebido en Compose con `AndroidView`, y el estilo vectorial `https://tiles.openfreemap.org/styles/liberty`. Ese estilo usa datos de OpenStreetMap, no necesita clave ni tiene límite declarado, y trae la atribución © OpenMapTiles © OpenStreetMap.

| Alternativa | Por qué no |
|---|---|
| osmdroid | Está en modo mantenimiento. Usa teselas ráster de `tile.openstreetmap.org`, cuya política de uso pide no usarlas como servicio principal de una app y exige un `User-Agent` propio. |
| Google Maps SDK | Requiere una clave de API con una cuenta de facturación (tarjeta). Viola la restricción dura. |
| `maplibre-compose` (biblioteca Compose nativa) | Es más joven y su API cambia entre versiones. `AndroidView` alcanza para un mapa con un solo pin. |

**Ciclo de vida**: `MapLibre.getInstance(context)` se llama una vez, perezosamente, al crear la vista. La vista de mapa recibe `onStart`, `onResume`, `onPause`, `onStop` y `onDestroy` desde el `LifecycleOwner` de Compose con un `DisposableEffect`. Los gestos de rotación e inclinación se desactivan.

**Pin**: es un ícono de Compose superpuesto en el centro, no un *marker* del mapa. Así, el punto elegido es siempre el centro de la cámara y no hay estado duplicado.

**La URL del estilo** va en `strings.xml` como recurso no traducible (`map_style_url`) para poder cambiarla sin tocar el código.

### D2. Servicio de autocompletado: Photon (komoot)

La búsqueda se hace con `GET https://photon.komoot.io/api/?q=<texto>&limit=8&lat=<lat>&lon=<lon>&bbox=-73.6,-55.1,-53.6,-21.8`:
- El `bbox` (Argentina) restringe los resultados.
- `lat` y `lon` (centro actual del mapa) priorizan los cercanos.
- Se piden 8 resultados porque se descartan los inválidos antes de quedarse con 5.

Además:
- Va un `User-Agent` identificable: `FixIt/<versionName> (UNAJ TP2)`.
- El timeout es de 10 s y se aplica con `firebaseCall(SEARCH_TIMEOUT_MS)`, el mismo envoltorio que usan la subida a Cloudinary y Firestore. Así el mapeo de errores queda en un solo lugar y el cliente compartido no se modifica.

| Alternativa | Por qué no |
|---|---|
| Nominatim | Su política de uso prohíbe explícitamente el autocompletado del lado del cliente. |
| `Geocoder.getFromLocationName` | Es gratuito, pero da malos resultados con texto parcial y depende del *backend* del dispositivo. Queda como alternativa de respaldo, detrás de la misma interfaz. |
| Google Places | Requiere facturación. |

**Formato de la respuesta**: GeoJSON `FeatureCollection`. Se lee `geometry.coordinates = [lon, lat]` y las propiedades `name`, `street`, `housenumber` y `city` (si falta, `district`, `locality` o `county`, en ese orden).

**Funciones puras** en `:core:data/photon` (sin Android, tests JVM):
- `parsePhotonResponse(body: String): List<PhotonFeature>`
- `formatPhotonAddress(feature): String?`, que implementa el requisito "Dirección legible de una sugerencia".

**Errores**:
- `IOException` → `Network`.
- Respuesta HTTP que no es 2xx, o JSON inválido → `Unknown`, con `Log.w` antes de convertir, igual que hace Gian en los mapeos de `:core:data`.
- `CancellationException` se relanza.

### D3. Contrato de dominio

```kotlin
// :core:domain/repository
interface AddressSearch {
    /** Raw suggestions for [query] near [near]; the use case validates and trims them. */
    suspend fun search(query: String, near: GeoPoint?): AppResult<List<Place>>
}

// :core:domain/usecase
class SearchAddressUseCase @Inject constructor(private val search: AddressSearch) {
    suspend operator fun invoke(query: String, near: GeoPoint?): AppResult<List<Place>> {
        val text = query.trim()
        if (text.length < MIN_QUERY_LENGTH) return AppResult.Success(emptyList())   // no service call
        return when (val result = search.search(text, near)) {
            is AppResult.Success -> AppResult.Success(
                result.data
                    .filter { it.address.isNotBlank() && Validators.coordinates(it.point) == null }
                    .take(MAX_SUGGESTIONS),
            )
            is AppResult.Error -> result
        }
    }
    companion object { const val MIN_QUERY_LENGTH = 3; const val MAX_SUGGESTIONS = 5 }
}
```

**Por qué la validación vive en el caso de uso**: así las reglas del spec (mínimo de 3, máximo de 5, descarte de inválidas) se prueban en `:core:domain` con un `TestAddressSearch` y no dependen de Photon.

**Implementaciones**:
- `FakeAddressSearch` (`:core:data/fake`): un catálogo fijo de lugares de Florencio Varela, Quilmes y Berazategui. Compara sin distinguir mayúsculas ni tildes (`Normalizer` NFD sin diacríticos).
- `PhotonAddressSearch` (`:core:data/photon`).

El binding va en `NetworkModule` (`@Binds AddressSearch → PhotonAddressSearch`).

### D4. Selector como diálogo de pantalla completa en `:core:ui`, con su propio ViewModel

`MapPickerDialog(initial: Place?, onConfirm: (Place) -> Unit, onDismiss: () -> Unit)` es un `Dialog` con `DialogProperties(usePlatformDefaultWidth = false)`. Adentro tiene un `Scaffold` con barra de búsqueda, lista de sugerencias, mapa, pin, campo de dirección y los botones "Cancelar" y "Confirmar". Cada pantalla que lo usa guarda un `rememberSaveable { mutableStateOf(false) }` para saber si está abierto.

`MapPickerViewModel` (`@HiltViewModel`, inyección asistida con `initial: Place?`) se obtiene con `hiltViewModel(key = "map-picker-<n>")`. La clave cambia en cada apertura (un contador `rememberSaveable`), así que cada apertura arranca limpia y una rotación conserva el estado.

**Estado**:

```kotlin
data class MapPickerUiState(
    val center: GeoPoint,
    val address: String,
    val isResolving: Boolean = false,
    val query: String = "",
    val suggestions: List<Place> = emptyList(),
    val searchUnavailable: Boolean = false,
    val cameraTarget: CameraTarget? = null,   // one-shot: move the camera to a suggestion; cleared with onCameraMoved()
)
```

**Eventos**:

| Evento | Qué hace |
|---|---|
| `onQueryChange(text)` | Actualiza `query`. Un `MutableStateFlow<String>` con `debounce(300)` + `mapLatest { searchAddress(it, center) }` (o `collectLatest`) asegura la espera y el descarte de resultados viejos. Texto vacío → sugerencias vacías de inmediato. |
| `onSuggestionClick(place)` | `center = place.point`, `address = place.address`, limpia `query` y `suggestions` y emite `cameraTarget`. Guarda `pendingProgrammaticMove = place.point`. |
| `onCameraIdle(point)` | Si `pendingProgrammaticMove` está cerca de `point` (menos de 1e-5°), lo limpia y no resuelve. Si no: `center = point`, `isResolving = true` y luego `addressResolver.resolve(point)`. `collectLatest` cancela la resolución anterior. Si falla → `address = ""`. |
| `onAddressChange(text)` | Actualiza `address`. Si se edita mientras hay una resolución en curso, la cancela: lo que escribe el usuario gana. |
| `onConfirm()` | Devuelve `Place(center, address.trim())`. La validación final la hace el formulario. |

Usa `AddressResolver` (interfaz de dominio) directamente, no `GetCurrentPlaceUseCase`: el mapa nunca toca el GPS, así que no se pide el permiso.

| Alternativa | Por qué no |
|---|---|
| Un destino de Navigation 3 en `:app` con un resultado devuelto a la feature | Habría que inventar un canal de resultados entre entradas (Nav3 no lo trae). Además el formulario tendría que sobrevivir a la ida y vuelta, y la clave tendría que viajar por los tres *callbacks* de `:app`. El diálogo comparte el `ViewModelStoreOwner` de la entrada y no cruza módulos. |
| Lógica del selector repetida en los tres ViewModels | Triplica el código y los tests. |
| Un módulo nuevo `:features:map` | Las features no pueden depender entre sí, así que igual tendría que vivir en `:core:ui`. |

**Costo**: `:core:ui` gana el *plugin* de Hilt + KSP, `hilt-lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` y las dependencias de test (JUnit, coroutines-test, `testFixtures(:core:domain)`). Es aceptable: sigue sin depender de `:core:data`.

### D5. Zona del cliente en el modelo

```kotlin
data class User(
    // ...existing fields
    val area: Place? = null, // client only: optional "de dónde es"; always null for a professional
)
data class ProfileForm(/* ... */ val area: Place? = null)
```

**`UpdateProfileUseCase`**: para un `CLIENT`, después de validar nombre y teléfono valida `area` si no es `null`:
- Dirección en blanco → `LOCATION_REQUIRED`.
- `Validators.coordinates` → `INVALID_COORDINATES`.

Ambos errores van al `FormField.LOCATION` existente. Para un `PROFESSIONAL`, `area` se ignora. `SignUpUseCase` no cambia: el registro nunca envía zona.

**Repositorios**:
- `FakeUserRepository`: copia `area`.
- `FirebaseUserRepository`:
  - Escribe `areaLat`, `areaLng` y `areaAddress`.
  - Con `area == null` escribe `FieldValue.delete()` en los tres campos: el formulario del cliente siempre envía la zona completa, así que `null` significa "quitar".
  - `UserDto.toDomain()` arma `area` solo si los tres campos existen. Un documento viejo se lee sin zona.

**`firestore.rules`**: el `hasOnly` de la edición propia suma `areaLat`, `areaLng` y `areaAddress`. Igual que con el resto del perfil, no se valida el tipo.

**Debilidad aceptada**: la zona queda legible para cualquier usuario autenticado, como el teléfono. El texto de la pantalla lo aclara ("Visible para los profesionales").

### D6. Integración en las pantallas

**`ProfileFormFields`** gana dos parámetros:
- `onPickOnMapClick: (() -> Unit)? = null`: si no es `null`, junto a "Usar mi ubicación" aparece "Elegir en el mapa".
- `showClientArea: Boolean = false`: muestra la sección "Zona" con la dirección (o "Sin zona"), "Usar mi ubicación", "Elegir en el mapa" y "Quitar zona".

Los ViewModels reciben el `Place` confirmado con `onPlacePicked(place)`:

| Pantalla | Cambio |
|---|---|
| `ProEditProfileScreen` | `onPickOnMapClick` abre el diálogo con `initial = form.base`. `onPlacePicked` → `form.copy(base = place)`. |
| `ClientEditProfileScreen` | `showClientArea = true`. Gana `onUseLocation`, igual que el profesional: inyecta `GetCurrentPlaceUseCase`, con `LocationPermission` e `isLocating`. Gana también `onPlacePicked`. "Quitar zona" no necesita un evento propio: `ProfileFormFields` lo resuelve con `onFormChange(area = null)`. `ProfileFormState` gana `area`, y `from(user)` la precarga. |
| `CreateRequestScreen` | Muestra "Elegir en el mapa" junto a "Usar mi ubicación" (`initial = place`). `onPlacePicked` → `point = place.point`, `address = place.address`. |

**`CreateRequestViewModel`** gana `AuthRepository` y `UserRepository`. En `init` lee una vez al usuario actual y, si tiene `area` y el formulario no tiene ubicación, la precarga. Si la lectura falla, no muestra error: el formulario queda vacío como antes.

**Permisos**: el diálogo nunca los pide. "Usar mi ubicación" sigue envuelto en `LocationPermission`.

**Textos nuevos** (solo en `strings.xml`, en español neutro y tratando al usuario de usted):

| Clave | Texto |
|---|---|
| `map_pick_on_map` | "Elegir en el mapa" |
| `map_search_hint` | "Buscar una dirección" |
| `map_search_unavailable` | "La búsqueda no está disponible. Puede mover el mapa para elegir el lugar." |
| `map_confirm` | "Confirmar" |
| `map_cancel` | "Cancelar" |
| `map_address_label` | "Dirección" |
| `profile_field_area` | "Zona" |
| `profile_area_empty` | "Sin zona" |
| `profile_area_clear` | "Quitar zona" |
| `profile_area_visibility` | "Visible para los profesionales." |

### D7. Ícono adaptativo

**Recursos** (revisado: el logo lleva la palabra "FixIt" y también se muestra en el inicio de sesión):
- `:core:ui` `res/values/colors.xml`: `fixit_logo_background`, el teal de la marca (`#0B5F62`). Es el fondo del ícono.
- `:core:ui` `res/drawable/fixit_logo_mark.xml`: un vector de 108 dp con una llave inglesa blanca a 45° y, delante y en el centro, "FixIt" en Manrope Bold ("Fix" en teal, "It" en arcilla, como el wordmark), con un halo claro. El texto está convertido a trazados, porque un vector drawable no dibuja texto. Todo queda dentro del círculo seguro de 66 dp. Es el primer plano del ícono y lo usa el componente `FixItLogo`.
- `:app` `res/drawable/ic_launcher_monochrome.xml`: la llave y la palabra en un solo color; las letras quedan caladas donde se cruzan con la llave.
- `:app` `res/mipmap-anydpi/ic_launcher.xml` y `ic_launcher_round.xml`, con `<adaptive-icon>` que combina el fondo, el primer plano y `<monochrome>`.

La barra superior sigue mostrando solo el wordmark de texto, sin el logo.

En el manifest se agregan `android:icon="@mipmap/ic_launcher"` y `android:roundIcon="@mipmap/ic_launcher_round"`.

No se usa `mipmap-anydpi-v26` porque el `minSdk` ya es 26, y no hay PNG.

Los colores son fijos porque el tema es dinámico: el ícono no puede seguirlo.

## Secuencia: elegir una sugerencia y confirmar

```
User         MapPickerDialog          MapPickerViewModel          SearchAddressUseCase/AddressSearch
 | types "Calle 12" |                         |                                   |
 |----------------->| onQueryChange           |                                   |
 |                  |------------------------>| debounce 300 ms                   |
 |                  |                         |---- invoke("Calle 12", center) -->|
 |                  |                         |<--- Success([S1..S5]) ------------|
 |                  |<-- suggestions ---------|                                   |
 | taps S2          | onSuggestionClick(S2)   |                                   |
 |----------------->|------------------------>| center=S2, address=S2, pending=S2 |
 |                  |<-- cameraTarget=S2 -----|                                   |
 |                  | camera animates; idle   |                                   |
 |                  | onCameraIdle(≈S2)       |                                   |
 |                  |------------------------>| matches pending: no reverse       |
 | taps Confirmar   | onConfirm -> Place(S2)  |                                   |
 |----------------->| onConfirm(place) -> screen ViewModel.onPlacePicked(place)    |
```

## Risks / Trade-offs

- **[Riesgo] Política o disponibilidad de Photon u OpenFreeMap.** → La búsqueda va detrás de `AddressSearch` y la URL del estilo en un recurso. El selector sigue usable sin sugerencias. Hay un ítem del checklist manual sin conexión.
- **[Riesgo] La versión de MapLibre o su `minSdk` no son compatibles con AGP 9.2.1, `compileSdk` 37 o `minSdk` 26.** → La tarea 3.1 fija primero la versión estable vigente y compila `:core:ui` antes de escribir código de mapa. Si falla, se evalúa osmdroid con teselas de OpenFreeMap en formato ráster, y la decisión se registra en D1.
- **[Compromiso] Tamaño del APK.** MapLibre agrega bibliotecas nativas por ABI. → Aceptado. Si hace falta, se limitan las ABI del *debug* a `x86_64` y `arm64-v8a`.
- **[Riesgo] Un `onCameraIdle` espurio después de mover la cámara a una sugerencia sobrescribe su dirección.** → Hay una tolerancia en `pendingProgrammaticMove` y un test JVM.
- **[Compromiso] El `Dialog` no tiene una animación de navegación propia.** → Aceptado: se comporta como los diálogos de confirmación que ya existen.
- **[Riesgo] Conflictos con el trabajo de Gian en `ProfileFormFields` y en las pantallas de edición.** → Rebase sobre `main` antes de cada unidad. Los parámetros nuevos tienen valores por defecto, así que las llamadas existentes compilan sin cambios.

## Migration Plan

1. Mergear por unidades (ver `tasks.md`). Los parámetros nuevos tienen valores por defecto, así que cada unidad compila y pasa `./gradlew test` por sí sola.
2. Desplegar `firestore.rules` (`firebase deploy --only firestore:rules`) **antes** de publicar un APK que escriba la zona. Un APK viejo con las reglas nuevas sigue funcionando.
3. **Reversión**: está en `proposal.md` (Plan de reversión). Las reglas nuevas son un superconjunto de las anteriores, así que no hace falta revertirlas.

## Checklist manual

Se completa al cerrar el cambio, en un emulador con Android 14 y conexión, salvo donde se indica otra cosa:

1. **`[app-icon]`**:
   - Ícono en el lanzador.
   - Distintas máscaras.
   - Ícono temático (Android 13 o superior).
   - Ícono en ajustes y recientes.
2. **`[address-search]`**:
   - Mapa y atribución visibles.
   - Mapa sin conexión (modo avión).
   - Gestos del mapa.
   - Comprobar a mano que escribir "Av. Calchaquí" sugiere direcciones de Florencio Varela o Quilmes.
3. **`[profiles]`**:
   - Perfil de cliente con zona y sin zona.
   - La zona se ve en otro dispositivo.
   - Otro usuario no puede cambiar la zona: escribir a mano con la consola o el *Emulator Suite*.
4. **`[repair-requests]`**: elegir en el mapa no pide permiso.
5. **Medir el APK de *debug*** antes y después del cambio, y anotar la diferencia en este documento.

## Open Questions

- ¿Hace falta limitar las ABI del APK de *debug* por tamaño? Se decide con la medición del punto 5 del checklist y no cambia las specs ni las tareas.
