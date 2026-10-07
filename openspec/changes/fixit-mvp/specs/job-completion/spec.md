# Delta para Finalización y reseña (`job-completion`)

> Cambio: `fixit-mvp`. Alcance: el cliente dueño marca un trabajo `ACCEPTED` como `COMPLETED` y, en el mismo flujo, califica al profesional con 1..5 estrellas y un comentario opcional; se mantiene la reputación del profesional.
>
> Convención de verificación: cada escenario está cubierto por un test JVM o por un ítem del checklist manual. Los escenarios cuyo título termina en `[manual]` se verifican a mano (ver `design.md` §15). El dominio devuelve números; el formato visual con el idioma del dispositivo se verifica a mano.

## ADDED Requirements

### Requirement: Completar y calificar en una sola operación [Fase 1]

El sistema DEBE validar la reseña y, como una única operación indivisible, cambiar la solicitud a `COMPLETED` (con fecha de finalización), crear la reseña (solicitud, profesional, cliente, calificación, comentario y fecha) y actualizar la reputación del profesional (suma de calificaciones y cantidad de reseñas). Si alguna parte falla, ninguna DEBE persistir. Solo el cliente dueño PUEDE completar, y solo desde `ACCEPTED`.

| Regla | Valor | Error |
|---|---|---|
| Calificación | Entero 1..5 | `Validation.InvalidRating` |
| Comentario | Opcional; recortado; hasta 300 caracteres; vacío se guarda como ausente | `Validation.CommentTooLong` |
| Quién completa | Solo el cliente dueño | `Unauthorized` |
| Desde qué estado | Solo `ACCEPTED` | `InvalidTransition(estado, COMPLETE)` |

#### Escenario: Completar con reseña
- DADO una solicitud `ACCEPTED` del cliente C asignada al profesional P, con suma de calificaciones 8 y 2 reseñas
- CUANDO C completa con calificación 5 y comentario "Excelente"
- ENTONCES la solicitud queda `COMPLETED`
- Y existe una reseña con calificación 5 y ese comentario
- Y la solicitud registra la fecha de finalización
- Y P queda con suma 13, 3 reseñas y promedio numérico 4.3

#### Escenario: Comentario omitido
- DADO un comentario `"   "`
- CUANDO C completa con calificación 4
- ENTONCES la reseña se guarda sin comentario

#### Escenario: Calificación fuera de rango
- DADO una calificación de 0, o de 6
- CUANDO C intenta completar
- ENTONCES el resultado es `Validation.InvalidRating` y la solicitud sigue `ACCEPTED`

#### Escenario: Límites exactos válidos
- DADO calificaciones de 1 y de 5, y un comentario de exactamente 300 caracteres
- CUANDO se valida cada dato
- ENTONCES todos son aceptados

#### Escenario: Comentario demasiado largo
- DADO un comentario de 301 caracteres
- CUANDO C intenta completar
- ENTONCES el resultado es `Validation.CommentTooLong` y la solicitud sigue `ACCEPTED`

#### Escenario: Completar una solicitud abierta
- DADO una solicitud `OPEN`
- CUANDO el cliente dueño intenta completarla
- ENTONCES el resultado es `InvalidTransition(OPEN, COMPLETE)`

#### Escenario: Completar una solicitud ya completada
- DADO una solicitud `COMPLETED`
- CUANDO el cliente dueño intenta completarla de nuevo
- ENTONCES el resultado es `InvalidTransition(COMPLETED, COMPLETE)`

#### Escenario: Completar una solicitud cancelada
- DADO una solicitud que estuvo `ACCEPTED` y fue cancelada por el cliente
- CUANDO el cliente dueño intenta completarla
- ENTONCES el resultado es `InvalidTransition(CANCELLED, COMPLETE)`
- Y no se crea ninguna reseña ni cambia la reputación del profesional

#### Escenario: Otro usuario intenta completar
- DADO el profesional asignado, o un cliente distinto del dueño
- CUANDO intenta completar la solicitud
- ENTONCES el resultado es `Unauthorized`

#### Escenario: Falla parcial
- DADO que no puede guardarse la reseña
- CUANDO C intenta completar
- ENTONCES la solicitud sigue `ACCEPTED` y la reputación de P no cambia

### Requirement: Una reseña por solicitud [Fase 1]

Cada solicitud DEBE tener como máximo una reseña. El invariante se cumple porque la reseña solo se crea al pasar de `ACCEPTED` a `COMPLETED` y `COMPLETED` es un estado terminal; no existe un error propio para una reseña duplicada.

#### Escenario: Segundo intento de completar
- DADO una solicitud `COMPLETED` con su reseña, y un profesional con suma 13 y 3 reseñas
- CUANDO el cliente dueño intenta completarla por segunda vez
- ENTONCES el resultado es `InvalidTransition(COMPLETED, COMPLETE)`
- Y sigue existiendo exactamente una reseña para esa solicitud
- Y la reputación del profesional no cambia

### Requirement: Pantalla de completar y calificar [Fase 1]

Desde el detalle de una solicitud `ACCEPTED`, "Marcar como completado" DEBE abrir la pantalla de completar y calificar. La pantalla DEBE cargar la solicitud por su identificador para mostrar el nombre del profesional asignado (si no puede cargarla, DEBE mostrar el error con la acción "Reintentar"), una calificación interactiva de estrellas sin selección inicial, un campo de comentario con contador `n/300` y el botón "Confirmar", deshabilitado hasta seleccionar al menos 1 estrella. Si el envío falla, DEBE conservar estrellas y comentario y permitir reintentar. Al confirmar con éxito, el cliente DEBE volver al detalle, que muestra `COMPLETED` y la reseña.

#### Escenario: Nombre del profesional
- DADO una solicitud `ACCEPTED` asignada al profesional "Carlos Gómez"
- CUANDO el cliente abre la pantalla de completar y calificar
- ENTONCES el estado de la pantalla contiene el nombre "Carlos Gómez"

#### Escenario: Botón deshabilitado sin estrellas
- DADO la pantalla recién abierta
- CUANDO se muestra
- ENTONCES "Confirmar" está deshabilitado y ninguna estrella está seleccionada

#### Escenario: Selección de estrellas
- DADO la pantalla recién abierta
- CUANDO el usuario toca la cuarta estrella
- ENTONCES la calificación es 4 y "Confirmar" está habilitado

#### Escenario: Error durante el envío
- DADO que el envío devuelve el error `Network`
- CUANDO el usuario confirma
- ENTONCES se muestra un mensaje de red, se conservan estrellas y comentario y puede reintentar

#### Escenario: Confirmación exitosa
- DADO una calificación de 5 estrellas
- CUANDO el usuario confirma y el envío es exitoso
- ENTONCES vuelve al detalle, que muestra el estado `COMPLETED` y la reseña

### Requirement: Visualización de la reseña [Fase 1]

En el detalle de una solicitud `COMPLETED`, tanto el cliente dueño como el profesional asignado DEBEN ver la reseña (estrellas y comentario, si lo hay).

#### Escenario: El profesional ve su calificación
- DADO un trabajo `COMPLETED` con reseña de 5 estrellas y comentario "Muy prolijo"
- CUANDO el profesional abre el detalle desde "Mis trabajos"
- ENTONCES ve 5 estrellas y "Muy prolijo"

### Requirement: Calificación unidireccional [Fase 1]

El profesional NO DEBE poder calificar al cliente.

#### Escenario: Sin acción de calificar para el profesional
- DADO un trabajo `COMPLETED` en "Mis trabajos"
- CUANDO el profesional abre el detalle
- ENTONCES no existe ninguna acción para calificar

### Requirement: Finalización indivisible entre dispositivos [Fase 2]

Con el backend de Fase 2, completar y calificar DEBE seguir siendo indivisible: si cualquier parte falla, la solicitud, la reseña y la reputación DEBEN quedar sin cambios. Un rechazo por permisos DEBE informarse como `Unauthorized` y una falta de conectividad como `Network`. Dos intentos simultáneos de completar la misma solicitud DEBEN producir un único éxito y una única reseña.

#### Escenario: Fallo parcial [manual]
- DADO que el servicio rechaza guardar la reseña por permisos
- CUANDO el cliente intenta completar
- ENTONCES el resultado es `Unauthorized`
- Y la solicitud sigue `ACCEPTED` y la reputación del profesional no cambia

#### Escenario: Sin conectividad [manual]
- DADO un dispositivo sin conexión, o una escritura que no termina en 15 segundos
- CUANDO el cliente intenta completar
- ENTONCES el resultado es `Network` y la solicitud sigue `ACCEPTED`

#### Escenario: Dos intentos simultáneos [manual]
- DADO una solicitud `ACCEPTED`
- CUANDO el cliente dueño intenta completarla dos veces a la vez desde dos dispositivos
- ENTONCES exactamente un intento tiene éxito y existe una sola reseña
