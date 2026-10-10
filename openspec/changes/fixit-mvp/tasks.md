# Tareas: FixIt MVP (Fases 1–4)

> Cambio `fixit-mvp`. Fuentes: `proposal.md`, `design.md`, `specs/*/spec.md` (aprobados; actualizados el 2026-10-08: fotos en Cloudinary, push en Fase 4). `openspec/drafts/tasks.md` es solo referencia (desactualizado; el diseño prevalece).
> **Apply NO debe comenzar hasta que el usuario lo pida explícitamente** (por ahora solo especificaciones; no hay código).
> Restricción dura: nada de lo planificado requiere tarjeta de crédito (Firebase Spark, Cloudinary gratuito, Cloudflare Workers gratuito).
> Convenciones: identificadores, tests y comentarios en inglés; tests con nombre `` `given X when Y then Z` ``; strings visibles solo vía `strings.xml` (español neutro); sin librería de mocks; Conventional Commits sin atribución de IA. Ciclo estricto: RED (test que falla) → GREEN (mínimo) → REFACTOR.
> Etiquetas de escenario: `[auth]`, `[prof]` (profiles), `[rr]` (repair-requests), `[feed]` (professional-feed), `[jc]` (job-completion), `[contact]`, `[loc]`, `[media]`, `[nav]`, `[notif]` (notifications, Fase 4). Un escenario `[manual]` se cubre en el checklist del final de cada fase (también las verificaciones de un servicio desplegado, como `curl` contra el Worker). Excepción [Fase 4]: la lógica que vive solo en el Worker (`worker/`, TypeScript) se cubre con tests `vitest` (`npm test` dentro de `worker/`) en lugar de tests JVM.

## Review Workload Forecast

| Campo | Valor |
|-------|-------|
| Líneas cambiadas estimadas | Fase 1: 9.000–12.000 (≈45 % tests); Fase 2: 2.800–3.800; Fase 3: 600–900; Fase 4: 3.000–4.500 (Worker ≈ 1.000–1.500; Android ≈ 2.000–3.000). Total: 15.400–21.200 |
| Riesgo de superar 400 líneas | Alto |
| PRs encadenados recomendados | Sí |
| División sugerida | 26 unidades de trabajo (ver abajo): Fase 1 en 9 PRs, Fase 2 en 5 PRs, Fase 3 en 2 PRs, Fase 4 en 10 PRs |
| Estrategia de entrega | ask-on-risk |
| Estrategia de cadena | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

Decisión pendiente del usuario: `stacked-to-main` (cada PR a main en orden), `feature-branch-chain` (rama tracker; PR 1 contra el tracker, cada PR siguiente contra el anterior; solo el tracker llega a main) o `size-exception` (solo viable para el andamiaje generado). Varias unidades (U4, U6, U7, U8, U23) pueden superar 400 líneas aun divididas; en ese caso dividir por pantalla/ViewModel o por subtareas indicadas. La recomendación previa (no vinculante) es `stacked-to-main`, porque el repositorio es local y no tiene remoto.

Paralelismo: las tareas dentro de una unidad son secuenciales (RED → GREEN). Entre unidades solo hay paralelismo técnico donde se indica (U5 con U3–U4; U11 con U12; Worker U17–U20 con Android U21–U25); como la regla es un único escritor, el orden de ejecución recomendado es el numérico.

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
| U10 | Configuración Firebase/Cloudinary/`BuildConfig`, `firebaseCall`/`asResult`, `ImageCompressor` (2.1–2.3) | PR 10 | Solo después de cerrar Fase 1. Incluye pasos `[manual]` (cuentas, preset). |
| U11 | Cloudinary: `buildUploadRequest`, `parseUploadResponse`, mapeo de errores, `CloudinaryUploader`, `NetworkModule` (2.4) | PR 11 | Depende de U10. Independiente de U12–U13 (paralelizable si hubiera dos escritores). |
| U12 | `FirebaseAuth/UserRepository` + Dtos (2.5.1–2.5.3) | PR 12 | Depende de U10. |
| U13 | `FirebaseRepairRequestRepository` (2.5.4–2.5.6) | PR 13 | Depende de U11 y U12. |
| U14 | Módulo Firebase, conmutación, reglas, índices, checklist Fase 2 (2.6–2.9) | PR 14 | Depende de U13. El diff no debe tocar `core/domain` ni `features/*` (salvo `:core:ui`/`:app` de build). |
| U15 | Fase 3 ubicación real (3.1–3.4, 3.7) | PR 15 | Depende de Fase 2 o de Fase 1 (bindings independientes). |
| U16 | Fase 3 fotos y permisos de UI (3.5, 3.6, 3.8, 3.9) | PR 16 | Depende de U15. |
| U17 | Worker: andamiaje (`wrangler`, TypeScript, `vitest`) + funciones puras `parseNotifyBody`, `decideTarget` (con vigencia del evento), `checkClaims` (4.1, 4.2) | PR 17 | Fase 4 depende de Fase 2 cerrada (U14). Código independiente del Android: U17–U20 pueden ir en paralelo con U21–U25 si hay dos escritores; con un solo escritor, en orden. |
| U18 | Worker: verificación del ID token (JWKS en caché) y lectura REST de Firestore (4.3, 4.4) | PR 18 | Depende de U17. |
| U19 | Worker: OAuth de la cuenta de servicio, envío FCM y manejador `/notify` (4.5, 4.6) | PR 19 | Depende de U18. |
| U20 | Worker: secretos, despliegue y medición de CPU en la ruta fría `[manual]` (4.7) | PR 20 (solo documentación/config) | Depende de U19. Resuelve el ítem §19 de Firestore REST con el ID token. |
| U21 | Android: `shouldNotify`, `PushMessageHandler`, dependencias FCM, `@ApplicationScope` (4.8, 4.9.1–4.9.4) | PR 21 | Depende de U14. |
| U22 | Android: `topicsFor`/`topicChanges`, `TopicSync` (con `TopicStore` y `NoticeCanceller`), `FirebaseTopicMessaging` (4.9.5–4.9.13) | PR 22 | Depende de U21. |
| U23 | Android: cliente del Worker, decorador `NotifyingRepairRequestRepository`, `withPush` y bindings Hilt (4.9.14–4.9.16, 4.10, 4.11) | PR 23 | Depende de U22. Puede superar 400 líneas: dividir en 4.9.14–4.10.2 y 4.10.3–4.11.2. |
| U24 | `:app` lógica pura: `deepLinkDestination`, `NavigationState.open`, textos fijos de los avisos (4.12.1–4.12.6) | PR 24 | Depende de U21. |
| U25 | `:app` Android: canal y notificaciones, `FixItMessagingService`, permiso `POST_NOTIFICATIONS`, `MainActivity`/`FixItApp` (4.12.7–4.12.10) | PR 25 | Depende de U22, U23 y U24. |
| U26 | Cierre de Fase 4: verificaciones §19 y checklist manual (4.13) | PR 26 | Depende de U20 y U25. |

---

## Fase 1: Fakes + UI completa (TP2)

### 1.1 Andamiaje Gradle (antes de cualquier tarea TDD)

- [x] 1.1.1 Documentar prerrequisitos en `README.md`: JDK 17 o 21 (la máquina tiene JDK 8 en PATH: usar el JBR de Android Studio vía `org.gradle.java.home` en `~/.gradle/gradle.properties`, no versionado); Android SDK Platform 37 y build-tools instalados con SDK Manager; `local.properties` (`sdk.dir`) ignorado por git.
- [x] 1.1.2 Generar el wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) con la versión compatible con AGP 9.2.1 (comparar con `mauromarod/unaj-am`) y crear `.gitignore` (`build/`, `.gradle/`, `local.properties`, `.idea/`).
- [x] 1.1.3 Crear `gradle/libs.versions.toml`: Kotlin 2.4.20, AGP 9.2.1, KSP, Hilt, Compose BOM 2026.09.00, Material 3, navigation3 1.2.0, lifecycle 2.11.0, Coil 3.6.3, coroutines (core/android/test), kotlinx-serialization, javax.inject, JUnit4. Confirmar la última versión estable al implementar (diseño §2).
- [x] 1.1.4 Crear `settings.gradle.kts` (7 `include`: `:app`, `:core:domain`, `:core:data`, `:core:ui`, `:features:auth`, `:features:client`, `:features:professional`; repositorios) y `build.gradle.kts` raíz (plugins con `apply false`).
- [x] 1.1.5 Crear `core/domain/build.gradle.kts`: `kotlin-jvm` + `java-test-fixtures`; `api(coroutines-core)`, `javax.inject`; tests: JUnit4 + `kotlinx-coroutines-test` (también como `testFixturesApi`). Sin Android, Hilt, Compose ni Firebase.
- [x] 1.1.6 Crear `core/data/build.gradle.kts` (Android library, Hilt, KSP, `kotlinx-coroutines-android`, depende de `:core:domain`; `testImplementation(testFixtures(project(":core:domain")))`) con `namespace` `ar.edu.unaj.fixit.core.data`, compile/target 37, minSdk 26.
- [x] 1.1.7 Crear `core/ui/build.gradle.kts` (Android library, Compose, Material 3, Coil 3; depende de `:core:domain`) y `AndroidManifest.xml` mínimo.
- [x] 1.1.8 Crear `features/{auth,client,professional}/build.gradle.kts` (Android library, Compose, Hilt, KSP, `lifecycle-runtime-compose`, `hilt-lifecycle-viewmodel-compose` (sustituye a `hilt-navigation-compose`, ver 1.2.3) de la cátedra; dependen de `:core:domain` y `:core:ui`, nunca de `:core:data` ni entre sí).
- [x] 1.1.9 Crear `app/build.gradle.kts` (application, Compose, Hilt, KSP, Kotlin Serialization, Navigation 3, `lifecycle-viewmodel-navigation3`; depende de todos los módulos) y `AndroidManifest.xml` mínimo con `FixItApplication`/`MainActivity` vacíos.
- [x] 1.1.10 RED: crear `core/domain/src/test/kotlin/ar/edu/unaj/fixit/core/domain/SmokeTest.kt` con un test que falla; ejecutar `./gradlew :core:domain:test` y comprobar que corre y reporta el fallo.
- [x] 1.1.11 GREEN: corregir el smoke test, ejecutar `./gradlew test` y `./gradlew assembleDebug` (todos los módulos compilan) y comprobar que importar `android.*` en `:core:domain` no compila (regla de dependencia); luego borrar `SmokeTest.kt`.
- [x] 1.1.12 Actualizar `openspec/config.yaml` (`testing.status: available`, `runner.runnable_now: true`, `layers.unit.available: true`) y registrar en `state.yaml` que `./gradlew test` ya es ejecutable.

### 1.2 Spikes de verificación (§19, antes de depender de ellos)

- [x] 1.2.1 testFixtures: crear un doble mínimo en `core/domain/src/testFixtures` y consumirlo desde `core/data/src/test` y `features/client/src/test`. Si un módulo Android no lo resuelve: respaldo = copiar los dobles en cada `src/test` (actualizar D12 en `design.md`). Resultado 2026-10-10: resuelto, sin respaldo; D12 se mantiene (`:core:data` y `:features:client` consumen `testFixtures` de `:core:domain`).
- [x] 1.2.2 Fábricas de Dagger: clase descartable con `@Inject constructor` en `:core:domain`, inyectada en un `@HiltViewModel` de `:features:auth`; ejecutar `:app:assembleDebug`. Si no genera la fábrica: respaldo = `@Provides` en un módulo Hilt de `:core:data`. Borrar la clase descartable. Resultado 2026-10-10: resuelto, sin respaldo; Hilt genera la fábrica en `:app` (`hiltJavaCompileDebug`). Clase descartable borrada.
- [x] 1.2.3 Artefactos de navegación: leer `mauromarod/unaj-am` y registrar en notas los artefactos exactos de `hiltViewModel()`, `rememberViewModelStoreNavEntryDecorator` (`lifecycle-viewmodel-navigation3`) y la semántica de `goBackFrom`; fijarlos en `libs.versions.toml`. Resultado 2026-10-10: la cátedra no usa Hilt; artefactos fijados en `libs.versions.toml` (`hilt-lifecycle-viewmodel-compose:1.4.0` reemplaza a `hilt-navigation-compose`, que arrastra Navigation 2; `lifecycle-viewmodel-navigation3:2.11.0`; `navigation3:1.2.0`). Notas en design §19.
- [x] 1.2.4 `NavBackStack` como `MutableList<NavKey>`: spike en `app/src/test` que construye y muta un `NavBackStack` en la JVM. Si falla: `NavigationState` recibe `MutableList<NavKey>` y `rememberNavigationState` adapta las pilas (diseño §9). Resultado 2026-10-10: resuelto, `NavBackStack<NavKey>` es `MutableList<NavKey>` en la JVM; sin respaldo. Spike borrado.

### 1.3 Dominio puro (`:core:domain`)

- [x] 1.3.1 Crear tipos sin comportamiento: `model/*` (User, RepairRequest, Review, enums, GeoPoint, Place, SessionState, forms, FeedItem, Contact, RequestDetail, `Limits`), `result/AppResult`, `DomainError`, `ValidationError`/`FormField`, y las interfaces `repository/*` (+ `Clock`) en `core/domain/src/main/kotlin/ar/edu/unaj/fixit/core/domain/`.
- [x] 1.3.2 RED `rules/RequestStateMachineTest`: las 12 combinaciones. Cubre `[rr]` Transiciones válidas; Transiciones inválidas desde estados no terminales; Estados terminales rechazan toda acción; Estados terminales; `[feed]` Segundo en aceptar (ACCEPTED+ACCEPT → AlreadyTaken).
- [x] 1.3.3 GREEN `rules/RequestStateMachine.kt`.
- [x] 1.3.4 RED `rules/HaversineTest`: `[loc]` Mismo punto; Distancia conocida; Simetría; Cruce del antimeridiano; Un grado de latitud; puntos antípodas sin `NaN`.
- [x] 1.3.5 GREEN `rules/Haversine.kt` (radio 6371.0088; `a.coerceIn(0.0, 1.0)`).
- [x] 1.3.6 RED `rules/MatchingTest`: `[feed]` Coincide; Categoría distinta; Fuera de radio; Solicitud no abierta; Exactamente en el radio (10.0/10 → true; 10.01/10 → false); `[loc]` Redondeo de la distancia a 0.1 km (`roundKm`).
- [x] 1.3.7 GREEN `rules/Matching.kt` (`isWithin`, `roundKm`, `distanceIfMatches`).
- [x] 1.3.8 RED `rules/ReputationTest`: `[prof]` Promedio con un decimal (13/3 → 4.3, 17/4 → 4.3); Redondeo en el punto medio; Sin reseñas (`null`).
- [x] 1.3.9 GREEN `rules/Reputation.kt` (`Reputation.average(): BigDecimal?`, HALF_UP).
- [x] 1.3.10 RED `rules/PhoneFormatterTest`: `[contact]` Número local argentino; Número internacional; Número ya normalizado; Teléfono con espacios y paréntesis antes del `+`; Número que ya empieza con 54 sin `+`; Enlace de WhatsApp (`https://wa.me/...` sin `?text`).
- [x] 1.3.11 GREEN `rules/PhoneFormatter.kt` (`whatsAppDigits`, `whatsAppUrl`).
- [x] 1.3.12 RED `rules/ContactRulesTest`: `[contact]` El cliente obtiene el contacto del profesional; El profesional obtiene el contacto del cliente; Trabajo completado; Solicitud abierta; Solicitud cancelada después de ser aceptada; Tercero (todos los no autorizados → `Unauthorized`).
- [x] 1.3.13 GREEN `rules/ContactRules.kt`.
- [x] 1.3.14 RED `rules/ValidatorsTest` (a): `normalizeEmail` (trim + lowercase), regex de email, `signIn`, `coordinates` (NaN inválido). Cubre `[loc]` Latitud fuera de rango; Límites exactos válidos; `[auth]` Email vacío; Contraseña vacía.
- [x] 1.3.15 GREEN `rules/Validators.kt` parte (a): `normalizeEmail`, `signIn`, `coordinates`.
- [x] 1.3.16 RED `rules/ValidatorsTest` (b) `signUp`/`profile`: `[auth]` Teléfono inválido; Teléfono con separadores válidos; Contraseña demasiado corta; Nombre fuera de rango; Límites exactos válidos; Profesional sin categorías; sin punto base; Punto base con coordenadas inválidas; Radio fuera de rango; Se informa solo el primer error; `[prof]` Nombre inválido; Teléfono inválido; Sin categorías; Radio fuera de rango.
- [x] 1.3.17 GREEN `Validators.signUp` y `Validators.profile` (orden de §5.3, regex de teléfono con separadores).
- [x] 1.3.18 RED `rules/ValidatorsTest` (c) `request`/`review`: `[rr]` Descripción vacía; Descripción fuera de rango; Límites exactos válidos; Cantidad de fotos inválida; Sin ubicación; Coordenadas fuera de rango; Se informa solo el primer error; `[jc]` Calificación fuera de rango; Límites exactos válidos; Comentario demasiado largo.
- [x] 1.3.19 GREEN `Validators.request` y `Validators.review`.
- [x] 1.3.20 REFACTOR: extraer helpers privados de `Validators`, revisar nombres de tests, ejecutar `./gradlew :core:domain:test`.

### 1.4 Dobles de prueba y casos de uso (`:core:domain`)

- [x] 1.4.1 Crear `core/domain/src/testFixtures/`: `TestAuthRepository` (su `session` está respaldada por un `MutableStateFlow<SessionState>` que el test controla, p. ej. con `setSession(...)`), `TestUserRepository`, `TestRepairRequestRepository` (listas configurables + ganchos de un solo uso `acceptError`, `completeError`, `updateProfileError`, `createError`, `cancelError`, `signInError`, `signUpError`, `observeError`), `TestLocation`, `MainDispatcherRule`.
- [x] 1.4.2 RED `usecase/SignInUseCaseTest`: `[auth]` Credenciales correctas; Credenciales incorrectas; Email vacío; Contraseña vacía; email normalizado antes de `signIn`.
- [x] 1.4.3 GREEN `usecase/SignInUseCase.kt`.
- [x] 1.4.4 RED `usecase/SignUpUseCaseTest`: `[auth]` Registro exitoso de cliente; Registro exitoso de profesional; Email ya registrado; Email con mayúsculas y espacios; Rol persistido; orden de validación incluidas coordenadas; valores recortados (`trim`).
- [x] 1.4.5 GREEN `usecase/SignUpUseCase.kt`.
- [x] 1.4.6 RED `usecase/UpdateProfileUseCaseTest`: `[prof]` Edición exitosa; Nombre inválido; Teléfono inválido; Edición exitosa de la zona de trabajo; Sin categorías; Radio fuera de rango; Actualizar el punto base.
- [x] 1.4.7 GREEN `usecase/UpdateProfileUseCase.kt`.
- [x] 1.4.8 RED `usecase/GetCurrentPlaceUseCaseTest`: `[loc]` Dirección no resoluble (`address = ""`); Falla simulada de ubicación (`LocationUnavailable`); Usar mi ubicación en Fase 1.
- [x] 1.4.9 GREEN `usecase/GetCurrentPlaceUseCase.kt`.
- [x] 1.4.10 RED `usecase/CreateRequestUseCaseTest`: `[rr]` Creación exitosa; Un profesional no puede crear; Publicar sin profesionales cercanos; Solicitud antigua sigue abierta; Múltiples abiertas; validación en orden y descripción recortada.
- [x] 1.4.11 GREEN `usecase/CreateRequestUseCase.kt`.
- [x] 1.4.12 RED `usecase/CancelRequestUseCaseTest`: `[rr]` Cancelar una solicitud abierta; Cancelar una solicitud aceptada; Cancelar no afecta la reputación; Cancelar una solicitud ajena; Estados terminales; La autorización precede a la transición.
- [x] 1.4.13 GREEN `usecase/CancelRequestUseCase.kt`.
- [x] 1.4.14 RED `usecase/ObserveFeedUseCaseTest`: `[feed]` Nueva solicitud aparece; Solicitud aceptada por otro desaparece; Solicitud cancelada desaparece; Error al cargar el feed (`Error(Network)`); Se agrega una categoría; Se reduce el radio; Los trabajos aceptados no se ven afectados; `distinctUntilChanged` (cambio de nombre/reputación no reinicia).
- [x] 1.4.15 GREEN `usecase/ObserveFeedUseCase.kt` (`distinctUntilChanged` antes de `flatMapLatest`; orden `createdAt` desc).
- [x] 1.4.16 RED `usecase/AcceptRequestUseCaseTest`: `[feed]` Aceptación exitosa; Segundo en aceptar; Aceptar de nuevo el mismo trabajo; Aceptar una solicitud cancelada; Categoría no habilitada; Cliente intenta aceptar; Sin verificación de distancia al aceptar; `[rr]` La autorización precede a la transición.
- [x] 1.4.17 GREEN `usecase/AcceptRequestUseCase.kt`.
- [x] 1.4.18 RED `usecase/ObserveRequestDetailUseCaseTest` (usar `first { ... }` por emisión transitoria): `[rr]` Solicitud inexistente; Cambio en vivo a ACCEPTED; Solicitud de otro cliente; Detalle de una solicitud cancelada tras ser aceptada; `[prof]` Detalle con profesional asignado; Profesional ve una solicitud abierta (`contact = null`); `[feed]` Detalle desde el feed; Solicitud tomada por otro profesional; Solicitud cancelada que no era propia; Solicitud propia aceptada; `[contact]` La cancelación oculta el contacto en vivo.
- [x] 1.4.19 GREEN `usecase/ObserveRequestDetailUseCase.kt` (`viewerIsAssignee`, distancia `roundKm`, `Unauthorized` → `contact = null`).
- [x] 1.4.20 RED `usecase/CompleteJobUseCaseTest`: `[jc]` Completar con reseña; Comentario omitido; Calificación fuera de rango; Comentario demasiado largo; Otro usuario intenta completar; Completar una solicitud abierta/ya completada/cancelada (`InvalidTransition` vía doble); comentario recortado, vacío → `null`.
- [x] 1.4.21 GREEN `usecase/CompleteJobUseCase.kt`.
- [x] 1.4.22 REFACTOR y verificar: ejecutar `./gradlew :core:domain:test`; repetir el spike 1.2.2 con los casos de uso reales inyectados en un ViewModel (fábricas de Dagger); anotar resultado y actualizar el respaldo si hace falta. Resultado 2026-10-10: resuelto, sin respaldo; los 10 casos de uso reales se inyectaron en un `@HiltViewModel` descartable de `:features:auth` y `:app:assembleDebug` generó el componente de Hilt con `new XxxUseCase(...)` para los 10 (las fábricas de Dagger se generan en el componente, no en `:core:domain`). Sin `RepositoryModule` (llega en 1.5.15) el grafo solo falla por los 5 repositorios sin binding (`MissingBinding`), por lo que el spike usó un módulo descartable con `@Provides` ficticios en `:app`. Clase y módulo descartables borrados. `./gradlew :core:domain:test`: 233 tests en verde (101 de reglas + 132 de casos de uso).

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

## Fase 2: Firebase (Auth + Firestore) y Cloudinary (solo bindings, sin tocar `core/domain` ni `features/*`)

> Prerrequisito: Fase 1 cerrada. Los Fakes NO se borran. Plan Spark de Firebase y plan gratuito de Cloudinary, sin tarjeta. No hay Cloud Storage for Firebase ni `storage.rules` (diseño §13.7).

### 2.1 Configuración

- [ ] 2.1.1 Crear el proyecto Firebase en el plan Spark (Auth email/contraseña y Firestore; **sin** Storage), registrar `applicationId` `ar.edu.unaj.fixit` y colocar `app/google-services.json` (decidir si se versiona). Confirmar que el panel de facturación no pide tarjeta.
- [ ] 2.1.2 `[manual]` Crear la cuenta gratuita de Cloudinary (sin tarjeta) y el *upload preset* según la tabla de diseño §13.7 (*Unsigned*; *asset folder* `fixit/requests`; *unique filename* sí; *overwrite* no; *allowed formats* `jpg`; *incoming transformation* `c_limit,w_1600,h_1600/q_auto:good`; el tamaño máximo por archivo es opcional y solo si la consola lo ofrece, de lo contrario rigen el límite de la cuenta de 10 MB y la transformación de entrada). Anotar *cloud name* y nombre del preset, guardar una captura de la consola y resolver el ítem §19 "Opciones del preset" (qué opciones existen realmente; carpeta, formatos y transformación son obligatorios, el tamaño máximo no).
- [ ] 2.1.3 Actualizar `libs.versions.toml` y `build.gradle.kts` raíz/`:app`: plugin `google-services`; Firebase BoM, `kotlinx-coroutines-play-services` y `geofire-android-common` en `:core:data`; `okhttp`, `okhttp-coroutines` y `kotlinx-serialization-json` en `:core:data` (verificar versión estable de OkHttp 5).
- [ ] 2.1.4 Agregar permiso `INTERNET` al manifest de `:app`, `coil-network-okhttp` a `:core:ui` y `androidx.exifinterface` a `:core:data`.
- [ ] 2.1.5 Configurar `CLOUDINARY_CLOUD_NAME` y `CLOUDINARY_UPLOAD_PRESET`: `core/data/build.gradle.kts` los lee de `local.properties` (no versionado) con `java.util.Properties` y los expone con `buildConfigField` (`buildFeatures { buildConfig = true }`); si faltan quedan vacíos. Documentar las claves en `README.md` y comprobar que `./gradlew test` y `:app:assembleDebug` siguen compilando sin las claves (D22).
- [ ] 2.1.6 Spike (§19): comprobar que `okhttp-coroutines` resuelve con la versión elegida y que `executeAsync()` compila; leer su documentación sobre cancelación del `Call`. Respaldo: `suspendCancellableCoroutine` + `enqueue` con `invokeOnCancellation { call.cancel() }` (actualizar §13.7/D21 si se usa). La cancelación real se comprueba en 2.9.4 ("sin red").

### 2.2 Utilidades de error (`:core:data/firebase`)

- [ ] 2.2.1 RED `firebase/FirebaseCallsTest` (`runTest` + `advanceTimeBy`): éxito → `Success`; excepción → `Error` mapeado (tabla §13.4: `FirebaseFirestoreException` `UNAVAILABLE`/`DEADLINE_EXCEEDED` → `Network`, `PERMISSION_DENIED` → `Unauthorized`, `NOT_FOUND` → `NotFound`, `FirebaseAuthInvalidCredentialsException`/`InvalidUserException` → `InvalidCredentials`, `FirebaseAuthUserCollisionException` → `EmailAlreadyInUse`, otras → `Unknown`); `DomainException` (también como causa) → su `error`; operación que no termina → `Error(Network)` a los 15 s de tiempo virtual (por defecto) y, con `timeoutMs = UPLOAD_TIMEOUT_MS`, a los 30 s; cancelación del llamador se propaga.
- [ ] 2.2.2 GREEN `firebase/FirebaseCalls.kt`: `WRITE_TIMEOUT_MS` (15 s), `UPLOAD_TIMEOUT_MS` (30 s), `firebaseCall(timeoutMs = WRITE_TIMEOUT_MS, block)`, `DomainException`, `Throwable.toDomainError()`.
- [ ] 2.2.3 RED `FirebaseCallsTest` (`asResult`): emite `Success` por elemento y `Error` mapeado si el flujo falla.
- [ ] 2.2.4 GREEN `asResult()` en `FirebaseCalls.kt`.

### 2.3 Compresión de imágenes (`:core:data/cloudinary`)

- [ ] 2.3.1 RED `cloudinary/ImageCompressorTest` (`targetSize` y `sampleSizeFor`): `[media]` Cálculo del tamaño de compresión (4000x3000 → 1600x1200; 3000x4000 → 1200x1600; 800x600 sin cambios; relación extrema sin lado en 0); Cálculo del submuestreo de decodificación (`sampleSizeFor`: 4000x3000 → 2; 12000x9000 → 4; 800x600 → 1; 3200x2400 → 2; ancho o alto ≤ 0 → 1).
- [ ] 2.3.2 GREEN `cloudinary/ImageCompressor.kt`: `targetSize` y `sampleSizeFor` puras + `compress(uri)` en `withContext(dispatcher)` (el `@IoDispatcher` llega por constructor, D13) con decodificación acotada (leer solo los límites con `inJustDecodeBounds`, decodificar con `inSampleSize = sampleSizeFor(...)`, nunca a tamaño completo), orientación EXIF, `targetSize` sobre las dimensiones ya rotadas, rotación y escala en **una sola** `Matrix` con una única llamada a `Bitmap.createBitmap(src, 0, 0, w, h, matrix, true)` (orden: decodificar submuestreada → una única transformación, una sola copia en memoria), reciclado del bitmap decodificado y del transformado y JPEG calidad 80 (diseño §13.6).

### 2.4 Subida a Cloudinary (`:core:data/cloudinary`)

- [ ] 2.4.1 RED `cloudinary/BuildUploadRequestTest`: `[media]` `POST` a `https://api.cloudinary.com/v1_1/{cloud}/image/upload`; cuerpo multipart con exactamente las partes `file` (`image/jpeg`, nombre `photo.jpg`) y `upload_preset`; ningún otro campo (sin API secret ni firma).
- [ ] 2.4.2 GREEN `cloudinary/CloudinaryUpload.kt`: `CloudinaryConfig`, `CloudinaryUploadException(httpCode)` y `buildUploadRequest(config, bytes)` pura.
- [ ] 2.4.3 RED `cloudinary/ParseUploadResponseTest`: 200 con `secure_url` → la URL; 200 sin `secure_url` → `CloudinaryUploadException(200)`; 400 con cuerpo de error → `CloudinaryUploadException(400)`; cuerpo que no es JSON → excepción (sin usar `org.json`).
- [ ] 2.4.4 GREEN `parseUploadResponse(code, body)` pura con `Json.parseToJsonElement(...).jsonObject["secure_url"]`.
- [ ] 2.4.5 RED `cloudinary/PhotoUploadErrorsTest` (`runTest`, `PhotoUploader` de prueba): `[media]` Mapeo de fallas de la subida: `firebaseCall { uploader.upload(bytes) }` con `IOException` → `Network`; subida que no termina → `Network` a los 30 s de tiempo virtual (`firebaseCall(UPLOAD_TIMEOUT_MS)`); `CloudinaryUploadException` (400, 401, 500) o `IllegalStateException` (configuración vacía) → `Unknown`; nunca lanza hacia afuera.
- [ ] 2.4.6 GREEN: interfaz `PhotoUploader { suspend fun upload(bytes: ByteArray): String }` y filas nuevas de `toDomainError()` (`java.io.IOException` → `Network`; `CloudinaryUploadException` → `Unknown` con el código en el log; el resto ya cae en `Unknown`).
- [ ] 2.4.7 Crear `cloudinary/CloudinaryUploader.kt` (`@Inject`, `check` de configuración no vacía, `client.newCall(buildUploadRequest(...)).executeAsync().use { parseUploadResponse(...) }`; sin dispatcher). Sin test JVM: la subida real se verifica en 2.9.4.
- [ ] 2.4.8 Crear `di/NetworkModule.kt` (`OkHttpClient` singleton, `CloudinaryConfig` desde `BuildConfig`, `@Binds PhotoUploader` → `CloudinaryUploader`) y comprobar `:app:assembleDebug`.

### 2.5 Repositorios Firebase (sin tests JVM: se verifican con el checklist 2.9)

- [ ] 2.5.1 Crear `firebase/Dtos.kt` (DTO + mappers; `getTimestamp(campo, ESTIMATE)`; `geohash` solo en el DTO; `photoUrls` como lista de URLs; `ratingSum = 0`/`reviewCount = 0` al crear usuario).
- [ ] 2.5.2 Crear `firebase/FirebaseAuthRepository.kt`: bandera de operación en curso, `signUp` (Auth → `users/{uid}`; rollback `user.delete()` en `NonCancellable`), `signIn`, `session` (`Loading`/`SignedOut`/`SignedIn`; vuelve a emitir cuando cambia el perfil, lo que usará `TopicSync` en Fase 4), cuenta huérfana y `signOut()`.
- [ ] 2.5.3 Crear `firebase/FirebaseUserRepository.kt` (`get`, `observe`, `updateProfile` con campos editables solamente).
- [ ] 2.5.4 Crear `FirebaseRepairRequestRepository` parte 1: `create` (por cada foto, en secuencia: `ImageCompressor.compress` **fuera** del timeout y `PhotoUploader.upload` dentro de su propio `firebaseCall(UPLOAD_TIMEOUT_MS)` de 30 s; luego la escritura de la solicitud con `photoUrls` dentro de otro `firebaseCall` de 15 s; la primera falla corta la creación, no se crea la solicitud y **no** se intenta borrar fotos; diseño §13.1 y §13.4), `get`, `observe`, `observeByClient`, `observeByProfessional`, `observeReview`.
- [ ] 2.5.5 `FirebaseRepairRequestRepository` parte 2: `observeOpenNear` con `GeoFireUtils` (un listener por límite, `asResult`, `combine`, sin duplicados).
- [ ] 2.5.6 `FirebaseRepairRequestRepository` parte 3: `accept` (idempotente), `cancel`, `completeWithReview` con `runTransaction` dentro de `firebaseCall`, lanzando `DomainException`.

### 2.6 Conmutación

- [ ] 2.6.1 Crear `di/FirebaseModule.kt` (`FirebaseAuth` y `FirebaseFirestore`; **sin** `FirebaseStorage`) y cambiar los tres `@Binds` de repositorios en `RepositoryModule`.
- [ ] 2.6.2 Verificar que el diff de Fase 2 no toca `core/domain` ni `features/*`; probar el rollback revirtiendo solo `RepositoryModule.kt`.

### 2.7 Reglas e índices

- [ ] 2.7.1 Crear `firestore.rules` (diseño §13.3, incluidos `hasOnly` de cancelar/completar, `existsAfter` y `isPhotoUrl`/`validPhotos` con el marcador `<CLOUD_NAME>`, que hay que sustituir por el *cloud name* real antes de desplegar).
- [ ] 2.7.2 Crear `firestore.indexes.json` (`status+geohash`, `clientId+createdAt desc`, `professionalId+acceptedAt desc`). No se crea `storage.rules`.
- [ ] 2.7.3 Desplegar reglas e índices (o Emulator Suite) y probar los rechazos esperados, incluido §19 "Regex de `validPhotos`": una `secure_url` real (con `v123...`; sin asumir que incluye la carpeta, que en carpetas dinámicas no figura en la URL) se acepta y una URL de otro host o con 0 o 4 fotos se rechaza. Comprobar también las marcas de tiempo fijadas por el servidor (§13.3): crear una solicitud con `createdAt` distinto de `request.time` (una fecha futura o pasada escrita a mano) se rechaza y con `serverTimestamp()` se acepta; aceptar, cancelar y completar con `acceptedAt`, `cancelledAt` o `completedAt` de otra fecha se rechazan.

### 2.8 Verificación abierta

- [ ] 2.8.1 Verificar (§19) escrituras offline de Firestore: `withTimeout` no cancela una escritura encolada. Si se sincroniza más tarde, usar `runTransaction` también en `create` y `updateProfile`.

### 2.9 Checklist manual de Fase 2 (26 `[manual]`)

- [ ] 2.9.1 Auth y perfiles (9): `[auth]` Reinicio con sesión; Registro sin conectividad; Mismos errores que en Fase 1; Falla al escribir el perfil; Pantalla de registro estable durante la operación; Cuenta sin perfil; `[prof]` Actualización en vivo de la reputación; La edición se ve en otro dispositivo; Intento de cambiar el email o el rol.
- [ ] 2.9.2 Solicitudes y feed (5): `[rr]` Solicitud visible en otro dispositivo; Fallo de red al publicar; `[feed]` Solicitud publicada desde otro dispositivo; Dos dispositivos aceptan; Sobrescritura rechazada.
- [ ] 2.9.3 Finalización y ubicación (5): `[jc]` Fallo parcial; Sin conectividad; Dos intentos simultáneos; `[loc]` Dentro del radio; Cerca pero fuera del radio.
- [ ] 2.9.4 Fotos y Cloudinary (7): `[media]` Subida exitosa; Firestore guarda solo las URLs; Calidad visual de la compresión (foto vertical con EXIF se ve derecha); Falla la segunda foto (la primera permanece en Cloudinary); Falla la escritura de la solicitud; Preset sin firma y sin secretos en la app (revisar repositorio, APK y opciones del preset); Cuentas sin tarjeta (Firebase Spark, Cloudinary gratuito). Comprobaciones extra del diseño §15: publicar con 3 fotos las deja en la carpeta `fixit/requests` de la *Media Library* (la carpeta no se verifica en la URL); una foto de 12 MP o más se procesa y se sube sin cerrar la app por falta de memoria; sin red, el mensaje de red aparece en 30 s como máximo (límite de la subida) y no queda una subida colgada; `local.properties` sin las claves → error genérico sin colgarse; un `curl` con un PDF al preset → rechazado; las opciones reales del preset coinciden con el diseño (si no hay tamaño máximo por preset, se anota que rige el límite de la cuenta); una solicitud escrita a mano con una URL ajena en `photoUrls` → rechazada por `validPhotos`.
- [ ] 2.9.5 Cierre: `./gradlew test` y `./gradlew build` en verde; marcar Fase 2 en `state.yaml`.

---

## Fase 3: Hardware (ubicación, cámara, Photo Picker)

> Sin cambios respecto de la versión anterior (la subida a Cloudinary de Fase 2 no afecta a `PhotoPickerButtons`: el dominio sigue recibiendo `String`). Estos escenarios son `[manual]` por depender de hardware (diseño §15); no hay tests JVM nuevos.

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

## Fase 4: Notificaciones push (FCM + Cloudflare Worker, opcional)

> Prerrequisito: Fase 2 cerrada (el Worker valida el ID token de Firebase y lee Firestore). Sin cambios en `:core:domain` ni `:features:*`: todo vive en `worker/`, `:core:data/push` y `:app`. Cloudflare Workers en el plan gratuito y FCM en Spark, sin tarjeta. La cuenta de servicio de FCM solo existe como secreto del Worker. Los tests del Worker son `vitest` en Node (`npm test` dentro de `worker/`), funciones puras y sin Miniflare (D23); la firma real, Firestore y FCM se verifican en el checklist.

### 4.1 Andamiaje del Worker (`worker/`, fuera de Gradle)

- [ ] 4.1.1 Crear `worker/` con `package.json` (scripts `test` y `dev`), `tsconfig.json` (estricto), `vitest.config.ts`, `wrangler.toml` (`[vars] FIREBASE_PROJECT_ID`, sin secretos) y `.gitignore` (`.dev.vars`, `node_modules/`, `.wrangler/`). Dependencias: `typescript`, `vitest`, `wrangler`, `@cloudflare/workers-types`. Fijar la versión de Node usada (WebCrypto global) en `README.md` del Worker.
- [ ] 4.1.2 RED/GREEN humo: un test `worker/test/smoke.test.ts` que falla; ejecutar `npm test` y comprobar que corre y reporta el fallo; corregirlo, comprobar el verde y borrar el archivo.
- [ ] 4.1.3 Actualizar `openspec/config.yaml` (agregar el corredor `npm test` en `worker/` para escenarios Worker-only de Fase 4) y registrar en `state.yaml` que `npm test` es ejecutable.

### 4.2 Funciones puras del Worker (test primero)

- [ ] 4.2.1 RED `worker/test/body.test.ts`: `parseNotifyBody(json)` acepta `type` ∈ {`NEW_REQUEST`, `REQUEST_ACCEPTED`, `REQUEST_CANCELLED`} y `requestId` que cumple `^[A-Za-z0-9]{1,40}$`; rechaza tipo desconocido, `requestId` con `/`, `..` o vacío, cuerpo que no es objeto; ignora cualquier otro campo (un tema o categoría en el cuerpo no aparece en el resultado).
- [ ] 4.2.2 GREEN `worker/src/body.ts` (`parseNotifyBody`, pura; extraída del paso 1 de `index.ts`).
- [ ] 4.2.3 RED `worker/test/rules.test.ts`: `decideTarget(type, request, uid)` (reglas de estado y llamador; la vigencia se agrega en 4.2.5): `[notif]` Contenido del mensaje de nueva solicitud (`GAS` y `r1` → `{ type: "NEW_REQUEST", requestId: "r1", category: "GAS" }` exactamente); Contenido de los mensajes de aceptación y de cancelación (solo `type` y `requestId`); `NEW_REQUEST` solo con `OPEN` y `clientId == uid` → tema `category_{CATEGORY}`; `REQUEST_ACCEPTED` solo con `ACCEPTED`, `professionalId == uid` → `user_{clientId}`; `REQUEST_CANCELLED` solo con `CANCELLED`, `clientId == uid` y `professionalId` presente → `user_{professionalId}`; cada condición incumplida (estado distinto, llamador ajeno, `professionalId` ausente) → rechazo 403; el tema y la categoría salen siempre de la solicitud leída, nunca del cuerpo. Es la cobertura automatizada de los escenarios de "Verificaciones del Worker" (que además están en el checklist 4.13.4).
- [ ] 4.2.4 GREEN `worker/src/rules.ts` (`decideTarget`, pura).
- [ ] 4.2.5 RED `worker/test/rules.test.ts` (b) vigencia del evento (protección ante repetición): `decideTarget(type, request, uid, nowMs)`: `[notif]` Evento fuera de la ventana de vigencia: `NEW_REQUEST` con `createdAt`, `REQUEST_ACCEPTED` con `acceptedAt` y `REQUEST_CANCELLED` con `cancelledAt` de hace más de `EVENT_MAX_AGE_MS = 120_000` → rechazo 403; marca ausente → 403; exactamente 120 s → aceptado; evento reciente → aceptado; **marca futura**: a más de `EVENT_MAX_SKEW_MS = 60_000` en el futuro → 403, exactamente 60 s en el futuro (desfase de relojes tolerado) → aceptado. Actualizar las llamadas de 4.2.3 para pasar `nowMs` con marcas recientes.
- [ ] 4.2.6 GREEN `worker/src/rules.ts`: `decideTarget(type, request, uid, nowMs)` con las constantes `EVENT_MAX_AGE_MS` y `EVENT_MAX_SKEW_MS` y la marca según el tipo (`createdAt`, `acceptedAt`, `cancelledAt`); con `age = nowMs − marca` se acepta `−EVENT_MAX_SKEW_MS ≤ age ≤ EVENT_MAX_AGE_MS`.
- [ ] 4.2.7 RED `worker/test/claims.test.ts`: `checkClaims(payload, projectId, nowSec)`: token válido aceptado; `aud` ajeno, `iss` ajeno, `exp <= now`, `iat > now + 60` y `sub` vacío → rechazo.
- [ ] 4.2.8 GREEN `worker/src/claims.ts` (`checkClaims`, pura; `iss == https://securetoken.google.com/{projectId}`, `aud == projectId`).

### 4.3 Verificación del ID token y caché de claves

- [ ] 4.3.1 RED `worker/test/idToken.test.ts`: con un par RSA generado en el test (`crypto.subtle.generateKey`) y un `getJwks` inyectado: `verifyIdToken` acepta un JWT RS256 bien firmado y devuelve el `uid`; rechaza firma alterada, `alg` distinto de `RS256`, `kid` ausente y token mal formado; `parseMaxAge(cacheControl)` y la caché de claves (no vuelve a pedir las claves antes de vencer; sí después, con reloj inyectado); **`kid` desconocido**: con la caché vigente, vuelve a pedir el JWKS **una sola vez** (si pasaron al menos 60 s desde la última descarga, reloj inyectado) y acepta el token si la clave rotada aparece; si sigue desconocido → rechazo; un segundo `kid` desconocido dentro de los 60 s → rechazo **sin** otra descarga (no se puede forzar una descarga por pedido).
- [ ] 4.3.2 GREEN `worker/src/idToken.ts`: decodificación del JWT, caché de JWKS a nivel de módulo según `Cache-Control: max-age` con una nueva descarga única (intervalo mínimo de 60 s) ante un `kid` desconocido, firma con `crypto.subtle.verify("RSASSA-PKCS1-v1_5", ...)` y claims con `checkClaims`.

### 4.4 Lectura de la solicitud en Firestore (REST)

- [ ] 4.4.1 RED `worker/test/firestore.test.ts` (con `fetch` inyectado): `fetchRequest(projectId, requestId, idToken)` pide `GET https://firestore.googleapis.com/v1/projects/{id}/databases/(default)/documents/requests/{requestId}` con `Authorization: Bearer <ID token del usuario>`; mapea los campos tipados a `{ status, clientId, professionalId?, category, createdAt, acceptedAt?, cancelledAt? }` (`stringValue`; `timestampValue` → epoch ms); `professionalId` ausente → `undefined`; 404 → `not_found`; otro error HTTP → fallo 502; `timestampValue` RFC 3339 sin fracción, con milisegundos, con microsegundos y con nanosegundos (`2026-10-08T12:34:56.123456789Z` da el mismo epoch ms que `2026-10-08T12:34:56.123Z`).
- [ ] 4.4.2 GREEN `worker/src/firestore.ts` (`fetchRequest` y mapeo del documento; la fracción de segundo de `timestampValue` se trunca a milisegundos antes de convertir, sin depender de `Date.parse` con más de 3 dígitos). El respaldo (cuenta de servicio con alcance `datastore`) solo se implementa si 4.7.3 demuestra que la lectura con el ID token no funciona.

### 4.5 OAuth de la cuenta de servicio y envío por FCM

- [ ] 4.5.1 RED `worker/test/googleAuth.test.ts`: `buildJwtClaims(clientEmail, nowSec)` (`iss`, `scope` `https://www.googleapis.com/auth/firebase.messaging`, `aud` `https://oauth2.googleapis.com/token`, `exp = iat + 3600`); `signJwt` RS256 con una clave PKCS#8 generada en el test, verificable con la clave pública; `getAccessToken` con `fetch` y reloj inyectados: usa la caché hasta `expires_in − 60 s` (una sola llamada) y la renueva después.
- [ ] 4.5.2 GREEN `worker/src/googleAuth.ts` (importa la clave PKCS#8 una sola vez, firma con `crypto.subtle.sign`, `POST` a `oauth2.googleapis.com/token` con `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer`, caché a nivel de módulo).
- [ ] 4.5.3 RED `worker/test/fcm.test.ts`: `[notif]` Prioridad y vigencia por tipo de evento: `buildFcmMessage(topic, data)` elige por `data.type`: `NEW_REQUEST` → `{ message: { topic, data, android: { priority: "NORMAL", ttl: "3600s" } } }`; `REQUEST_ACCEPTED` y `REQUEST_CANCELLED` → `priority: "HIGH"`, `ttl: "86400s"`; ninguno con bloque `notification`; `sendFcm` hace `POST https://fcm.googleapis.com/v1/projects/{id}/messages:send` con `Authorization: Bearer`; respuesta no 2xx → falla.
- [ ] 4.5.4 GREEN `worker/src/fcm.ts`.

### 4.6 Manejador `/notify`

- [ ] 4.6.1 RED `worker/test/index.test.ts` (dependencias inyectadas): orden de comprobaciones de §14b.7 (ruta → método → cuerpo → token → ...): otra ruta → 404, método distinto de `POST` → 405 (con `Allow: POST`), cuerpo inválido → 400 (con cualquier token); sin token o token inválido, **con un cuerpo válido**, → 401 sin llamar a Firestore ni FCM; solicitud inexistente → 404; condiciones incumplidas de `decideTarget` (incluido un evento vencido) → 403 sin enviar; éxito → 204 con exactamente un envío FCM al tema derivado de la solicitud leída (cuerpo con tema/categoría ajenos ignorado); fallo de Firestore, OAuth o FCM → 502.
- [ ] 4.6.2 GREEN `worker/src/index.ts`: orquesta los pasos de §14b.7 (ruta y método → validar cuerpo → verificar token → releer solicitud → decidir → token OAuth → enviar) con `env.FIREBASE_PROJECT_ID` y el secreto `GOOGLE_SERVICE_ACCOUNT`; responde sin esperar la entrega de FCM más allá de la aceptación del mensaje; sin CORS, sin rate limiting (D18) y sin KV/D1 salvo el plan B (c) de 4.7.4.

### 4.7 Cuentas, secretos y despliegue del Worker `[manual]`

- [ ] 4.7.1 `[manual]` Crear la cuenta gratuita de Cloudflare (sin tarjeta); en el proyecto Firebase habilitar "Firebase Cloud Messaging API" y crear una cuenta de servicio **dedicada** con el rol mínimo *Firebase Cloud Messaging API Admin* (no la del Admin SDK); descargar su JSON fuera del repositorio.
- [ ] 4.7.2 `[manual]` Cargar el secreto con `wrangler secret put GOOGLE_SERVICE_ACCOUNT`; para pruebas locales usar `worker/.dev.vars` (ignorado por git). Confirmar con `git status` y una búsqueda de `private_key` que ningún archivo con la cuenta de servicio está en el repositorio.
- [ ] 4.7.3 `[manual]` Probar con `wrangler dev` y desplegar con `wrangler deploy`; anotar la URL. Con un ID token real y `curl` (obtenerlo con `signInWithPassword`, ver la nota de 4.13.4), comprobar el flujo y resolver §19 "Firestore REST con el ID token": la lectura con `Authorization: Bearer <ID token>` funciona y aplica las reglas. Si no funciona, implementar el respaldo de 4.4.2 (alcance `datastore`) con su test.
- [ ] 4.7.4 `[manual]` Medir el tiempo de CPU por pedido en el panel de Workers tomando como caso base la **ruta fría** (primer pedido de un *isolate* nuevo: importar JWKS + verificar RS256 + importar PKCS#8 + firmar el JWT + JSON; §19 "CPU del Worker") y luego la ruta caliente. Si la ruta fría supera 10 ms, aplicar el plan B escalonado (diseño §14b.7) y repetir la medición tras cada paso: (a) importar cada `CryptoKey` una sola vez y mantener el token OAuth y el JWKS en la memoria del *isolate*; (b) comprobar si `caches.default` (*Cache API*) persiste entre pedidos en `*.workers.dev` (la documentación de Cloudflare solo afirma que las operaciones de caché funcionan en dominios personalizados y no hay dominio propio): guardar un valor, repetir el pedido y verificar si se recupera; si persiste, guardar allí el token OAuth y el JWKS; (c) si no persiste, usar Workers KV del plan gratuito (sin tarjeta; confirmar sus límites vigentes) para el token OAuth y el JWKS, con `[[kv_namespaces]]` en `wrangler.toml` y un KV inyectado en sus tests; (d) si ninguna alcanza, aceptar el costo de la ruta fría y anotarlo en `state.yaml`. No se asume que la *Cache API* ni KV sean suficientes hasta medirlos.

### 4.8 Dependencias y configuración Android

- [ ] 4.8.1 Agregar `firebase-messaging` (BoM) y `androidx.core:core-ktx` (`NotificationManagerCompat`) a `:core:data`, y `firebase-messaging` a `:app`; `PUSH_WORKER_URL` desde `local.properties` → `buildConfigField` en `:core:data` (vacío por defecto, como en 2.1.5); documentar la clave en `README.md`; declarar `POST_NOTIFICATIONS` en el manifest de `:app`. Comprobar que `./gradlew test` y `:app:assembleDebug` compilan sin la clave.
- [ ] 4.8.2 Crear el calificador `@ApplicationScope` y, en `CoreModule`, `@Provides @Singleton @ApplicationScope fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob())` (solo lo reciben el decorador y `TopicSync`; es un *scope*, no un dispatcher inyectado). Sin test JVM propio: los tests usan `backgroundScope`; se verifica con `:app:assembleDebug` en 4.11.1.

### 4.9 Lógica push en `:core:data/push`

- [ ] 4.9.1 RED `push/PushDecisionTest` (`shouldNotify`): `[notif]` Solicitud dentro del radio; Solicitud fuera del radio; Solicitud exactamente en el límite del radio (distancia sin redondear igual al radio → `true`); Categoría ya no vigente; Solicitud que ya no está OPEN; Sin sesión de profesional (visor `CLIENT` o `null`); `REQUEST_ACCEPTED` y `REQUEST_CANCELLED` con el visor correcto, con un visor ajeno y con un estado distinto; prefiltro `mayMatchCategory(category, viewer)`: categoría vigente → `true`, no vigente → `false`, visor `CLIENT` o `null` con categoría → `false`, payload sin categoría → `true`.
- [ ] 4.9.2 GREEN `push/PushDecision.kt`: `PushEventType`, `PushEvent`, `shouldNotify` (reutiliza `Matching.distanceIfMatches`; sin cambios de dominio) y `mayMatchCategory`.
- [ ] 4.9.3 RED `push/PushMessageHandlerTest` (dobles de `testFixtures`; `session` controlable de `TestAuthRepository`): `handle(data)` devuelve `null` sin sesión, con `type` desconocido o sin `requestId`; `[notif]` Categoría ya no vigente (prefiltro): un `NEW_REQUEST` cuya `category` no figura entre las categorías del perfil de la sesión → `null` **sin ninguna llamada a `get`** de los repositorios de usuarios ni de solicitudes; Falla la lectura de la solicitud (`get` con `Error`) → `null` sin lanzar; `get` del usuario con `Error` → `null`; flujo correcto → el `PushEvent`.
- [ ] 4.9.4 GREEN `push/PushMessageHandler.kt` (solo interfaces del dominio; prefiltro por categoría con `authRepository.session.first { it !is Loading }` antes de cualquier `get`; la ubicación del profesional no sale del dispositivo).
- [ ] 4.9.5 RED `push/TopicsTest`: `[notif]` Temas de un cliente (`{user_c1}`); Temas de un profesional (`{user_p1, category_PLUMBING, category_GAS}`); Temas sin sesión (vacío); `topicChanges(applied, maybe, desired)`: iniciar sesión suscribe todo, editar categorías suscribe solo las agregadas y desuscribe solo las quitadas, editar solo el radio no genera operaciones, cerrar sesión desuscribe todo lo de `applied ∪ maybe`; un tema `maybe` que sigue deseado se vuelve a suscribir; un tema presente en `applied` y en `maybe` a la vez (estado inconsistente) y deseado no se suscribe de nuevo (se trata como `applied`) y no deseado se desuscribe una sola vez.
- [ ] 4.9.6 GREEN `push/Topics.kt` (`topicsFor`, `topicChanges`, puras).
- [ ] 4.9.7 RED `push/TopicSyncTest` (a) operaciones (`TestAuthRepository.session` + `RecordingTopicMessaging` configurable para fallar + `backgroundScope`): `[notif]` Suscripción al iniciar sesión; Edición de categorías actualiza los temas; Edición que no cambia categorías; Cerrar sesión desuscribe todo; Falla de suscripción no bloquea la operación (una operación que falla no detiene las demás ni la recolección y no llega a la presentación); `Loading` se ignora; emisiones repetidas sin cambios no generan operaciones; **Operación fallida se reintenta en la siguiente emisión de sesión** (el tema fallido no entra en `applied`; con el suscriptor recuperado y una nueva clave de sesión se aplica solo lo pendiente, sin repetir lo que ya tuvo éxito). Las fallas dejan el tema en `maybe` (no en `applied`) y una suscripción exitosa lo mueve de `maybe` a `applied`; la persistencia de esos estados se prueba en 4.9.9, y el token nuevo, el tiempo agotado, la cancelación y la concurrencia en 4.9.11. Una reemisión que solo cambia el nombre o la reputación del perfil, o el paso del caché al servidor del mismo perfil, no genera operaciones ni cancela las que están en curso (`distinctUntilChanged` sobre la proyección); el reintento ocurre con la siguiente clave (por ejemplo, editar categorías) o al arrancar.
- [ ] 4.9.8 GREEN `push/TopicSync.kt` y `TopicMessaging` (`subscribe`/`unsubscribe` devuelven `Boolean`: éxito o falla): primera versión de `start()`, que recolecta `AuthRepository.session` en el `@ApplicationScope`, ignora `Loading`, aplica `topicChanges(applied, maybe, desired)` y ejecuta cada operación de forma independiente y de mejor esfuerzo; el estado `TopicState(applied, maybe)` vive en memoria de forma **transitoria** hasta 4.9.10, y esta recolección simple (solo `session`) la **reemplaza** 4.9.12 por la versión definitiva con `distinctUntilChanged`, `combine(…, tokenEpoch)` y `collectLatest`. Aquí una operación exitosa mueve el tema a (o fuera de) `applied`; la regla de intención previa (`maybe`) se define una sola vez en 4.9.10.
- [ ] 4.9.9 RED `push/TopicSyncTest` (b) persistencia y bandeja (`InMemoryTopicStore`, `RecordingNoticeCanceller`): `[notif]` **Desuscripción fallida al cerrar sesión se repara al arrancar** (cierre de sesión con el suscriptor sin conexión → `applied` conserva los temas; un `TopicSync` nuevo con el mismo `InMemoryTopicStore` y la sesión `SignedOut` desuscribe `user_p1` y `category_GAS`); un `TopicSync` nuevo con sesión restaurada y `applied` ya igual a lo deseado no genera operaciones; con el almacén vacío suscribe todo; **Cerrar sesión retira los avisos de la bandeja** (la sesión pasa a `SignedOut` → `RecordingNoticeCanceller.cancelAll()` invocado; en `SignedIn` no se invoca).
- [ ] 4.9.10 GREEN `push/TopicSync.kt`: `TopicState(applied, maybe)` con la invariante `applied ∩ maybe = ∅` e interfaz `TopicStore` (`load(): TopicState`, `save(state)` síncrono, `markTokenDirty()`/`isTokenDirty()`/`clearTokenDirty()`); `start()` carga el estado al iniciar. **Escritura previa (write-ahead), definida aquí una sola vez** (4.9.12 y el diseño §14b.4 se refieren a ella): el estado se persiste tras **cada** mutación, no solo tras las exitosas: la intención (`maybe += t`) **antes** de llamar a `subscribe`, y la confirmación (`applied += t; maybe -= t`) o la baja (`applied -= t; maybe -= t`) **después** de que la operación termine bien; si falla, vence o se cancela, el tema queda en `maybe`. Interfaz `NoticeCanceller` (`cancelAll()`) y su invocación al recibir `SignedOut`.
- [ ] 4.9.11 RED `push/TopicSyncTest` (c) concurrencia y token (`TestAuthRepository.session` controlable, `RecordingTopicMessaging` cuyas operaciones se pueden bloquear o dejar vencer, `InMemoryTopicStore`, `RecordingNoticeCanceller`, `backgroundScope`): `[notif]` **Token nuevo con la sesión en determinación** (sesión `Loading` con temas ya aplicados y persistidos; `resubscribe()` no suscribe nada y vacía `applied` y `maybe`, también en el almacén; al pasar a `SignedIn` se suscribe a todo lo deseado aunque ya figurara aplicado antes del token); `resubscribe()` con la sesión ya `SignedIn` suscribe todo en la siguiente reconciliación y sin sesión no suscribe nada; **Cerrar sesión con operaciones pendientes cancela la bandeja de inmediato** (un `TopicMessaging` bloqueado en una suscripción; al pasar a `SignedOut` el `cancelAll()` ya se invocó antes de que termine ninguna operación y después se desuscribe `applied ∪ maybe`); **Suscripción con tiempo agotado se desuscribe al cerrar sesión** (una suscripción que no termina vence a los 15 s de tiempo virtual: el tema queda en `maybe` persistido y no en `applied`, y al cerrar sesión se desuscribe de él además de los aplicados); una operación interrumpida por una clave nueva (incluida una emisión `Loading`) deja su tema en `maybe`; **un tiempo agotado no aborta la emisión** (el `RecordingTopicMessaging` hace que una suscripción falle tras 15 s de tiempo virtual, como lo hace `FirebaseTopicMessaging` con `withTimeoutOrNull`; el tema siguiente de la misma emisión se suscribe igual y queda en `applied`, y el vencido queda en `maybe`); **token nuevo y muerte del proceso** (`resubscribe()` deja `isTokenDirty()` en `true` en el `InMemoryTopicStore` de inmediato, antes de que corra el recolector; un `TopicSync` nuevo con ese mismo almacén vacía `applied` y `maybe`, borra el indicador y suscribe todo lo deseado); una reemisión que solo cambia el nombre o la reputación no cancela una operación en curso; tras una confirmación y una cancelación seguidas, el `InMemoryTopicStore` solo registra estados disjuntos (`applied ∩ maybe = ∅`).
- [ ] 4.9.12 GREEN `push/TopicSync.kt`: versión definitiva de `start()` (reemplaza la de 4.9.8): `session.map(::sessionKey).distinctUntilChanged()` con `SessionKey(kind, uid, categorías)`, luego `combine(keys, tokenEpoch).collectLatest` (se elige `collectLatest` y no `conflate()` porque este no interrumpe el bloque en curso y un `SignedOut` esperaría hasta 15 s por cada operación pendiente; una emisión `Loading` también interrumpe el bloque, y es intencional); `SignedOut` invoca `cancelAll()` como primera acción del bloque; `Loading` nunca suscribe; sin `Mutex`: toda mutación y su `save` ocurren dentro del bloque (único consumidor); aplica la escritura previa de 4.9.10, y las confirmaciones (`applied += t; maybe -= t`, bajas) corren en `withContext(NonCancellable)`; `resubscribe()` hace `store.markTokenDirty()` (síncrono) y luego incrementa la época, y el bloque (o el siguiente arranque) que ve `isTokenDirty()` vacía el estado y borra el indicador (diseño §14b.4).
- [ ] 4.9.13 Crear `push/FirebaseTopicMessaging.kt` (`subscribeToTopic`/`unsubscribeFromTopic` con `withTimeoutOrNull(15_000) { task.await() }`: `null` o cualquier excepción salvo `CancellationException` se registra y devuelve falla `false`; nunca propaga `TimeoutCancellationException`, para que un tiempo agotado no escape de `reconcile`), `push/SharedPrefsTopicStore.kt` (`SharedPreferences` `topic_sync`: `save` hace un único `edit().putStringSet(KEY_APPLIED, applied.toSet()).putStringSet(KEY_MAYBE, maybe.toSet()).commit()` síncrono, porque el recolector no corre en el hilo principal; nunca modifica los `Set` devueltos por `getStringSet` (los copia con `toSet()` al leer y al escribir); el indicador `KEY_TOKEN_DIRTY` es una clave aparte con `commit()`) y `push/AndroidNoticeCanceller.kt` (`NotificationManagerCompat.from(context).cancelAll()`). Sin test JVM: se verifica en 4.13.3 y 4.13.6.
- [ ] 4.9.14 RED `push/BuildNotifyRequestTest`: `buildNotifyRequest(baseUrl, idToken, event)` → `POST {baseUrl}/notify`, cabecera `Authorization: Bearer <token>`, cuerpo JSON con exactamente `type` y `requestId`.
- [ ] 4.9.15 GREEN `push/WorkerClient.kt`: `WorkerClient` (`send(idToken, event)`), `WorkerConfig(baseUrl)`, `IdTokenProvider` y la función pura `buildNotifyRequest`.
- [ ] 4.9.16 Crear `push/HttpWorkerClient.kt` (OkHttp `executeAsync()`; la respuesta no se interpreta, solo `Log.w` si no es 2xx) y `push/FirebaseIdTokenProvider.kt` (`getIdToken(false)?.await()`). Sin test JVM: se verifica en 4.13.1 y 4.13.5.

### 4.10 Decorador del repositorio de solicitudes

- [ ] 4.10.1 RED `push/NotifyingRepairRequestRepositoryTest` (a) disparadores (`FakeRepairRequestRepository` sobre `FakeDatabase` como repositorio real, `RecordingWorkerClient`, `FixedIdTokenProvider`, `backgroundScope`): `[notif]` Crear solicita el envío una vez (1 pedido `NEW_REQUEST` con `requestId` e ID token); Creación fallida no envía aviso; Aceptar solicita el envío una vez; Aceptación rechazada no envía aviso (`AlreadyTaken`); Aceptación repetida no duplica el aviso (lectura previa acotada: ya `ACCEPTED` por el mismo profesional); Cancelar un ACCEPTED solicita el envío (1 pedido `REQUEST_CANCELLED`, solo con `professionalId != null`); Cancelar un OPEN no envía aviso; Cancelación rechazada no envía aviso (`Unauthorized`); error con `failNextWrite` no envía; sin ID token no envía; `get`, `observe*` y `completeWithReview` delegan sin pedidos.
- [ ] 4.10.2 GREEN `push/NotifyingRepairRequestRepository.kt` mínimo: `RepairRequestRepository by delegate`, `create`/`accept`/`cancel` con la lectura previa de `accept` y `fire(type, id)` lanzado en el `scope` inyectado; devuelve siempre el resultado del repositorio real.
- [ ] 4.10.3 RED `NotifyingRepairRequestRepositoryTest` (b) mejor esfuerzo: `[notif]` Lectura previa lenta no bloquea la aceptación (`HangingGetRepository`: la lectura previa nunca responde → `accept` termina con `Success` tras esperar como máximo 2 s de tiempo virtual y se pide exactamente 1 aviso); Falla del Worker al crear (error → `Success` con la solicitud); Falla del Worker al aceptar (excepción de red → `Success` y `ACCEPTED`); Falla del Worker al cancelar (sin respuesta → `Success` y `CANCELLED`); Tiempo de espera del Worker (`HangingWorkerClient`: el resultado ya fue `Success` y el pedido se descarta a los 5 s de tiempo virtual, sin reintentos); Sin Worker configurado (`withPush` con URL vacía devuelve la misma instancia sin decorar y no se realiza ningún pedido; con URL no vacía devuelve el decorador). Si ya pasan con 4.10.2, conservarlos como pruebas de caracterización.
- [ ] 4.10.4 GREEN completar el decorador: `try`/`catch` (propaga `CancellationException`, traga el resto), `withTimeoutOrNull(PUSH_TIMEOUT_MS = 5_000)`, la lectura previa de `accept` acotada con `withTimeoutOrNull(PRE_READ_TIMEOUT_MS = 2_000)` (al agotarse o fallar, `alreadyMine = false`) y la fábrica pura `withPush(real, workerUrl, worker, tokens, scope)`.

### 4.11 Bindings Hilt de Fase 4

- [ ] 4.11.1 Bindings Hilt de Fase 4 (diseño §11): en `RepositoryModule`, reemplazar el `@Binds` de `RepairRequestRepository` por un `@Provides @Singleton` que llama `withPush(real, config.baseUrl, worker, tokens, @ApplicationScope scope)` y agregar los `@Binds` de `WorkerClient` → `HttpWorkerClient`, `IdTokenProvider` → `FirebaseIdTokenProvider`, `TopicMessaging` → `FirebaseTopicMessaging`, `TopicStore` → `SharedPrefsTopicStore` y `NoticeCanceller` → `AndroidNoticeCanceller`; en `FirebaseModule`, `@Provides FirebaseMessaging`; en `NetworkModule`, `@Provides WorkerConfig` desde `BuildConfig.PUSH_WORKER_URL` (el `OkHttpClient` es el de Fase 2). Ejecutar `./gradlew :app:assembleDebug`.
- [ ] 4.11.2 Verificar que el diff de Fase 4 no toca `core/domain` ni `features/*`; probar el rollback con `PUSH_WORKER_URL` vacío (repositorio sin decorar) y revirtiendo solo `RepositoryModule.kt`.

### 4.12 `:app`: aviso visible, navegación y permiso

- [ ] 4.12.1 RED `app/src/test/.../navigation/DeepLinkTest`: `[notif]` Aviso de aceptación abre el detalle del cliente (`REQUESTS` + `ClientRequestDetailKey`); Aviso de nueva solicitud abre el detalle del profesional (`FEED` + `ProRequestDetailKey`); Aviso de cancelación abre el detalle del profesional (`MY_JOBS` + `ProRequestDetailKey`); Rol que no corresponde al evento → `null`; Sin sesión (`role == null`) → `null`.
- [ ] 4.12.2 GREEN `navigation/DeepLink.kt`: `DeepLinkDestination` y `deepLinkDestination(type, role, requestId)` pura.
- [ ] 4.12.3 RED `navigation/NavigationStateOpenTest` (`MutableList<NavKey>` comunes): `open(destination)` selecciona la pestaña, reinicia su pila a la raíz y deja el detalle arriba, también si la pestaña ya estaba activa con historial; `open(null)` muestra la raíz de la pestaña inicial.
- [ ] 4.12.4 GREEN `NavigationState.open(destination)` (`selectTab` + `resetToRoot` + `navigate`).
- [ ] 4.12.5 RED `push/NoticeTextTest`: `[notif]` El aviso visible no filtra datos personales: `noticeTextFor(type)` recibe solo el tipo de evento (sin solicitud ni personas), devuelve una clave de texto fija distinta para cada uno de los tres tipos y no depende de datos de la solicitud.
- [ ] 4.12.6 GREEN `push/NoticeText.kt` (`noticeTextFor`) y textos fijos en `strings.xml` de `:app` (español neutro): "Un profesional aceptó tu solicitud", "Hay una nueva solicitud cerca", "El cliente canceló un trabajo aceptado", y el nombre del canal.
- [ ] 4.12.7 Crear `push/Notifications.kt`: canal `requests` de importancia alta creado en `FixItApplication.onCreate`; `show(context, event)` con `tag = "${type}_${requestId}"`, id fijo, `setOnlyAlertOnce(true)`, `setAutoCancel(true)` (un aviso repetido o un doble toque simultáneo deja un solo aviso visible), `PendingIntent` `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT` con `requestCode = tag.hashCode()` hacia `MainActivity` con extras `type` y `requestId`; no publica nada si las notificaciones no están permitidas (sin `SecurityException`).
- [ ] 4.12.8 Crear `push/FixItMessagingService.kt` (`@AndroidEntryPoint`, registrado en el manifest): `onMessageReceived` → `runBlocking { withTimeoutOrNull(10_000) { handler.handle(message.data) } }` y `Notifications.show` si el evento no es `null`; `onNewToken` → `topicSync.resubscribe()`. `FixItApplication.onCreate` llama `topicSync.start()`. Si 4.13.6 muestra que el trabajo no cabe en el tiempo de FCM, aplicar el respaldo de WorkManager.
- [ ] 4.12.9 Pedir `POST_NOTIFICATIONS` en `AppShell` con `rememberLauncherForActivityResult(RequestPermission())`: solo si `SDK_INT >= 33` y no concedido, una vez al mostrarse el shell de un usuario con sesión; nunca en la pantalla de autenticación; denegar no muestra errores ni bloquea pantallas.
- [ ] 4.12.10 `MainActivity` (`singleTop`): guardar el par pendiente en un `MutableStateFlow<PushEvent?>` (en `onCreate` solo si `savedInstanceState == null`, y en `onNewIntent`); `FixItApp` lo procesa cuando la experiencia ya no es `LOADING`: en `AUTH` lo descarta; en `CLIENT`/`PROFESSIONAL`, dentro del `key(userId)`, `LaunchedEffect(event) { navigationState.open(deepLinkDestination(event.type, user.role, event.requestId)); consume() }`. Ejecutar `:app:assembleDebug`.

### 4.13 Cierre de Fase 4: verificaciones y checklist manual

> Total Fase 4: 27 escenarios `[manual]`. Se necesitan dos dispositivos o emuladores con Google Play. Cada ítem se tilda con fecha y dispositivo.
> Precauciones (diseño §15, "Notas para las pruebas de Fase 4"): **"app cerrada" = deslizarla desde recientes, NO *Forzar detención*** (FCM no entrega a una app detenida a la fuerza); en dispositivos de fabricantes con gestor de batería (Xiaomi, Huawei, Oppo, Samsung) desactivar la optimización y permitir el inicio automático, o usar emuladores; una suscripción nueva a un tema puede tardar minutos en propagarse (iniciar sesión con antelación o esperar antes del primer aviso de nueva solicitud); el aviso de nueva solicitud usa prioridad normal y puede demorar en segundo plano o con la app cerrada.

- [ ] 4.13.1 Eventos (4): `[notif]` El cliente recibe el aviso al aceptar; Solo los profesionales de la categoría reciben el aviso (probar uno fuera de radio y uno de otra categoría); Un cliente no recibe avisos de nuevas solicitudes; El profesional asignado recibe el aviso (y cancelar un `OPEN` no avisa a nadie).
- [ ] 4.13.2 Permiso, presentación y toque (8): `[notif]` Permiso concedido; Permiso denegado; Versión anterior a Android 13; Aviso con la app abierta; Aviso con la app en segundo plano (el de nueva solicitud puede demorar); Aviso con la app cerrada (deslizada, no detenida a la fuerza; solo aceptación y cancelación); Tocar el aviso abre la pantalla correcta (con la app cerrada y en segundo plano); Aviso duplicado se reemplaza (un reintento de aceptación y un doble toque simultáneo en "Aceptar" dejan un solo aviso visible y el segundo no vuelve a sonar). Resuelve §19 "Enlace profundo y compuerta" (después de `LOADING`, dentro del `key(userId)`, `onCreate` y `onNewIntent`, sin reaplicar en rotación).
- [ ] 4.13.3 Sesión (2): `[notif]` Cerrar la app no desuscribe; Sin avisos tras cerrar sesión (también: la bandeja del sistema queda sin los avisos anteriores de la cuenta, `cancelAll`; cerrar sesión con la red cortada y volver a abrir la app con red completa la desuscripción; editar categorías cambia qué avisos llegan; otro usuario en el mismo dispositivo no ve avisos del anterior).
- [ ] 4.13.4 Verificaciones del Worker con `curl` contra el Worker desplegado o `wrangler dev` (10): `[notif]` Token inválido o ausente; Solicitud inexistente; Aceptación llamada por quien no es el profesional asignado; Aceptación con la solicitud aún OPEN; Nueva solicitud llamada por alguien que no es el dueño; Nueva solicitud con la solicitud ya no OPEN; Cancelación con la solicitud no CANCELLED; Cancelación sin profesional asignado; Cancelación llamada por alguien que no es el cliente dueño; El destino sale de Firestore, no del pedido. Notas: para obtener ID tokens de usuarios distintos: `POST https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=<WEB_API_KEY>` con `{"email","password","returnSecureToken":true}` → `idToken` (válido 1 h; la *Web API key* está en la configuración del proyecto Firebase o como `current_key` en `google-services.json`), una vez por cuenta de prueba (cliente dueño, cliente ajeno, profesional asignado, profesional ajeno). Además, repetir un pedido válido pasados 2 min debe dar 403 (vigencia; cubierto también por `vitest`). Además, un `GET /notify` debe responder 405 (cubierto por `vitest`).
- [ ] 4.13.5 Mejor esfuerzo y cuentas (3): `[notif]` Con el Worker caído, la operación funciona en el dispositivo; Sin la cuenta de servicio en el repositorio ni en el APK (`rg private_key` en el repositorio y búsqueda en el APK descomprimido); Cuentas sin tarjeta (Cloudflare gratuito, Firebase Spark).
- [ ] 4.13.6 Verificaciones §19 de Fase 4: (a) `onMessageReceived` con la app cerrada (Hilt, restauración de sesión, dos lecturas dentro del tiempo de FCM; respaldo WorkManager); (b) comportamiento de `subscribeToTopic`/`unsubscribeFromTopic` sin conexión; (c) la demora real del aviso de nueva solicitud (prioridad normal) en segundo plano, con la app cerrada y en Doze (aceptar como limitación o pasar a prioridad alta con TTL corto); (d) el consumo de lecturas de Firestore en el panel (`1 + 2N` por nueva solicitud); registrar los resultados en `state.yaml` (`open_items` → `resolved`).
- [ ] 4.13.7 Cierre: `./gradlew test`, `./gradlew build` y `npm test` en `worker/` en verde; documentar el despliegue del Worker en `README.md`; marcar Fase 4 como lista en `state.yaml`.

---

## Resumen de trazabilidad

Totales verificados contra los specs (10 capacidades): **87 requisitos / 310 escenarios / 90 `[manual]`** (220 con test automatizado: JVM, o `vitest` en `worker/` para los escenarios solo del Worker).

| Spec | Requisitos | Escenarios | Con test automatizado | `[manual]` (checklist) |
|------|-----------:|-----------:|----------------------:|-----------------------:|
| auth | 10 | 32 | 24 | 8 |
| profiles | 11 | 25 | 18 | 7 |
| repair-requests | 12 | 37 | 31 | 6 |
| professional-feed | 10 | 38 | 33 | 5 |
| job-completion | 6 | 21 | 18 | 3 |
| contact | 4 | 17 | 13 | 4 |
| location | 8 | 21 | 12 | 9 |
| media | 6 | 22 | 7 | 15 |
| navigation | 8 | 25 | 19 | 6 |
| notifications | 12 | 72 | 45 | 27 |
| **Total** | **87** | **310** | **220** | **90** |

Por fase: Fase 1 = 196 escenarios (172 test + 24 `[manual]`, checklist 1.11); Fase 2 = 29 (3 test: `targetSize`, `sampleSizeFor` y mapeo de fallas de la subida + 26 `[manual]`, checklist 2.9); Fase 3 = 13 (todos `[manual]`, checklists 3.7 y 3.8); Fase 4 = 72 (45 test, JVM o `vitest` + 27 `[manual]`, checklist 4.13). Suma: 196 + 29 + 13 + 72 = 310.

Recuento de tareas (antes → después):

| Fase | Antes | Después | Detalle |
|------|------:|--------:|---------|
| 1 | 125 | 125 | sin cambios (1.1: 12, 1.2: 4, 1.3: 20, 1.4: 22, 1.5: 16, 1.6: 5, 1.7: 4, 1.8: 12, 1.9: 10, 1.10: 13, 1.11: 7) |
| 2 | 27 | 37 | (sin tareas nuevas en la ronda de correcciones: `sampleSizeFor` se agregó a 2.3.1 y 2.3.2) 2.1: 6, 2.2: 4, 2.3: 2, 2.4: 8, 2.5: 6, 2.6: 2, 2.7: 3, 2.8: 1, 2.9: 5 |
| 3 | 9 | 9 | sin cambios |
| 4 | 0 | 66 | 4.1: 3, 4.2: 8 (vigencia del evento: +2), 4.3: 2, 4.4: 2, 4.5: 4, 4.6: 2, 4.7: 4, 4.8: 2, 4.9: 16 (persistencia de `TopicSync` y limpieza de la bandeja: +2; concurrencia, token nuevo y estado `maybe`: +2), 4.10: 4, 4.11: 2, 4.12: 10, 4.13: 7 |
| **Total** | **161** | **237** | |

### Escenarios de `media` (cambio respecto de la versión anterior)

| Escenario | Fase | Cobertura |
|-----------|------|-----------|
| Límite en la interfaz; Quitar una foto; Agregar foto de ejemplo; Las fotos llegan al detalle | 1 | 1.8.3, 1.8.5 (tests de ViewModel) |
| Imagen que no carga; Descripción accesible | 1 | 1.11.2 (`[manual]`) |
| Cálculo del tamaño de compresión; Cálculo del submuestreo de decodificación | 2 | 2.3.1, 2.3.2 (tests JVM) |
| Mapeo de fallas de la subida | 2 | 2.4.5 (test JVM, tiempo virtual) |
| Subida exitosa; Firestore guarda solo las URLs; Calidad visual de la compresión; Falla la segunda foto; Falla la escritura de la solicitud; Preset sin firma y sin secretos en la app; Cuentas sin tarjeta | 2 | 2.9.4 (`[manual]`; el contrato del pedido y de la respuesta además tiene tests en 2.4.1 y 2.4.3) |
| Selección múltiple; 2 fotos ya cargadas; Cancelar la selección; Foto tomada; Captura cancelada; Dispositivo sin cámara | 3 | 3.8 (`[manual]`) |

Se eliminó el escenario "Usuario sin permiso" (ya no existe `storage.rules`).

### Escenarios de `notifications` (72)

| Requisito | Escenarios | Cobertura |
|-----------|------------|-----------|
| Permiso de notificaciones | Permiso concedido; Permiso denegado; Versión anterior a Android 13 (3) | 4.12.9 (implementación) + 4.13.2 (`[manual]`) |
| Ciclo de vida de las suscripciones | Temas de un cliente; Temas de un profesional; Temas sin sesión (3) | 4.9.5, 4.9.6 |
| | Suscripción al iniciar sesión; Edición de categorías actualiza los temas; Edición que no cambia categorías; Cerrar sesión desuscribe todo; Falla de suscripción no bloquea la operación; Operación fallida se reintenta en la siguiente emisión de sesión (6) | 4.9.5–4.9.8 (`topicChanges` + `TopicSync`) |
| | Desuscripción fallida al cerrar sesión se repara al arrancar; Cerrar sesión retira los avisos de la bandeja (2) | 4.9.9, 4.9.10 (`TopicSync` con `TopicStore` y `NoticeCanceller`) |
| | Token nuevo con la sesión en determinación; Cerrar sesión con operaciones pendientes cancela la bandeja de inmediato; Suscripción con tiempo agotado se desuscribe al cerrar sesión (3) | 4.9.11, 4.9.12 (`TopicSync`: concurrencia, token nuevo y estado `maybe`, `tokenDirty` y tiempo agotado sin abortar la emisión) |
| | Cerrar la app no desuscribe; Sin avisos tras cerrar sesión (2) | 4.13.3 (`[manual]`) |
| Aviso de solicitud aceptada | Aceptar solicita el envío una vez; Aceptación rechazada no envía aviso; Aceptación repetida no duplica el aviso (3) | 4.10.1, 4.10.2 |
| | El cliente recibe el aviso al aceptar (1) | 4.13.1 (`[manual]`) |
| | Aviso duplicado se reemplaza (1) | 4.13.2 (`[manual]`) |
| Aviso de nueva solicitud | Crear solicita el envío una vez; Creación fallida no envía aviso (2) | 4.10.1, 4.10.2 |
| | Prioridad y vigencia por tipo de evento (1) | 4.5.3, 4.5.4 (`vitest`) |
| | Solo los profesionales de la categoría reciben el aviso; Un cliente no recibe avisos de nuevas solicitudes (2) | 4.13.1 (`[manual]`) |
| Aviso de cancelación de un trabajo aceptado | Cancelar un ACCEPTED solicita el envío; Cancelar un OPEN no envía aviso; Cancelación rechazada no envía aviso (3) | 4.10.1, 4.10.2 |
| | El profesional asignado recibe el aviso (1) | 4.13.1 (`[manual]`) |
| Verificaciones del Worker | Los 10 escenarios `[manual]` (token inválido, solicitud inexistente, aceptación ajena/OPEN, nueva ajena/no OPEN, cancelación no CANCELLED/sin profesional/ajena, destino desde Firestore) | 4.13.4 (`[manual]`); además tests `vitest` en 4.2.3, 4.2.5, 4.2.7, 4.3.1, 4.4.1 y 4.6.1 |
| | Evento fuera de la ventana de vigencia (1) | 4.2.5, 4.2.6 (`vitest`), 4.6.1 |
| Privacidad del contenido | Contenido del mensaje de nueva solicitud; Contenido de los mensajes de aceptación y de cancelación (2) | 4.2.3, 4.2.4 (`vitest`) |
| | El aviso visible no filtra datos personales (1) | 4.12.5, 4.12.6 |
| Filtro de distancia en el dispositivo | Solicitud dentro del radio; fuera del radio; exactamente en el límite; Categoría ya no vigente; Solicitud que ya no está OPEN; Sin sesión de profesional (6) | 4.9.1, 4.9.2 (`shouldNotify`, `mayMatchCategory`); "Categoría ya no vigente" también en 4.9.3, 4.9.4 (sin lecturas) |
| | Falla la lectura de la solicitud (1) | 4.9.3, 4.9.4 |
| Avisos visibles en primer plano | Aviso con la app abierta / en segundo plano / cerrada (3) | 4.13.2 (`[manual]`) |
| Navegación al tocar el aviso | Aceptación abre el detalle del cliente; Nueva solicitud abre el detalle del profesional; Cancelación abre el detalle del profesional; Rol que no corresponde; Sin sesión (5) | 4.12.1–4.12.4 |
| | Tocar el aviso abre la pantalla correcta (1) | 4.13.2 (`[manual]`) |
| Avisos de mejor esfuerzo | Falla del Worker al crear / al aceptar / al cancelar; Tiempo de espera del Worker; Lectura previa lenta no bloquea la aceptación; Sin Worker configurado (6) | 4.10.3, 4.10.4 |
| | Con el Worker caído, la operación funciona en el dispositivo (1) | 4.13.5 (`[manual]`) |
| Sin secretos ni tarjeta en los avisos | Sin la cuenta de servicio en el repositorio ni en el APK; Cuentas sin tarjeta (2) | 4.7.2, 4.13.5 (`[manual]`) |

Cobertura débil a revisar en `sdd-verify`: "El aviso visible no filtra datos personales" se prueba de forma estructural (`noticeTextFor` solo recibe el tipo de evento), no inspeccionando la notificación publicada; la parte visual se confirma en 4.13.2. Los escenarios del Worker marcados `[manual]` conservan su ítem de checklist aunque `vitest` también cubra la lógica.

### Ítems abiertos de §19 y su tarea

| Ítem | Tarea |
|------|-------|
| `NavigationState` de la cátedra / artefactos `hiltViewModel` y nav3 | 1.2.3, 1.10.6 |
| `testFixtures` consumible por módulos Android | 1.2.1 |
| Fábricas de Dagger para casos de uso de `:core:domain` | 1.2.2, 1.4.22, 1.10.13 |
| `NavBackStack` como `MutableList<NavKey>` | 1.2.4 |
| Retención de ViewModels por pestaña | 1.10.11 |
| `key(userId)` descarta ViewModels | 1.10.12, 1.11.5 |
| Escrituras offline de Firestore | 2.8.1 |
| `okhttp-coroutines` / `executeAsync()` (artefacto, versión, cancelación del `Call`) | 2.1.6, 2.4.7, 2.9.4 |
| Opciones del *upload preset* de Cloudinary (tamaño máximo opcional; carpeta no figura en la URL) | 2.1.2, 2.9.4 |
| Regex de `validPhotos` | 2.7.3, 2.9.4 |
| CPU del Worker (10 ms), ruta fría y plan B escalonado (memoria → *Cache API* si persiste en `workers.dev` → Workers KV → aceptar) | 4.7.4 |
| Firestore REST con el ID token del usuario | 4.7.3 (respaldo en 4.4.2) |
| Operaciones de temas sin conexión | 4.9.9, 4.9.11, 4.13.3, 4.13.6 (b) |
| Entrega de `NEW_REQUEST` con prioridad normal; presupuesto de lecturas de Firestore | 4.13.6 (c), 4.13.6 (d) |
| Trabajo dentro de `onMessageReceived` con la app cerrada | 4.12.8, 4.13.6 (a) |
| Enlace profundo y compuerta | 4.12.10, 4.13.2 |
