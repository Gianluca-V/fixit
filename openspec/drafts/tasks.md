# FixIt — Tareas de implementación

Orden test-first: cada bloque "Test" se escribe y se ve fallar (rojo) antes de su "Impl".
Referencias entre corchetes apuntan a la spec correspondiente. Marcar `[x]` al completar.

---

## Fase 1 — Entrega TP2 (Fake + UI completa)

### 1.0 Andamiaje del proyecto

- [ ] 1.0.1 Crear proyecto con `settings.gradle.kts` incluyendo `:app`, `:core:domain`, `:core:data`, `:core:ui`, `:features:auth`, `:features:client`, `:features:professional`.
- [ ] 1.0.2 `gradle/libs.versions.toml` con Kotlin 2.4.20, AGP 9.2.1, Compose BOM 2026.09.00, lifecycle 2.11.0, navigation3 1.2.0, coroutines 1.11.0, serialization 1.11.0, Coil 3.6.3; Hilt y hilt-navigation-compose en su última estable.
- [ ] 1.0.3 `:core:domain` como módulo `kotlin("jvm")` (sin Android); deps: coroutines-core, javax.inject; test: junit4, coroutines-test.
- [ ] 1.0.4 Configurar `compileSdk/targetSdk 37`, `minSdk 26` en módulos Android; Hilt + KSP en `:app`, `:core:data` y features.
- [ ] 1.0.5 `FixItApplication` con `@HiltAndroidApp`; `MainActivity` `@AndroidEntryPoint` con `setContent { FixItTheme { FixItNavHost() } }`.
- [ ] 1.0.6 Verificar regla de dependencias: ninguna feature depende de `:core:data` (revisar `build.gradle.kts`).

### 1.1 `:core:domain` — Fundamentos

- [ ] 1.1.1 Test: `AppResult` `map`/`flatMap`/`onSuccess`/`onError`.
- [ ] 1.1.2 Impl: `AppResult`, `DomainError` (jerarquía completa de `design.md §1.1`), `Limits`.
- [ ] 1.1.3 Impl: modelos (`User`, `ProfessionalInfo`, `RepairRequest`, `Review`, `GeoPoint`, `Location`, `FeedItem`, `Contact`, `SessionState`, enums).
- [ ] 1.1.4 Test: `ProfessionalInfo.ratingAverage` (0 reseñas → 0.0; 13/3 → 4.333) [profiles].
- [ ] 1.1.5 Impl: `Clock` + `FixedClock` (test fixture), `IdGenerator`.

### 1.2 `:core:domain` — Servicios puros

- [ ] 1.2.1 Test: `HaversineDistance` (mismo punto, Varela–Quilmes ≈ 9.8 km, simetría, antimeridiano) [location].
- [ ] 1.2.2 Impl: `HaversineDistance`.
- [ ] 1.2.3 Test: `GeoPoint` con coordenadas inválidas → `InvalidCoordinates` [location].
- [ ] 1.2.4 Impl: validación de `GeoPoint`.
- [ ] 1.2.5 Test parametrizado: `RequestStateMachine` 12 combinaciones estado × acción [repair-requests].
- [ ] 1.2.6 Impl: `RequestStateMachine`.
- [ ] 1.2.7 Test: `RequestMatcher` (coincide, categoría distinta, fuera de radio, no abierta, límite exacto) [professional-feed].
- [ ] 1.2.8 Impl: `RequestMatcher`.
- [ ] 1.2.9 Test: `PhoneNumberNormalizer` (local AR, internacional, ya normalizado) [contact].
- [ ] 1.2.10 Impl: `PhoneNumberNormalizer`.
- [ ] 1.2.11 Test: `Validators` (email, teléfono, nombre).
- [ ] 1.2.12 Impl: `Validators`.

### 1.3 `:core:domain` — Interfaces y fakes de test

- [ ] 1.3.1 Impl: interfaces `AuthRepository`, `UserRepository`, `RepairRequestRepository`, `ReviewRepository`, `LocationProvider`, `AddressResolver`.
- [ ] 1.3.2 Impl (test fixtures): fakes simples escritos a mano para tests de casos de uso (`TestAuthRepository`, `TestRepairRequestRepository` con registro de llamadas, etc.).

### 1.4 `:core:domain` — Casos de uso de autenticación [auth]

- [ ] 1.4.1 Test: `SignUpClientUseCase` (éxito, email en uso, cada validación, no llama repo si inválido).
- [ ] 1.4.2 Impl: `SignUpClientUseCase`.
- [ ] 1.4.3 Test: `SignUpProfessionalUseCase` (éxito con perfil inicial 0/0, sin categorías, radio 0 y 51, sin ubicación base).
- [ ] 1.4.4 Impl: `SignUpProfessionalUseCase`.
- [ ] 1.4.5 Test: `SignInUseCase` (éxito, credenciales inválidas, email vacío).
- [ ] 1.4.6 Impl: `SignInUseCase`, `SignOutUseCase`, `ObserveSessionUseCase`, `ObserveCurrentUserUseCase`.

### 1.5 `:core:domain` — Casos de uso de solicitudes [repair-requests]

- [ ] 1.5.1 Test: `CreateRepairRequestUseCase` (éxito con `OPEN`/`clientId`/`createdAt`; rol profesional → `Unauthorized`; 7 validaciones en orden; límites 10 y 500 válidos; sexta solicitud abierta permitida).
- [ ] 1.5.2 Impl: `CreateRepairRequestUseCase`.
- [ ] 1.5.3 Test: `ObserveMyRequestsUseCase` (orden desc, solo propias, vacío, filtro).
- [ ] 1.5.4 Impl: `ObserveMyRequestsUseCase`.
- [ ] 1.5.5 Test: `ObserveRequestDetailUseCase` (no encontrada, ajena → `Unauthorized`, `OPEN` sin contraparte, `ACCEPTED` con contraparte y reputación, `COMPLETED` con reseña, profesional ve `OPEN` sin datos del cliente).
- [ ] 1.5.6 Impl: `ObserveRequestDetailUseCase`.
- [ ] 1.5.7 Test: `CancelRepairRequestUseCase` (éxito, aceptada → `InvalidTransition`, ajena → `Unauthorized`).
- [ ] 1.5.8 Impl: `CancelRepairRequestUseCase`.

### 1.6 `:core:domain` — Feed y aceptación [professional-feed]

- [ ] 1.6.1 Test: `ObserveFeedUseCase` (filtra por matcher, calcula distancia con 1 decimal, orden desc, emite al cambiar fuente, propaga `Network`).
- [ ] 1.6.2 Impl: `ObserveFeedUseCase`.
- [ ] 1.6.3 Test: `AcceptRepairRequestUseCase` (éxito, segundo → `AlreadyTaken`, cancelada → `InvalidTransition`, categoría no habilitada → `Unauthorized`, cliente → `Unauthorized`, varios aceptados sin límite).
- [ ] 1.6.4 Impl: `AcceptRepairRequestUseCase`.
- [ ] 1.6.5 Test: `ObserveMyJobsUseCase` (solo asignadas, `ACCEPTED` primero).
- [ ] 1.6.6 Impl: `ObserveMyJobsUseCase`.

### 1.7 `:core:domain` — Finalización, contacto y ubicación

- [ ] 1.7.1 Test: `CompleteRequestWithReviewUseCase` (éxito y agregado 8/2 → 13/3, comentario blank → null, rating 0/6, comentario 301, `OPEN`/`COMPLETED` → `InvalidTransition`, no dueño → `Unauthorized`) [job-completion].
- [ ] 1.7.2 Impl: `CompleteRequestWithReviewUseCase`.
- [ ] 1.7.3 Test: `GetCounterpartContactUseCase` (cliente↔profesional, `OPEN` → `Unauthorized`, tercero → `Unauthorized`) [contact].
- [ ] 1.7.4 Impl: `GetCounterpartContactUseCase`.
- [ ] 1.7.5 Test: `GetCurrentLocationUseCase` (éxito, resolver falla → dirección vacía, provider falla → `LocationUnavailable`) [location].
- [ ] 1.7.6 Impl: `GetCurrentLocationUseCase`.

### 1.8 `:core:data` — Implementaciones Fake

- [ ] 1.8.1 Impl: `InMemoryStore` con `MutableStateFlow` + `Mutex`; `SeedData.default()` (tabla de `design.md §7`).
- [ ] 1.8.2 Test de contrato `RepairRequestRepositoryContractTest` (abstracto, reutilizable en Fase 2): create/observe/accept/cancel/completeWithReview.
- [ ] 1.8.3 Test: concurrencia — 10 `async` aceptando la misma solicitud → 1 éxito, 9 `AlreadyTaken` [professional-feed].
- [ ] 1.8.4 Test: carrera aceptar → cancelar → `InvalidTransition(ACCEPTED, CANCEL)`.
- [ ] 1.8.5 Test: reseña duplicada → `ReviewAlreadyExists`; fallo intermedio no deja cambios parciales.
- [ ] 1.8.6 Impl: `FakeRepairRequestRepository` (usa `RequestStateMachine` dentro del `Mutex`).
- [ ] 1.8.7 Test + Impl: `FakeAuthRepository` (email duplicado, credenciales inválidas, sesión en memoria, rol persistido tras re-login).
- [ ] 1.8.8 Impl: `FakeUserRepository`, `FakeReviewRepository`, `FakeLocationProvider`, `FakeAddressResolver`, `FakePhotoUploader`, `SystemClock`, `UuidIdGenerator`.
- [ ] 1.8.9 Impl: `RepositoryModule`, `LocationModule`, `CoreModule` (Hilt `@Binds` a Fakes).

### 1.9 `:core:ui`

- [ ] 1.9.1 Impl: `FixItTheme` (Material 3, colores dinámicos opcionales, tipografía).
- [ ] 1.9.2 Impl: `strings.xml` en español: etiquetas de categorías, estados, mensajes de todos los `DomainError`.
- [ ] 1.9.3 Impl: `DomainError.asMessage()` (`when` exhaustivo) y `ServiceCategory.labelRes()`, `RequestStatus.labelRes()`.
- [ ] 1.9.4 Impl: `LoadingContent`, `ErrorContent(error, onRetry)`, `EmptyContent`, `RatingStars` (lectura e interactivo), `CategoryChip`, `StatusBadge`, `ReputationSummary`, `PhotoThumbnails`, `PhotoPager`, `ContactCard`; cada uno con `@Preview`.

### 1.10 `:features:auth` [auth]

- [ ] 1.10.1 Test: `SignInViewModel` (cambio de campos, `isSubmitting`, error de validación, `InvalidCredentials` en `userMessage`, `onUserMessageShown` limpia).
- [ ] 1.10.2 Impl: `SignInUiState`, `SignInViewModel`, `SignInScreen` (+ previews), `SignInRoute(onSignUpClick)`.
- [ ] 1.10.3 Test: `SignUpViewModel` (selección de rol muestra campos de profesional, toggle de categorías, usar ubicación, radio, envío exitoso, errores).
- [ ] 1.10.4 Impl: `SignUpUiState`, `SignUpViewModel`, `SignUpScreen` (+ previews cliente/profesional), `SignUpRoute(onBack)`.

### 1.11 `:features:client`

- [ ] 1.11.1 Test: `MyRequestsViewModel` (Loading → Success ordenado, vacío, filtro, Error + retry) [repair-requests].
- [ ] 1.11.2 Impl: `MyRequests*` (Route con `onCreateClick`, `onRequestClick`).
- [ ] 1.11.3 Test: `CreateRequestViewModel` (selección única de categoría, contador, agregar/quitar fotos y `canAddPhoto`, usar ubicación ok/falla, editar dirección, error de validación conserva datos, éxito expone `createdRequestId`) [repair-requests, media, location].
- [ ] 1.11.4 Impl: `CreateRequest*` (fotos mock en Fase 1; Route con `onRequestCreated`, `onBack`).
- [ ] 1.11.5 Test: `ClientRequestDetailViewModel` (estados `OPEN`/`ACCEPTED`/`COMPLETED`/`CANCELLED`, actualización en vivo, diálogo de cancelación, cancelación ok y `InvalidTransition`) [repair-requests, contact].
- [ ] 1.11.6 Impl: `ClientRequestDetail*` (Route con `onCompleteClick`, `onCallClick`, `onWhatsAppClick`, `onBack`).
- [ ] 1.11.7 Test: `CompleteRequestViewModel` (`canSubmit` inicial false, selección de estrellas, contador, error de red conserva datos, éxito marca `completed`) [job-completion].
- [ ] 1.11.8 Impl: `CompleteRequest*` (Route con `onCompleted`, `onBack`).
- [ ] 1.11.9 Test + Impl: `ClientProfile*` (Route con `onSignOutClick`) [profiles].

### 1.12 `:features:professional`

- [ ] 1.12.1 Test: `FeedViewModel` (Loading → Success, aparece nueva, desaparece aceptada por otro, vacío, error + retry) [professional-feed].
- [ ] 1.12.2 Impl: `Feed*` (tarjeta con categoría, extracto, miniatura, distancia; Route con `onRequestClick`).
- [ ] 1.12.3 Test: `ProRequestDetailViewModel` (diálogo de confirmación, `isAccepting`, éxito → `acceptedRequestId`, `AlreadyTaken` → `userMessage` + volver, vista de trabajo `ACCEPTED` con contacto, `COMPLETED` con reseña).
- [ ] 1.12.4 Impl: `ProRequestDetail*` (Route con `onAccepted`, `onBack`, `onCallClick`, `onWhatsAppClick`).
- [ ] 1.12.5 Test + Impl: `MyJobs*` (orden, estados) [professional-feed].
- [ ] 1.12.6 Test + Impl: `ProfessionalProfile*` (categorías, radio, reputación "4.3"/"Sin reseñas", cerrar sesión) [profiles].

### 1.13 `:app` — Navegación y contacto [navigation, contact]

- [ ] 1.13.1 Test: `TopLevelBackStack` (`add`, `removeLast`, `replaceTop`, `switchTab` conserva pilas, `resetTab`, atrás en pestaña no inicial vuelve a la inicial).
- [ ] 1.13.2 Impl: `TopLevelBackStack`, claves `@Serializable` `NavKey`, enums `ClientTab`/`ProTab`.
- [ ] 1.13.3 Test: `AuthGateViewModel` (Loading, SignedOut, SignedIn CLIENT/PROFESSIONAL).
- [ ] 1.13.4 Impl: `FixItNavHost` + `AuthGate` + grafo Auth (`NavDisplay`, decoradores).
- [ ] 1.13.5 Impl: `ClientShell` y `ProfessionalShell` (`NavigationBar`, `NavDisplay` por pestaña, `entryProvider`, cableado de callbacks según tabla de `specs/navigation`).
- [ ] 1.13.6 Impl: `ContactLauncher` (`ACTION_DIAL`, `wa.me`, manejo de `ActivityNotFoundException`) y `<queries>` en el manifest.
- [ ] 1.13.7 Verificación manual: recorrer flujo completo con cuentas seed (cliente crea → profesional ve y acepta → contacto → cliente completa y califica → reputación actualizada); rotación conserva formulario.

### 1.14 Cierre de Fase 1

- [ ] 1.14.1 Revisar que cada escenario `[Fase 1]` de `specs/` tenga al menos un test.
- [ ] 1.14.2 Ejecutar `./gradlew test` y lint sin errores.
- [ ] 1.14.3 Capturas de pantalla y README de entrega (si la cátedra lo requiere).

---

## Fase 2 — Firebase

### 2.0 Configuración

- [ ] 2.0.1 Crear proyecto Firebase, registrar app, agregar `google-services.json` (fuera del control de versiones si el repo es público).
- [ ] 2.0.2 Agregar Firebase BoM (Auth, Firestore, Storage) y `geofire-android-common` a `:core:data`; plugin `google-services` en `:app`.
- [ ] 2.0.3 Configurar Firebase Emulator Suite para pruebas locales.

### 2.1 DTOs y mappers

- [ ] 2.1.1 Test: `UserMapper`, `RequestMapper` (incluye `geohash` solo en DTO), `ReviewMapper` ida y vuelta.
- [ ] 2.1.2 Impl: DTOs y mappers.
- [ ] 2.1.3 Test + Impl: `FirebaseErrorMapper` (`UserCollision` → `EmailAlreadyInUse`, `InvalidCredentials`, `Network`, `PERMISSION_DENIED` → `Unauthorized`, `UNAVAILABLE` → `Network`, `NOT_FOUND`).

### 2.2 Repositorios Firebase

- [ ] 2.2.1 Impl: `FirebaseAuthRepository` (signUp crea `users/{uid}`; sesión persistente vía `AuthStateListener` → `Flow`).
- [ ] 2.2.2 Impl: `FirebaseUserRepository` (snapshot listener).
- [ ] 2.2.3 Impl: `FirebaseStoragePhotoUploader` (compresión, rutas, limpieza ante fallo) [media].
- [ ] 2.2.4 Impl: `FirebaseRepairRequestRepository.create` (id previo, subida, geohash, escritura).
- [ ] 2.2.5 Impl: `observeOpenCandidates` con `getGeoHashQueryBounds` + `combine` + dedupe + `awaitClose` [professional-feed].
- [ ] 2.2.6 Impl: `accept`, `cancel` con `runTransaction` + `RequestStateMachine`.
- [ ] 2.2.7 Impl: `completeWithReview` con una transacción (solicitud + reseña + `FieldValue.increment`) [job-completion].
- [ ] 2.2.8 Impl: `FirebaseReviewRepository`.
- [ ] 2.2.9 Ejecutar `RepairRequestRepositoryContractTest` contra Firebase en el emulador (test instrumentado).

### 2.3 Seguridad, índices y wiring

- [ ] 2.3.1 Escribir `firestore.rules`, `storage.rules`, `firestore.indexes.json` según `design.md §8`.
- [ ] 2.3.2 Tests de reglas con el emulador (aceptar dos veces, cancelar ajena, reseña duplicada, cambiar rol).
- [ ] 2.3.3 `FirebaseModule` + `FirebaseRepositoryModule`; desactivar bindings Fake (flag o eliminación del módulo). Verificar que `:core:domain` y `:features:*` no cambian (diff vacío).
- [ ] 2.3.4 Verificación manual con dos dispositivos: feed en vivo, carrera de aceptación, reputación en vivo.

---

## Fase 3 — Hardware (Clase 5)

### 3.1 Ubicación [location]

- [ ] 3.1.1 Declarar `ACCESS_COARSE_LOCATION` y `ACCESS_FINE_LOCATION`; agregar `play-services-location`.
- [ ] 3.1.2 Impl: `FusedLocationProvider` (`getCurrentLocation`, timeout 10 s → `LocationUnavailable`).
- [ ] 3.1.3 Impl: `GeocoderAddressResolver` (API 33+ asíncrona, previa en `@IoDispatcher`).
- [ ] 3.1.4 Impl: UI de permisos en `CreateRequestRoute` y `SignUpRoute` (rationale, denegado, denegado permanente → ajustes).
- [ ] 3.1.5 Cambiar `LocationModule` a implementaciones reales.

### 3.2 Fotos [media]

- [ ] 3.2.1 Configurar `FileProvider` (`res/xml/file_paths.xml`, directorio de caché).
- [ ] 3.2.2 Impl: acción "Cámara" con `TakePicture` (descartar archivo si `false`; ocultar si no hay cámara).
- [ ] 3.2.3 Impl: acción "Galería" con `PickMultipleVisualMedia(3 - actuales)` / `PickVisualMedia`.
- [ ] 3.2.4 Verificación manual en dispositivo real: permisos, GPS desactivado, captura cancelada, picker cancelado.
