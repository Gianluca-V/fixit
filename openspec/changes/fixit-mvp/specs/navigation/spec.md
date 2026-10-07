# Delta para Navegación (`navigation`)

> Cambio: `fixit-mvp`. Alcance: qué pantallas existen para cada rol, a cuáles puede llegar el usuario, cómo se pasa de una a otra y cómo se comporta "Atrás". Describe comportamiento visible, no la tecnología de navegación.
>
> Convención de verificación: cada escenario está cubierto por un test JVM o por un ítem del checklist manual. Los escenarios cuyo título termina en `[manual]` se verifican a mano (ver `design.md` §15).

## ADDED Requirements

### Requirement: Compuerta por sesión y rol [Fase 1]

Al abrir la app DEBE mostrarse exactamente una de estas experiencias: una pantalla de carga mientras se determina la sesión, la experiencia de autenticación si no hay sesión, la experiencia de cliente si la sesión es de un `CLIENT`, o la experiencia de profesional si es de un `PROFESSIONAL`. Al iniciar o cerrar sesión, el historial de pantallas anterior DEBE descartarse por completo.

#### Escenario: Sesión en determinación
- DADO que la sesión todavía se está determinando
- CUANDO se abre la app
- ENTONCES se muestra la pantalla de carga

#### Escenario: Sesión previa de profesional
- DADO una sesión activa de un usuario `PROFESSIONAL`
- CUANDO se abre la app
- ENTONCES se muestra la experiencia de profesional con la pestaña "Feed" seleccionada

#### Escenario: Sin sesión
- DADO que no hay sesión
- CUANDO se abre la app
- ENTONCES se muestra la pantalla de inicio de sesión

#### Escenario: Iniciar sesión como cliente
- DADO la pantalla de inicio de sesión
- CUANDO un `CLIENT` inicia sesión
- ENTONCES se muestra la pestaña "Solicitudes" con "Mis solicitudes" como única pantalla del historial

#### Escenario: Cerrar sesión
- DADO un profesional con varias pantallas abiertas
- CUANDO toca "Cerrar sesión" en su Perfil
- ENTONCES se muestra la pantalla de inicio de sesión
- Y "Atrás" cierra la app en lugar de volver a pantallas anteriores

#### Escenario: Otro usuario tras cerrar sesión no ve datos del anterior [manual]
- DADO un usuario que usó la app y cerró sesión
- CUANDO otro usuario inicia sesión en el mismo dispositivo y recorre sus pantallas
- ENTONCES ninguna pantalla muestra datos, formularios a medio completar ni historial del usuario anterior

### Requirement: Catálogo de pantallas por rol [Fase 1]

Cada rol DEBE poder llegar solo a las pantallas de su experiencia, según la tabla. Ningún rol DEBE poder llegar a pantallas del otro.

| Experiencia | Pestaña | Pantallas |
|---|---|---|
| Autenticación | — | Inicio de sesión (raíz); Registro (con selector de rol y, para profesional, categorías, punto base y radio) |
| Cliente | "Solicitudes" | Mis solicitudes (raíz); Nueva solicitud; Detalle de solicitud; Completar y calificar |
| Cliente | "Perfil" | Perfil del cliente (raíz); Editar perfil |
| Profesional | "Feed" | Feed (raíz); Detalle de solicitud del profesional |
| Profesional | "Mis trabajos" | Mis trabajos (raíz); Detalle de solicitud del profesional |
| Profesional | "Perfil" | Perfil del profesional (raíz); Editar perfil |

#### Escenario: Pestañas del cliente
- DADO un cliente con sesión iniciada
- CUANDO se muestra la barra de pestañas
- ENTONCES contiene exactamente "Solicitudes" y "Perfil"

#### Escenario: Pestañas del profesional
- DADO un profesional con sesión iniciada
- CUANDO se muestra la barra de pestañas
- ENTONCES contiene exactamente "Feed", "Mis trabajos" y "Perfil"

#### Escenario: Sin acceso a pantallas del otro rol [manual]
- DADO un cliente con sesión iniciada
- CUANDO recorre todas las pantallas a las que puede llegar
- ENTONCES ninguna es "Feed", "Mis trabajos" ni el Detalle de solicitud del profesional

### Requirement: Pestañas con historial propio [Fase 1]

Cada pestaña DEBE mantener su propio historial de pantallas, y volver a una pestaña ya visitada DEBE restaurar ese historial. Tocar la pestaña ya activa DEBE volver a su pantalla raíz. "Atrás" desde la raíz de una pestaña que no es la inicial ("Solicitudes" para cliente, "Feed" para profesional) DEBE seleccionar la pestaña inicial; desde la raíz de la pestaña inicial DEBE cerrar la app.

#### Escenario: Historial independiente por pestaña
- DADO el profesional en Feed y luego en el Detalle de una solicitud
- CUANDO cambia a "Perfil" y vuelve a "Feed"
- ENTONCES ve nuevamente el Detalle de la solicitud

#### Escenario: Volver a tocar la pestaña activa
- DADO la pestaña "Feed" activa con un Detalle abierto
- CUANDO toca nuevamente "Feed"
- ENTONCES la pestaña vuelve a su raíz (el Feed)

#### Escenario: Atrás en una pestaña no inicial
- DADO la pestaña "Perfil" en su raíz
- CUANDO el usuario presiona "Atrás"
- ENTONCES se selecciona la pestaña inicial

#### Escenario: Atrás en la raíz de la pestaña inicial [manual]
- DADO la pestaña inicial en su raíz
- CUANDO el usuario presiona "Atrás"
- ENTONCES la app se cierra

### Requirement: Flujos del cliente [Fase 1]

El cliente DEBE poder recorrer estos flujos: desde "Mis solicitudes" abrir "Nueva solicitud" o el detalle de una solicitud; al publicar con éxito pasar al detalle de la solicitud creada, de modo que "Atrás" desde ese detalle lleve a "Mis solicitudes" y no al formulario; desde el detalle de una solicitud `ACCEPTED` abrir "Completar y calificar" y, al confirmar con éxito, volver al detalle.

#### Escenario: Publicar una solicitud
- DADO el historial "Mis solicitudes" → "Nueva solicitud"
- CUANDO el cliente publica una solicitud con éxito
- ENTONCES se muestra el detalle de la solicitud creada
- Y "Atrás" desde ese detalle muestra "Mis solicitudes"

#### Escenario: Completar y calificar
- DADO el detalle de una solicitud `ACCEPTED`
- CUANDO el cliente toca "Marcar como completado" y confirma la calificación con éxito
- ENTONCES vuelve al detalle, que muestra el estado `COMPLETED`

#### Escenario: Salir de Completar y calificar sin confirmar
- DADO la pantalla "Completar y calificar" abierta desde el detalle
- CUANDO el usuario vuelve atrás
- ENTONCES se muestra el detalle sin cambios y la solicitud sigue `ACCEPTED`

### Requirement: Flujos del profesional [Fase 1]

El profesional DEBE poder recorrer estos flujos: desde el Feed o desde "Mis trabajos" abrir el detalle de una solicitud; al aceptar con éxito, cambiar a la pestaña "Mis trabajos" mostrando el detalle de ese trabajo, y dejar el Feed en su raíz; si pierde la carrera o la solicitud ya no está disponible, mostrar el mensaje correspondiente y, al confirmarlo, volver al Feed.

#### Escenario: Aceptar un trabajo
- DADO el Feed con el Detalle de la solicitud R abierto
- CUANDO el profesional acepta R con éxito
- ENTONCES la pestaña activa es "Mis trabajos" y muestra el detalle de R
- Y la pestaña "Feed" quedó en su raíz

#### Escenario: Perder la carrera al aceptar
- DADO el Feed con el Detalle de la solicitud R abierto
- CUANDO el resultado de aceptar es `AlreadyTaken`
- ENTONCES se muestra el mensaje correspondiente
- Y al confirmarlo se vuelve al Feed

#### Escenario: Abrir un trabajo
- DADO la pestaña "Mis trabajos"
- CUANDO el profesional toca un trabajo
- ENTONCES se muestra su detalle y "Atrás" vuelve a "Mis trabajos"

### Requirement: Editar perfil desde Perfil [Fase 1]

Desde la pantalla Perfil de cada rol DEBE poder accederse a "Editar perfil". Al guardar con éxito DEBE volverse al Perfil mostrando los datos actualizados. Salir sin guardar DEBE volver al Perfil con los datos anteriores.

#### Escenario: Guardar cambios
- DADO la pantalla "Editar perfil" con un nombre modificado
- CUANDO el usuario guarda con éxito
- ENTONCES se muestra el Perfil con el nombre nuevo

#### Escenario: Salir sin guardar
- DADO la pantalla "Editar perfil" con cambios sin guardar
- CUANDO el usuario vuelve atrás
- ENTONCES se muestra el Perfil con los datos anteriores

### Requirement: Barra superior y botón Atrás [Fase 1]

Las pantallas que no son raíz de pestaña DEBEN mostrar una barra superior con una acción "Atrás" que vuelve a la pantalla anterior. Las pantallas raíz de pestaña DEBEN mostrar su título sin acción "Atrás".

#### Escenario: Detalle con acción Atrás
- DADO el detalle de una solicitud abierto desde "Mis solicitudes"
- CUANDO el usuario toca la acción "Atrás" de la barra superior
- ENTONCES vuelve a "Mis solicitudes"

#### Escenario: Pantalla raíz sin acción Atrás [manual]
- DADO la pantalla "Mis solicitudes"
- CUANDO se muestra
- ENTONCES muestra su título y no tiene acción "Atrás"

### Requirement: Conservación del estado de pantalla [Fase 1]

Al rotar el dispositivo, las pantallas con formularios DEBEN conservar lo cargado. Al volver atrás desde una pantalla, su estado DEBE descartarse.

#### Escenario: Rotación del formulario [manual]
- DADO el formulario de nueva solicitud con descripción escrita y 2 fotos
- CUANDO se rota el dispositivo
- ENTONCES la descripción y las 2 fotos se conservan

#### Escenario: Estado descartado al volver [manual]
- DADO el formulario de nueva solicitud con una descripción escrita
- CUANDO el usuario vuelve atrás y abre "Nueva solicitud" otra vez
- ENTONCES el formulario aparece vacío
