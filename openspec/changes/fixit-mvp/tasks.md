# Tareas: FixIt MVP (Fases 1–3)

> Cambio `fixit-mvp`. Fuentes: `proposal.md`, `design.md`, `specs/*/spec.md` (aprobados). `openspec/drafts/tasks.md` es solo referencia (desactualizado; el diseño prevalece).
> **Apply NO debe comenzar hasta que el usuario lo pida explícitamente** (por ahora solo especificaciones; no hay código).
> Convenciones: identificadores, tests y comentarios en inglés; tests con nombre `` `given X when Y then Z` ``; strings visibles solo vía `strings.xml` (español neutro); sin librería de mocks; Conventional Commits sin atribución de IA. Ciclo estricto: RED (test que falla) → GREEN (mínimo) → REFACTOR.
> Etiquetas de escenario: `[auth]`, `[prof]` (profiles), `[rr]` (repair-requests), `[feed]` (professional-feed), `[jc]` (job-completion), `[contact]`, `[loc]`, `[media]`, `[nav]`. Un escenario `[manual]` se cubre en el checklist del final de cada fase.

## Review Workload Forecast

| Campo | Valor |
|-------|-------|
| Líneas cambiadas estimadas | Fase 1: 9.000–12.000 (≈45 % tests); Fase 2: 2.500–3.500; Fase 3: 600–900. Total: 12.000–16.500 |
| Riesgo de superar 400 líneas | Alto |
| PRs encadenados recomendados | Sí |
| División sugerida | 15 unidades de trabajo (ver abajo): Fase 1 en 9 PRs, Fase 2 en 4 PRs, Fase 3 en 2 PRs |
| Estrategia de entrega | ask-on-risk |
| Estrategia de cadena | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

Decisión pendiente del usuario: `stacked-to-main` (cada PR a main en orden), `feature-branch-chain` (rama tracker; PR 1 contra el tracker, cada PR siguiente contra el anterior; solo el tracker llega a main) o `size-exception` (solo viable para el andamiaje generado). Varias unidades (U4, U6, U7, U8) pueden superar 400 líneas aun divididas; en ese caso dividir por pantalla/ViewModel.

### Suggested Work Units

| Unidad | Objetivo | PR probable | Notas |
|--------|----------|-------------|-------|
| U1 | Andamiaje Gradle + spikes §19 (1.1, 1.2) | PR 1 | Base: rama tracker/main. Incluye smoke test y `./gradlew test` ejecutable. Candidato a size-exception (wrapper y catálogo). |
| U2 | Dominio: tipos y reglas puras con tests (1.3) | PR 2 | Depende de U1. |
| U3 | Dobles `testFixtures` + 10 casos de uso con tests (1.4) | PR 3 | Depende de U2. |
| U4 | `:core:data` Fakes, seed y DI (1.5) | PR 4 | Depende de U3. Puede superar 400 líneas; dividir en 1.5.1–1.5.8 y 1.5.9–1.5.16. |
| U5 | `:core:ui` tema, textos, componentes (1.6) | PR 5 | Depende de U1/U2; independiente de U3–U4. |
| U6 | `:features:auth` (1.7) | PR 6 | Depende de U3, U5. |
| U7 | `:features:client` (1.8) | PR 7 | Depende de U3, U5. Dividir por pantalla si excede el presupuesto. |
| U8 | `:features:professional` (1.9) | PR 8 | Depende de U3, U5. Dividir por pantalla si excede el presupuesto. |
| U9 | `:app` navegación, compuerta, Hilt wiring + cierre de Fase 1 (1.10, 1.11) | PR 9 | Depende de U4–U8. Cierra Fase 1 (checklist manual). |
| U10 | Firebase base: dependencias, `firebaseCall`/`asResult`, `ImageCompressor` (2.1–2.3) | PR 10 | Solo después de cerrar Fase 1. |
| U11 | `FirebaseAuth/UserRepository` + Dtos (2.4.1–2.4.3) | PR 11 | Depende de U10. |
| U12 | `FirebaseRepairRequestRepository` (2.4.4–2.4.6) | PR 12 | Depende de U11. |
| U13 | Módulo Firebase, reglas, índices, checklist Fase 2 (2.5–2.8) | PR 13 | Depende de U12. El diff no debe tocar `core/domain` ni `features/*` (salvo `:core:ui`/`:app` de build). |
| U14 | Fase 3 ubicación real (3.1–3.4, 3.7) | PR 14 | Depende de Fase 2 o de Fase 1 (bindings independientes). |
| U15 | Fase 3 fotos y permisos de UI (3.5, 3.6, 3.8, 3.9) | PR 15 | Depende de U14. |

---

## Fase 1: Fakes + UI completa (TP2)

### 1.1 Andamiaje Gradle (antes de cualquier tarea TDD)

- [ ] 1.1.1 Documentar prerrequisitos en `README.md`: JDK 17 o 21 (la máquina tiene JDK 8 en PATH: usar el JBR de Android Studio vía `org.gradle.java.home` en `~/.gradle/gradle.properties`, no versionado); Android SDK Platform 37 y build-tools instalados con SDK Manager; `local.properties` (`sdk.dir`) ignorado por git.
- [ ] 1.1.2 Generar el wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) con la versión compatible con AGP 9.2.1 (comparar con `mauromarod/unaj-am`) y crear `.gitignore` (`build/`, `.gradle/`, `local.properties`, `.idea/`).
- [ ] 1.1.3 Crear `gradle/libs.versions.toml`: Kotlin 2.4.20, AGP 9.2.1, KSP, Hilt, Compose BOM 2026.09.00, Material 3, navigation3 1.2.0, lifecycle 2.11.0, Coil 3.6.3, coroutines (core/android/test), kotlinx-serialization, javax.inject, JUnit4. Confirmar la última versión estable al implementar (diseño §2).
- [ ] 1.1.4 Crear `settings.gradle.kts` (7 `include`: `:app`, `:core:domain`, `:core:data`, `:core:ui`, `:features:auth`, `:features:client`, `:features:professional`; repositorios) y `build.gradle.kts` raíz (plugins con `apply false`).
- [ ] 1.1.5 Crear `core/domain/build.gradle.kts`: `kotlin-jvm` + `java-test-fixtures`; `api(coroutines-core)`, `javax.inject`; tests: JUnit4 + `kotlinx-coroutines-test` (también como `testFixturesApi`). Sin Android, Hilt, Compose ni Firebase.
- [ ] 1.1.6 Crear `core/data/build.gradle.kts` (Android library, Hilt, KSP, `kotlinx-coroutines-android`, depende de `:core:domain`; `testImplementation(testFixtures(project(":core:domain")))`) con `namespace` `ar.edu.unaj.fixit.core.data`, compile/target 37, minSdk 26.
- [ ] 1.1.7 Crear `core/ui/build.gradle.kts` (Android library, Compose, Material 3, Coil 3; depende de `:core:domain`) y `AndroidManifest.xml` mínimo.
- [ ] 1.1.8 Crear `features/{auth,client,professional}/build.gradle.kts` (Android library, Compose, Hilt, KSP, `lifecycle-runtime-compose`, `hilt-navigation-compose` o artefacto equivalente de la cátedra; dependen de `:core:domain` y `:core:ui`, nunca de `:core:data` ni entre sí).
- [ ] 1.1.9 Crear `app/build.gradle.kts` (application, Compose, Hilt, KSP, Kotlin Serialization, Navigation 3, `lifecycle-viewmodel-navigation3`; depende de todos los módulos) y `AndroidManifest.xml` mínimo con `FixItApplication`/`MainActivity` vacíos.
- [ ] 1.1.10 RED: crear `core/domain/src/test/kotlin/ar/edu/unaj/fixit/core/domain/SmokeTest.kt` con un test que falla; ejecutar `./gradlew :core:domain:test` y comprobar que corre y reporta el fallo.
- [ ] 1.1.11 GREEN: corregir el smoke test, ejecutar `./gradlew test` y `./gradlew assembleDebug` (todos los módulos compilan) y comprobar que importar `android.*` en `:core:domain` no compila (regla de dependencia); luego borrar `SmokeTest.kt`.
- [ ] 1.1.12 Actualizar `openspec/config.yaml` (`testing.status: available`, `runner.runnable_now: true`, `layers.unit.available: true`) y registrar en `state.yaml` que `./gradlew test` ya es ejecutable.

### 1.2 Spikes de verificación (§19, antes de depender de ellos)

- [ ] 1.2.1 testFixtures: crear un doble mínimo en `core/domain/src/testFixtures` y consumirlo desde `core/data/src/test` y `features/client/src/test`. Si un módulo Android no lo resuelve: respaldo = copiar los dobles en cada `src/test` (actualizar D12 en `design.md`).
- [ ] 1.2.2 Fábricas de Dagger: clase descartable con `@Inject constructor` en `:core:domain`, inyectada en un `@HiltViewModel` de `:features:auth`; ejecutar `:app:assembleDebug`. Si no genera la fábrica: respaldo = `@Provides` en un módulo Hilt de `:core:data`. Borrar la clase descartable.
- [ ] 1.2.3 Artefactos de navegación: leer `mauromarod/unaj-am` y registrar en notas los artefactos exactos de `hiltViewModel()`, `rememberViewModelStoreNavEntryDecorator` (`lifecycle-viewmodel-navigation3`) y la semántica de `goBackFrom`; fijarlos en `libs.versions.toml`.
- [ ] 1.2.4 `NavBackStack` como `MutableList<NavKey>`: spike en `app/src/test` que construye y muta un `NavBackStack` en la JVM. Si falla: `NavigationState` recibe `MutableList<NavKey>` y `rememberNavigationState` adapta las pilas (diseño §9).

### 1.3 Dominio puro (`:core:domain`)

- [ ] 1.3.1 Crear tipos sin comportamiento: `model/*` (User, RepairRequest, Review, enums, GeoPoint, Place, SessionState, forms, FeedItem, Contact, RequestDetail, `Limits`), `result/AppResult`, `DomainError`, `ValidationError`/`FormField`, y las interfaces `repository/*` (+ `Clock`) en `core/domain/src/main/kotlin/ar/edu/unaj/fixit/core/domain/`.
- [ ] 1.3.2 RED `rules/RequestStateMachineTest`: las 12 combinaciones. Cubre `[rr]` Transiciones válidas; Transiciones inválidas desde estados no terminales; Estados terminales rechazan toda acción; Estados terminales; `[feed]` Segundo en aceptar (ACCEPTED+ACCEPT → AlreadyTaken).
- [ ] 1.3.3 GREEN `rules/RequestStateMachine.kt`.
- [ ] 1.3.4 RED `rules/HaversineTest`: `[loc]` Mismo punto; Distancia conocida; Simetría; Cruce del antimeridiano; Un grado de latitud; puntos antípodas sin `NaN`.
- [ ] 1.3.5 GREEN `rules/Haversine.kt` (radio 6371.0088; `a.coerceIn(0.0, 1.0)`).
- [ ] 1.3.6 RED `rules/MatchingTest`: `[feed]` Coincide; Categoría distinta; Fuera de radio; Solicitud no abierta; Exactamente en el radio (10.0/10 → true; 10.01/10 → false); `[loc]` Redondeo de la distancia a 0.1 km (`roundKm`).
- [ ] 1.3.7 GREEN `rules/Matching.kt` (`isWithin`, `roundKm`, `distanceIfMatches`).
- [ ] 1.3.8 RED `rules/ReputationTest`: `[prof]` Promedio con un decimal (13/3 → 4.3, 17/4 → 4.3); Redondeo en el punto medio; Sin reseñas (`null`).
- [ ] 1.3.9 GREEN `rules/Reputation.kt` (`Reputation.average(): BigDecimal?`, HALF_UP).
- [ ] 1.3.10 RED `rules/PhoneFormatterTest`: `[contact]` Número local argentino; Número internacional; Número ya normalizado; Teléfono con espacios y paréntesis antes del `+`; Número que ya empieza con 54 sin `+`; Enlace de WhatsApp (`https://wa.me/...` sin `?text`).
- [ ] 1.3.11 GREEN `rules/PhoneFormatter.kt` (`whatsAppDigits`, `whatsAppUrl`).
- [ ] 1.3.12 RED `rules/ContactRulesTest`: `[contact]` El cliente obtiene el contacto del profesional; El profesional obtiene el contacto del cliente; Trabajo completado; Solicitud abierta; Solicitud cancelada después de ser aceptada; Tercero (todos los no autorizados → `Unauthorized`).
- [ ] 1.3.13 GREEN `rules/ContactRules.kt`.
- [ ] 1.3.14 RED `rules/ValidatorsTest` (a): `normalizeEmail` (trim + lowercase), regex de email, `signIn`, `coordinates` (NaN inválido). Cubre `[loc]` Latitud fuera de rango; Límites exactos válidos; `[auth]` Email vacío; Contraseña vacía.
- [ ] 1.3.15 GREEN `rules/Validators.kt` parte (a): `normalizeEmail`, `signIn`, `coordinates`.
- [ ] 1.3.16 RED `rules/ValidatorsTest` (b) `signUp`/`profile`: `[auth]` Teléfono inválido; Teléfono con separadores válidos; Contraseña demasiado corta; Nombre fuera de rango; Límites exactos válidos; Profesional sin categorías; sin punto base; Punto base con coordenadas inválidas; Radio fuera de rango; Se informa solo el primer error; `[prof]` Nombre inválido; Teléfono inválido; Sin categorías; Radio fuera de rango.
- [ ] 1.3.17 GREEN `Validators.signUp` y `Validators.profile` (orden de §5.3, regex de teléfono con separadores).
- [ ] 1.3.18 RED `rules/ValidatorsTest` (c) `request`/`review`: `[rr]` Descripción vacía; Descripción fuera de rango; Límites exactos válidos; Cantidad de fotos inválida; Sin ubicación; Coordenadas fuera de rango; Se informa solo el primer error; `[jc]` Calificación fuera de rango; Límites exactos válidos; Comentario demasiado largo.
- [ ] 1.3.19 GREEN `Validators.request` y `Validators.review`.
- [ ] 1.3.20 REFACTOR: extraer helpers privados de `Validators`, revisar nombres de tests, ejecutar `./gradlew :core:domain:test`.

### 1.4 Dobles de prueba y casos de uso (`:core:domain`)

- [ ] 1.4.1 Crear `core/domain/src/testFixtures/`: `TestAuthRepository`, `TestUserRepository`, `TestRepairRequestRepository` (listas configurables + ganchos de un solo uso `acceptError`, `completeError`, `updateProfileError`, `createError`, `cancelError`, `signInError`, `signUpError`, `observeError`), `TestLocation`, `MainDispatcherRule`.
- [ ] 1.4.2 RED `usecase/SignInUseCaseTest`: `[auth]` Credenciales correctas; Credenciales incorrectas; Email vacío; Contraseña vacía; email normalizado antes de `signIn`.
- [ ] 1.4.3 GREEN `usecase/SignInUseCase.kt`.
- [ ] 1.4.4 RED `usecase/SignUpUseCaseTest`: `[auth]` Registro exitoso de cliente; Registro exitoso de profesional; Email ya registrado; Email con mayúsculas y espacios; Rol persistido; orden de validación incluidas coordenadas; valores recortados (`trim`).
- [ ] 1.4.5 GREEN `usecase/SignUpUseCase.kt`.
- [ ] 1.4.6 RED `usecase/UpdateProfileUseCaseTest`: `[prof]` Edición exitosa; Nombre inválido; Teléfono inválido; Edición exitosa de la zona de trabajo; Sin categorías; Radio fuera de rango; Actualizar el punto base.
- [ ] 1.4.7 GREEN `usecase/UpdateProfileUseCase.kt`.
- [ ] 1.4.8 RED `usecase/GetCurrentPlaceUseCaseTest`: `[loc]` Dirección no resoluble (`address = ""`); Falla simulada de ubicación (`LocationUnavailable`); Usar mi ubicación en Fase 1.
- [ ] 1.4.9 GREEN `usecase/GetCurrentPlaceUseCase.kt`.
- [ ] 1.4.10 RED `usecase/CreateRequestUseCaseTest`: `[rr]` Creación exitosa; Un profesional no puede crear; Publicar sin profesionales cercanos; Solicitud antigua sigue abierta; Múltiples abiertas; validación en orden y descripción recortada.
- [ ] 1.4.11 GREEN `usecase/CreateRequestUseCase.kt`.
- [ ] 1.4.12 RED `usecase/CancelRequestUseCaseTest`: `[rr]` Cancelar una solicitud abierta; Cancelar una solicitud aceptada; Cancelar no afecta la reputación; Cancelar una solicitud ajena; Estados terminales; La autorización precede a la transición.
- [ ] 1.4.13 GREEN `usecase/CancelRequestUseCase.kt`.
- [ ] 1.4.14 RED `usecase/ObserveFeedUseCaseTest`: `[feed]` Nueva solicitud aparece; Solicitud aceptada por otro desaparece; Solicitud cancelada desaparece; Error al cargar el feed (`Error(Network)`); Se agrega una categoría; Se reduce el radio; Los trabajos aceptados no se ven afectados; `distinctUntilChanged` (cambio de nombre/reputación no reinicia).
- [ ] 1.4.15 GREEN `usecase/ObserveFeedUseCase.kt` (`distinctUntilChanged` antes de `flatMapLatest`; orden `createdAt` desc).
- [ ] 1.4.16 RED `usecase/AcceptRequestUseCaseTest`: `[feed]` Aceptación exitosa; Segundo en aceptar; Aceptar de nuevo el mismo trabajo; Aceptar una solicitud cancelada; Categoría no habilitada; Cliente intenta aceptar; Sin verificación de distancia al aceptar; `[rr]` La autorización precede a la transición.
- [ ] 1.4.17 GREEN `usecase/AcceptRequestUseCase.kt`.
- [ ] 1.4.18 RED `usecase/ObserveRequestDetailUseCaseTest` (usar `first { ... }` por emisión transitoria): `[rr]` Solicitud inexistente; Cambio en vivo a ACCEPTED; Solicitud de otro cliente; Detalle de una solicitud cancelada tras ser aceptada; `[prof]` Detalle con profesional asignado; Profesional ve una solicitud abierta (`contact = null`); `[feed]` Detalle desde el feed; Solicitud tomada por otro profesional; Solicitud cancelada que no era propia; Solicitud propia aceptada; `[contact]` La cancelación oculta el contacto en vivo.
- [ ] 1.4.19 GREEN `usecase/ObserveRequestDetailUseCase.kt` (`viewerIsAssignee`, distancia `roundKm`, `Unauthorized` → `contact = null`).
- [ ] 1.4.20 RED `usecase/CompleteJobUseCaseTest`: `[jc]` Completar con reseña; Comentario omitido; Calificación fuera de rango; Comentario demasiado largo; Otro usuario intenta completar; Completar una solicitud abierta/ya completada/cancelada (`InvalidTransition` vía doble); comentario recortado, vacío → `null`.
- [ ] 1.4.21 GREEN `usecase/CompleteJobUseCase.kt`.
- [ ] 1.4.22 REFACTOR y verificar: ejecutar `./gradlew :core:domain:test`; repetir el spike 1.2.2 con los casos de uso reales inyectados en un ViewModel (fábricas de Dagger); anotar resultado y actualizar el respaldo si hace falta.

### 1.5 Datos Fake y DI (`:core:data`)

- [ ] 1.5.1 RED `fake/FakeDatabaseTest`: un único `MutableStateFlow<DbState>`; `signIn`/`signOut` son `state.update {}` atómicos (sin `Mutex` ni `runBlocking`); las escrituras de datos usan el `Mutex`; `failNextWrite` solo lo consume la siguiente escritura que supera la validación (no `signIn`, `signOut` ni escrituras rechazadas por `Unauthorized`/validación); fechas = `clock.now()`.
- [ ] 1.5.2 GREEN `fake/FakeDatabase.kt` y `DbState` (`@Singleton`, `Clock` por constructor; lambda pura bajo `mutex.withLock`; hook leído antes y consumido tras decidir).
- [ ] 1.5.3 RED `fake/SeedDataTest`: todos los usuarios con teléfono; `ratingSum`/`reviewCount` de cada profesional = suma/cantidad de sus reseñas; fechas `createdAt < acceptedAt < completedAt`; contraseña `test1234`.
- [ ] 1.5.4 GREEN `fake/SeedData.kt` (c1, c2, p1–p3, r1–r8 de diseño §12).
- [ ] 1.5.5 RED `fake/FakeAuthRepositoryTest`: `[auth]` Registro exitoso de cliente/profesional; Email ya registrado; Email con mayúsculas y espacios; Rol persistido; Credenciales correctas/incorrectas; Cerrar sesión; Reinicio de la app en Fase 1; `session` nunca falla.
- [ ] 1.5.6 GREEN `fake/FakeAuthRepository.kt` (normaliza email al guardar y comparar).
- [ ] 1.5.7 RED `fake/FakeUserRepositoryTest`: `[prof]` Edición exitosa; Edición exitosa de la zona de trabajo; Actualizar el punto base; Fallo al guardar (`failNextWrite`); Las solicitudes no cambian de estado; `[prof]` Contacto con el teléfono vigente; email/rol/reputación intactos; `observe` → `NotFound`.
- [ ] 1.5.8 GREEN `fake/FakeUserRepository.kt`.
- [ ] 1.5.9 RED `fake/FakeRepairRequestRepositoryTest` (a) lecturas: `[rr]` Lista ordenada; Solo solicitudes propias; Creación exitosa (`createdAt`); `[feed]` Trabajo aceptado aparece; Trabajo completado; Trabajo cancelado por el cliente; Orden de la lista; Sin límite de trabajos (Varios aceptados); `observeOpenNear`, `observeReview`; `distinctUntilChanged`.
- [ ] 1.5.10 GREEN `fake/FakeRepairRequestRepository.kt` parte (a): `create`, `get`, `observe*`.
- [ ] 1.5.11 RED `fake/FakeRepairRequestRepositoryTest` (b) escrituras atómicas: `[feed]` Concurrencia real (10 profesionales → 1 `Success` + 9 `AlreadyTaken`); Aceptar de nuevo el mismo trabajo (idempotente); `[rr]` Cancelar antes de que otro acepte; Aceptar antes de que el cliente cancele; Cancelar no afecta la reputación; `[jc]` Falla parcial; Segundo intento de completar; completar con agregado de reputación; `acceptedAt`/`cancelledAt`/`completedAt` = `clock.now()`.
- [ ] 1.5.12 GREEN `FakeRepairRequestRepository` parte (b): `accept` (idempotente antes de la máquina de estados), `cancel`, `completeWithReview` bajo `Mutex`.
- [ ] 1.5.13 RED `fake/FakeLocationTest`: `[loc]` Usar mi ubicación en Fase 1 (Florencio Varela, "Av. San Martín 2000, Florencio Varela"); Falla simulada de ubicación (`failWith`).
- [ ] 1.5.14 GREEN `fake/FakeLocation.kt` (`LocationProvider` + `AddressResolver`).
- [ ] 1.5.15 Crear `di/RepositoryModule.kt` (`@Binds` Fake), `di/CoreModule.kt` (`Clock`, `@IoDispatcher`) y calificador `@IoDispatcher`. Sin test JVM; se verifica con `:app:assembleDebug` en 1.10.13.
- [ ] 1.5.16 REFACTOR: revisar duplicación en los Fakes y ejecutar `./gradlew :core:data:test`.

### 1.6 `:core:ui`

> Sin tests JVM (diseño §15: Compose se cubre con `@Preview` + checklist manual).

- [ ] 1.6.1 Crear `theme/FixItTheme.kt` (Material 3) y `res/values/strings.xml` base en español neutro.
- [ ] 1.6.2 Crear `text/DomainErrorMessages.kt` (`DomainError.asMessage()` con un único `when`, incluido el mensaje de cuenta sin perfil) y `ServiceCategory.label()`, con sus strings.
- [ ] 1.6.3 Crear `components/`: `LoadingContent`, `ErrorContent`, `StatusBadge`, `CategoryChips` (+ `@Preview`).
- [ ] 1.6.4 Crear `components/`: `PhotoThumbnails`, `PhotoPager`, `PhotoPickerButtons` (Fase 1: "Agregar foto de ejemplo") y `res/drawable/sample_repair_1..3`; `contentDescription` accesible y placeholder de error de Coil.
- [ ] 1.6.5 Crear `components/`: `RatingBar`, `ContactCard` (Llamar `ACTION_DIAL`, WhatsApp `ACTION_VIEW`, `try/catch ActivityNotFoundException`), `ReputationText` (idioma del dispositivo, `minimumFractionDigits = 1`), `ProfileFormFields` + `ProfileFormState`.

### 1.7 `:features:auth`

- [ ] 1.7.1 RED `signin/SignInViewModelTest` (`MainDispatcherRule` + `SignInUseCase` real + `TestAuthRepository`): `[auth]` Envío en curso; Error de validación junto al campo; Mensaje mostrado una sola vez; Credenciales incorrectas.
- [ ] 1.7.2 GREEN `signin/SignInViewModel.kt` + `SignInScreen.kt` (Route/Screen/`@Preview`).
- [ ] 1.7.3 RED `signup/SignUpViewModelTest`: `[auth]` Envío en curso; Error de validación junto al campo; Mensaje mostrado una sola vez; Email ya registrado; rol obligatorio; campos de profesional solo para `PROFESSIONAL`.
- [ ] 1.7.4 GREEN `signup/SignUpViewModel.kt` + `SignUpScreen.kt` (sin acción para cambiar rol tras el registro).

### 1.8 `:features:client`

- [ ] 1.8.1 RED `myrequests/MyRequestsViewModelTest`: `[rr]` Lista ordenada; Solo solicitudes propias; Loading → Success/Error (`Error(Network)` sin `.catch`).
- [ ] 1.8.2 GREEN `myrequests/MyRequestsViewModel.kt` + `MyRequestsScreen.kt`.
- [ ] 1.8.3 RED `create/CreateRequestViewModelTest`: `[rr]` Selección única de categoría; Error de validación visible; Creación exitosa (`createdRequestId` consumido por `onNavigated`); `[media]` Límite en la interfaz; Quitar una foto; Agregar foto de ejemplo; `[loc]` Usar mi ubicación en Fase 1; Editar la dirección; Falla simulada de ubicación.
- [ ] 1.8.4 GREEN `create/CreateRequestViewModel.kt` + `CreateRequestScreen.kt`.
- [ ] 1.8.5 RED `detail/ClientRequestDetailViewModelTest` (`first { ... }`): `[rr]` Solicitud inexistente; Solicitud de otro cliente; Cambio en vivo a ACCEPTED; Detalle de una solicitud cancelada tras ser aceptada; Cancelar una solicitud abierta/aceptada; `[prof]` Detalle con profesional asignado; `[contact]` La cancelación oculta el contacto en vivo; `[media]` Las fotos llegan al detalle; `onRetry` pone `Loading`; `load` idempotente.
- [ ] 1.8.6 GREEN `detail/ClientRequestDetailViewModel.kt` + `ClientRequestDetailScreen.kt` (estado OPEN "Esperando que un profesional acepte"; `ContactCard` en ACCEPTED/COMPLETED).
- [ ] 1.8.7 RED `complete/CompleteJobViewModelTest`: `[jc]` Nombre del profesional; Botón deshabilitado sin estrellas; Selección de estrellas; Error durante el envío; Confirmación exitosa; `loadError`.
- [ ] 1.8.8 GREEN `complete/CompleteJobViewModel.kt` + `CompleteJobScreen.kt` (carga el detalle con `ObserveRequestDetailUseCase`).
- [ ] 1.8.9 RED `profile/ClientProfileViewModelTest`: Loading → Success/Error desde `UserRepository.observe`; `[auth]` Cerrar sesión (`signOut()`).
- [ ] 1.8.10 GREEN `profile/ClientProfileViewModel.kt` + `ClientProfileScreen.kt`.
- [ ] 1.8.11 RED `profile/ClientEditProfileViewModelTest`: `[prof]` Valores precargados; Edición exitosa; Nombre inválido; Teléfono inválido; Fallo al guardar; `saved` consumido por `onNavigated`.
- [ ] 1.8.12 GREEN `profile/ClientEditProfileViewModel.kt` + `ClientEditProfileScreen.kt` (email y rol de solo lectura).

### 1.9 `:features:professional`

- [ ] 1.9.1 RED `feed/FeedViewModelTest`: `[feed]` Nueva solicitud aparece; Error al cargar el feed; Se agrega una categoría; Se reduce el radio.
- [ ] 1.9.2 GREEN `feed/FeedViewModel.kt` + `FeedScreen.kt`.
- [ ] 1.9.3 RED `detail/ProRequestDetailViewModelTest` (`first { ... }`; doble con `acceptError`): `[feed]` Detalle desde el feed; Solicitud tomada por otro profesional; Solicitud cancelada que no era propia; Solicitud propia aceptada; Otro profesional acepta mientras se mira el detalle; Perder la carrera; Solicitud cancelada mientras se miraba; Ganar la aceptación; `[prof]` Profesional ve una solicitud abierta; `[contact]` El profesional obtiene el contacto del cliente; `[jc]` El profesional ve su calificación; Sin acción de calificar para el profesional.
- [ ] 1.9.4 GREEN `detail/ProRequestDetailViewModel.kt` + `ProRequestDetailScreen.kt` ("Aceptar trabajo" solo con `OPEN`; aviso "Esta solicitud ya no está disponible"; diálogo de carrera perdida).
- [ ] 1.9.5 RED `myjobs/MyJobsViewModelTest`: `[feed]` Trabajo aceptado aparece; Trabajo completado; Trabajo cancelado por el cliente; Orden de la lista; Varios aceptados (separa activos y finalizados).
- [ ] 1.9.6 GREEN `myjobs/MyJobsViewModel.kt` + `MyJobsScreen.kt` (la lista muestra solo el estado).
- [ ] 1.9.7 RED `profile/ProProfileViewModelTest`: `[prof]` Datos del perfil de profesional; `[auth]` Cerrar sesión; Loading → Success/Error.
- [ ] 1.9.8 GREEN `profile/ProProfileViewModel.kt` + `ProProfileScreen.kt`.
- [ ] 1.9.9 RED `profile/ProEditProfileViewModelTest`: `[prof]` Valores precargados; Edición exitosa; Edición exitosa de la zona de trabajo; Sin categorías; Radio fuera de rango; Actualizar el punto base; Fallo al guardar.
- [ ] 1.9.10 GREEN `profile/ProEditProfileViewModel.kt` + `ProEditProfileScreen.kt`.

### 1.10 `:app` navegación y compuerta

- [ ] 1.10.1 RED `app/src/test/.../navigation/ExperienceTest` + `SessionViewModelTest`: `[nav]` Sesión en determinación; Sesión previa de profesional; Sin sesión; Iniciar sesión como cliente; Cerrar sesión (`Loading`→LOADING, `SignedOut`→AUTH, roles→CLIENT/PROFESSIONAL).
- [ ] 1.10.2 GREEN `navigation/Experience.kt` y `SessionViewModel` (en `FixItApp.kt`).
- [ ] 1.10.3 RED `navigation/TopLevelDestinationTest`: `[nav]` Pestañas del cliente (`[REQUESTS, CLIENT_PROFILE]`); Pestañas del profesional (`[FEED, MY_JOBS, PRO_PROFILE]`); `authTabs = [AUTH]`.
- [ ] 1.10.4 GREEN `navigation/Destinations.kt` (claves `@Serializable : NavKey`, solo IDs) y `TopLevelDestination.kt`.
- [ ] 1.10.5 RED `navigation/NavigationStateTest` (a) con `MutableList<NavKey>` comunes: `[nav]` Historial independiente por pestaña; Volver a tocar la pestaña activa; Atrás en una pestaña no inicial; `goBackFrom` ignora claves que no están arriba; `navigate` ignora la clave ya arriba; pila `AUTH` ("Atrás" en `SignInKey` → `false`); `resetToRoot`.
- [ ] 1.10.6 GREEN `navigation/NavigationState.kt`: copiar de `mauromarod/unaj-am` (hallazgos de 1.2.3/1.2.4) y adaptar a dos roles + `AUTH`; `rememberNavigationState(tabs)`.
- [ ] 1.10.7 RED `navigation/NavigationFlowsTest` (b): `[nav]` Publicar una solicitud; Completar y calificar; Salir de Completar y calificar sin confirmar; Aceptar un trabajo (`goBackFrom` + `selectTab(MY_JOBS)` + `resetToRoot` + `navigate`); Perder la carrera al aceptar; Abrir un trabajo; Detalle con acción Atrás; Guardar cambios; Salir sin guardar. Si ya pasan con 1.10.6, conservarlos como pruebas de caracterización.
- [ ] 1.10.8 Crear `navigation/AppNavDisplay.kt`: `NavDisplay` con un `entryProvider` por experiencia, decoradores `rememberSaveableStateHolderNavEntryDecorator()` y `rememberViewModelStoreNavEntryDecorator()`; callbacks de §9 resueltos aquí.
- [ ] 1.10.9 Crear `ui/AppShell.kt` (`Scaffold` + `NavigationBar` desde la lista de pestañas del rol; `selectTab`).
- [ ] 1.10.10 Crear `navigation/FixItApp.kt` (compuerta con `key(userId)`), `FixItApplication.kt` (`@HiltAndroidApp`), `MainActivity.kt` (`@AndroidEntryPoint`) y manifest; ejecutar `./gradlew :app:assembleDebug` y la app en emulador con los datos de `SeedData`.
- [ ] 1.10.11 Verificar (§19) retención de ViewModels por pestaña: cambiar de pestaña y volver conserva ViewModels y estado guardable; registrar el resultado (si se pierden: documentar aceptación o ajustar `NavDisplay`).
- [ ] 1.10.12 Verificar (§19, D20) `key(userId)`: cerrar sesión e iniciar con otro usuario no conserva ViewModels ni estado; si falla, aplicar el respaldo (reiniciar pilas con `resetToRoot` antes de `signOut()` y, si no basta, recrear la `Activity`).
- [ ] 1.10.13 Verificar el cableado Hilt de punta a punta (`RepositoryModule`, casos de uso con `@Inject`, `hiltViewModel()` en cada Route); registrar los resultados de los spikes de §19 en `state.yaml` (`open_items` → `resolved`).

### 1.11 Cierre de Fase 1: checklist manual

> Cada ítem se tilda con fecha y dispositivo/emulador. Total Fase 1: 24 escenarios `[manual]`.

- [ ] 1.11.1 Intents de Android (3): `[contact]` Llamar; Abrir WhatsApp; Sin aplicación disponible.
- [ ] 1.11.2 Render visual (9): `[prof]` Visualizar perfil de cliente; Formato visual del perfil de profesional; Formato del promedio según el idioma (4,3 / 4,0); `[rr]` Contador de descripción; Lista vacía; Detalle sin edición; `[feed]` Feed vacío; `[media]` Imagen que no carga; Descripción accesible.
- [ ] 1.11.3 Rotación y retención (3): `[auth]` Sesión estable durante el uso; `[nav]` Rotación del formulario; Estado descartado al volver.
- [ ] 1.11.4 Diálogos de confirmación (2): `[rr]` Descartar la confirmación de cancelación; `[feed]` Descartar la confirmación de aceptación.
- [ ] 1.11.5 Compuerta y botón Atrás (2): `[nav]` Otro usuario tras cerrar sesión no ve datos del anterior; Atrás en la raíz de la pestaña inicial.
- [ ] 1.11.6 Revisión de pantallas (5): `[auth]` Sin acción para cambiar el rol; `[prof]` Email y rol no editables; `[nav]` Sin acceso a pantallas del otro rol; Pantalla raíz sin acción Atrás; `[contact]` Sin pantalla de chat.
- [ ] 1.11.7 Cierre: `./gradlew test` y `./gradlew build` en verde; recorrer el flujo completo (registro → publicar → feed → aceptar → contactar → completar y calificar); confirmar que `:core:domain` no depende de Android; marcar Fase 1 como lista en `state.yaml`.

---

## Fase 2: Firebase (solo bindings, sin tocar `core/domain` ni `features/*`)

> Prerrequisito: Fase 1 cerrada. Los Fakes NO se borran.

### 2.1 Configuración

- [ ] 2.1.1 Crear el proyecto Firebase (Auth email/contraseña, Firestore, Storage), registrar `applicationId` `ar.edu.unaj.fixit` y colocar `app/google-services.json` (decidir si se versiona).
- [ ] 2.1.2 Actualizar `libs.versions.toml` y `build.gradle.kts` raíz/`:app`: plugin `google-services`; Firebase BoM, `kotlinx-coroutines-play-services`, `geofire-android-common` en `:core:data`.
- [ ] 2.1.3 Agregar permiso `INTERNET` al manifest de `:app`, `coil-network-okhttp` a `:core:ui` y `androidx.exifinterface` a `:core:data`.

### 2.2 Utilidades de error (`:core:data/firebase`)

- [ ] 2.2.1 RED `firebase/FirebaseCallsTest` (`runTest` + `advanceTimeBy`): éxito → `Success`; excepción → `Error` mapeado (tabla §13.4); `DomainException` (también como causa) → su `error`; operación que no termina → `Error(Network)` a los 15 s de tiempo virtual; cancelación del llamador se propaga.
- [ ] 2.2.2 GREEN `firebase/FirebaseCalls.kt`: `WRITE_TIMEOUT_MS`, `firebaseCall`, `DomainException`, `Throwable.toDomainError()`.
- [ ] 2.2.3 RED `FirebaseCallsTest` (`asResult`): emite `Success` por elemento y `Error` mapeado si el flujo falla.
- [ ] 2.2.4 GREEN `asResult()` en `FirebaseCalls.kt`.

### 2.3 Compresión de imágenes

- [ ] 2.3.1 RED `firebase/ImageCompressorTest` (`targetSize`): `[media]` Cálculo del tamaño de compresión (4000x3000 → 1600x1200; 3000x4000 → 1200x1600; 800x600 sin cambios; relación extrema sin lado en 0).
- [ ] 2.3.2 GREEN `firebase/ImageCompressor.kt`: `targetSize` pura + `compress(uri)` en `withContext(@IoDispatcher)` con EXIF, JPEG calidad 80.

### 2.4 Repositorios Firebase (sin tests JVM: se verifican con el checklist 2.8)

- [ ] 2.4.1 Crear `firebase/Dtos.kt` (DTO + mappers; `getTimestamp(campo, ESTIMATE)`; `geohash` solo en el DTO; `ratingSum = 0`/`reviewCount = 0` al crear usuario).
- [ ] 2.4.2 Crear `firebase/FirebaseAuthRepository.kt`: bandera de operación en curso, `signUp` (Auth → `users/{uid}`; rollback `user.delete()` en `NonCancellable`), `signIn`, `session` (`Loading`/`SignedOut`/`SignedIn`), cuenta huérfana y `signOut()`.
- [ ] 2.4.3 Crear `firebase/FirebaseUserRepository.kt` (`get`, `observe`, `updateProfile` con campos editables solamente).
- [ ] 2.4.4 Crear `FirebaseRepairRequestRepository` parte 1: `create` (compresión + `putBytes` con `contentType image/jpeg` + limpieza de fotos huérfanas), `get`, `observe`, `observeByClient`, `observeByProfessional`, `observeReview`.
- [ ] 2.4.5 `FirebaseRepairRequestRepository` parte 2: `observeOpenNear` con `GeoFireUtils` (un listener por límite, `asResult`, `combine`, sin duplicados).
- [ ] 2.4.6 `FirebaseRepairRequestRepository` parte 3: `accept` (idempotente), `cancel`, `completeWithReview` con `runTransaction` dentro de `firebaseCall`, lanzando `DomainException`.

### 2.5 Conmutación

- [ ] 2.5.1 Crear `di/FirebaseModule.kt` (`FirebaseAuth`, `FirebaseFirestore`, `FirebaseStorage` con `maxUploadRetryTimeMillis = 15_000`) y cambiar los tres `@Binds` de repositorios en `RepositoryModule`.
- [ ] 2.5.2 Verificar que el diff de Fase 2 no toca `core/domain` ni `features/*`; probar el rollback revirtiendo solo `RepositoryModule.kt`.

### 2.6 Reglas e índices

- [ ] 2.6.1 Crear `firestore.rules` (diseño §13.3, incluidos `hasOnly` de cancelar/completar y `existsAfter`).
- [ ] 2.6.2 Crear `firestore.indexes.json` (`status+geohash`, `clientId+createdAt desc`, `professionalId+acceptedAt desc`).
- [ ] 2.6.3 Crear `storage.rules` (create/update con tamaño y `contentType`; delete separado).
- [ ] 2.6.4 Desplegar reglas e índices (o Emulator Suite) y probar los rechazos esperados.

### 2.7 Verificación abierta

- [ ] 2.7.1 Verificar (§19) escrituras offline de Firestore: `withTimeout` no cancela una escritura encolada. Si se sincroniza más tarde, usar `runTransaction` también en `create` y `updateProfile`.

### 2.8 Checklist manual de Fase 2 (24 `[manual]` + compresión)

- [ ] 2.8.1 Auth y perfiles (9): `[auth]` Reinicio con sesión; Registro sin conectividad; Mismos errores que en Fase 1; Falla al escribir el perfil; Pantalla de registro estable durante la operación; Cuenta sin perfil; `[prof]` Actualización en vivo de la reputación; La edición se ve en otro dispositivo; Intento de cambiar el email o el rol.
- [ ] 2.8.2 Solicitudes y feed (5): `[rr]` Solicitud visible en otro dispositivo; Fallo de red al publicar; `[feed]` Solicitud publicada desde otro dispositivo; Dos dispositivos aceptan; Sobrescritura rechazada.
- [ ] 2.8.3 Finalización y ubicación (5): `[jc]` Fallo parcial; Sin conectividad; Dos intentos simultáneos; `[loc]` Dentro del radio; Cerca pero fuera del radio.
- [ ] 2.8.4 Fotos (5): `[media]` Subida exitosa; Calidad visual de la compresión (foto vertical con EXIF se ve derecha); Falla la segunda foto; Falla la escritura de la solicitud; Usuario sin permiso.
- [ ] 2.8.5 Cierre: `./gradlew test` y `./gradlew build` en verde; marcar Fase 2 en `state.yaml`.

---

## Fase 3: Hardware (ubicación, cámara, Photo Picker)

> Estos escenarios son `[manual]` por depender de hardware (diseño §15); no hay tests JVM nuevos.

- [ ] 3.1 Agregar al manifest de `:app` los permisos de ubicación y cámara y el `FileProvider` (`res/xml/file_paths.xml`); `play-services-location` en `:core:data`.
- [ ] 3.2 Crear `device/FusedLocationProvider.kt` (`getCurrentLocation`, prioridad balanceada, timeout 10 s → `LocationUnavailable`).
- [ ] 3.3 Crear `device/GeocoderAddressResolver.kt` (recibe `@IoDispatcher` por constructor; falla de dirección → `""` en el caso de uso).
- [ ] 3.4 Cambiar los dos `@Binds` de ubicación en `RepositoryModule` (`LocationProvider`, `AddressResolver`).
- [ ] 3.5 Crear en `:core:ui` el helper de permisos (`rememberLauncherForActivityResult(RequestMultiplePermissions())`, solo al tocar "Usar mi ubicación"; manejo de denegado y denegado permanente con acceso a Ajustes).
- [ ] 3.6 Cambiar `PhotoPickerButtons` (`:core:ui`): `PickVisualMedia` si falta 1 foto, `PickMultipleVisualMedia(faltantes)` si faltan 2 o más, `TakePicture` con `FileProvider`; aplicar `take(faltantes)` y entregar `uri.toString()`; ocultar la cámara si no hay dispositivo.
- [ ] 3.7 Checklist manual de ubicación (7): `[loc]` Permiso concedido; Permiso denegado; Permiso denegado de forma permanente; Solo se pide al usar la acción; GPS desactivado; Tiempo de espera agotado; Punto base real del profesional.
- [ ] 3.8 Checklist manual de fotos (6): `[media]` Selección múltiple; 2 fotos ya cargadas; Cancelar la selección; Foto tomada; Captura cancelada; Dispositivo sin cámara.
- [ ] 3.9 Cierre: `./gradlew test` y `./gradlew build` en verde; confirmar que el cambio solo toca `RepositoryModule`, `:core:ui`, `:core:data/device` y `:app`; marcar Fase 3 en `state.yaml`.

---

## Resumen de trazabilidad

Totales verificados contra los specs: **75 requisitos / 234 escenarios / 61 `[manual]`** (173 con test JVM).

| Spec | Requisitos | Escenarios | Con test JVM | `[manual]` (checklist) |
|------|-----------:|-----------:|-------------:|-----------------------:|
| auth | 10 | 32 | 24 | 8 |
| profiles | 11 | 25 | 18 | 7 |
| repair-requests | 12 | 37 | 31 | 6 |
| professional-feed | 10 | 38 | 33 | 5 |
| job-completion | 6 | 21 | 18 | 3 |
| contact | 4 | 17 | 13 | 4 |
| location | 8 | 21 | 12 | 9 |
| media | 6 | 18 | 5 | 13 |
| navigation | 8 | 25 | 19 | 6 |
| **Total** | **75** | **234** | **173** | **61** |

Por fase: Fase 1 = 196 escenarios (172 test + 24 `[manual]`, checklist 1.11); Fase 2 = 25 (1 test `targetSize` + 24 `[manual]`, checklist 2.8); Fase 3 = 13 (todos `[manual]`, checklists 3.7 y 3.8).

Recuento de tareas: Fase 1 = 125 (1.1: 12, 1.2: 4, 1.3: 20, 1.4: 22, 1.5: 16, 1.6: 5, 1.7: 4, 1.8: 12, 1.9: 10, 1.10: 13, 1.11: 7); Fase 2 = 27; Fase 3 = 9. **Total = 161**.

### Ítems abiertos de §19 y su tarea

| Ítem | Tarea |
|------|-------|
| `NavigationState` de la cátedra / artefactos `hiltViewModel` y nav3 | 1.2.3, 1.10.6 |
| `testFixtures` consumible por módulos Android | 1.2.1 |
| Fábricas de Dagger para casos de uso de `:core:domain` | 1.2.2, 1.4.22, 1.10.13 |
| `NavBackStack` como `MutableList<NavKey>` | 1.2.4 |
| Retención de ViewModels por pestaña | 1.10.11 |
| `key(userId)` descarta ViewModels | 1.10.12, 1.11.5 |
| Escrituras offline de Firestore | 2.7.1 |
