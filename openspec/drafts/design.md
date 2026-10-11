# FixIt — Diseño técnico

Este documento concreta los contratos que implementan las especificaciones de `specs/`. Los
fragmentos de Kotlin son contratos de referencia, no código final.

## 1. Modelo de dominio (`:core:domain/model`)

```kotlin
enum class UserRole { CLIENT, PROFESSIONAL }

enum class ServiceCategory { ELECTRICITY, PLUMBING, GAS, CARPENTRY, PAINTING, LOCKSMITH, MASONRY, OTHER }

enum class RequestStatus { OPEN, ACCEPTED, COMPLETED, CANCELLED }

enum class RequestAction { ACCEPT, CANCEL, COMPLETE }

data class GeoPoint(val latitude: Double, val longitude: Double)

data class Location(val point: GeoPoint, val address: String)

data class ProfessionalInfo(
    val categories: Set<ServiceCategory>,
    val baseLocation: Location,
    val radiusKm: Int,
    val ratingSum: Int = 0,
    val reviewCount: Int = 0,
) {
    val ratingAverage: Double get() = if (reviewCount == 0) 0.0 else ratingSum.toDouble() / reviewCount
}

data class User(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val role: UserRole,
    val professional: ProfessionalInfo? = null, // non-null iff role == PROFESSIONAL
)

data class RepairRequest(
    val id: String,
    val clientId: String,
    val category: ServiceCategory,
    val description: String,
    val photoUrls: List<String>,
    val location: Location,
    val createdAt: Long,
    val status: RequestStatus,
    val professionalId: String? = null,
    val acceptedAt: Long? = null,
    val completedAt: Long? = null,
    val cancelledAt: Long? = null,
)

data class PhotoRef(val uri: String)

data class NewRepairRequest(            // input of CreateRepairRequestUseCase
    val category: ServiceCategory?,
    val description: String,
    val photos: List<PhotoRef>,
    val location: Location?,
)

data class Review(
    val requestId: String,              // also the review id (1 review per request)
    val professionalId: String,
    val clientId: String,
    val rating: Int,                    // 1..5
    val comment: String?,               // <= 300 chars, null if blank
    val createdAt: Long,
)

data class FeedItem(                    // no client personal data
    val requestId: String,
    val category: ServiceCategory,
    val descriptionPreview: String,
    val thumbnailUrl: String?,
    val distanceKm: Double,
    val createdAt: Long,
)

data class Contact(val name: String, val phone: String, val waDigits: String)

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: User) : SessionState
}
```

### 1.1 Errores (`:core:domain/error`)

```kotlin
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Error(val error: DomainError) : AppResult<Nothing>
}

sealed interface DomainError {
    data object Network : DomainError
    data object NotFound : DomainError
    data object Unauthorized : DomainError
    data object InvalidCredentials : DomainError
    data object EmailAlreadyInUse : DomainError
    data object AlreadyTaken : DomainError
    data object ReviewAlreadyExists : DomainError
    data object LocationUnavailable : DomainError
    data class InvalidTransition(val from: RequestStatus, val action: RequestAction) : DomainError
    data class Unknown(val message: String?) : DomainError

    sealed interface Validation : DomainError {
        // auth / profile
        data object InvalidEmail : Validation
        data object WeakPassword : Validation
        data object InvalidName : Validation
        data object InvalidPhone : Validation
        data object RoleRequired : Validation
        data object CategoriesRequired : Validation
        data object BaseLocationRequired : Validation
        data object InvalidRadius : Validation
        data object InvalidCoordinates : Validation
        // request
        data object CategoryRequired : Validation
        data object EmptyDescription : Validation
        data object DescriptionTooShort : Validation
        data object DescriptionTooLong : Validation
        data object PhotosRequired : Validation
        data object TooManyPhotos : Validation
        data object LocationRequired : Validation
        // review
        data object InvalidRating : Validation
        data object CommentTooLong : Validation
    }
}
```

Helpers: `inline fun <T, R> AppResult<T>.map(...)`, `flatMap`, `onSuccess`, `onError`.
En `:core:ui`: `@Composable fun DomainError.asMessage(): String` (un `when` exhaustivo sobre
`stringResource`) y `fun ServiceCategory.labelRes(): Int`.

### 1.2 Límites de validación (`:core:domain/model/Limits.kt`)

```kotlin
object Limits {
    const val NAME_MIN = 2;          const val NAME_MAX = 60
    const val PASSWORD_MIN = 6
    const val PHONE_DIGITS_MIN = 8;  const val PHONE_DIGITS_MAX = 15
    const val RADIUS_MIN_KM = 1;     const val RADIUS_MAX_KM = 50
    const val DESCRIPTION_MIN = 10;  const val DESCRIPTION_MAX = 500
    const val PHOTOS_MIN = 1;        const val PHOTOS_MAX = 3
    const val RATING_MIN = 1;        const val RATING_MAX = 5
    const val COMMENT_MAX = 300
    const val EARTH_RADIUS_KM = 6371.0088
}
```

### 1.3 Servicios puros de dominio

| Clase | Firma | Notas |
|---|---|---|
| `RequestStateMachine` | `fun transition(from: RequestStatus, action: RequestAction): AppResult<RequestStatus>` | Tabla de `specs/repair-requests`. `object`. |
| `HaversineDistance` | `fun km(a: GeoPoint, b: GeoPoint): Double` | `object`. |
| `RequestMatcher` | `fun matches(pro: ProfessionalInfo, r: RepairRequest): Boolean` | `OPEN` ∧ categoría ∧ distancia ≤ radio. |
| `PhoneNumberNormalizer` | `fun digitsOnly(raw: String): String`, `fun toWhatsApp(raw: String): String` | Reglas de `specs/contact`. |
| `Validators` | `isValidEmail`, `isValidPhone`, etc. | Usados por casos de uso. |
| `Clock` | `interface Clock { fun now(): Long }` | `SystemClock` en data; `FixedClock` en tests. |

## 2. Interfaces de repositorio (`:core:domain/repository`)

```kotlin
interface AuthRepository {
    fun observeSession(): Flow<SessionState>
    suspend fun currentUser(): User?
    suspend fun signUp(email: String, password: String, profile: NewUserProfile): AppResult<User>
    suspend fun signIn(email: String, password: String): AppResult<User>
    suspend fun signOut()
}

data class NewUserProfile(
    val name: String, val phone: String, val role: UserRole,
    val professional: ProfessionalInfo? = null,
)

interface UserRepository {
    fun observeUser(userId: String): Flow<AppResult<User>>
    suspend fun getUser(userId: String): AppResult<User>
}

interface RepairRequestRepository {
    suspend fun create(clientId: String, request: NewRepairRequest): AppResult<RepairRequest>
    fun observeById(requestId: String): Flow<AppResult<RepairRequest>>
    fun observeByClient(clientId: String): Flow<AppResult<List<RepairRequest>>>
    fun observeByProfessional(professionalId: String): Flow<AppResult<List<RepairRequest>>>
    /** Candidates: OPEN + category in [categories], roughly near [center]. Exact distance filter is done in domain. */
    fun observeOpenCandidates(center: GeoPoint, radiusKm: Int, categories: Set<ServiceCategory>): Flow<AppResult<List<RepairRequest>>>
    /** Atomic: validates with RequestStateMachine. Second accept -> AlreadyTaken. */
    suspend fun accept(requestId: String, professionalId: String): AppResult<RepairRequest>
    /** Atomic: only owner, only OPEN. */
    suspend fun cancel(requestId: String, clientId: String): AppResult<RepairRequest>
    /** Atomic: ACCEPTED -> COMPLETED + review + rating aggregate. */
    suspend fun completeWithReview(requestId: String, clientId: String, rating: Int, comment: String?): AppResult<Review>
}

interface ReviewRepository {
    fun observeByRequest(requestId: String): Flow<AppResult<Review?>>
}

interface LocationProvider { suspend fun currentLocation(): AppResult<GeoPoint> }
interface AddressResolver { suspend fun resolve(point: GeoPoint): AppResult<String> }
```

> `completeWithReview` vive en `RepairRequestRepository` porque requiere atomicidad entre tres
> documentos; separarlo en repositorios distintos rompería la transacción.

## 3. Catálogo de casos de uso (`:core:domain/usecase`)

Todos exponen `operator fun invoke`. Los que requieren usuario lo obtienen de
`AuthRepository.currentUser()`; si es `null` devuelven `Unauthorized`.

| Caso de uso | Entrada | Salida | Reglas |
|---|---|---|---|
| `ObserveSessionUseCase` | — | `Flow<SessionState>` | — |
| `SignInUseCase` | `email, password` | `AppResult<User>` | Email válido, contraseña no vacía. |
| `SignUpClientUseCase` | `email, password, name, phone` | `AppResult<User>` | Tabla de `specs/auth`. |
| `SignUpProfessionalUseCase` | `email, password, name, phone, categories, baseLocation?, radiusKm` | `AppResult<User>` | Además: categorías ≥1, ubicación, radio 1..50. |
| `SignOutUseCase` | — | `Unit` | — |
| `ObserveCurrentUserUseCase` | — | `Flow<AppResult<User>>` | Sesión → `observeUser`. |
| `GetCurrentLocationUseCase` | — | `AppResult<Location>` | Dirección vacía si el resolver falla. |
| `CreateRepairRequestUseCase` | `NewRepairRequest` | `AppResult<RepairRequest>` | Rol `CLIENT`; validación en orden de spec. |
| `ObserveMyRequestsUseCase` | `filter: RequestStatus?` | `Flow<AppResult<List<RepairRequest>>>` | Orden `createdAt` desc. |
| `ObserveRequestDetailUseCase` | `requestId` | `Flow<AppResult<RequestDetail>>` | Combina solicitud + contraparte + reseña; aplica privacidad según rol y estado. |
| `CancelRepairRequestUseCase` | `requestId` | `AppResult<RepairRequest>` | Delegado atómico al repo. |
| `ObserveFeedUseCase` | — | `Flow<AppResult<List<FeedItem>>>` | Perfil profesional → candidatos → `RequestMatcher` → `FeedItem` con distancia; orden `createdAt` desc. |
| `AcceptRepairRequestUseCase` | `requestId` | `AppResult<RepairRequest>` | Rol `PROFESSIONAL`, categoría incluida; atómico en repo. |
| `ObserveMyJobsUseCase` | — | `Flow<AppResult<List<RepairRequest>>>` | `ACCEPTED` primero, luego `acceptedAt` desc. |
| `CompleteRequestWithReviewUseCase` | `requestId, rating, comment` | `AppResult<Review>` | Rating 1..5, comentario ≤300 (blank→null), dueño, atómico. |
| `GetCounterpartContactUseCase` | `requestId` | `AppResult<Contact>` | Estado `ACCEPTED`/`COMPLETED` y participante. |

```kotlin
data class RequestDetail(
    val request: RepairRequest,
    val viewerRole: UserRole,
    val counterpart: CounterpartSummary?, // null when OPEN/CANCELLED
    val review: Review?,
    val distanceKm: Double?,              // only for professional viewer
)
data class CounterpartSummary(val name: String, val contact: Contact, val ratingAverage: Double?, val reviewCount: Int?)
```

## 4. Estado de UI por pantalla

Patrón base: `sealed interface XxxUiState { data object Loading; data class Success(...); data class Error(val error: DomainError) }`.
Los formularios usan un único `data class` de estado (siempre "Success" editable) con
`isSubmitting` y `userMessage`.

| Pantalla | Estado | Campos relevantes | Eventos |
|---|---|---|---|
| `SignIn` | `SignInUiState` (data class) | `email, password, isSubmitting, fieldError: Validation?, userMessage: DomainError?` | `onEmailChange, onPasswordChange, onSubmit, onUserMessageShown` |
| `SignUp` | `SignUpUiState` (data class) | `role: UserRole?, email, password, name, phone, categories: Set, baseLocation: Location?, radiusKm: Int, isLocating, isSubmitting, fieldError, userMessage` | `onRoleSelect, onXxxChange, onCategoryToggle, onUseMyLocationClick, onRadiusChange, onSubmit, onUserMessageShown` |
| `MyRequests` | sealed | `Success(requests, filter: RequestStatus?)` | `onFilterSelect, onRetry` |
| `CreateRequest` | `CreateRequestUiState` (data class) | `category, description, photos: List<PhotoRef>, location, isLocating, isSubmitting, fieldError, userMessage`; derivados `descriptionCount`, `canAddPhoto` | `onCategorySelect, onDescriptionChange, onAddPhoto(ref), onRemovePhoto(index), onUseMyLocationClick, onAddressChange, onSubmit, onUserMessageShown` |
| `ClientRequestDetail` | sealed | `Success(detail: RequestDetail, isCancelling, showCancelDialog, userMessage)` | `onCancelClick, onCancelConfirm, onCancelDismiss, onUserMessageShown` |
| `CompleteRequest` | sealed | `Success(professionalName, rating: Int, comment, isSubmitting, userMessage)`; `canSubmit = rating in 1..5 && !isSubmitting` | `onRatingChange, onCommentChange, onSubmit, onUserMessageShown` |
| `ClientProfile` | sealed | `Success(user)` | `onSignOutClick` |
| `Feed` | sealed | `Success(items: List<FeedItem>)` | `onRetry` |
| `ProRequestDetail` | sealed | `Success(detail, isAccepting, showAcceptDialog, userMessage)` | `onAcceptClick, onAcceptConfirm, onAcceptDismiss, onUserMessageShown` |
| `MyJobs` | sealed | `Success(jobs)` | `onRetry` |
| `ProfessionalProfile` | sealed | `Success(user)` (reputación vía `user.professional`) | `onSignOutClick` |

Eventos de navegación (éxito al crear, aceptar, completar) se modelan como campo de estado
de un solo uso (`createdRequestId: String?`, `acceptedRequestId`, `completed: Boolean`) consumido en el
`Route` con `LaunchedEffect`, que llama al callback y luego a `onNavigationHandled()`.

## 5. Navegación (`:app/navigation`)

```kotlin
@Serializable data object SignInKey : NavKey
@Serializable data object SignUpKey : NavKey
@Serializable data object MyRequestsKey : NavKey
@Serializable data object CreateRequestKey : NavKey
@Serializable data class ClientRequestDetailKey(val requestId: String) : NavKey
@Serializable data class CompleteRequestKey(val requestId: String) : NavKey
@Serializable data object ClientProfileKey : NavKey
@Serializable data object FeedKey : NavKey
@Serializable data class ProRequestDetailKey(val requestId: String) : NavKey
@Serializable data object MyJobsKey : NavKey
@Serializable data object ProfessionalProfileKey : NavKey

enum class ClientTab(val root: NavKey) { REQUESTS(MyRequestsKey), PROFILE(ClientProfileKey) }
enum class ProTab(val root: NavKey) { FEED(FeedKey), JOBS(MyJobsKey), PROFILE(ProfessionalProfileKey) }

class TopLevelBackStack<T : Enum<T>>(startTab: T, roots: Map<T, NavKey>) {
    val activeTab: T
    val activeStack: List<NavKey>
    fun add(key: NavKey); fun removeLast(): Boolean; fun replaceTop(key: NavKey)
    fun switchTab(tab: T); fun resetTab(tab: T)
}
```

`ClientShell`/`ProfessionalShell`: `Scaffold(bottomBar = NavigationBar)` + `NavDisplay(backStack = state.activeStack, onBack = { state.removeLast() }, entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()), entryProvider = entryProvider { ... })`.
El estado de las pilas se respalda con `rememberNavBackStack`/`rememberSaveable` por pestaña.

## 6. Inyección de dependencias (Hilt)

| Módulo Hilt | Ubicación | Contenido |
|---|---|---|
| `RepositoryModule` (Fase 1) | `:core:data/di` | `@Binds AuthRepository → FakeAuthRepository`, `UserRepository → FakeUserRepository`, `RepairRequestRepository → FakeRepairRequestRepository`, `ReviewRepository → FakeReviewRepository`. Todos `@Singleton` y compartiendo `InMemoryStore`. |
| `FirebaseRepositoryModule` (Fase 2) | `:core:data/di` | Mismos `@Binds` hacia `Firebase*`. Reemplaza al anterior (se elimina el `@InstallIn` del Fake o se usa un flavor/flag `BuildConfig.USE_FAKES`). |
| `FirebaseModule` (Fase 2) | `:core:data/di` | `@Provides FirebaseAuth`, `FirebaseFirestore`, `FirebaseStorage`. |
| `LocationModule` | `:core:data/di` | Fase 1: `FakeLocationProvider`, `FakeAddressResolver`. Fase 3: `FusedLocationProvider`, `GeocoderAddressResolver`. |
| `CoreModule` | `:core:data/di` | `Clock → SystemClock`, `IdGenerator → UuidIdGenerator`, `@IoDispatcher CoroutineDispatcher`. |

Los casos de uso tienen `@Inject constructor` (se agrega `javax.inject:javax.inject` a
`:core:domain`, dependencia JVM pura). Los ViewModels con argumento usan `@AssistedInject` y
`@HiltViewModel(assistedFactory = ...)`.

## 7. Datos simulados (Fase 1, `:core:data/fake/seed`)

`InMemoryStore` contiene `MutableStateFlow<Map<String, User>>`, `MutableStateFlow<Map<String, RepairRequest>>`,
`MutableStateFlow<Map<String, Review>>` y `Map<email, password>`, protegido por un `Mutex` para las
operaciones compuestas. Contraseña de todas las cuentas de prueba: `test1234` (solo datos mock locales).

### Usuarios

| id | Rol | Nombre | Email | Teléfono | Categorías | Base (lat, lng) | Radio | Reseñas (suma/cant.) |
|---|---|---|---|---|---|---|---|---|
| `c1` | CLIENT | Ana López | `ana@fixit.test` | 11 5555-0001 | — | — | — | — |
| `c2` | CLIENT | Bruno Díaz | `bruno@fixit.test` | 11 5555-0002 | — | — | — | — |
| `p1` | PROFESSIONAL | Carlos Gómez | `carlos@fixit.test` | 11 5555-0101 | ELECTRICITY, GAS | (-34.8065, -58.2788) Florencio Varela | 10 | 18 / 4 (4.5) |
| `p2` | PROFESSIONAL | Diana Ruiz | `diana@fixit.test` | 11 5555-0102 | PLUMBING, GAS | (-34.7206, -58.2546) Quilmes | 8 | 0 / 0 |
| `p3` | PROFESSIONAL | Esteban Paz | `esteban@fixit.test` | 11 5555-0103 | CARPENTRY, PAINTING, OTHER | (-34.7600, -58.2120) Berazategui | 15 | 9 / 2 (4.5) |

### Solicitudes

| id | Cliente | Categoría | Ubicación (lat, lng) | Estado | Profesional | Visible en feed de |
|---|---|---|---|---|---|---|
| `r1` | c1 | ELECTRICITY | (-34.7950, -58.2700) F. Varela | OPEN | — | p1 (~1.5 km) |
| `r2` | c1 | PLUMBING | (-34.7300, -58.2600) Quilmes | OPEN | — | p2 (~1.2 km) |
| `r3` | c2 | GAS | (-34.7700, -58.2650) | OPEN | — | p1 (~4.2 km), p2 (~5.6 km) |
| `r4` | c2 | PAINTING | (-34.7650, -58.2200) | ACCEPTED | p3 | — |
| `r5` | c1 | ELECTRICITY | (-34.8100, -58.2800) | COMPLETED | p1 (reseña 5) | — |
| `r6` | c2 | MASONRY | (-34.8000, -58.2750) | OPEN | — | nadie (ningún profesional con MASONRY) |
| `r7` | c1 | ELECTRICITY | (-34.6037, -58.3816) CABA | OPEN | — | nadie (fuera de radio de p1) |

Las fotos mock usan recursos `drawable` (`sample_repair_1..3`) referenciados como
`android.resource://com.vespelabs.fixit/drawable/...`. El seed se aplica al construir `InMemoryStore`
(solo en `debug` y en tests con `SeedData.default()`); los tests de dominio usan sus propios fakes.

## 8. Modelo de datos en Firestore (Fase 2)

### `users/{uid}`

| Campo | Tipo | Notas |
|---|---|---|
| `name`, `email`, `phone` | string | |
| `role` | string | `"CLIENT"` \| `"PROFESSIONAL"`; inmutable. |
| `categories` | array<string> | Solo profesionales. |
| `baseLat`, `baseLng` | number | Solo profesionales. |
| `baseAddress` | string | |
| `radiusKm` | number | 1..50. |
| `ratingSum`, `reviewCount` | number | Solo modificables por la transacción de reseña. |
| `createdAt` | timestamp | |

### `requests/{requestId}`

| Campo | Tipo | Notas |
|---|---|---|
| `clientId` | string | `== request.auth.uid` al crear. |
| `category` | string | |
| `description` | string | 10..500. |
| `photoUrls` | array<string> | 1..3. |
| `lat`, `lng` | number | |
| `geohash` | string | `GeoFireUtils.getGeoHashForLocation`, precisión 10. |
| `address` | string | |
| `status` | string | |
| `professionalId` | string \| null | |
| `createdAt`, `acceptedAt`, `completedAt`, `cancelledAt` | timestamp \| null | `serverTimestamp()`. |

### `reviews/{requestId}`

`professionalId`, `clientId`, `rating` (1..5), `comment` (string \| null, ≤300), `createdAt`.

### Índices compuestos (`firestore.indexes.json`)

| Colección | Campos | Uso |
|---|---|---|
| `requests` | `status ASC, category ASC, geohash ASC` | Feed (geohash bounds). |
| `requests` | `clientId ASC, createdAt DESC` | Mis solicitudes. |
| `requests` | `professionalId ASC, acceptedAt DESC` | Mis trabajos. |

### Security Rules (borrador)

```
rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {
    function signedIn() { return request.auth != null; }
    function me() { return get(/databases/$(db)/documents/users/$(request.auth.uid)).data; }
    function isPro() { return signedIn() && me().role == 'PROFESSIONAL'; }

    match /users/{uid} {
      allow read: if signedIn();   // phone exposure is limited by app logic; see ADR-10
      allow create: if request.auth.uid == uid
                    && request.resource.data.ratingSum == 0
                    && request.resource.data.reviewCount == 0;
      allow update: if (request.auth.uid == uid
                        && !request.resource.data.diff(resource.data).affectedKeys()
                             .hasAny(['role', 'ratingSum', 'reviewCount']))
                    || (signedIn()   // review aggregate, only alongside a review in the same transaction
                        && request.resource.data.diff(resource.data).affectedKeys()
                             .hasOnly(['ratingSum', 'reviewCount'])
                        && request.resource.data.reviewCount == resource.data.reviewCount + 1);
    }

    match /requests/{id} {
      allow read: if signedIn();
      allow create: if signedIn() && me().role == 'CLIENT'
                    && request.resource.data.clientId == request.auth.uid
                    && request.resource.data.status == 'OPEN'
                    && request.resource.data.professionalId == null
                    && request.resource.data.photoUrls.size() >= 1
                    && request.resource.data.photoUrls.size() <= 3
                    && request.resource.data.description.size() >= 10
                    && request.resource.data.description.size() <= 500;
      allow update: if
        // accept: OPEN -> ACCEPTED by a matching professional
        (isPro() && resource.data.status == 'OPEN'
          && request.resource.data.status == 'ACCEPTED'
          && request.resource.data.professionalId == request.auth.uid
          && resource.data.category in me().categories
          && request.resource.data.diff(resource.data).affectedKeys()
               .hasOnly(['status', 'professionalId', 'acceptedAt']))
        // cancel: OPEN -> CANCELLED by owner
        || (resource.data.clientId == request.auth.uid
          && resource.data.status == 'OPEN'
          && request.resource.data.status == 'CANCELLED'
          && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['status', 'cancelledAt']))
        // complete: ACCEPTED -> COMPLETED by owner, review must be created in same transaction
        || (resource.data.clientId == request.auth.uid
          && resource.data.status == 'ACCEPTED'
          && request.resource.data.status == 'COMPLETED'
          && existsAfter(/databases/$(db)/documents/reviews/$(id))
          && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['status', 'completedAt']));
      allow delete: if false;
    }

    match /reviews/{requestId} {
      allow read: if signedIn();
      allow create: if signedIn()
        && get(/databases/$(db)/documents/requests/$(requestId)).data.clientId == request.auth.uid
        && getAfter(/databases/$(db)/documents/requests/$(requestId)).data.status == 'COMPLETED'
        && request.resource.data.rating is int
        && request.resource.data.rating >= 1 && request.resource.data.rating <= 5;
      allow update, delete: if false;
    }
  }
}
```

> La regla del agregado de reputación es la parte más débil (cualquier usuario autenticado podría
> incrementar `reviewCount` si escribe una reseña válida en la misma transacción). Se acepta para el
> alcance académico; la alternativa robusta es una Cloud Function disparada por `reviews` (ver ADR-9).

### Storage

- Ruta: `requests/{requestId}/photo_{0..2}.jpg`.
- Reglas: escritura si `request.auth != null`, `request.resource.size < 5 * 1024 * 1024` y
  `request.resource.contentType.matches('image/.*')`; lectura si `request.auth != null`. La verificación
  de dueño se hace con metadato `ownerUid == request.auth.uid` porque el documento aún no existe al subir.

## 9. Decisiones (ADR resumidos)

| # | Decisión | Alternativas | Motivo / tradeoff |
|---|---|---|---|
| ADR-1 | Una sola app con rol fijo elegido en el registro y dos shells de navegación. | Dos apps; rol cambiable. | Menos código duplicado y una sola entrega. Rol fijo simplifica reglas de seguridad y estados; costo: una persona que quiera ambos roles necesita dos cuentas. |
| ADR-2 | Auth con email + contraseña. | Google Sign-In, teléfono/OTP. | Disponible igual en Fake y Firebase, sin configuración de SHA ni SMS. Costo: menor conveniencia y sin verificación de teléfono. |
| ADR-3 | Fases con repositorios Fake intercambiables vía Hilt. | Firebase desde el inicio. | Permite entregar el TP2 sin backend, testear sin red y demostrar la regla de dependencia. Costo: dos implementaciones a mantener; mitigado con tests de contrato compartidos. |
| ADR-4 | Validaciones en casos de uso; límites en `Limits`. | Validar en ViewModel. | Reglas testeables en JVM y reutilizables. Descripción 10..500 y comentario ≤300 elegidos para forzar información útil sin textos excesivos. |
| ADR-5 | Máquina de estados pura (`RequestStateMachine`) usada por todas las implementaciones. | Lógica de estado dispersa en repos. | Una sola fuente de verdad; test exhaustivo de 12 combinaciones. |
| ADR-6 | Aceptación atómica: `Mutex` en Fake, transacción en Firestore; segundo intento → `AlreadyTaken`. | Optimista sin transacción; cola de asignación. | Garantiza "el primero gana" sin servidor propio. Costo: reintentos de transacción bajo contención (aceptable por baja concurrencia). |
| ADR-7 | Matching por categoría + Haversine en dominio; geohash con `geofire-android-common` solo en data. | GeoPoint de Firestore (sin consultas por radio), backend con PostGIS. | Firestore no tiene consultas geográficas nativas. El geohash reduce lecturas y el filtro exacto en dominio elimina falsos positivos; costo: hasta ~9 listeners por feed y lecturas extra de candidatos en bordes. |
| ADR-8 | Feed en tiempo real con `Flow` (snapshot listeners en Fase 2); sin push. | Pull-to-refresh; FCM. | Experiencia "en vivo" con el feed abierto, sin servidor de notificaciones. Costo: el profesional solo se entera si tiene la app abierta. |
| ADR-9 | Completar + reseñar en una única operación; agregado `ratingSum`/`reviewCount` actualizado en la misma transacción. | Recalcular promedio leyendo todas las reseñas; Cloud Function. | Lectura O(1) del promedio y consistencia. Costo: reglas de seguridad más complejas; una Cloud Function sería más segura pero agrega plan Blaze. |
| ADR-10 | Contacto por `ACTION_DIAL` y `wa.me`; teléfono visible solo tras `ACCEPTED`. | Chat propio; `ACTION_CALL`. | Cero infraestructura y sin permiso `CALL_PHONE`. Costo: en Fase 2 los documentos `users` son legibles por usuarios autenticados, por lo que la privacidad del teléfono es de app, no de reglas; mejora futura: subcolección `users/{uid}/private` legible solo por participantes. |
| ADR-11 | Categorías como `enum` fijo con etiquetas en `strings.xml`. | Categorías dinámicas en Firestore. | Exhaustividad en `when`, sin carga remota, i18n simple. Costo: agregar una categoría requiere release. |
| ADR-12 | Sin límites de solicitudes abiertas ni trabajos aceptados. | Topes configurables. | Decisión de producto para el alcance académico; riesgo de spam aceptado. |
| ADR-13 | Navigation 3 con una pila por pestaña y claves con solo IDs. | Navigation Compose 2.x. | Alineado con la cátedra; la pila es estado observable y testeable. Costo: API más reciente, menos ejemplos. |
| ADR-14 | No existe `:features:profile`; cada rol tiene su perfil en su feature y componentes compartidos en `:core:ui`. | Feature de perfil común. | Las pantallas de perfil difieren por rol; evita dependencias cruzadas entre features. |
| ADR-15 | Errores como valores (`AppResult`/`DomainError`) y fakes escritos a mano. | Excepciones; Mockito/MockK. | Flujo explícito y testeable; los fakes documentan el contrato. Costo: más código de prueba. |
| ADR-16 | Edición de solicitudes fuera de alcance. | Permitir editar en `OPEN`. | Evita condiciones de carrera con profesionales que ya leyeron la solicitud; el cliente puede cancelar y volver a publicar. |
