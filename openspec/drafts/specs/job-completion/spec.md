# Especificación: Finalización y reseña (`job-completion`)

## Propósito

Permitir que el cliente dueño marque un trabajo `ACCEPTED` como `COMPLETED` y, en el mismo flujo,
califique al profesional con 1..5 estrellas y un comentario opcional. Mantener el agregado de
reputación del profesional (suma y cantidad de reseñas).

Módulos: `:features:client` (`CompleteAndRate`), `:core:ui` (`RatingStars`), `:core:domain`
(`CompleteRequestWithReviewUseCase`, `ObserveReviewForRequestUseCase`), `:core:data`
(`ReviewRepository`, `RepairRequestRepository`, `UserRepository`).

## Reglas

| Regla | Valor | Error |
|---|---|---|
| Calificación | Entero 1..5 | `Validation.InvalidRating` |
| Comentario | Opcional; recortado; ≤ 300 caracteres; vacío se guarda como `null` | `Validation.CommentTooLong` |
| Reseñas por solicitud | Exactamente 1 | `ReviewAlreadyExists` |
| Quién completa | Solo el cliente dueño | `Unauthorized` |
| Desde qué estado | Solo `ACCEPTED` | `InvalidTransition(estado, COMPLETE)` |

## Requisitos

### Requirement: Completar y calificar en una sola operación [Fase 1]

`CompleteRequestWithReviewUseCase(requestId, rating, comment)` DEBE validar la reseña y luego,
como una única operación atómica del repositorio: cambiar la solicitud a `COMPLETED` con
`completedAt`, crear la `Review` (`requestId`, `professionalId`, `clientId`, `rating`, `comment`,
`createdAt`) y actualizar el agregado del profesional (`ratingSum += rating`, `reviewCount += 1`).
Si alguna parte falla, ninguna DEBE persistir.

#### Escenario: Completar con reseña
- DADO una solicitud `ACCEPTED` del cliente C asignada al profesional P con `ratingSum = 8`, `reviewCount = 2`
- CUANDO C ejecuta `CompleteRequestWithReviewUseCase(id, 5, "Excelente")`
- ENTONCES la solicitud queda `COMPLETED`
- Y existe una `Review` con `rating = 5`
- Y P queda con `ratingSum = 13`, `reviewCount = 3` y promedio formateado `"4.3"`

#### Escenario: Comentario omitido
- DADO un comentario `"   "`
- CUANDO se completa con rating 4
- ENTONCES la `Review` se guarda con `comment = null`

#### Escenario: Calificación fuera de rango
- DADO rating 0 o 6
- ENTONCES el resultado es `Error(Validation.InvalidRating)` y la solicitud sigue `ACCEPTED`

#### Escenario: Comentario demasiado largo
- DADO un comentario de 301 caracteres
- ENTONCES el resultado es `Error(Validation.CommentTooLong)`

#### Escenario: Completar una solicitud OPEN
- DADO una solicitud `OPEN`
- ENTONCES el resultado es `Error(InvalidTransition(OPEN, COMPLETE))`

#### Escenario: Completar una ya completada
- DADO una solicitud `COMPLETED`
- ENTONCES el resultado es `Error(InvalidTransition(COMPLETED, COMPLETE))` y no se crea una segunda reseña

#### Escenario: Otro usuario intenta completar
- DADO el profesional asignado o un cliente distinto del dueño
- ENTONCES el resultado es `Error(Unauthorized)`

### Requirement: Una reseña por solicitud [Fase 1]

El repositorio DEBE impedir más de una `Review` por `requestId`. En Fase 2 el ID del documento de
reseña DEBE ser igual al `requestId`, lo que hace la unicidad estructural.

#### Escenario: Reseña duplicada
- DADO una `Review` existente para la solicitud X
- CUANDO se intenta crear otra para X por cualquier vía
- ENTONCES el resultado es `Error(ReviewAlreadyExists)`

### Requirement: Pantalla Completar y calificar [Fase 1]

Desde el detalle de una solicitud `ACCEPTED`, el botón "Marcar como completado" DEBE navegar a
`CompleteRequestKey(requestId)`. La pantalla DEBE mostrar el nombre del profesional, `RatingStars`
interactivo (sin selección inicial), campo de comentario con contador `n/300` y botón "Confirmar",
deshabilitado hasta seleccionar al menos 1 estrella. Al confirmar con éxito, la feature DEBE
invocar `onCompleted()` y `:app` DEBE volver al detalle, que mostrará `COMPLETED` y la reseña.

#### Escenario: Botón deshabilitado sin estrellas
- DADO la pantalla recién abierta
- ENTONCES `uiState.canSubmit` es `false`

#### Escenario: Selección de estrellas
- CUANDO el usuario toca la cuarta estrella
- ENTONCES `rating = 4` y `canSubmit = true`

#### Escenario: Error durante el envío
- DADO que el repositorio devuelve `Network`
- ENTONCES se muestra snackbar con el mensaje de red, se conservan estrellas y comentario y se puede reintentar

### Requirement: Visualización de la reseña [Fase 1]

En el detalle de una solicitud `COMPLETED`, tanto el cliente como el profesional asignado DEBEN
ver la reseña (estrellas y comentario, si lo hay).

#### Escenario: Profesional ve su calificación
- DADO un trabajo `COMPLETED` con reseña 5 y comentario "Muy prolijo"
- CUANDO el profesional abre el detalle desde "Mis trabajos"
- ENTONCES ve 5 estrellas y "Muy prolijo"

### Requirement: Calificación unidireccional [Fase 1]

El profesional NO DEBE poder calificar al cliente (calificación mutua fuera de alcance).

#### Escenario: Sin acción de calificar para profesional
- DADO un trabajo `COMPLETED` en "Mis trabajos"
- ENTONCES no existe acción para calificar

### Requirement: Transacción en Firestore [Fase 2]

`FirebaseRepairRequestRepository.completeWithReview` DEBE ejecutar una única
`firestore.runTransaction` que: lea `requests/{id}` y `users/{professionalId}`; valide con
`RequestStateMachine` y que `clientId == auth.uid`; verifique que `reviews/{id}` no exista;
escriba `requests/{id}.status = COMPLETED`, `completedAt`; cree `reviews/{id}`; y actualice
`users/{professionalId}.ratingSum` y `reviewCount` con `FieldValue.increment`.

#### Escenario: Fallo parcial
- DADO que la escritura de la reseña falla por reglas de seguridad
- ENTONCES la transacción completa se revierte, la solicitud sigue `ACCEPTED` y el agregado no cambia

#### Escenario: Mapeo de errores
- DADO `FirebaseFirestoreException` con código `PERMISSION_DENIED`
- ENTONCES se devuelve `Error(Unauthorized)`; `UNAVAILABLE` mapea a `Network`
