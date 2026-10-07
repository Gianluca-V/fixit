# Especificación: Navegación (`navigation`)

## Propósito

Definir la estructura de navegación con Navigation 3: compuerta de sesión, grafo de autenticación,
grafo de cliente y grafo de profesional, cada uno con pestañas (`NavigationBar`) y una pila de
navegación por pestaña. Las features no navegan: exponen callbacks que `:app` traduce en
operaciones sobre las pilas.

Módulos: `:app` (`FixItNavHost`, `AuthGate`, `ClientShell`, `ProfessionalShell`, claves de navegación,
`TopLevelBackStack`), `:features:*` (`XxxRoute` con callbacks).

## Claves de navegación

Todas las claves son `@Serializable` e implementan `NavKey`. Los argumentos son solo IDs.

| Clave | Tipo | Grafo | Pantalla (Route) | Módulo |
|---|---|---|---|---|
| `SignInKey` | `data object` | Auth | `SignInRoute` | `:features:auth` |
| `SignUpKey` | `data object` | Auth | `SignUpRoute` (incluye selector de rol y campos de profesional) | `:features:auth` |
| `MyRequestsKey` | `data object` | Cliente — pestaña "Solicitudes" (raíz) | `MyRequestsRoute` | `:features:client` |
| `CreateRequestKey` | `data object` | Cliente — pestaña "Solicitudes" | `CreateRequestRoute` | `:features:client` |
| `ClientRequestDetailKey(requestId: String)` | `data class` | Cliente — pestaña "Solicitudes" | `ClientRequestDetailRoute` | `:features:client` |
| `CompleteRequestKey(requestId: String)` | `data class` | Cliente — pestaña "Solicitudes" | `CompleteRequestRoute` | `:features:client` |
| `ClientProfileKey` | `data object` | Cliente — pestaña "Perfil" (raíz) | `ClientProfileRoute` | `:features:client` |
| `FeedKey` | `data object` | Profesional — pestaña "Feed" (raíz) | `FeedRoute` | `:features:professional` |
| `ProRequestDetailKey(requestId: String)` | `data class` | Profesional — pestañas "Feed" y "Mis trabajos" | `ProRequestDetailRoute` | `:features:professional` |
| `MyJobsKey` | `data object` | Profesional — pestaña "Mis trabajos" (raíz) | `MyJobsRoute` | `:features:professional` |
| `ProfessionalProfileKey` | `data object` | Profesional — pestaña "Perfil" (raíz) | `ProfessionalProfileRoute` | `:features:professional` |

## Requisitos

### Requirement: Compuerta por sesión y rol [Fase 1]

`FixItNavHost` DEBE observar el estado de sesión y renderizar exactamente uno de: pantalla de carga,
grafo Auth, `ClientShell` o `ProfessionalShell`. Al cambiar de estado (login, logout) DEBE
descartarse la pila anterior por completo.

#### Escenario: Login como cliente
- DADO el grafo Auth visible
- CUANDO un `CLIENT` inicia sesión
- ENTONCES se muestra `ClientShell` con `MyRequestsKey` como única entrada de la pestaña Solicitudes

#### Escenario: Logout
- DADO `ProfessionalShell` con varias entradas en las pilas
- CUANDO cierra sesión
- ENTONCES se muestra `SignInKey` y "Atrás" cierra la app

### Requirement: Pestañas por rol [Fase 1]

`ClientShell` DEBE mostrar un `NavigationBar` con "Solicitudes" y "Perfil". `ProfessionalShell`
DEBE mostrar "Feed", "Mis trabajos" y "Perfil". Cada pestaña DEBE mantener su propia pila
(`TopLevelBackStack`), y volver a seleccionar una pestaña DEBE restaurar su pila previa.

#### Escenario: Pila independiente por pestaña
- DADO el profesional en Feed → Detalle de solicitud
- CUANDO cambia a "Perfil" y vuelve a "Feed"
- ENTONCES ve nuevamente el Detalle de solicitud

#### Escenario: Reselección de pestaña
- DADO la pestaña "Feed" activa con Detalle en la pila
- CUANDO toca nuevamente "Feed"
- ENTONCES la pila de esa pestaña vuelve a su raíz

#### Escenario: Atrás en pestaña no inicial
- DADO la pestaña "Perfil" en su raíz
- CUANDO el usuario presiona "Atrás"
- ENTONCES se selecciona la pestaña inicial ("Solicitudes" o "Feed"); desde la raíz de la pestaña inicial, "Atrás" cierra la app

### Requirement: NavDisplay y decoradores [Fase 1]

Cada grafo DEBE usar `NavDisplay` con `entryProvider { entry<Key> { ... } }` y los decoradores
`rememberSaveableStateHolderNavEntryDecorator()` y `rememberViewModelStoreNavEntryDecorator()`, de
modo que cada entrada tenga su propio `ViewModelStore` (el ViewModel se destruye al quitar la entrada)
y su estado guardable sobreviva a cambios de configuración.

#### Escenario: Rotación
- DADO el formulario de creación con descripción escrita
- CUANDO se rota el dispositivo
- ENTONCES la descripción y las fotos se conservan

#### Escenario: ViewModel liberado
- DADO `ClientRequestDetailKey("r1")` en la pila
- CUANDO el usuario vuelve atrás
- ENTONCES el `ViewModel` de esa entrada se limpia (`onCleared`)

### Requirement: Argumentos solo como IDs [Fase 1]

Las claves NO DEBEN transportar objetos de dominio. Los ViewModels DEBEN recibir el `requestId`
mediante inyección asistida de Hilt (`@AssistedInject` + `hiltViewModel<VM, VM.Factory>(creationCallback = { it.create(key.requestId) })`)
y cargar los datos desde el repositorio.

#### Escenario: Detalle por ID
- DADO `ProRequestDetailKey("r42")`
- ENTONCES el ViewModel observa `ObserveRequestDetailUseCase("r42")`

### Requirement: Features sin navegación propia [Fase 1]

Ningún módulo `:features:*` DEBE depender de las claves de navegación ni manipular la pila. Cada
`Route` DEBE exponer callbacks; `:app` los conecta:

| Route | Callback | Acción en `:app` |
|---|---|---|
| `SignInRoute` | `onSignUpClick()` | push `SignUpKey` |
| `SignUpRoute` | `onBack()` | pop |
| `MyRequestsRoute` | `onCreateClick()` / `onRequestClick(id)` | push `CreateRequestKey` / push `ClientRequestDetailKey(id)` |
| `CreateRequestRoute` | `onRequestCreated(id)` / `onBack()` | reemplazar tope por `ClientRequestDetailKey(id)` / pop |
| `ClientRequestDetailRoute` | `onCompleteClick(id)` / `onBack()` | push `CompleteRequestKey(id)` / pop |
| `CompleteRequestRoute` | `onCompleted()` / `onBack()` | pop |
| `FeedRoute` | `onRequestClick(id)` | push `ProRequestDetailKey(id)` |
| `ProRequestDetailRoute` | `onAccepted(id)` / `onBack()` | pop en Feed, cambiar a "Mis trabajos" y push `ProRequestDetailKey(id)` / pop |
| `MyJobsRoute` | `onJobClick(id)` | push `ProRequestDetailKey(id)` |
| `*ProfileRoute` | `onSignOutClick()` | ejecuta sign out; la compuerta cambia de grafo |

Los callbacks de contacto (`onCallClick`, `onWhatsAppClick`) se conectan al `ContactLauncher`.

#### Escenario: Publicar solicitud
- DADO la pila `[MyRequestsKey, CreateRequestKey]`
- CUANDO se invoca `onRequestCreated("r9")`
- ENTONCES la pila es `[MyRequestsKey, ClientRequestDetailKey("r9")]`

#### Escenario: Aceptar trabajo
- DADO Feed con pila `[FeedKey, ProRequestDetailKey("r5")]`
- CUANDO se invoca `onAccepted("r5")`
- ENTONCES la pila de Feed es `[FeedKey]`, la pestaña activa es "Mis trabajos" y su pila termina en `ProRequestDetailKey("r5")`

### Requirement: Barra superior [Fase 1]

Las pantallas no raíz DEBEN mostrar `TopAppBar` con botón de navegación "Atrás" que invoca `onBack()`.
Las pantallas raíz de pestaña muestran el título sin botón de volver.

#### Escenario: Detalle con flecha atrás
- DADO `ClientRequestDetailKey` visible
- CUANDO se toca la flecha
- ENTONCES se invoca `onBack()`

### Requirement: Pruebas de navegación sin Android [Fase 1]

`TopLevelBackStack` DEBE ser una clase de `:app` sin dependencias de Compose en su lógica
(operaciones `add`, `removeLast`, `switchTab`, `replaceTop`, `resetTab`) y DEBE testearse con JUnit.

#### Escenario: switchTab conserva pilas
- DADO `Feed: [FeedKey, ProRequestDetailKey("a")]`
- CUANDO `switchTab(MyJobs)` y luego `switchTab(Feed)`
- ENTONCES la pila activa es `[FeedKey, ProRequestDetailKey("a")]`
