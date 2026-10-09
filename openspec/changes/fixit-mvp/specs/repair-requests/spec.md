# Delta para Solicitudes de reparación (`repair-requests`)

> Cambio: `fixit-mvp`. Alcance: publicar, validar, listar, ver y cancelar solicitudes, y la máquina de estados de `RepairRequest`, que es la única fuente de verdad sobre qué transiciones son válidas. Estados: `OPEN`, `ACCEPTED`, `COMPLETED`, `CANCELLED`.
>
> Convención de verificación: cada escenario está cubierto por un test JVM o por un ítem del checklist manual. Los escenarios cuyo título termina en `[manual]` se verifican a mano (ver `design.md` §15).

## ADDED Requirements

### Requirement: Crear solicitud [Fase 1]

El cliente DEBE poder publicar una solicitud con exactamente una categoría (`ServiceCategory`), una descripción, entre 1 y 3 fotos y una ubicación con dirección legible. La solicitud DEBE crearse en estado `OPEN`, asociada al usuario autenticado, con la fecha de creación y sin profesional asignado. La descripción DEBE guardarse recortada. Solo los usuarios con rol `CLIENT` PUEDEN crear solicitudes.

#### Escenario: Creación exitosa
- DADO un cliente autenticado, categoría `PLUMBING`, descripción de 40 caracteres, 2 fotos y ubicación válida
- CUANDO publica la solicitud
- ENTONCES la solicitud se crea con `status = OPEN` y sin profesional asignado
- Y aparece en su lista "Mis solicitudes"

#### Escenario: Un profesional no puede crear
- DADO un usuario autenticado con rol `PROFESSIONAL`
- CUANDO intenta publicar una solicitud
- ENTONCES el resultado es `Unauthorized` y no se crea ninguna solicitud

### Requirement: Validación de la solicitud [Fase 1]

El sistema DEBE validar la solicitud antes de crearla, evaluando las reglas en el orden de la tabla y devolviendo solo el primer error. Si hay un error, NO DEBE crearse ninguna solicitud.

| Orden | Regla | Error |
|---|---|---|
| 1 | Categoría seleccionada | `Validation.CategoryRequired` |
| 2 | Descripción recortada no vacía | `Validation.EmptyDescription` |
| 3 | Descripción recortada de al menos 10 caracteres | `Validation.DescriptionTooShort` |
| 4 | Descripción recortada de como máximo 500 caracteres | `Validation.DescriptionTooLong` |
| 5 | Al menos 1 foto | `Validation.PhotosRequired` |
| 6 | Como máximo 3 fotos | `Validation.TooManyPhotos` |
| 7 | Ubicación con coordenadas y dirección no vacía | `Validation.LocationRequired` |
| 8 | Coordenadas válidas: latitud en [-90, 90] y longitud en [-180, 180] | `Validation.InvalidCoordinates` |

#### Escenario: Descripción vacía
- DADO una descripción `"   "`
- CUANDO el cliente intenta publicar
- ENTONCES el resultado es `Validation.EmptyDescription` y no se crea ninguna solicitud

#### Escenario: Descripción fuera de rango
- DADO una descripción de 9 caracteres, o de 501 caracteres
- CUANDO el cliente intenta publicar
- ENTONCES el resultado es `Validation.DescriptionTooShort` para 9 caracteres y `Validation.DescriptionTooLong` para 501

#### Escenario: Límites exactos válidos
- DADO descripciones de exactamente 10 y de exactamente 500 caracteres, y 1 y 3 fotos
- CUANDO se valida cada combinación
- ENTONCES todas son aceptadas

#### Escenario: Cantidad de fotos inválida
- DADO 0 fotos, o 4 fotos
- CUANDO el cliente intenta publicar
- ENTONCES el resultado es `Validation.PhotosRequired` para 0 y `Validation.TooManyPhotos` para 4

#### Escenario: Sin ubicación
- DADO que no hay coordenadas, o la dirección está vacía
- CUANDO el cliente intenta publicar
- ENTONCES el resultado es `Validation.LocationRequired`

#### Escenario: Coordenadas fuera de rango
- DADO una ubicación con latitud 91, o con longitud 181, y dirección no vacía
- CUANDO el cliente intenta publicar
- ENTONCES el resultado es `Validation.InvalidCoordinates` y no se crea ninguna solicitud

#### Escenario: Se informa solo el primer error
- DADO que no se eligió categoría y la descripción está vacía
- CUANDO el cliente intenta publicar
- ENTONCES el resultado es `Validation.CategoryRequired`

### Requirement: Formulario de creación [Fase 1]

La pantalla de creación DEBE ofrecer: selector de categoría de selección única, campo de descripción con contador `n/500`, sección de fotos (agregar y quitar, máximo 3), ubicación (botón "Usar mi ubicación" y dirección editable) y botón "Publicar". Mientras se publica, el botón DEBE estar deshabilitado. Si la publicación falla, el formulario DEBE conservar lo cargado. Al publicar con éxito, el cliente DEBE ver el detalle de la solicitud recién creada.

#### Escenario: Selección única de categoría
- DADO `PLUMBING` seleccionado
- CUANDO el usuario toca `GAS`
- ENTONCES solo `GAS` queda seleccionado

#### Escenario: Contador de descripción [manual]
- DADO una descripción de 120 caracteres
- CUANDO se muestra el formulario
- ENTONCES el contador muestra "120/500"

#### Escenario: Error de validación visible
- DADO que el cliente publica sin fotos
- CUANDO el resultado es `Validation.PhotosRequired`
- ENTONCES el error se muestra en la sección de fotos y el formulario conserva lo cargado

### Requirement: Publicación sin profesionales compatibles [Fase 1]

El sistema NO DEBE verificar antes de publicar si existen profesionales disponibles. Una solicitud válida DEBE publicarse igual y el cliente DEBE ver el mensaje "Esperando que un profesional acepte" mientras la solicitud esté `OPEN`.

#### Escenario: Publicar sin profesionales cercanos
- DADO que no existe ningún profesional con esa categoría y un radio que alcance la ubicación
- CUANDO el cliente publica una solicitud válida
- ENTONCES la solicitud se crea en `OPEN`
- Y el detalle muestra "Esperando que un profesional acepte"

### Requirement: Las solicitudes abiertas no vencen [Fase 1]

Una solicitud `OPEN` NO DEBE vencer ni cambiar de estado por el paso del tiempo. DEBE permanecer `OPEN` hasta que un profesional la acepte o el cliente la cancele.

#### Escenario: Solicitud antigua sigue abierta
- DADO una solicitud `OPEN` creada hace 30 días
- CUANDO un profesional compatible abre su feed
- ENTONCES la solicitud sigue `OPEN` y aparece en el feed

### Requirement: Edición no permitida [Fase 1]

Una solicitud publicada NO DEBE poder editarse. No DEBE existir ninguna acción para modificar categoría, descripción, fotos o ubicación después de crearla.

#### Escenario: Detalle sin edición [manual]
- DADO una solicitud `OPEN` propia
- CUANDO el cliente abre el detalle
- ENTONCES la única acción disponible es "Cancelar solicitud"

### Requirement: Listar mis solicitudes [Fase 1]

La pestaña "Mis solicitudes" DEBE mostrar en tiempo real las solicitudes del cliente autenticado, ordenadas de la más reciente a la más antigua, con categoría, extracto de la descripción, estado y fecha. NO DEBE mostrar solicitudes de otros clientes.

#### Escenario: Lista ordenada
- DADO tres solicitudes del cliente creadas en t1 < t2 < t3
- CUANDO abre "Mis solicitudes"
- ENTONCES el orden mostrado es t3, t2, t1

#### Escenario: Solo solicitudes propias
- DADO solicitudes de otros clientes en el sistema
- CUANDO el cliente abre "Mis solicitudes"
- ENTONCES esas solicitudes no aparecen

#### Escenario: Lista vacía [manual]
- DADO un cliente sin solicitudes
- CUANDO abre "Mis solicitudes"
- ENTONCES se muestra un estado vacío con la acción "Nueva solicitud"

### Requirement: Detalle de solicitud del cliente [Fase 1]

El detalle DEBE mostrar categoría, descripción completa, fotos, dirección, fecha y estado, y DEBE actualizarse en tiempo real. Solo el cliente dueño PUEDE verlo. Según el estado DEBE mostrar:

| Estado | Contenido adicional | Acciones |
|---|---|---|
| `OPEN` | "Esperando que un profesional acepte" | Cancelar solicitud |
| `ACCEPTED` | Profesional asignado (nombre, reputación) y datos de contacto | Llamar, WhatsApp, Marcar como completado, Cancelar solicitud |
| `COMPLETED` | Profesional asignado, sus datos de contacto (ver `contact`) y reseña dejada | Llamar, WhatsApp |
| `CANCELLED` | "Solicitud cancelada"; sin datos del profesional ni de contacto | — |

#### Escenario: Solicitud inexistente
- DADO un identificador de solicitud que no existe
- CUANDO el cliente intenta abrir el detalle
- ENTONCES el resultado es `NotFound` y se ofrece volver

#### Escenario: Cambio en vivo a ACCEPTED
- DADO el detalle abierto de una solicitud `OPEN`
- CUANDO un profesional la acepta
- ENTONCES el detalle pasa a mostrar al profesional y las acciones de contacto sin recargar

#### Escenario: Solicitud de otro cliente
- DADO una solicitud que pertenece a otro cliente
- CUANDO el cliente intenta abrir su detalle
- ENTONCES el resultado es `Unauthorized`

#### Escenario: Detalle de una solicitud cancelada tras ser aceptada
- DADO una solicitud que estuvo `ACCEPTED` y fue cancelada
- CUANDO el cliente abre el detalle
- ENTONCES se muestra "Solicitud cancelada"
- Y no se muestran el nombre, la reputación ni los datos de contacto del profesional
- Y no hay acciones disponibles

### Requirement: Cancelar solicitud [Fase 1]

Solo el cliente dueño DEBE poder cancelar una solicitud, y solo mientras esté `OPEN` o `ACCEPTED`. La cancelación DEBE pedir confirmación. La verificación del estado y el cambio DEBEN ser atómicos respecto de una aceptación concurrente. Al cancelar una solicitud `ACCEPTED`, ni el cliente ni el profesional DEBEN volver a ver los datos de contacto del otro, y la solicitud DEBE seguir asociada al profesional que la había aceptado para que la vea en "Mis trabajos" como "Cancelada por el cliente". Una solicitud cancelada NO DEBE reaparecer en ningún feed ni afectar la reputación del profesional.

#### Escenario: Cancelar una solicitud abierta
- DADO una solicitud `OPEN` propia visible en el feed de profesionales compatibles
- CUANDO el cliente confirma la cancelación
- ENTONCES `status = CANCELLED` y se registra la fecha de cancelación
- Y la solicitud desaparece de esos feeds

#### Escenario: Cancelar una solicitud aceptada
- DADO una solicitud `ACCEPTED` propia asignada al profesional P
- CUANDO el cliente confirma la cancelación
- ENTONCES `status = CANCELLED`
- Y ni el cliente ni P ven los datos de contacto del otro
- Y la solicitud aparece en "Mis trabajos" de P como "Cancelada por el cliente"
- Y no reaparece en ningún feed

#### Escenario: Cancelar no afecta la reputación
- DADO una solicitud `ACCEPTED` asignada a un profesional con suma de calificaciones 8 y 2 reseñas
- CUANDO el cliente la cancela
- ENTONCES no se crea ninguna reseña y el profesional sigue con suma 8 y 2 reseñas

#### Escenario: Descartar la confirmación de cancelación [manual]
- DADO una solicitud `OPEN` o `ACCEPTED` propia
- CUANDO el cliente inicia la cancelación y rechaza la confirmación
- ENTONCES el estado de la solicitud no cambia

#### Escenario: Cancelar una solicitud ajena
- DADO una solicitud `OPEN` de otro cliente, o un usuario con rol `PROFESSIONAL`
- CUANDO se intenta cancelar
- ENTONCES el resultado es `Unauthorized` y el estado no cambia

#### Escenario: Estados terminales
- DADO una solicitud `COMPLETED`, o una `CANCELLED`
- CUANDO el cliente dueño intenta cancelarla
- ENTONCES el resultado es `InvalidTransition(COMPLETED, CANCEL)` o `InvalidTransition(CANCELLED, CANCEL)` respectivamente

#### Escenario: Cancelar antes de que otro acepte
- DADO una solicitud `OPEN`
- CUANDO el cliente la cancela y después un profesional intenta aceptarla
- ENTONCES la aceptación devuelve `InvalidTransition(CANCELLED, ACCEPT)` y el estado sigue `CANCELLED`

#### Escenario: Aceptar antes de que el cliente cancele
- DADO una solicitud `OPEN`
- CUANDO el profesional P la acepta y después el cliente la cancela
- ENTONCES la cancelación es exitosa y el estado final es `CANCELLED`, asociada a P

### Requirement: Máquina de estados [Fase 1]

El sistema DEBE aplicar exactamente la siguiente tabla de transiciones (4 estados × 3 acciones = 12 combinaciones):

| Estado actual | Acción | Actor permitido | Resultado |
|---|---|---|---|
| `OPEN` | `ACCEPT` | Profesional con la categoría de la solicitud | `ACCEPTED` |
| `OPEN` | `CANCEL` | Cliente dueño | `CANCELLED` |
| `OPEN` | `COMPLETE` | — | `InvalidTransition(OPEN, COMPLETE)` |
| `ACCEPTED` | `ACCEPT` | — | `AlreadyTaken` |
| `ACCEPTED` | `CANCEL` | Cliente dueño | `CANCELLED` |
| `ACCEPTED` | `COMPLETE` | Cliente dueño | `COMPLETED` |
| `COMPLETED` | `ACCEPT` / `CANCEL` / `COMPLETE` | — | `InvalidTransition(COMPLETED, acción)` |
| `CANCELLED` | `ACCEPT` / `CANCEL` / `COMPLETE` | — | `InvalidTransition(CANCELLED, acción)` |

`COMPLETED` y `CANCELLED` son estados terminales. Un actor sin permiso DEBE recibir `Unauthorized` antes de evaluarse la validez de la transición, en cualquier estado. `ACCEPT` sobre `ACCEPTED` devuelve `AlreadyTaken` (y no `InvalidTransition`) porque es la carrera esperable entre profesionales. Excepción: si la solicitud ya fue aceptada por **el mismo** profesional que vuelve a aceptarla, la operación es idempotente y devuelve éxito sin cambios (ver `professional-feed`); la verificación la hace el repositorio dentro del bloque atómico, antes de consultar la tabla.

#### Escenario: Transiciones válidas
- DADO una solicitud y un actor autorizado
- CUANDO se aplica la acción
- ENTONCES `(OPEN, ACCEPT)` resulta en `ACCEPTED`, `(OPEN, CANCEL)` en `CANCELLED`, `(ACCEPTED, CANCEL)` en `CANCELLED` y `(ACCEPTED, COMPLETE)` en `COMPLETED`

#### Escenario: Transiciones inválidas desde estados no terminales
- DADO una solicitud `OPEN`, o una `ACCEPTED`
- CUANDO se aplica `COMPLETE` a la `OPEN`, o `ACCEPT` a la `ACCEPTED`
- ENTONCES los resultados son `InvalidTransition(OPEN, COMPLETE)` y `AlreadyTaken` respectivamente

#### Escenario: Estados terminales rechazan toda acción
- DADO una solicitud `COMPLETED` o `CANCELLED`
- CUANDO se aplica `ACCEPT`, `CANCEL` o `COMPLETE` con un actor autorizado
- ENTONCES el resultado es `InvalidTransition(estado, acción)` para las 6 combinaciones

#### Escenario: La autorización precede a la transición
- DADO una solicitud `COMPLETED` y un cliente que no es su dueño
- CUANDO intenta cancelarla
- ENTONCES el resultado es `Unauthorized` y no `InvalidTransition`

### Requirement: Sin límite de solicitudes abiertas [Fase 1]

El sistema NO DEBE limitar la cantidad de solicitudes `OPEN` por cliente.

#### Escenario: Múltiples abiertas
- DADO un cliente con 5 solicitudes `OPEN`
- CUANDO publica una sexta solicitud válida
- ENTONCES se crea correctamente

### Requirement: Solicitudes persistentes entre dispositivos [Fase 2]

Con el backend de Fase 2, las solicitudes DEBEN persistir y verse desde cualquier dispositivo en el que el dueño inicie sesión, y los profesionales DEBEN verlas en tiempo real. Si la creación falla por falta de conectividad, o si la escritura no termina en 15 segundos o la subida de una foto no termina en 30 segundos, DEBE informarse `Network`, NO DEBE quedar una solicitud a medio crear y el formulario DEBE conservar lo cargado.

#### Escenario: Solicitud visible en otro dispositivo [manual]
- DADO una solicitud publicada por un cliente desde un dispositivo
- CUANDO el cliente inicia sesión en otro dispositivo
- ENTONCES la solicitud aparece en "Mis solicitudes" con su estado actual

#### Escenario: Fallo de red al publicar [manual]
- DADO un dispositivo sin conexión
- CUANDO el cliente publica una solicitud válida
- ENTONCES el resultado es `Network`, como máximo a los 30 segundos (límite de la subida de la primera foto)
- Y no se crea ninguna solicitud ni queda una a medio crear
- Y el formulario conserva lo cargado para reintentar
