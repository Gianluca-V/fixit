# Delta para Feed del profesional (`professional-feed`)

> Cambio: `fixit-mvp`. Alcance: feed en tiempo real de solicitudes compatibles, detalle, aceptación atómica (el primero gana) y la lista "Mis trabajos". Estados y errores según `repair-requests`.
>
> Convención de verificación: cada escenario está cubierto por un test JVM o por un ítem del checklist manual. Los escenarios cuyo título termina en `[manual]` se verifican a mano (ver `design.md` §15). El dominio devuelve números (distancia en km); el formato visual con el idioma del dispositivo se verifica a mano.

## ADDED Requirements

### Requirement: Matching por categoría y distancia [Fase 1]

Una solicitud DEBE ser visible para un profesional si y solo si: (1) su estado es `OPEN`, (2) su categoría está entre las categorías del profesional y (3) la distancia Haversine entre el punto base del profesional y la ubicación de la solicitud es menor o igual que el radio del profesional. El límite es inclusivo y se evalúa con la función pura `Matching.isWithin(distanceKm, radiusKm)` sobre la distancia sin redondear. El filtro por distancia exacta DEBE aplicarse en todas las fases.

#### Escenario: Coincide
- DADO un profesional con categoría `PLUMBING`, punto base (-34.80, -58.27) y radio 10 km
- Y una solicitud `OPEN` de `PLUMBING` a unos 3 km
- CUANDO se evalúa la visibilidad
- ENTONCES la solicitud es visible

#### Escenario: Categoría distinta
- DADO la misma solicitud pero de categoría `GAS`
- CUANDO se evalúa la visibilidad
- ENTONCES la solicitud no es visible

#### Escenario: Fuera de radio
- DADO una solicitud `OPEN` de `PLUMBING` a unos 12 km
- CUANDO se evalúa la visibilidad
- ENTONCES la solicitud no es visible

#### Escenario: Solicitud no abierta
- DADO una solicitud de `PLUMBING` a 1 km en estado `ACCEPTED`, `COMPLETED` o `CANCELLED`
- CUANDO se evalúa la visibilidad
- ENTONCES la solicitud no es visible

#### Escenario: Exactamente en el radio
- DADO distancias precalculadas de 10.0 km y de 10.01 km, y un radio de 10 km
- CUANDO se evalúa `Matching.isWithin(distanceKm, 10)` con cada una
- ENTONCES `isWithin(10.0, 10)` es verdadero (el límite es inclusivo) y `isWithin(10.01, 10)` es falso

### Requirement: Feed en tiempo real [Fase 1]

El feed DEBE actualizarse sin recargar cuando se crea, se acepta o se cancela una solicitud relevante. Los ítems DEBEN ordenarse de la más reciente a la más antigua e incluir la distancia en km redondeada a 0.1 km (número; la interfaz la muestra con el formato del idioma del dispositivo). El feed NO DEBE exponer teléfono ni nombre del cliente. Si falla la carga, DEBE mostrar el error con la acción "Reintentar".

#### Escenario: Nueva solicitud aparece
- DADO el feed abierto de un profesional con categoría `ELECTRICITY` y radio 10 km
- CUANDO un cliente publica una solicitud `ELECTRICITY` a 2 km
- ENTONCES el feed la incluye sin recargar, con `distanceKm = 2.0`

#### Escenario: Solicitud aceptada por otro desaparece
- DADO una solicitud visible en el feed de los profesionales A y B
- CUANDO A la acepta
- ENTONCES la solicitud desaparece del feed de B

#### Escenario: Solicitud cancelada desaparece
- DADO una solicitud visible en el feed
- CUANDO el cliente la cancela
- ENTONCES deja de aparecer en el feed

#### Escenario: Feed vacío [manual]
- DADO que ninguna solicitud coincide
- CUANDO el profesional abre el feed
- ENTONCES se muestra un estado vacío que explica que se filtra por sus categorías y su radio

#### Escenario: Error al cargar el feed
- DADO que la carga falla con el error `Network`
- CUANDO el profesional abre el feed
- ENTONCES se muestra el error y la acción "Reintentar"

### Requirement: El feed responde a cambios en el perfil [Fase 1]

Si el profesional edita sus categorías, su punto base o su radio, el feed DEBE actualizarse con los nuevos valores sin recargar. Esos cambios NO DEBEN afectar a los trabajos ya `ACCEPTED`, que se conservan y siguen mostrándose en "Mis trabajos".

#### Escenario: Se agrega una categoría
- DADO un profesional con categoría `PLUMBING` y una solicitud `OPEN` de `GAS` dentro de su radio que no ve
- CUANDO agrega `GAS` a sus categorías y guarda
- ENTONCES la solicitud aparece en su feed

#### Escenario: Se reduce el radio
- DADO una solicitud `OPEN` compatible a 8 km visible con radio 10 km
- CUANDO el profesional cambia el radio a 5 km y guarda
- ENTONCES la solicitud desaparece de su feed

#### Escenario: Los trabajos aceptados no se ven afectados
- DADO un profesional con un trabajo `ACCEPTED` de `PLUMBING` ubicado a 8 km
- CUANDO quita `PLUMBING` de sus categorías y reduce el radio a 5 km
- ENTONCES el trabajo sigue `ACCEPTED` en "Mis trabajos" con los datos de contacto del cliente

### Requirement: Detalle de solicitud para el profesional [Fase 1]

El profesional DEBE poder abrir una solicitud del feed o de "Mis trabajos" y ver categoría, descripción, fotos, dirección tal como la cargó el cliente, distancia y fecha. El contenido y las acciones dependen del estado de la solicitud y de quién la mira:

| Estado | Quién la mira | Contenido adicional | Acciones |
|---|---|---|---|
| `OPEN` | Cualquier profesional | Sin nombre ni teléfono del cliente | Aceptar trabajo |
| `ACCEPTED` o `COMPLETED` | Profesional asignado | Contacto del cliente (ver `contact`); en `COMPLETED`, la reseña | Llamar, WhatsApp |
| `CANCELLED` | Profesional que la había aceptado | "Cancelada por el cliente"; sin contacto | — |
| `ACCEPTED`, `COMPLETED` o `CANCELLED` | Cualquier otro profesional | Aviso "Esta solicitud ya no está disponible"; sin contacto | — |

El botón "Aceptar trabajo" DEBE estar visible solo cuando la solicitud está `OPEN`. La aceptación DEBE pedir confirmación. El detalle NO DEBE mostrar teléfono ni nombre del cliente mientras la solicitud esté `OPEN`. El detalle se actualiza en vivo; aun así, el resultado `AlreadyTaken` sigue siendo alcanzable (por ejemplo, con el diálogo de confirmación ya abierto o por la latencia de la Fase 2) y se maneja según "Resultado de la aceptación en la pantalla".

#### Escenario: Detalle desde el feed
- DADO una solicitud `OPEN` compatible
- CUANDO el profesional toca su tarjeta
- ENTONCES ve categoría, descripción, fotos, dirección, distancia y fecha, y el botón "Aceptar trabajo"
- Y no ve teléfono ni nombre del cliente

#### Escenario: Descartar la confirmación de aceptación [manual]
- DADO el detalle de una solicitud `OPEN`
- CUANDO el profesional toca "Aceptar trabajo" y rechaza la confirmación
- ENTONCES la solicitud sigue `OPEN` y sin profesional asignado

#### Escenario: Solicitud tomada por otro profesional
- DADO una solicitud `ACCEPTED` por el profesional A
- CUANDO el profesional B abre su detalle
- ENTONCES ve el aviso "Esta solicitud ya no está disponible"
- Y no ve el botón "Aceptar trabajo" ni datos de contacto

#### Escenario: Solicitud cancelada que no era propia
- DADO una solicitud cancelada por el cliente mientras estaba `OPEN`
- CUANDO un profesional abre su detalle
- ENTONCES ve el aviso "Esta solicitud ya no está disponible"
- Y no ve el botón "Aceptar trabajo"

#### Escenario: Solicitud propia aceptada
- DADO una solicitud `ACCEPTED` asignada al profesional A
- CUANDO A abre su detalle desde "Mis trabajos"
- ENTONCES ve los datos de contacto del cliente
- Y no ve el botón "Aceptar trabajo" ni el aviso de no disponible

#### Escenario: Otro profesional acepta mientras se mira el detalle
- DADO el detalle abierto de una solicitud `OPEN` en el dispositivo del profesional B
- CUANDO el profesional A la acepta
- ENTONCES el detalle de B pasa a mostrar el aviso "Esta solicitud ya no está disponible" sin recargar
- Y deja de mostrar el botón "Aceptar trabajo"

### Requirement: Aceptación atómica, el primero gana [Fase 1]

Aceptar una solicitud DEBE ser una operación atómica: verificar el estado, verificar la categoría del profesional y asignarle la solicitud (`ACCEPTED`, con profesional y fecha de aceptación) como un único paso indivisible. Si dos o más profesionales aceptan a la vez, exactamente uno DEBE tener éxito. Aceptar es idempotente para el mismo profesional: si la solicitud ya está `ACCEPTED` y asignada a quien vuelve a aceptarla (por ejemplo, por un doble toque o un reintento), el resultado es exitoso y nada cambia. Solo un profesional con la categoría de la solicitud PUEDE aceptarla; un usuario `CLIENT` o un profesional sin esa categoría DEBE recibir `Unauthorized`. El sistema NO DEBE volver a verificar la distancia al aceptar: el radio solo filtra el feed.

#### Escenario: Aceptación exitosa
- DADO una solicitud `OPEN` compatible
- CUANDO el profesional A la acepta
- ENTONCES el resultado es exitoso, `status = ACCEPTED` y el profesional asignado es A

#### Escenario: Segundo en aceptar
- DADO una solicitud ya aceptada por A
- CUANDO B intenta aceptarla
- ENTONCES el resultado es `AlreadyTaken` y el profesional asignado sigue siendo A

#### Escenario: Aceptar de nuevo el mismo trabajo
- DADO una solicitud ya aceptada por A
- CUANDO A vuelve a aceptarla
- ENTONCES el resultado es exitoso, la solicitud sigue `ACCEPTED` asignada a A y la fecha de aceptación no cambia

#### Escenario: Concurrencia real
- DADO una solicitud `OPEN`
- CUANDO 10 profesionales distintos intentan aceptarla en paralelo
- ENTONCES exactamente uno obtiene éxito y nueve obtienen `AlreadyTaken`

#### Escenario: Aceptar una solicitud cancelada
- DADO una solicitud `CANCELLED`
- CUANDO un profesional compatible intenta aceptarla
- ENTONCES el resultado es `InvalidTransition(CANCELLED, ACCEPT)`

#### Escenario: Categoría no habilitada
- DADO un profesional sin la categoría de la solicitud
- CUANDO intenta aceptarla
- ENTONCES el resultado es `Unauthorized` y la solicitud sigue `OPEN`

#### Escenario: Cliente intenta aceptar
- DADO un usuario con rol `CLIENT`
- CUANDO intenta aceptar una solicitud
- ENTONCES el resultado es `Unauthorized`

#### Escenario: Sin verificación de distancia al aceptar
- DADO una solicitud `OPEN` de la categoría del profesional que quedó fuera de su radio después de que abrió el detalle
- CUANDO el profesional la acepta
- ENTONCES la aceptación es exitosa

### Requirement: Resultado de la aceptación en la pantalla [Fase 1]

Si el profesional pierde la carrera (`AlreadyTaken`), DEBE mostrarse el mensaje "Otro profesional ya tomó este trabajo" y, al confirmarlo, DEBE volverse al Feed. Si la solicitud fue cancelada mientras tanto (`InvalidTransition`), DEBE mostrarse el mensaje "La solicitud ya no está disponible" y, al confirmarlo, DEBE volverse al Feed. Si gana, DEBE ver el trabajo en "Mis trabajos". Estos mensajes se prueban a nivel de caso de uso y de ViewModel con un repositorio de prueba que devuelve el error, porque con el detalle en vivo solo son alcanzables en una carrera real.

#### Escenario: Perder la carrera
- DADO el detalle abierto de una solicitud que otro profesional acaba de aceptar
- CUANDO el profesional confirma "Aceptar trabajo" y el resultado es `AlreadyTaken`
- ENTONCES se muestra el mensaje "Otro profesional ya tomó este trabajo"
- Y al confirmarlo se vuelve al Feed

#### Escenario: Solicitud cancelada mientras se miraba
- DADO el detalle abierto de una solicitud que el cliente acaba de cancelar
- CUANDO el profesional confirma "Aceptar trabajo"
- ENTONCES se muestra el mensaje "La solicitud ya no está disponible"
- Y al confirmarlo se vuelve al Feed

#### Escenario: Ganar la aceptación
- DADO el detalle abierto de una solicitud `OPEN` compatible
- CUANDO el profesional confirma y el resultado es exitoso
- ENTONCES ve el trabajo en "Mis trabajos" con los datos de contacto del cliente

### Requirement: Sin límite de trabajos [Fase 1]

El sistema NO DEBE limitar la cantidad de trabajos `ACCEPTED` simultáneos de un profesional.

#### Escenario: Varios aceptados
- DADO un profesional con 3 trabajos `ACCEPTED`
- CUANDO acepta otra solicitud compatible
- ENTONCES la aceptación es exitosa

### Requirement: Mis trabajos [Fase 1]

La pestaña "Mis trabajos" DEBE listar en tiempo real las solicitudes asignadas al profesional autenticado: `ACCEPTED`, `COMPLETED` y las `CANCELLED` que habían sido aceptadas por él. DEBE ordenarlas con las `ACCEPTED` primero y luego por fecha de aceptación descendente. Cada ítem de la lista DEBE mostrar el estado del trabajo; la calificación recibida NO se muestra en la lista, sino en el detalle del trabajo (ver `job-completion`). Una solicitud cancelada por el cliente DEBE mostrarse con la etiqueta "Cancelada por el cliente" y sin datos de contacto del cliente. Al abrir un trabajo `ACCEPTED` o `COMPLETED`, el detalle DEBE mostrar los datos de contacto del cliente (ver `contact`).

#### Escenario: Trabajo aceptado aparece
- DADO que el profesional aceptó una solicitud
- CUANDO abre "Mis trabajos"
- ENTONCES el trabajo aparece con estado `ACCEPTED`

#### Escenario: Trabajo completado
- DADO un trabajo `ACCEPTED` del profesional
- CUANDO el cliente lo marca como completado y califica
- ENTONCES el ítem pasa a mostrar el estado `COMPLETED`

#### Escenario: Trabajo cancelado por el cliente
- DADO un trabajo `ACCEPTED` del profesional
- CUANDO el cliente cancela la solicitud
- ENTONCES el ítem pasa a mostrarse como "Cancelada por el cliente" sin recargar
- Y su detalle no muestra nombre ni teléfono del cliente

#### Escenario: Orden de la lista
- DADO un trabajo `COMPLETED` aceptado en t1, otro `ACCEPTED` aceptado en t2 y otro `ACCEPTED` aceptado en t3 (t1 < t2 < t3)
- CUANDO el profesional abre "Mis trabajos"
- ENTONCES el orden es: el aceptado en t3, el aceptado en t2 y por último el `COMPLETED`

### Requirement: Feed en tiempo real entre dispositivos [Fase 2]

Con el backend de Fase 2, el feed DEBE reflejar solicitudes creadas, aceptadas o canceladas desde otros dispositivos sin recargar, con el mismo resultado que en Fase 1 para los mismos datos.

#### Escenario: Solicitud publicada desde otro dispositivo [manual]
- DADO el feed abierto de un profesional compatible en un dispositivo
- CUANDO un cliente publica una solicitud compatible desde otro dispositivo
- ENTONCES la solicitud aparece en el feed sin recargar

### Requirement: Aceptación concurrente entre dispositivos [Fase 2]

Con el backend de Fase 2, cuando dos profesionales aceptan casi a la vez desde dispositivos distintos, exactamente uno DEBE tener éxito y el otro DEBE recibir `AlreadyTaken`. Un profesional NO DEBE poder sobrescribir una solicitud que ya no está `OPEN` por ninguna vía; ese intento DEBE rechazarse con `Unauthorized`.

#### Escenario: Dos dispositivos aceptan [manual]
- DADO dos profesionales compatibles que aceptan la misma solicitud casi simultáneamente
- CUANDO ambas aceptaciones llegan al servicio
- ENTONCES una tiene éxito y la otra devuelve `AlreadyTaken`

#### Escenario: Sobrescritura rechazada [manual]
- DADO una solicitud `ACCEPTED` por A
- CUANDO B intenta cambiarla directamente a su nombre por una vía distinta de aceptar
- ENTONCES el resultado es `Unauthorized` y el profesional asignado sigue siendo A
