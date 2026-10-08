# Delta para Notificaciones (`notifications`)

> Cambio: `fixit-mvp`. Alcance: avisos push opcionales [Fase 4] enviados con FCM por un Cloudflare Worker. Tres eventos: solicitud aceptada (aviso al cliente), nueva solicitud `OPEN` (aviso a profesionales compatibles) y cancelación de un trabajo `ACCEPTED` (aviso al profesional asignado). Quedan fuera: avisos de trabajo completado, reseña recibida o recordatorios, bandeja de notificaciones dentro de la app y preferencias por usuario.
>
> Restricción dura: el proyecto NO DEBE requerir tarjeta de crédito. Los avisos NO usan Cloud Functions ni el plan Blaze; los envía un Worker de Cloudflare en el plan gratuito.
>
> Esta capacidad es dueña de todo el comportamiento de suscripción a temas. `auth` (iniciar y cerrar sesión) y `profiles` (editar categorías) solo la referencian.
>
> Temas de FCM: `user_{uid}` (avisos a una persona) y `category_{CATEGORY}` (nuevas solicitudes de una categoría).
>
> Convención de verificación: cada escenario está cubierto por un test JVM (lógica pura y decorador en `:core:data` con dobles escritos a mano) o por un ítem del checklist manual. Los escenarios cuyo título termina en `[manual]` se verifican a mano (ver `design.md` §15). Las verificaciones del código del Worker (TypeScript, fuera de Gradle) y la entrega real de FCM se verifican como `[manual]` contra el Worker desplegado o en `wrangler dev`.

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

#### Escenario: Falla de suscripción no bloquea la operación
- DADO un suscriptor de prueba que falla al suscribirse
- CUANDO la persona inicia sesión o guarda sus categorías
- ENTONCES la operación termina con éxito y la falla no llega a la capa de presentación

### Requirement: Aviso de solicitud aceptada [Fase 4]

Cuando un profesional acepta una solicitud, el cliente dueño DEBE recibir un aviso en su tema `user_{clientId}`. Tras una aceptación exitosa, la app DEBE pedir al Worker el envío incluyendo el ID token de Firebase y el `requestId`. El aviso DEBE enviarse una sola vez por aceptación efectiva. Una aceptación idempotente del mismo profesional (ver `professional-feed`) NO DEBE generar un segundo aviso.

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

### Requirement: Aviso de nueva solicitud [Fase 4]

Cuando un cliente publica una solicitud, el Worker DEBE enviar un mensaje de datos (sin bloque de notificación visible) al tema `category_{CATEGORY}` de la categoría de la solicitud. Tras una creación exitosa, la app DEBE pedir al Worker el envío incluyendo el ID token y el `requestId`. El dispositivo del profesional DEBE decidir si muestra el aviso aplicando el filtro de distancia (ver "Filtro de distancia en el dispositivo"). Un cliente NO DEBE recibir avisos de nuevas solicitudes.

#### Escenario: Crear solicita el envío una vez
- DADO un decorador del repositorio de solicitudes con un cliente del Worker de prueba
- CUANDO un cliente crea una solicitud con éxito
- ENTONCES el cliente del Worker recibe exactamente 1 pedido de tipo nueva solicitud con el `requestId` y el ID token

#### Escenario: Creación fallida no envía aviso
- DADO un repositorio cuya creación falla con un error
- CUANDO un cliente intenta publicar
- ENTONCES el cliente del Worker no recibe ningún pedido

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

El tema de destino y la categoría DEBEN salir de lo leído en Firestore, nunca del cuerpo del pedido. Si una condición no se cumple, el Worker NO DEBE enviar nada y DEBE responder con un error 4xx. El Worker DEBE responder sin esperar la entrega de FCM más allá de la aceptación del mensaje.

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

Al recibir el mensaje de datos de nueva solicitud, el dispositivo del profesional DEBE leer la solicitud en Firestore con la sesión del usuario y mostrar el aviso solo si se cumplen todas estas condiciones: hay sesión de `PROFESSIONAL`; la solicitud está `OPEN`; su categoría está entre las categorías vigentes del profesional; y la distancia Haversine entre el punto base del profesional y la ubicación de la solicitud cumple `Matching.isWithin(distanceKm, radiusKm)` (límite inclusivo, igual que el feed, ver `professional-feed`). Si la lectura falla o la solicitud no tiene ubicación, el aviso NO DEBE mostrarse. El filtro NO DEBE enviar la ubicación del profesional a ningún servicio.

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

Los avisos DEBEN mostrarse como notificación del sistema también cuando la app está en primer plano, generada desde `onMessageReceived`, en un canal de notificaciones de importancia alta creado por la app. Con la app en segundo plano o cerrada, los avisos de aceptación y de cancelación DEBEN mostrarse igualmente.

#### Escenario: Aviso con la app abierta [manual]
- DADO la app en primer plano con sesión iniciada y el permiso concedido
- CUANDO llega un aviso de su rol
- ENTONCES aparece una notificación del sistema

#### Escenario: Aviso con la app en segundo plano [manual]
- DADO la app en segundo plano con el permiso concedido
- CUANDO llega un aviso de su rol
- ENTONCES aparece una notificación del sistema

#### Escenario: Aviso con la app cerrada [manual]
- DADO la app cerrada sin haber cerrado sesión
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

Los avisos son de mejor esfuerzo. Si el Worker no responde, devuelve un error o la red falla, la creación, la aceptación o la cancelación DEBEN considerarse exitosas, con el mismo resultado y el mismo estado final que sin avisos, y el fallo NO DEBE llegar a la capa de presentación ni mostrarse como error. El pedido al Worker NO DEBE bloquear el resultado de la operación por más de 5 segundos; si se agota, el pedido se descarta sin reintentos. Si el Worker no está configurado (Fases 1 a 3), el comportamiento DEBE ser idéntico al de un repositorio sin avisos.

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
