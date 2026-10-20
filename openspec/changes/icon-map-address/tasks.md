# Tareas: ícono de la app, mapa y autocompletado de dirección (`icon-map-address`)

> Cambio `icon-map-address`. Fuentes: `proposal.md`, `design.md` y `specs/*/spec.md`. Rama: `feat/icon-map-address`. Se archiva **después** de `fixit-mvp`.
>
> **Convenciones**: las mismas de `fixit-mvp`:
> - Identificadores, tests y comentarios en inglés. Tests con nombre `` `given X when Y then Z` ``.
> - Textos visibles solo en `strings.xml`, en español neutro y tratando al usuario de usted.
> - Sin biblioteca de mocks.
> - Conventional Commits sin atribución de IA.
> - Ciclo estricto: RED → GREEN → REFACTOR.
> - Gradle: `JAVA_HOME=/home/sergio/android-studio/jbr ANDROID_HOME=/home/sergio/Android/Sdk sh ./gradlew …`.
>
> **Etiquetas de escenario**: `[icon]` (app-icon), `[addr]` (address-search), `[loc]`, `[prof]`, `[rr]`. Los escenarios `[manual]` se cubren en el grupo 8 (checklist del `design.md`).

## Review Workload Forecast

| Campo | Valor |
|-------|-------|
| Líneas cambiadas estimadas | 2.400–3.200 (≈40 % tests): ícono ≈ 120; dominio ≈ 300; datos ≈ 500; selector ≈ 900; integración ≈ 700; reglas y docs ≈ 80 |
| Riesgo de superar 400 líneas | Alto |
| PRs encadenados recomendados | Sí |
| División sugerida | 6 unidades (ver abajo) |
| Estrategia de entrega | ask-on-risk |
| Estrategia de cadena | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

**Decisión pendiente del usuario**: cómo se entrega.
- `stacked-to-main`: un PR por unidad contra `main`, en orden.
- `feature-branch-chain`: la rama `feat/icon-map-address` hace de *tracker* y cada unidad es un PR contra ella.

Recomendación (no vinculante): `feature-branch-chain`, porque Gian trabaja en paralelo sobre `main` y así `main` recibe un solo merge revisado. M3 y M5 pueden superar 400 líneas: en ese caso, dividirlas por subgrupo, como se indica en la tabla.

### Unidades de trabajo

| Unidad | Contenido | Dependencias |
|---|---|---|
| M1 | Ícono adaptativo (grupo 1) | Ninguna |
| M2 | Dominio: `AddressSearch`, `SearchAddressUseCase`, zona del cliente en `User`, `ProfileForm` y `UpdateProfileUseCase` (grupo 2) | Ninguna |
| M3 | Datos: `FakeAddressSearch`, Photon, DTO de usuario con zona, Fake de usuario, bindings y `firestore.rules` (grupo 3). Si pasa de 400 líneas, dividir en 3.1–3.5 y 3.6–3.10. | M2 |
| M4 | `:core:ui`: dependencias, `MapPickerViewModel` (grupo 4) | M2 |
| M5 | `:core:ui`: `MapPickerDialog` con MapLibre y `ProfileFormFields` (grupo 5). Si pasa de 400 líneas, dividir en 5.1–5.3 y 5.4–5.6. | M4 |
| M6 | Integración en creación de solicitud y en ambas ediciones de perfil, más el cierre (grupos 6, 7 y 8) | M3, M5 |

## 1. Ícono adaptativo (`:app`)

- [x] 1.1 Crear `res/drawable/ic_launcher_background.xml` (color sólido `#1565C0`) y `res/drawable/ic_launcher_foreground.xml`: un vector de 108 dp con una llave inglesa blanca a 45°, cuyo trazado quede dentro del círculo seguro de 66 dp (D7). Verificar en la vista previa de Android Studio, o con `:app:assembleDebug`, que los recursos compilan.
- [x] 1.2 Crear `res/drawable/ic_launcher_monochrome.xml` (mismo trazado, un solo color) y `res/mipmap-anydpi/ic_launcher.xml` y `ic_launcher_round.xml` con `<adaptive-icon>` (`background`, `foreground` y `monochrome`). Verificar que `:app:assembleDebug` compila.
- [x] 1.3 Agregar `android:icon="@mipmap/ic_launcher"` y `android:roundIcon="@mipmap/ic_launcher_round"` al `<application>` del manifest. Verificar con `:app:assembleDebug` y con `aapt2 dump badging` sobre el APK que aparezcan `application-icon` y el ícono redondo.

## 2. Dominio (`:core:domain`)

- [x] 2.1 RED `SearchAddressUseCaseTest` con un `TestAddressSearch` nuevo en `testFixtures` (respuesta configurable y registro de llamadas). Cubre `[addr]`:
  - Texto demasiado corto: no hay llamada al servicio.
  - Máximo de sugerencias: 8 → 5, en el mismo orden.
  - Sugerencias inválidas descartadas: sin dirección o con latitud 91.
  - Servicio no disponible: `Network`.
  - El texto que llega al servicio va recortado.

  Verificar que el test falla por falta de la clase.
- [x] 2.2 GREEN: interfaz `repository/AddressSearch` y `usecase/SearchAddressUseCase` según D3. Verificar que `./gradlew :core:domain:test` pasa.
- [x] 2.3 RED en `UpdateProfileUseCaseTest`, para la zona del cliente `[prof]`:
  - Guardar la zona: el repositorio recibe `area`.
  - Quitar la zona: `area = null`.
  - Zona con dirección vacía: `LOCATION_REQUIRED` y nada persistido.
  - Zona con coordenadas inválidas: `INVALID_COORDINATES` y nada persistido.
  - Un profesional con `area` en el formulario: se ignora.

  Verificar que falla.
- [x] 2.4 GREEN: `User.area: Place? = null`, `ProfileForm.area: Place? = null`, y en `UpdateProfileUseCase` la validación de la zona para `CLIENT` (D5). Ajustar `TestUserRepository` y `TestData` (parámetro `area` opcional). Verificar que `./gradlew :core:domain:test` pasa, incluidos los tests anteriores sin cambios.
- [x] 2.5 RED/GREEN en `SignUpUseCaseTest` `[prof]`: "El registro no pide zona". El usuario creado tiene `area == null` aunque el formulario traiga una. Verificar que pasa.

## 3. Datos (`:core:data`)

- [x] 3.1 RED `FakeAddressSearchTest`:
  - "quilmes" encuentra lugares de Quilmes sin distinguir mayúsculas.
  - "calchaqui" encuentra "Av. Calchaquí…" sin distinguir tildes.
  - Un texto sin coincidencias devuelve una lista vacía.

  Verificar que falla.
- [x] 3.2 GREEN `fake/FakeAddressSearch` con un catálogo fijo de Florencio Varela, Quilmes y Berazategui (D3). Verificar que el test pasa.
- [x] 3.3 RED `photon/ParsePhotonResponseTest` con JSON de ejemplo escritos a mano en el test:
  - FeatureCollection con 2 resultados: coordenadas `[lon, lat]` invertidas correctamente.
  - Propiedades faltantes → `null`.
  - JSON inválido → excepción de parseo.

  Verificar que falla.
- [x] 3.4 GREEN `photon/PhotonResponse.kt`: `parsePhotonResponse` con la API de `JsonElement`. Verificar que el test pasa.
- [x] 3.5 RED/GREEN `photon/FormatPhotonAddressTest` + `formatPhotonAddress`, que cubren `[addr]` "Dirección legible de una sugerencia":
  - Calle con número y localidad.
  - Lugar sin calle.
  - Calle sin número.
  - Localidad tomada de `district`, `locality` o `county` cuando falta `city`.
  - Nada usable → `null`.

  Verificar que pasa.
- [x] 3.6 RED `photon/PhotonRequestTest`: `buildPhotonRequest(query, near, userAgent)` arma la URL con `q` codificado, `limit=8`, `bbox` de Argentina, y `lat`/`lon` solo si `near` no es `null`, más la cabecera `User-Agent`. Verificar que falla.
- [x] 3.7 GREEN `photon/PhotonAddressSearch` (`@Inject`, usa el `OkHttpClient` compartido; timeout de 10 s con `firebaseCall`). Errores según D2: `IOException` → `Network`; respuesta que no es 2xx o JSON inválido → `Unknown`, con `Log.w`; `CancellationException` se relanza. Agregar un test JVM del mapeo de errores, siguiendo el patrón de `PhotoUploadErrorsTest`. Verificar que `./gradlew :core:data:test` pasa.
- [x] 3.8 RED en `DtosTest` `[prof]`:
  - "Usuario anterior sin zona": un documento sin campos `area*` se lee como cliente con `area == null`.
  - Los campos de una zona se leen y se escriben (`areaLat`, `areaLng`, `areaAddress`).
  - `area == null` en la edición de un cliente produce el borrado de los tres campos.

  Verificar que falla.
- [x] 3.9 GREEN: `UserFields` y `UserDto` con la zona, `toDomain()` y el mapa de actualización de perfil con `FieldValue.delete()` (D5), y `FakeUserRepository`/`FakeDatabase` copiando `area`. Agregar una zona a un cliente de `SeedData` y ajustar `SeedDataTest`. Verificar que `./gradlew :core:data:test` pasa.
- [x] 3.10 Agregar `@Binds AddressSearch → PhotonAddressSearch` en `NetworkModule` (con un comentario sobre cómo volver a `FakeAddressSearch`). En `firestore.rules`, sumar `areaLat`, `areaLng` y `areaAddress` al `hasOnly` de la edición propia y documentar la debilidad aceptada en el comentario de `read`. Verificar que `:app:assembleDebug` compila; el despliegue de las reglas se prueba en 8.3.

## 4. `MapPickerViewModel` (`:core:ui`)

- [ ] 4.1 Agregar a `core/ui/build.gradle.kts` los *plugins* `ksp` y `hilt` y las dependencias `hilt-android`, `hilt-compiler` (KSP), `androidx-hilt-lifecycle-viewmodel-compose` y `lifecycle-runtime-compose`, más las de test `junit`, `kotlinx-coroutines-test` y `testFixtures(project(":core:domain"))`. Verificar que `./gradlew :core:ui:test` corre: agregar un test que falle, comprobar que se reporta, corregirlo y borrarlo.
- [ ] 4.2 RED `MapPickerViewModelTest` (con `StandardTestDispatcher`, `TestAddressSearch` y `TestLocation` como `AddressResolver`), parte de apertura y mapa `[addr]`:
  - Apertura sin ubicación previa: centro en Florencio Varela y ninguna llamada a `LocationProvider`.
  - Apertura con ubicación previa.
  - Mover el mapa resuelve la dirección: `isResolving` pasa a `true` y luego a `false`.
  - Dirección no resoluble en el mapa: dirección vacía.
  - Dos `onCameraIdle` seguidos: solo vale la última resolución.

  Verificar que falla.
- [ ] 4.3 GREEN: `MapPickerViewModel` con inyección asistida (`initial: Place?`) y `MapPickerUiState`, más `onCameraIdle` y `onAddressChange`, según D4. Verificar que los tests de 4.2 pasan.
- [ ] 4.4 RED, parte de búsqueda `[addr]`:
  - Espera entre teclas: 3 cambios en menos de 300 ms → 1 búsqueda con el último texto. Se avanza el tiempo virtual.
  - Resultado viejo descartado: la búsqueda de "Calle 1" responde después que la de "Calle 12" y no pisa sus sugerencias; usar un `TestAddressSearch` con respuestas diferidas.
  - Búsqueda fallida: `searchUnavailable = true`, sin sugerencias, y `onConfirm` sigue funcionando.
  - Borrar el texto oculta las sugerencias.
  - La búsqueda usa el centro actual como `near`.

  Verificar que falla.
- [ ] 4.5 GREEN: `onQueryChange` con `debounce(300)` + `mapLatest` (D4). Verificar que los tests de 4.4 pasan.
- [ ] 4.6 RED/GREEN, sugerencia y confirmación `[addr]`:
  - Elegir una sugerencia: centro y dirección de la sugerencia, se emite `cameraTarget`, se ocultan las sugerencias y el `onCameraIdle` siguiente en el mismo punto no resuelve.
  - `onCameraMoved` limpia `cameraTarget`.
  - Confirmar devuelve `Place(center, address.trim())` con la dirección editada.
  - Editar la dirección durante una resolución cancela esa resolución.

  Verificar que `./gradlew :core:ui:test` pasa.

## 5. `MapPickerDialog` y `ProfileFormFields` (`:core:ui`)

- [ ] 5.1 Agregar al catálogo de versiones `maplibre = "<estable vigente>"` (`org.maplibre.gl:android-sdk`; verificar la última versión estable y su `minSdk` antes de fijarla) y la dependencia en `:core:ui`. Verificar que `:app:assembleDebug` compila con AGP 9.2.1, `compileSdk` 37 y `minSdk` 26. Si no compila, aplicar la alternativa de D1/Riesgos y registrar la decisión en `design.md` (D1).
- [ ] 5.2 `components/MapLibreMap.kt`: un `AndroidView` con `MapView`, el estilo desde `R.string.map_style_url` (no traducible), el ciclo de vida con `DisposableEffect` sobre `LocalLifecycleOwner`, rotación e inclinación desactivadas, `addOnCameraIdleListener` → `onCameraIdle(GeoPoint)` y animación de la cámara cuando cambia `cameraTarget` → `onCameraMoved()`. No lleva test JVM: lo cubre el checklist 8.2. Verificar que compila y que tiene una `@Preview` con un marcador de posición, porque MapLibre no se dibuja en la vista previa.
- [ ] 5.3 `components/MapPickerDialog.kt`: el `Route` (que obtiene el ViewModel con `hiltViewModel` y una clave por apertura) y la `Screen` sin estado (búsqueda, sugerencias, mapa con el pin superpuesto en el centro, campo de dirección y "Cancelar"/"Confirmar"). Los textos de D6 van en `core/ui/res/values/strings.xml`. Verificar que compila y que hay `@Preview` de la `Screen` con sugerencias y con búsqueda no disponible.
- [ ] 5.4 `ProfileFormFields`: parámetro `onPickOnMapClick` (botón "Elegir en el mapa" junto a "Usar mi ubicación" en la sección del punto base), y `showClientArea` con `onClearAreaClick` (sección "Zona" con dirección o "Sin zona", "Usar mi ubicación", "Elegir en el mapa", "Quitar zona" y la nota de visibilidad). `ProfileFormState` gana `area`, y `from(user)` y `toProfileForm()` la incluyen. Verificar con un test JVM de `ProfileFormState` (`from` con y sin zona; `toProfileForm` pasa `area`) y con `./gradlew test`. Las llamadas existentes compilan sin cambios.
- [ ] 5.5 `@Preview` de `ProfileFormFields` con zona y sin zona. Verificar en Android Studio o compilando.
- [ ] 5.6 Verificar que `./gradlew test` y `:app:assembleDebug` pasan en verde.

## 6. Integración en el profesional y el cliente (perfiles)

- [ ] 6.1 RED en `ProEditProfileViewModelTest` `[prof]`: "Punto base elegido en el mapa". `onPlacePicked(Q)` y guardar persisten la base Q con su dirección y conservan las categorías, el radio y la reputación. Verificar que falla.
- [ ] 6.2 GREEN: `ProEditProfileViewModel.onPlacePicked`, y en `ProEditProfileScreen` abrir `MapPickerDialog` con `initial = form.base` desde `onPickOnMapClick`. Verificar que los tests pasan.
- [ ] 6.3 RED en `ClientEditProfileViewModelTest` `[prof]`:
  - Zona precargada al editar.
  - Guardar la zona desde el mapa.
  - Guardar la zona con la ubicación actual (`onUseLocation` con `TestLocation`).
  - Quitar la zona.
  - Zona con dirección vacía: el error se muestra junto a la zona.
  - Ubicación no disponible al tocar "Usar mi ubicación": mensaje, y se conserva la zona anterior.

  Verificar que falla.
- [ ] 6.4 GREEN: `ClientEditProfileViewModel` inyecta `GetCurrentPlaceUseCase` y gana `onUseLocation`, `isLocating`, `onPlacePicked` y `onClearArea`. `ClientEditProfileScreen` usa `showClientArea`, `LocationPermission` y `MapPickerDialog`. Verificar que `./gradlew :features:client:test` pasa.
- [ ] 6.5 `ClientProfileScreen` muestra la zona o "Sin zona" (`[prof]` "Perfil de cliente con zona" y "sin zona", que son `[manual]`). Verificar que compila y que hay `@Preview` con y sin zona.

## 7. Integración en la creación de solicitud (`:features:client`)

- [ ] 7.1 RED en `CreateRequestViewModelTest` `[rr]`:
  - Ubicación precargada con la zona del cliente.
  - Cliente sin zona: la ubicación queda vacía.
  - Si la lectura del usuario falla, no hay mensaje y la ubicación queda vacía.
  - Publicar con una ubicación elegida en el mapa: la solicitud creada tiene Q y su dirección.

  `[loc]` "Ubicación elegida en el mapa": `onPlacePicked` reemplaza juntas las coordenadas y la dirección obtenidas antes con "Usar mi ubicación". Verificar que falla.
- [ ] 7.2 GREEN: `CreateRequestViewModel` inyecta `AuthRepository` y `UserRepository`, precarga la zona una vez en `init` sin pisar una ubicación ya cargada, y gana `onPlacePicked`. `CreateRequestScreen` muestra "Elegir en el mapa" y abre `MapPickerDialog` con `initial = place`. Verificar que `./gradlew :features:client:test` pasa y que los tests existentes de creación siguen en verde.
- [ ] 7.3 Verificar que `./gradlew test` y `./gradlew build` pasan en verde. Revisar que `:features:*` no dependan de `:core:data` ni entre sí (`build.gradle.kts` sin cambios de módulos en las features).

## 8. Checklist manual y cierre

- [ ] 8.1 `[icon]` (4): Ícono en el lanzador; Distintas máscaras; Ícono temático; Ícono en ajustes y recientes.
- [ ] 8.2 `[addr]` (4): Mapa y atribución visibles; Mapa sin conexión; Gestos del mapa; búsqueda real de "Av. Calchaquí" con sugerencias de la zona sur.
- [ ] 8.3 `[prof]` (4): desplegar `firestore.rules` (`firebase deploy --only firestore:rules`). Después: Perfil de cliente con zona; Perfil de cliente sin zona; La zona se ve en otro dispositivo; Otro usuario no puede cambiar la zona.
- [ ] 8.4 `[rr]` (1): Elegir en el mapa no pide permiso. Medir el APK de *debug* antes y después y anotar la diferencia en `design.md` (resuelve la pregunta abierta del `design.md`).
- [ ] 8.5 Cierre: `./gradlew test` y `./gradlew build` en verde, `openspec validate icon-map-address` sin errores, y actualizar el README con los servicios externos (OpenFreeMap y Photon, sin clave) y la atribución.
