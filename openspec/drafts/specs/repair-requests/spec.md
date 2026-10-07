# Especificación: Solicitudes de reparación (`repair-requests`)

## Propósito

Permitir que un cliente publique una solicitud de reparación, consulte sus solicitudes, vea el
detalle de cada una y cancele las que sigan abiertas. Define la máquina de estados de
`RepairRequest`, que es la única fuente de verdad sobre qué transiciones son válidas.

Módulos: `:features:client` (`MyRequests`, `CreateRequest`, `ClientRequestDetail`),
`:core:domain` (`CreateRepairRequestUseCase`, `ObserveMyRequestsUseCase`,
`ObserveRequestDetailUseCase`, `CancelRepairRequestUseCase`, `RequestStateMachine`),
`:core:data` (`FakeRepairRequestRepository`, `FirebaseRepairRequestRepository`).

## Modelo

| Campo | Tipo | Regla |
|---|---|---|
| `id` | `String` | Generado por el repositorio. |
| `clientId` | `String` | Usuario autenticado; NO DEBE tomarse de la UI. |
| `category` | `ServiceCategory` | Exactamente 1. |
| `description` | `String` | Recortada; 10..500 caracteres. |
| `photoUrls` | `List<String>` | 1..3 elementos. |
| `location` | `GeoPoint(lat, lng)` + `address: String` | Obligatoria; `address` no vacía. |
| `createdAt` | `Long` | Epoch millis desde `Clock`. |
| `status` | `RequestStatus` | Inicial `OPEN`. |
| `professionalId` | `String?` | `null` salvo en `ACCEPTED` / `COMPLETED`. |
| `acceptedAt`, `completedAt`, `cancelledAt` | `Long?` | Marca temporal de cada transición. |

## Máquina de estados

```
            accept (profesional, atómico)          complete (cliente dueño)
   OPEN ─────────────────────────────────▶ ACCEPTED ─────────────────────────▶ COMPLETED
     │
     │ cancel (cliente dueño)
     ▼
 CANCELLED
```

| Estado actual | Acción | Actor permitido | Estado destino | Error si no se cumple |
|---|---|---|---|---|
| `OPEN` | `ACCEPT` | Profesional con categoría compatible | `ACCEPTED` | `AlreadyTaken` si otro aceptó antes |
| `OPEN` | `CANCEL` | Cliente dueño | `CANCELLED` | `Unauthorized` si no es el dueño |
| `ACCEPTED` | `COMPLETE` | Cliente dueño | `COMPLETED` | `Unauthorized` si no es el dueño |
| `OPEN` | `COMPLETE` | — | — | `InvalidTransition(OPEN, COMPLETE)` |
| `ACCEPTED` | `ACCEPT` | — | — | `AlreadyTaken` |
| `ACCEPTED` | `CANCEL` | — | — | `InvalidTransition(ACCEPTED, CANCEL)` |
| `COMPLETED` | cualquiera | — | — | `InvalidTransition(COMPLETED, acción)` |
| `CANCELLED` | `ACCEPT` | — | — | `InvalidTransition(CANCELLED, ACCEPT)` |
| `CANCELLED` | `CANCEL` / `COMPLETE` | — | — | `InvalidTransition(CANCELLED, acción)` |

`COMPLETED` y `CANCELLED` son estados terminales. `RequestStateMachine.transition(current, action)`
es una función pura de dominio que devuelve `AppResult<RequestStatus>`; los repositorios
(Fake y Firebase) DEBEN usarla para validar dentro de su sección atómica.

> Nota: `ACCEPT` sobre `ACCEPTED` devuelve `AlreadyTaken` (no `InvalidTransition`) porque es el
> caso esperable de carrera entre profesionales y merece un mensaje específico.

## Requisitos

### Requirement: Crear solicitud [Fase 1]

El cliente DEBE poder publicar una solicitud con exactamente una categoría, una descripción,
entre 1 y 3 fotos y una ubicación con dirección legible. La solicitud DEBE crearse en estado
`OPEN`, con `clientId` del usuario autenticado, `createdAt` del `Clock` y `professionalId = null`.

#### Escenario: Creación exitosa
- DADO un cliente autenticado, categoría `PLUMBING`, descripción de 40 caracteres, 2 fotos y ubicación válida
- CUANDO se ejecuta `CreateRepairRequestUseCase`
- ENTONCES el resultado es `Success(RepairRequest)` con `status = OPEN` y `professionalId = null`
- Y la solicitud aparece en `ObserveMyRequestsUseCase`

#### Escenario: Solo clientes pueden crear
- DADO un usuario autenticado con rol `PROFESSIONAL`
- CUANDO se ejecuta `CreateRepairRequestUseCase`
- ENTONCES el resultado es `Error(Unauthorized)`

### Requirement: Validación de la solicitud [Fase 1]

`CreateRepairRequestUseCase` DEBE validar antes de llamar al repositorio, en este orden, y
devolver el primer error:

| Orden | Regla | Error |
|---|---|---|
| 1 | Categoría seleccionada | `Validation.CategoryRequired` |
| 2 | Descripción recortada no vacía | `Validation.EmptyDescription` |
| 3 | Descripción ≥ 10 caracteres | `Validation.DescriptionTooShort` |
| 4 | Descripción ≤ 500 caracteres | `Validation.DescriptionTooLong` |
| 5 | Al menos 1 foto | `Validation.PhotosRequired` |
| 6 | Como máximo 3 fotos | `Validation.TooManyPhotos` |
| 7 | Ubicación presente y `address` no vacía | `Validation.LocationRequired` |

#### Escenario: Descripción vacía
- DADO una descripción `"   "`
- CUANDO se ejecuta `CreateRepairRequestUseCase`
- ENTONCES el resultado es `Error(Validation.EmptyDescription)` y el repositorio no es invocado

#### Escenario: Descripción corta
- DADO una descripción de 9 caracteres
- ENTONCES el resultado es `Error(Validation.DescriptionTooShort)`

#### Escenario: Descripción larga
- DADO una descripción de 501 caracteres
- ENTONCES el resultado es `Error(Validation.DescriptionTooLong)`

#### Escenario: Límites exactos válidos
- DADO descripciones de 10 y de 500 caracteres
- ENTONCES ambas son aceptadas

#### Escenario: Sin fotos
- DADO 0 fotos
- ENTONCES el resultado es `Error(Validation.PhotosRequired)`

#### Escenario: Demasiadas fotos
- DADO 4 fotos
- ENTONCES el resultado es `Error(Validation.TooManyPhotos)`

#### Escenario: Sin ubicación
- DADO ubicación nula o `address` vacía
- ENTONCES el resultado es `Error(Validation.LocationRequired)`

### Requirement: Formulario de creación [Fase 1]

La pantalla `CreateRequest` DEBE ofrecer: selector de categoría (`FilterChip` de selección única),
campo de descripción con contador `n/500`, sección de fotos (agregar / quitar, máximo 3),
ubicación (botón "Usar mi ubicación" y dirección editable) y botón "Publicar". Mientras se
publica, el botón DEBE deshabilitarse. Al publicar con éxito, la feature DEBE invocar
`onRequestCreated(requestId)` y `:app` DEBE navegar al detalle reemplazando la pantalla de creación.

#### Escenario: Selección única de categoría
- DADO `PLUMBING` seleccionado
- CUANDO el usuario toca `GAS`
- ENTONCES solo `GAS` queda seleccionado

#### Escenario: Botón agregar foto oculto al llegar a 3
- DADO 3 fotos cargadas
- ENTONCES el control "Agregar foto" está deshabilitado

#### Escenario: Error de validación visible
- DADO que el usuario publica sin fotos
- CUANDO el caso de uso devuelve `PhotosRequired`
- ENTONCES el `UiState` expone el error en la sección de fotos y el formulario conserva lo cargado

### Requirement: Edición no permitida [Fase 1]

Una solicitud publicada NO DEBE poder editarse. No existe caso de uso ni acción de UI para
modificar categoría, descripción, fotos o ubicación después de crearla.

#### Escenario: Detalle sin edición
- DADO una solicitud `OPEN` propia
- CUANDO el cliente abre el detalle
- ENTONCES las acciones disponibles son únicamente "Cancelar solicitud"

### Requirement: Listar mis solicitudes [Fase 1]

La pestaña "Mis solicitudes" DEBE mostrar en tiempo real (`Flow`) las solicitudes del cliente
autenticado, ordenadas por `createdAt` descendente, con categoría, extracto de descripción,
estado (`StatusBadge`) y fecha. PUEDE filtrarse por estado con `FilterChip`
(Todas / Abiertas / Aceptadas / Completadas / Canceladas).

#### Escenario: Lista ordenada
- DADO tres solicitudes del cliente creadas en t1 < t2 < t3
- CUANDO se observa `ObserveMyRequestsUseCase`
- ENTONCES el orden emitido es t3, t2, t1

#### Escenario: Solo propias
- DADO solicitudes de otros clientes en el repositorio
- ENTONCES no aparecen en la lista

#### Escenario: Lista vacía
- DADO un cliente sin solicitudes
- ENTONCES `MyRequestsUiState.Success(requests = emptyList())` y la pantalla muestra un estado vacío con acción "Nueva solicitud"

#### Escenario: Filtro por estado
- DADO el filtro "Abiertas"
- ENTONCES solo se muestran solicitudes `OPEN`

### Requirement: Detalle de solicitud (cliente) [Fase 1]

El detalle DEBE mostrar categoría, descripción completa, fotos, dirección, fecha y estado,
y actualizarse en tiempo real. Según el estado:

| Estado | Contenido adicional | Acciones |
|---|---|---|
| `OPEN` | "Esperando profesional" | Cancelar solicitud |
| `ACCEPTED` | Profesional (nombre, reputación) + tarjeta de contacto | Llamar, WhatsApp, Marcar como completado |
| `COMPLETED` | Profesional + reseña dejada | — |
| `CANCELLED` | "Solicitud cancelada" | — |

#### Escenario: Solicitud inexistente
- DADO un `requestId` que no existe
- CUANDO se observa el detalle
- ENTONCES `UiState.Error(NotFound)` y se ofrece volver

#### Escenario: Cambio en vivo a ACCEPTED
- DADO el detalle abierto de una solicitud `OPEN`
- CUANDO un profesional la acepta
- ENTONCES el detalle pasa a mostrar el profesional y las acciones de contacto sin recargar

#### Escenario: Acceso ajeno
- DADO una solicitud de otro cliente
- CUANDO el cliente intenta observar su detalle
- ENTONCES el resultado es `Error(Unauthorized)`

### Requirement: Cancelar solicitud [Fase 1]

Solo el cliente dueño DEBE poder cancelar, y solo mientras la solicitud esté `OPEN`. La
cancelación DEBE pedir confirmación en un `AlertDialog`. La verificación de estado y el
cambio DEBEN ser atómicos respecto de una aceptación concurrente.

#### Escenario: Cancelación exitosa
- DADO una solicitud `OPEN` propia
- CUANDO se ejecuta `CancelRepairRequestUseCase`
- ENTONCES `status = CANCELLED`, `cancelledAt` se registra y desaparece del feed de profesionales

#### Escenario: Cancelar una aceptada
- DADO una solicitud `ACCEPTED`
- CUANDO se ejecuta `CancelRepairRequestUseCase`
- ENTONCES el resultado es `Error(InvalidTransition(ACCEPTED, CANCEL))`

#### Escenario: Cancelar ajena
- DADO una solicitud `OPEN` de otro cliente
- ENTONCES el resultado es `Error(Unauthorized)`

#### Escenario: Carrera cancelar vs aceptar
- DADO una solicitud `OPEN`
- CUANDO un profesional la acepta y luego el cliente intenta cancelarla
- ENTONCES la cancelación devuelve `InvalidTransition(ACCEPTED, CANCEL)` y el estado sigue `ACCEPTED`

### Requirement: Máquina de estados pura [Fase 1]

`RequestStateMachine` DEBE implementar exactamente la tabla de transiciones de esta
especificación, sin dependencias, y DEBE estar cubierta por un test parametrizado que
recorra las 12 combinaciones estado × acción.

#### Escenario: Transiciones válidas
- DADO `(OPEN, ACCEPT)`, `(OPEN, CANCEL)`, `(ACCEPTED, COMPLETE)`
- ENTONCES devuelven `Success(ACCEPTED)`, `Success(CANCELLED)`, `Success(COMPLETED)`

#### Escenario: Estados terminales
- DADO `COMPLETED` o `CANCELLED` y cualquier acción
- ENTONCES devuelve `Error(InvalidTransition)`

### Requirement: Sin límites de cantidad [Fase 1]

El sistema NO DEBE limitar la cantidad de solicitudes `OPEN` por cliente.

#### Escenario: Múltiples abiertas
- DADO un cliente con 5 solicitudes `OPEN`
- CUANDO crea una sexta válida
- ENTONCES se crea correctamente

### Requirement: Persistencia en Firestore [Fase 2]

`FirebaseRepairRequestRepository` DEBE guardar las solicitudes en la colección `requests`,
incluyendo el campo `geohash` calculado a partir de la ubicación, subir las fotos a Storage antes
de crear el documento (ver `specs/media/spec.md`), y realizar `cancel` mediante una transacción
que lea el estado, valide con `RequestStateMachine` y escriba.

#### Escenario: Documento creado con geohash
- DADO una solicitud válida en (-34.80, -58.27)
- CUANDO se crea en Fase 2
- ENTONCES el documento contiene `geohash` de precisión 10 calculado con `GeoFireUtils.getGeoHashForLocation`

#### Escenario: Fallo de red
- DADO que Firestore no está disponible
- CUANDO se crea la solicitud
- ENTONCES el resultado es `Error(Network)` y no quedan fotos huérfanas sin intentar limpieza
