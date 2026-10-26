# Delta para Notificaciones (`notifications`)

> Cambio: `fixit-mvp`. Alcance: avisos push opcionales [Fase 4] enviados con FCM por un Cloudflare Worker. Tres eventos: solicitud aceptada (aviso al cliente), nueva solicitud `OPEN` (aviso a profesionales compatibles) y cancelación de un trabajo `ACCEPTED` (aviso al profesional asignado). Quedan fuera: avisos de trabajo completado, reseña recibida o recordatorios, bandeja de notificaciones dentro de la app y preferencias por usuario.
>
> Restricción dura: el proyecto NO DEBE requerir tarjeta de crédito. Los avisos NO usan Cloud Functions ni el plan Blaze; los envía un Worker de Cloudflare en el plan gratuito.
>
> Esta capacidad es dueña de todo el comportamiento de suscripción a temas. `auth` (iniciar y cerrar sesión) y `profiles` (editar categorías) solo la referencian.
>
> Temas de FCM: `user_{uid}` (avisos a una persona) y `category_{CATEGORY}` (nuevas solicitudes de una categoría).
>
> Convención de verificación: cada escenario está cubierto por (a) un test JVM para la lógica de Android (decorador y lógica pura en `:core:data`, `:app`, con dobles escritos a mano), (b) un test `vitest` en `worker/` para la lógica que vive solo en el Worker (contenido, prioridad y vigencia del payload, verificaciones de cada evento, caché de claves), o (c) un ítem del checklist manual. Los escenarios cuyo título termina en `[manual]` se verifican a mano (ver `design.md` §15): la firma real del ID token, la lectura REST de Firestore, el envío y la entrega real de FCM y la presentación en el dispositivo. Los escenarios de "Verificaciones del Worker" llevan `[manual]` por ejercitar el Worker desplegado (o `wrangler dev`) y, además, su lógica tiene tests `vitest`.

## ADDED Requirements

### Requirement: Permiso de notificaciones [Fase 4]

En Android 13 o superior, la app DEBE solicitar el permiso `POST_NOTIFICATIONS` en tiempo de ejecución y DEBERÍA hacerlo después de iniciar sesión. Si el permiso se deniega, la app DEBE seguir funcionando con normalidad (crear, aceptar, cancelar, completar) y NO DEBE mostrar avisos. Denegar el permiso NO DEBE considerarse un error ni bloquear ninguna pantalla. En versiones anteriores a Android 13 NO DEBE pedirse ningún permiso.

#### Escenario: Permiso concedido [manual]
- DADO un dispositivo con Android 13 o superior y una sesión recién iniciada
- CUANDO la persona concede el permiso de notificaciones
- ENTONCES los avisos de los eventos de su rol se muestran

#### Escenario: Permiso denegado [manual]
- DADO un dispositivo con Android 13 o superior
- CUANDO la persona deniega el permiso de notificaciones
- ENTONCES la app sigue funcionando y no se muestra ningún aviso
- Y publicar, aceptar y cancelar funcionan igual

#### Escenario: Versión anterior a Android 13 [manual]
- DADO un dispositivo con Android 12 o inferior
- CUANDO la persona inicia sesión
- ENTONCES no se muestra ningún diálogo de permiso

### Requirement: Ciclo de vida de las suscripciones [Fase 4]

El conjunto de temas a los que debe estar suscrito un dispositivo DEBE ser una función pura de la sesión: para un `CLIENT`, `{user_{uid}}`; para un `PROFESSIONAL`, `{user_{uid}}` más `category_{C}` por cada categoría `C` de su perfil; sin sesión, el conjunto vacío.

- Al iniciar sesión (o al restaurarse una sesión al abrir la app), el dispositivo DEBE suscribirse a los temas de ese conjunto.
- Al editar sus categorías, el profesional DEBE suscribirse a los temas de las categorías agregadas y desuscribirse de los de las categorías quitadas, sin tocar `user_{uid}` ni los temas que se mantienen.
- Al cerrar sesión, el dispositivo DEBE desuscribirse de todos los temas (`user_{uid}` y todos los `category_*`) de esa cuenta.
- Cerrar o matar la app NO DEBE desuscribir ningún tema.
- Al cerrar sesión, la app DEBE retirar de la bandeja del sistema los avisos que siguen visibles, para que no los vea la persona que inicie sesión después en el mismo dispositivo. Esta retirada DEBE ocurrir de inmediato al cerrar sesión, sin esperar a que terminen operaciones de suscripción pendientes.
- Cada **reconciliación** DEBE: suscribirse a todos los temas del conjunto deseado, desuscribirse de cada `category_{C}` de las ocho categorías que no esté en el conjunto deseado y desuscribirse de cada tema `user_*` registrado en el dispositivo que no esté en el conjunto deseado. Las operaciones de temas de FCM son idempotentes, así que repetir una suscripción o una desuscripción ya aplicada es seguro; por eso el dispositivo NO registra qué categorías aplicó.
- Hay una reconciliación al arrancar, con cada emisión de sesión que cambie el usuario o sus categorías (las que solo cambian el nombre, la reputación o el radio no cuentan) y cuando FCM entrega un token nuevo. Una emisión de determinación (`Loading`) NO DEBE suscribir nada.
- El dispositivo DEBE registrar de forma persistente cada tema `user_{uid}` **antes** de suscribirse a él, y DEBE borrarlo del registro solo cuando su desuscripción termina con éxito, de modo que una desuscripción fallida de una cuenta anterior se repita en cada reconciliación hasta que tenga éxito y NO DEBEN quedar suscripciones de una cuenta anterior.
- Si alguna operación de una reconciliación falla (por ejemplo, sin conexión o por tiempo agotado), la reconciliación completa DEBE repetirse mientras la app siga abierta, con una espera creciente (30 s, que se duplica en cada intento hasta un máximo de 10 min), sin esperar a otro cambio de sesión. Una reconciliación nueva (por un cambio de sesión o un token nuevo) reemplaza a la que estaba en curso o esperando.
- Las operaciones sobre los temas DEBEN aplicarse de forma serializada, y un cambio de sesión nuevo (por ejemplo, cerrar sesión) NO DEBE quedar en cola detrás de operaciones lentas: interrumpe las que están en curso.
- Un fallo al suscribirse o desuscribirse NO DEBE hacer fallar iniciar sesión, cerrar sesión ni guardar el perfil.

#### Escenario: Temas de un cliente
- DADO un cliente con `uid = c1`
- CUANDO se calcula el conjunto de temas
- ENTONCES es `{user_c1}`

#### Escenario: Temas de un profesional
- DADO un profesional con `uid = p1` y categorías `{PLUMBING, GAS}`
- CUANDO se calcula el conjunto de temas
- ENTONCES es `{user_p1, category_PLUMBING, category_GAS}`

#### Escenario: Temas sin sesión
- DADO que no hay sesión
- CUANDO se calcula el conjunto de temas
- ENTONCES es el conjunto vacío

#### Escenario: Suscripción al iniciar sesión
- DADO un profesional `p1` con categorías `{PLUMBING}` y un suscriptor de prueba sin temas
- CUANDO inicia sesión
- ENTONCES el suscriptor queda suscrito a `user_p1` y `category_PLUMBING`

#### Escenario: Edición de categorías actualiza los temas
- DADO un profesional suscrito a `user_p1`, `category_PLUMBING` y `category_GAS`
- CUANDO cambia sus categorías a `{GAS, ELECTRICITY}` y guarda
- ENTONCES se suscribe a `category_ELECTRICITY` y se desuscribe de `category_PLUMBING`
- Y sigue suscrito a `user_p1` y `category_GAS`

#### Escenario: Edición que no cambia categorías
- DADO un profesional suscrito a `user_p1` y `category_GAS`
- CUANDO edita solo su radio y guarda
- ENTONCES no se realiza ninguna suscripción ni desuscripción

#### Escenario: Cerrar sesión desuscribe todo
- DADO un profesional suscrito a `user_p1`, `category_PLUMBING` y `category_GAS`
- CUANDO cierra sesión
- ENTONCES el suscriptor queda sin ninguno de esos temas

#### Escenario: Cerrar la app no desuscribe [manual]
- DADO un profesional suscrito a `user_p1` y `category_GAS`
- CUANDO el proceso de la app se cierra sin cerrar sesión
- ENTONCES el dispositivo conserva ambos temas y se siguen recibiendo avisos

#### Escenario: Sin avisos tras cerrar sesión [manual]
- DADO una cuenta que cerró sesión en un dispositivo
- CUANDO ocurre un evento que la alcanzaría (por ejemplo, una nueva solicitud de su categoría)
- ENTONCES ese dispositivo no muestra ningún aviso
- Y la bandeja del sistema no conserva avisos anteriores de esa cuenta

#### Escenario: Falla de suscripción no bloquea la operación
- DADO un suscriptor de prueba que falla al suscribirse
- CUANDO la persona inicia sesión o guarda sus categorías
- ENTONCES la operación termina con éxito y la falla no llega a la capa de presentación

#### Escenario: Operación fallida se reintenta con la app abierta
- DADO un suscriptor de prueba que falla al suscribirse a `category_GAS` durante el inicio de sesión de un profesional con categorías `{GAS, PLUMBING}`
- CUANDO el suscriptor se recupera y pasan 30 segundos (tiempo virtual) sin ninguna emisión de sesión nueva
- ENTONCES el suscriptor queda suscrito a `user_p1`, `category_GAS` y `category_PLUMBING`

#### Escenario: Desuscripción fallida de una cuenta anterior se repara con la cuenta siguiente
- DADO un cliente `c1` que cerró sesión con el suscriptor de prueba sin conexión, de modo que `user_c1` sigue suscrito y registrado
- CUANDO el suscriptor se recupera y el profesional `p1` inicia sesión en el mismo dispositivo
- ENTONCES el suscriptor queda sin `user_c1` y suscrito a los temas de `p1`
- Y `user_c1` ya no figura en el registro del dispositivo

#### Escenario: Desuscripción fallida al cerrar sesión se repara al arrancar
- DADO un profesional suscrito a `user_p1` y `category_GAS` cuyo cierre de sesión falló al desuscribirse (suscriptor de prueba sin conexión), con el estado de temas persistido de prueba
- CUANDO la app arranca de nuevo sin sesión
- ENTONCES se desuscribe de `user_p1` y `category_GAS`

#### Escenario: Cerrar sesión retira los avisos de la bandeja
- DADO avisos de la cuenta visibles en la bandeja (un gestor de avisos de prueba)
- CUANDO la sesión pasa a sin sesión
- ENTONCES se cancelan todos los avisos de la app

#### Escenario: Token nuevo con la sesión en determinación
- DADO un profesional `p1` con categorías `{GAS}`, los temas `user_p1` y `category_GAS` ya aplicados y la sesión en determinación (`Loading`)
- CUANDO FCM entrega un token nuevo
- ENTONCES no se realiza ninguna suscripción mientras la sesión sigue en determinación
- Y cuando la sesión pasa a `SignedIn(p1)` el suscriptor de prueba recibe la suscripción a `user_p1` y `category_GAS`, aunque ya estuviera suscrito a ellos antes del token

#### Escenario: Cerrar sesión con operaciones pendientes cancela la bandeja de inmediato
- DADO un profesional con operaciones de suscripción en curso que no terminan (suscriptor de prueba bloqueado) y un gestor de avisos de prueba
- CUANDO la sesión pasa a sin sesión
- ENTONCES se cancelan todos los avisos de la bandeja sin esperar a que las operaciones pendientes terminen
- Y después se desuscribe de `user_p1` y de todas las categorías

#### Escenario: Suscripción con tiempo agotado se desuscribe al cerrar sesión
- DADO un suscriptor de prueba cuya suscripción a `category_GAS` no termina (tiempo agotado, en tiempo virtual) y un profesional que inicia sesión con categorías `{GAS}`
- CUANDO se agota el tiempo de la operación y luego el profesional cierra sesión
- ENTONCES el suscriptor recibe la desuscripción de `category_GAS` y de `user_p1`
- Y la operación siguiente a la que agotó el tiempo, en la misma reconciliación, se aplicó igual

### Requirement: Aviso de solicitud aceptada [Fase 4]

Cuando un profesional acepta una solicitud, el cliente dueño DEBE recibir un aviso en su tema `user_{clientId}`. Tras una aceptación exitosa, la app DEBE pedir al Worker el envío incluyendo el ID token de Firebase y el `requestId`. El dispositivo del cliente DEBE mostrar como máximo un aviso visible por aceptación efectiva: la app solicita el envío una vez por aceptación, y si dos toques simultáneos del mismo profesional generan dos pedidos, el segundo aviso reemplaza al primero en la bandeja (misma etiqueta y sin volver a sonar). Una aceptación idempotente del mismo profesional (ver `professional-feed`) NO DEBE generar un segundo pedido, salvo que la lectura previa con la que se detecta se agote o falle (ver "Avisos de mejor esfuerzo"): en ese caso se trata como una aceptación nueva y el pedido duplicado se resuelve en el dispositivo por reemplazo del aviso.

#### Escenario: El cliente recibe el aviso al aceptar [manual]
- DADO un cliente con una solicitud `OPEN` y su dispositivo con sesión iniciada
- CUANDO un profesional compatible la acepta
- ENTONCES el cliente recibe un aviso de solicitud aceptada

#### Escenario: Aceptar solicita el envío una vez
- DADO un decorador del repositorio de solicitudes con un cliente del Worker de prueba y una solicitud `OPEN`
- CUANDO un profesional la acepta con éxito
- ENTONCES el cliente del Worker recibe exactamente 1 pedido de tipo aceptación con el `requestId` y el ID token

#### Escenario: Aceptación rechazada no envía aviso
- DADO una solicitud que otro profesional ya aceptó
- CUANDO un segundo profesional intenta aceptarla y recibe `AlreadyTaken`
- ENTONCES el cliente del Worker no recibe ningún pedido

#### Escenario: Aceptación repetida no duplica el aviso
- DADO una solicitud ya `ACCEPTED` por el profesional A
- CUANDO el profesional A la acepta de nuevo y la operación es idempotente
- ENTONCES el cliente del Worker no recibe un nuevo pedido

#### Escenario: Aviso duplicado se reemplaza [manual]
- DADO un aviso de solicitud aceptada visible en la bandeja del cliente
- CUANDO llega un segundo aviso de la misma aceptación (por ejemplo, por dos toques simultáneos en "Aceptar" del profesional)
- ENTONCES la bandeja muestra un solo aviso, que reemplaza al anterior
- Y el segundo no vuelve a sonar

### Requirement: Aviso de nueva solicitud [Fase 4]

Cuando un cliente publica una solicitud, el Worker DEBE enviar un mensaje de datos (sin bloque de notificación visible) al tema `category_{CATEGORY}` de la categoría de la solicitud. Tras una creación exitosa, la app DEBE pedir al Worker el envío incluyendo el ID token y el `requestId`. El mensaje de nueva solicitud DEBE enviarse con prioridad normal y vigencia (TTL) de 1 hora, porque la mayoría de los dispositivos lo descarta por distancia y un aviso con más de 1 hora ya no es útil; los mensajes de aceptación y de cancelación DEBEN enviarse con prioridad alta y vigencia de 24 horas. El dispositivo del profesional DEBE decidir si muestra el aviso aplicando el filtro de distancia (ver "Filtro de distancia en el dispositivo"). Un cliente NO DEBE recibir avisos de nuevas solicitudes.

#### Escenario: Crear solicita el envío una vez
- DADO un decorador del repositorio de solicitudes con un cliente del Worker de prueba
- CUANDO un cliente crea una solicitud con éxito
- ENTONCES el cliente del Worker recibe exactamente 1 pedido de tipo nueva solicitud con el `requestId` y el ID token

#### Escenario: Creación fallida no envía aviso
- DADO un repositorio cuya creación falla con un error
- CUANDO un cliente intenta publicar
- ENTONCES el cliente del Worker no recibe ningún pedido

#### Escenario: Prioridad y vigencia por tipo de evento
- DADO los mensajes construidos para nueva solicitud, aceptación y cancelación
- CUANDO se inspecciona su configuración de entrega
- ENTONCES el de nueva solicitud tiene prioridad normal y vigencia de `3600s`
- Y los de aceptación y de cancelación tienen prioridad alta y vigencia de `86400s`
- Y ninguno incluye bloque de notificación

#### Escenario: Solo los profesionales de la categoría reciben el aviso [manual]
- DADO un profesional de `PLUMBING` y otro de `GAS`, ambos con la solicitud dentro de su radio
- CUANDO un cliente publica una solicitud de `PLUMBING`
- ENTONCES solo el profesional de `PLUMBING` recibe el aviso

#### Escenario: Un cliente no recibe avisos de nuevas solicitudes [manual]
- DADO un cliente con sesión iniciada
- CUANDO otro cliente publica una solicitud
- ENTONCES el primer cliente no recibe ningún aviso

### Requirement: Aviso de cancelación de un trabajo aceptado [Fase 4]

Cuando el cliente dueño cancela una solicitud en estado `ACCEPTED`, el profesional asignado DEBE recibir un aviso en su tema `user_{professionalId}`. Tras la cancelación exitosa, la app DEBE pedir al Worker el envío incluyendo el ID token y el `requestId`, solo si la solicitud estaba `ACCEPTED` y tenía profesional asignado. Cancelar una solicitud `OPEN` NO DEBE generar ningún aviso.

#### Escenario: El profesional asignado recibe el aviso [manual]
- DADO una solicitud `ACCEPTED` por el profesional P
- CUANDO el cliente dueño la cancela
- ENTONCES P recibe un aviso de trabajo cancelado

#### Escenario: Cancelar un ACCEPTED solicita el envío
- DADO un decorador del repositorio con un cliente del Worker de prueba y una solicitud `ACCEPTED` con `professionalId = p1`
- CUANDO el cliente dueño la cancela con éxito
- ENTONCES el cliente del Worker recibe exactamente 1 pedido de tipo cancelación con el `requestId` y el ID token

#### Escenario: Cancelar un OPEN no envía aviso
- DADO una solicitud `OPEN`
- CUANDO el cliente dueño la cancela con éxito
- ENTONCES el cliente del Worker no recibe ningún pedido

#### Escenario: Cancelación rechazada no envía aviso
- DADO una solicitud cuya cancelación devuelve un error (por ejemplo, `Unauthorized`)
- CUANDO se intenta cancelar
- ENTONCES el cliente del Worker no recibe ningún pedido

### Requirement: Verificaciones del Worker [Fase 4]

El Worker NO DEBE confiar en el cuerpo del pedido más allá del tipo de evento y el `requestId`. Para cada pedido DEBE: (1) verificar el ID token de Firebase (firma, emisor, audiencia y vigencia) y rechazar con 401 si no es válido; (2) leer la solicitud en Firestore por `requestId`; y (3) enviar el aviso solo si se cumplen las condiciones del evento:

| Evento | Estado leído | Quien llama (`uid` del token) | Destino |
|---|---|---|---|
| Nueva solicitud | `OPEN` | cliente dueño (`clientId`) | tema `category_{CATEGORY}` leído de la solicitud |
| Solicitud aceptada | `ACCEPTED` con `professionalId` | ese `professionalId` | tema `user_{clientId}` |
| Cancelación de un `ACCEPTED` | `CANCELLED` con `professionalId` | cliente dueño (`clientId`) | tema `user_{professionalId}` |

El tema de destino y la categoría DEBEN salir de lo leído en Firestore, nunca del cuerpo del pedido. Además, el evento DEBE ser reciente (protección ante la repetición de pedidos con un ID token que sigue vigente): la marca de tiempo del servidor que corresponde al evento (`createdAt` para nueva solicitud, `acceptedAt` para aceptación y `cancelledAt` para cancelación) NO DEBE tener más de 2 minutos de antigüedad ni estar más de 60 segundos en el futuro (desfase de relojes tolerado), y si falta el Worker DEBE tratarla como vencida. Las reglas de Firestore DEBEN fijar esas marcas a la hora del servidor, de modo que un cliente no pueda escribir una fecha propia. Si una condición no se cumple, el Worker NO DEBE enviar nada y DEBE responder con un error 4xx. El Worker DEBE responder sin esperar la entrega de FCM más allá de la aceptación del mensaje.

#### Escenario: Token inválido o ausente [manual]
- DADO un pedido sin ID token, con un token vencido o con un token de otro proyecto
- CUANDO el Worker lo recibe
- ENTONCES responde 401 y no envía ningún mensaje

#### Escenario: Solicitud inexistente [manual]
- DADO un pedido con un `requestId` que no existe
- CUANDO el Worker lo recibe con un token válido
- ENTONCES responde un error 4xx y no envía ningún mensaje

#### Escenario: Aceptación llamada por quien no es el profesional asignado [manual]
- DADO una solicitud `ACCEPTED` por el profesional A
- CUANDO un token del profesional B pide el aviso de aceptación
- ENTONCES el Worker no envía ningún mensaje

#### Escenario: Aceptación con la solicitud aún OPEN [manual]
- DADO una solicitud en estado `OPEN`
- CUANDO se pide el aviso de aceptación
- ENTONCES el Worker no envía ningún mensaje

#### Escenario: Nueva solicitud llamada por alguien que no es el dueño [manual]
- DADO una solicitud `OPEN` del cliente C1
- CUANDO un token del cliente C2 pide el aviso de nueva solicitud
- ENTONCES el Worker no envía ningún mensaje

#### Escenario: Nueva solicitud con la solicitud ya no OPEN [manual]
- DADO una solicitud del cliente C1 en estado `ACCEPTED` o `CANCELLED`
- CUANDO el token de C1 pide el aviso de nueva solicitud
- ENTONCES el Worker no envía ningún mensaje

#### Escenario: Cancelación con la solicitud no CANCELLED [manual]
- DADO una solicitud `ACCEPTED` que no fue cancelada
- CUANDO el cliente dueño pide el aviso de cancelación
- ENTONCES el Worker no envía ningún mensaje

#### Escenario: Cancelación sin profesional asignado [manual]
- DADO una solicitud `CANCELLED` sin `professionalId`
- CUANDO el cliente dueño pide el aviso de cancelación
- ENTONCES el Worker no envía ningún mensaje

#### Escenario: Cancelación llamada por alguien que no es el cliente dueño [manual]
- DADO una solicitud `CANCELLED` que tenía profesional asignado
- CUANDO un token distinto del cliente dueño pide el aviso de cancelación
- ENTONCES el Worker no envía ningún mensaje

#### Escenario: Evento fuera de la ventana de vigencia
- DADO una solicitud `ACCEPTED` cuya marca `acceptedAt` es de hace más de 2 minutos, ausente o más de 60 segundos en el futuro, y un token válido del profesional asignado
- CUANDO se evalúa el pedido de aviso de aceptación
- ENTONCES el Worker lo rechaza con un error 4xx y no envía ningún mensaje
- Y lo mismo ocurre con `createdAt` para nueva solicitud y con `cancelledAt` para cancelación
- Y un evento de hace menos de 2 minutos, o con una marca futura dentro del desfase tolerado, se acepta

#### Escenario: El destino sale de Firestore, no del pedido [manual]
- DADO un pedido válido que incluye en su cuerpo un tema o una categoría distintos de los de la solicitud
- CUANDO el Worker lo procesa
- ENTONCES el mensaje se envía al tema derivado de la solicitud leída y se ignoran los valores del cuerpo

### Requirement: Privacidad del contenido de los avisos [Fase 4]

Los mensajes enviados a temas NO DEBEN contener datos personales ni coordenadas: el mensaje de datos de nueva solicitud DEBE llevar únicamente el `requestId`, la categoría y el tipo de evento, y los avisos de aceptación y de cancelación DEBEN llevar únicamente el `requestId` y el tipo de evento. El texto visible del aviso DEBE armarse en el dispositivo a partir de textos de la app y NO DEBE incluir nombre, teléfono, dirección ni descripción de la solicitud.

#### Escenario: Contenido del mensaje de nueva solicitud
- DADO una solicitud con categoría `GAS` e id `r1`
- CUANDO se construye el payload de nueva solicitud
- ENTONCES contiene exactamente las claves `type`, `requestId` y `category` con los valores `NEW_REQUEST`, `r1` y `GAS`

#### Escenario: Contenido de los mensajes de aceptación y cancelación
- DADO una solicitud con id `r1`
- CUANDO se construyen los payloads de aceptación y de cancelación
- ENTONCES cada uno contiene exactamente las claves `type` y `requestId`
- Y `type` vale `REQUEST_ACCEPTED` y `REQUEST_CANCELLED`, respectivamente

#### Escenario: El aviso visible no filtra datos personales
- DADO un payload recibido en el dispositivo
- CUANDO se arma el texto del aviso
- ENTONCES el título y el cuerpo salen de los textos de la app y no contienen datos de la solicitud ni de las personas

### Requirement: Filtro de distancia en el dispositivo [Fase 4]

Al recibir el mensaje de datos de nueva solicitud, el dispositivo del profesional DEBE leer la solicitud en Firestore con la sesión del usuario y mostrar el aviso solo si se cumplen todas estas condiciones: hay sesión de `PROFESSIONAL`; la solicitud está `OPEN`; su categoría está entre las categorías vigentes del profesional; y la distancia Haversine entre el punto base del profesional y la ubicación de la solicitud cumple `Matching.isWithin(distanceKm, radiusKm)` (límite inclusivo, igual que el feed, ver `professional-feed`). Como prefiltro barato, si el mensaje de nueva solicitud trae una categoría que no figura entre las categorías vigentes del profesional según su sesión, el dispositivo NO DEBE leer nada de Firestore y NO DEBE mostrar el aviso. Si la lectura falla o la solicitud no tiene ubicación, el aviso NO DEBE mostrarse. El filtro NO DEBE enviar la ubicación del profesional a ningún servicio.

#### Escenario: Solicitud dentro del radio
- DADO un profesional de `PLUMBING` con radio 10 km y una solicitud `OPEN` de `PLUMBING` a 4 km de su punto base
- CUANDO llega el mensaje de datos de esa solicitud
- ENTONCES se decide mostrar el aviso

#### Escenario: Solicitud fuera del radio
- DADO un profesional con radio 10 km y una solicitud `OPEN` de su categoría a 15 km
- CUANDO llega el mensaje de datos
- ENTONCES no se muestra el aviso

#### Escenario: Solicitud exactamente en el límite del radio
- DADO una solicitud cuya distancia sin redondear es igual al radio del profesional
- CUANDO llega el mensaje de datos
- ENTONCES se decide mostrar el aviso

#### Escenario: Categoría ya no vigente
- DADO un profesional que quitó `GAS` de sus categorías y un mensaje de datos de categoría `GAS`
- CUANDO llega el mensaje
- ENTONCES no se muestra el aviso
- Y no se lee ni la solicitud ni el usuario en Firestore

#### Escenario: Solicitud que ya no está OPEN
- DADO un mensaje de datos de una solicitud que al leerse está `ACCEPTED` o `CANCELLED`
- CUANDO llega el mensaje
- ENTONCES no se muestra el aviso

#### Escenario: Falla la lectura de la solicitud
- DADO un repositorio de solicitudes de prueba que falla al leer
- CUANDO llega el mensaje de datos
- ENTONCES no se muestra el aviso y no se lanza ninguna excepción

#### Escenario: Sin sesión de profesional
- DADO que no hay sesión o la sesión es de un `CLIENT`
- CUANDO llega un mensaje de datos de nueva solicitud
- ENTONCES no se muestra el aviso

### Requirement: Avisos visibles en primer plano [Fase 4]

Los avisos DEBEN mostrarse como notificación del sistema también cuando la app está en primer plano, generada desde `onMessageReceived`, en un canal de notificaciones de importancia alta creado por la app. Con la app en segundo plano o cerrada (deslizada desde recientes; no aplica a una app detenida a la fuerza), los avisos de aceptación y de cancelación (prioridad alta) DEBEN mostrarse igualmente. El aviso de nueva solicitud (prioridad normal) DEBE mostrarse con la app en primer plano y DEBERÍA mostrarse con la app en segundo plano o cerrada, pero puede demorar según las restricciones de ahorro de batería del sistema; no se garantiza su entrega inmediata.

#### Escenario: Aviso con la app abierta [manual]
- DADO la app en primer plano con sesión iniciada y el permiso concedido
- CUANDO llega un aviso de su rol
- ENTONCES aparece una notificación del sistema

#### Escenario: Aviso con la app en segundo plano [manual]
- DADO la app en segundo plano con el permiso concedido
- CUANDO llega un aviso de su rol
- ENTONCES aparece una notificación del sistema (el aviso de nueva solicitud puede demorar por su prioridad normal)

#### Escenario: Aviso con la app cerrada [manual]
- DADO la app cerrada (deslizada desde recientes, no detenida a la fuerza) sin haber cerrado sesión
- CUANDO llega un aviso de aceptación o de cancelación
- ENTONCES aparece una notificación del sistema

### Requirement: Navegación al tocar el aviso [Fase 4]

Al tocar un aviso, la app DEBE abrir el detalle de la solicitud correspondiente según el rol de la sesión: el detalle del cliente para los avisos de aceptación; el detalle del profesional para los avisos de nueva solicitud y de cancelación. El destino DEBE ser una función pura de (tipo de evento, rol, `requestId`). Si no hay sesión, la app DEBE mostrar la autenticación y NO DEBE abrir el detalle. Si el rol de la sesión no corresponde al tipo de evento, la app NO DEBE abrir un detalle y DEBE mostrar la pantalla inicial de su rol.

#### Escenario: Aviso de aceptación abre el detalle del cliente
- DADO un aviso de tipo aceptación con `requestId = r1` y una sesión de `CLIENT`
- CUANDO se resuelve el destino
- ENTONCES es el detalle de solicitud del cliente para `r1`

#### Escenario: Aviso de nueva solicitud abre el detalle del profesional
- DADO un aviso de tipo nueva solicitud con `requestId = r1` y una sesión de `PROFESSIONAL`
- CUANDO se resuelve el destino
- ENTONCES es el detalle de solicitud del profesional para `r1`

#### Escenario: Aviso de cancelación abre el detalle del profesional
- DADO un aviso de tipo cancelación con `requestId = r1` y una sesión de `PROFESSIONAL`
- CUANDO se resuelve el destino
- ENTONCES es el detalle de solicitud del profesional para `r1`

#### Escenario: Rol que no corresponde al evento
- DADO un aviso de tipo aceptación y una sesión de `PROFESSIONAL`
- CUANDO se resuelve el destino
- ENTONCES no hay detalle de destino y se muestra la pantalla inicial del rol

#### Escenario: Sin sesión
- DADO un aviso tocado sin sesión iniciada
- CUANDO se resuelve el destino
- ENTONCES no hay detalle de destino y se muestra la autenticación

#### Escenario: Tocar el aviso abre la pantalla correcta [manual]
- DADO un aviso de cada tipo en la bandeja del sistema
- CUANDO la persona lo toca, con la app cerrada y con la app en segundo plano
- ENTONCES se abre el detalle de la solicitud del rol correspondiente

### Requirement: Avisos de mejor esfuerzo [Fase 4]

Los avisos son de mejor esfuerzo. Si el Worker no responde, devuelve un error o la red falla, la creación, la aceptación o la cancelación DEBEN considerarse exitosas, con el mismo resultado y el mismo estado final que sin avisos, y el fallo NO DEBE llegar a la capa de presentación ni mostrarse como error. El pedido al Worker NO DEBE bloquear el resultado de la operación por más de 5 segundos; si se agota, el pedido se descarta sin reintentos. La lectura previa con la que el decorador detecta una aceptación idempotente NO DEBE retrasar la aceptación más de 2 segundos; si se agota o falla, se trata como una aceptación nueva. Si el Worker no está configurado (Fases 1 a 3), el comportamiento DEBE ser idéntico al de un repositorio sin avisos.

#### Escenario: Falla del Worker al crear
- DADO un cliente del Worker de prueba que devuelve un error
- CUANDO el cliente crea una solicitud con éxito
- ENTONCES el resultado es `Success` con la solicitud creada

#### Escenario: Falla del Worker al aceptar
- DADO un cliente del Worker de prueba que lanza una excepción de red
- CUANDO un profesional acepta una solicitud con éxito
- ENTONCES el resultado es `Success` y la solicitud queda `ACCEPTED`

#### Escenario: Falla del Worker al cancelar
- DADO un cliente del Worker de prueba que no responde
- CUANDO el cliente dueño cancela una solicitud `ACCEPTED`
- ENTONCES el resultado es `Success` y la solicitud queda `CANCELLED`

#### Escenario: Tiempo de espera del Worker
- DADO un cliente del Worker de prueba que nunca responde y un reloj virtual
- CUANDO se completa una creación y pasan más de 5 segundos
- ENTONCES el pedido se descarta y el resultado de la operación ya fue `Success`

#### Escenario: Lectura previa lenta no bloquea la aceptación
- DADO un decorador cuyo repositorio real tarda más de 2 segundos (tiempo virtual) en responder la lectura previa de la solicitud
- CUANDO un profesional acepta una solicitud `OPEN` con éxito
- ENTONCES la aceptación termina con `Success` habiendo esperado como máximo 2 segundos por esa lectura
- Y el cliente del Worker recibe exactamente 1 pedido de aceptación

#### Escenario: Sin Worker configurado
- DADO un repositorio de solicitudes sin cliente del Worker (Fases 1 a 3)
- CUANDO se crea, acepta o cancela una solicitud
- ENTONCES el comportamiento es el mismo que sin avisos y no se realiza ningún pedido

#### Escenario: Con el Worker caído, la operación funciona en el dispositivo [manual]
- DADO el Worker detenido o inaccesible
- CUANDO se publica, acepta y cancela una solicitud
- ENTONCES las tres operaciones funcionan y el resto de la app no muestra errores

### Requirement: Sin secretos ni tarjeta en los avisos [Fase 4]

La cuenta de servicio de FCM DEBE guardarse solo como secreto del Worker. NO DEBE estar en el repositorio ni en el APK. El Worker DEBE correr en el plan gratuito de Cloudflare y FCM en el plan Spark de Firebase, sin tarjeta cargada.

#### Escenario: Sin la cuenta de servicio en el repositorio ni en el APK [manual]
- DADO el repositorio y el APK de Fase 4
- CUANDO se buscan archivos y cadenas de una cuenta de servicio de Google (`private_key`, `client_email` de servicio)
- ENTONCES no hay ninguna coincidencia

#### Escenario: Cuentas sin tarjeta [manual]
- DADO la cuenta de Cloudflare y el proyecto Firebase usados en Fase 4
- CUANDO se revisan sus paneles de facturación
- ENTONCES el Worker está en el plan gratuito, Firebase está en el plan Spark y no hay tarjeta cargada
