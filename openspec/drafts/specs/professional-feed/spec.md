# Especificación: Feed del profesional (`professional-feed`)

## Propósito

Mostrar a cada profesional, en tiempo real, las solicitudes `OPEN` que coinciden con sus
categorías y que están dentro de su radio de trabajo, y permitirle aceptar una de forma atómica:
el primero en aceptar obtiene el trabajo. También cubre la lista "Mis trabajos".

Módulos: `:features:professional` (`Feed`, `ProfessionalRequestDetail`, `MyJobs`),
`:core:domain` (`ObserveFeedUseCase`, `MatchesProfessionalUseCase`/`RequestMatcher`,
`AcceptRepairRequestUseCase`, `ObserveMyJobsUseCase`, `HaversineDistance`),
`:core:data` (`FakeRepairRequestRepository`, `FirebaseRepairRequestRepository`).

## Regla de matching

Una solicitud `r` es visible para el profesional `p` si y solo si:

1. `r.status == OPEN`, y
2. `r.category in p.categories`, y
3. `haversineKm(p.baseLocation, r.location) <= p.radiusKm`.

La función Haversine vive en `:core:domain` (Kotlin puro), con radio terrestre `6371.0088` km.
El filtro final por distancia exacta SIEMPRE se aplica en dominio, independientemente de la fase.

## Requisitos

### Requirement: Matching por categoría y distancia [Fase 1]

`RequestMatcher.matches(profile, request)` DEBE implementar la regla de matching. El límite es
inclusivo (distancia igual al radio coincide).

#### Escenario: Coincide
- DADO un profesional `{PLUMBING}`, base (-34.80, -58.27), radio 10 km
- Y una solicitud `OPEN` `PLUMBING` a ~3 km
- ENTONCES `matches` es `true`

#### Escenario: Categoría distinta
- DADO la misma solicitud pero categoría `GAS`
- ENTONCES `matches` es `false`

#### Escenario: Fuera de radio
- DADO una solicitud `PLUMBING` a ~12 km
- ENTONCES `matches` es `false`

#### Escenario: No abierta
- DADO una solicitud `PLUMBING` a 1 km en estado `ACCEPTED`
- ENTONCES `matches` es `false`

#### Escenario: Exactamente en el radio
- DADO una distancia calculada igual al radio
- ENTONCES `matches` es `true`

### Requirement: Feed en tiempo real [Fase 1]

`ObserveFeedUseCase` DEBE devolver un `Flow<AppResult<List<FeedItem>>>` que emita cada vez que
cambien las solicitudes relevantes (creación, aceptación, cancelación). Los ítems DEBEN ordenarse
por `createdAt` descendente e incluir la distancia en km (1 decimal) para mostrarla en la tarjeta.
El `FeedItem` NO DEBE incluir teléfono ni datos personales del cliente.

#### Escenario: Nueva solicitud aparece
- DADO el feed abierto de un profesional `{ELECTRICITY}` con radio 10
- CUANDO un cliente publica una solicitud `ELECTRICITY` a 2 km
- ENTONCES el feed emite una lista que la incluye

#### Escenario: Solicitud aceptada por otro desaparece
- DADO una solicitud visible en el feed de los profesionales A y B
- CUANDO A la acepta
- ENTONCES el feed de B emite una lista sin esa solicitud

#### Escenario: Solicitud cancelada desaparece
- DADO una solicitud visible en el feed
- CUANDO el cliente la cancela
- ENTONCES deja de aparecer

#### Escenario: Feed vacío
- DADO que ninguna solicitud coincide
- ENTONCES `FeedUiState.Success(items = emptyList())` y la pantalla muestra un estado vacío explicando categorías y radio

#### Escenario: Error del repositorio
- DADO que el flujo emite `Error(Network)`
- ENTONCES `FeedUiState.Error(Network)` con acción "Reintentar"

### Requirement: Detalle de solicitud para el profesional [Fase 1]

El profesional DEBE poder abrir una solicitud del feed y ver categoría, descripción, fotos,
dirección aproximada, distancia y fecha, con un botón "Aceptar trabajo". La aceptación DEBE
pedir confirmación.

#### Escenario: Detalle desde el feed
- DADO una solicitud `OPEN` compatible
- CUANDO el profesional toca su tarjeta
- ENTONCES `:app` navega a `ProRequestDetailKey(requestId)` y se muestra el botón "Aceptar trabajo"

### Requirement: Aceptación atómica — el primero gana [Fase 1]

`AcceptRepairRequestUseCase(requestId)` DEBE, de forma atómica: leer la solicitud, verificar
con `RequestStateMachine` que la transición `ACCEPT` es válida, verificar que el profesional
coincide por categoría, y escribir `status = ACCEPTED`, `professionalId` y `acceptedAt`.
En Fase 1, `FakeRepairRequestRepository` DEBE garantizar la atomicidad con un `Mutex` (o
`MutableStateFlow.update` con compare-and-set) de modo que dos aceptaciones concurrentes
produzcan exactamente un éxito.

#### Escenario: Aceptación exitosa
- DADO una solicitud `OPEN` compatible
- CUANDO el profesional A ejecuta `AcceptRepairRequestUseCase`
- ENTONCES el resultado es `Success`, `status = ACCEPTED` y `professionalId = A`

#### Escenario: Segundo en aceptar
- DADO una solicitud ya aceptada por A
- CUANDO B ejecuta `AcceptRepairRequestUseCase`
- ENTONCES el resultado es `Error(AlreadyTaken)` y `professionalId` sigue siendo A

#### Escenario: Concurrencia real
- DADO una solicitud `OPEN`
- CUANDO 10 corrutinas de profesionales distintos ejecutan `accept` en paralelo (`runTest` + `async`)
- ENTONCES exactamente una obtiene `Success` y nueve obtienen `Error(AlreadyTaken)`

#### Escenario: Aceptar una cancelada
- DADO una solicitud `CANCELLED`
- ENTONCES el resultado es `Error(InvalidTransition(CANCELLED, ACCEPT))`

#### Escenario: Categoría no habilitada
- DADO un profesional sin la categoría de la solicitud
- ENTONCES el resultado es `Error(Unauthorized)`

#### Escenario: Cliente intenta aceptar
- DADO un usuario con rol `CLIENT`
- ENTONCES el resultado es `Error(Unauthorized)`

#### Escenario: UI tras perder la carrera
- DADO el detalle abierto y el profesional pulsa "Aceptar"
- CUANDO el resultado es `AlreadyTaken`
- ENTONCES se muestra el snackbar "Otro profesional ya tomó este trabajo" y la feature invoca `onBack()`

#### Escenario: UI tras ganar
- CUANDO el resultado es `Success`
- ENTONCES la feature invoca `onAccepted(requestId)` y `:app` navega al detalle del trabajo en la pestaña "Mis trabajos"

### Requirement: Sin límite de trabajos [Fase 1]

El sistema NO DEBE limitar la cantidad de trabajos `ACCEPTED` simultáneos de un profesional.

#### Escenario: Varios aceptados
- DADO un profesional con 3 trabajos `ACCEPTED`
- CUANDO acepta otra solicitud compatible
- ENTONCES la aceptación es exitosa

### Requirement: Mis trabajos [Fase 1]

La pestaña "Mis trabajos" DEBE listar en tiempo real las solicitudes con
`professionalId == profesional autenticado` (estados `ACCEPTED` y `COMPLETED`), ordenadas con
`ACCEPTED` primero y luego por `acceptedAt` descendente. Al abrir un trabajo `ACCEPTED`, el detalle
DEBE mostrar los datos de contacto del cliente (ver `specs/contact/spec.md`).

#### Escenario: Trabajo aceptado aparece
- DADO que el profesional aceptó una solicitud
- ENTONCES aparece en "Mis trabajos" con estado `ACCEPTED`

#### Escenario: Trabajo completado
- CUANDO el cliente la marca como completada
- ENTONCES el ítem pasa a `COMPLETED` y muestra la calificación recibida

### Requirement: Consulta geográfica en Firestore [Fase 2]

Firestore no tiene consultas geográficas nativas. `FirebaseRepairRequestRepository.observeOpenNear`
DEBE: calcular los rangos con `GeoFireUtils.getGeoHashQueryBounds(center, radiusMeters)`;
por cada rango, registrar un snapshot listener sobre
`requests where status == "OPEN" and category in [categorías] orderBy geohash startAt/endAt`;
combinar los resultados (`combine`), deduplicar por `id`, y entregar al dominio, que aplica el
filtro exacto de Haversine. Al cancelar el `Flow` DEBEN removerse todos los listeners
(`awaitClose { registrations.forEach { it.remove() } }`).

> `category in [...]` admite hasta 30 valores; con 8 categorías el límite no aplica.

#### Escenario: Falsos positivos del geohash
- DADO una solicitud dentro del rectángulo del geohash pero a 10.4 km con radio 10
- CUANDO llega en el snapshot
- ENTONCES el dominio la descarta

#### Escenario: Índice compuesto
- DADO la consulta `status ==`, `category in`, `orderBy geohash`
- ENTONCES existe el índice compuesto `(status ASC, category ASC, geohash ASC)` declarado en `firestore.indexes.json`

### Requirement: Aceptación por transacción [Fase 2]

`FirebaseRepairRequestRepository.accept` DEBE usar `firestore.runTransaction`, leer el documento,
validar con `RequestStateMachine`, y escribir `status`, `professionalId` y `acceptedAt`
(`FieldValue.serverTimestamp()`). Las Security Rules DEBEN además impedir que un profesional
sobrescriba una solicitud no `OPEN`.

#### Escenario: Dos dispositivos aceptan
- DADO dos profesionales que aceptan casi simultáneamente
- ENTONCES Firestore reintenta la transacción perdedora, que al releer encuentra `ACCEPTED` y devuelve `AlreadyTaken`
