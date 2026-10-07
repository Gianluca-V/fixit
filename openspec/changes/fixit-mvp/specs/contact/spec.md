# Delta para Contacto (`contact`)

> Cambio: `fixit-mvp`. Alcance: una vez aceptado un trabajo, cliente y profesional ven el nombre y el teléfono de la contraparte y se comunican por llamada o WhatsApp mediante las aplicaciones del dispositivo. No hay chat dentro de la app.
>
> Convención de verificación: cada escenario está cubierto por un test JVM o por un ítem del checklist manual. Los escenarios cuyo título termina en `[manual]` se verifican a mano (ver `design.md` §15).

## ADDED Requirements

### Requirement: Datos de contacto de la contraparte [Fase 1]

El sistema DEBE entregar nombre y teléfono de la contraparte únicamente cuando la solicitud está `ACCEPTED` o `COMPLETED` y quien consulta es el cliente dueño o el profesional asignado. En cualquier otro caso (solicitud `OPEN`, solicitud `CANCELLED` aunque haya estado `ACCEPTED`, o un tercero) DEBE devolver `Unauthorized` y NO DEBE mostrarse ningún dato de contacto. Los datos mostrados DEBEN ser los vigentes del perfil de la contraparte.

#### Escenario: El cliente obtiene el contacto del profesional
- DADO una solicitud `ACCEPTED` del cliente C asignada al profesional P
- CUANDO C consulta el contacto
- ENTONCES obtiene el nombre y el teléfono de P

#### Escenario: El profesional obtiene el contacto del cliente
- DADO la misma solicitud
- CUANDO P consulta el contacto
- ENTONCES obtiene el nombre y el teléfono de C

#### Escenario: Trabajo completado
- DADO una solicitud `COMPLETED`
- CUANDO C o P consultan el contacto
- ENTONCES cada uno obtiene los datos de la contraparte

#### Escenario: Solicitud abierta
- DADO una solicitud `OPEN`
- CUANDO cualquier usuario consulta el contacto
- ENTONCES el resultado es `Unauthorized`

#### Escenario: Solicitud cancelada después de ser aceptada
- DADO una solicitud `ACCEPTED` que el cliente cancela
- CUANDO C o P consultan el contacto
- ENTONCES el resultado es `Unauthorized` para ambos
- Y ninguna de sus pantallas muestra nombre ni teléfono del otro

#### Escenario: La cancelación oculta el contacto en vivo
- DADO el detalle de una solicitud `ACCEPTED` abierto por P con el contacto del cliente visible
- CUANDO el cliente cancela la solicitud
- ENTONCES el detalle de P deja de mostrar el contacto y muestra "Cancelada por el cliente" sin recargar

#### Escenario: Tercero
- DADO un profesional distinto del asignado, o un cliente distinto del dueño
- CUANDO consulta el contacto de la solicitud
- ENTONCES el resultado es `Unauthorized`

### Requirement: Normalización de teléfono [Fase 1]

El sistema DEBE derivar del teléfono ingresado los dígitos para WhatsApp (`PhoneFormatter.whatsAppDigits`) y el enlace `https://wa.me/<dígitos>` (`PhoneFormatter.whatsAppUrl`), que NO incluye el parámetro `?text`. Primero DEBE recortar el texto y quitar espacios, guiones y paréntesis; después comprueba si el resultado empieza con `+` y conserva solo los dígitos. Luego:

1. Si el resultado empieza con `+`, los dígitos DEBEN usarse tal como están.
2. Si no empieza con `+` pero los dígitos empiezan con `54`, DEBEN usarse tal como están, sin agregar prefijo.
3. En cualquier otro caso, se asume un celular argentino y DEBEN anteponerse `549`.

Limitación documentada: un número fijo o extranjero ingresado sin `+` (y que no empiece con `54`), y los formatos troncales argentinos `0xx 15…`, generan un enlace de WhatsApp inválido (no se agrega lógica para ellos). Para evitarlo, el usuario puede ingresar el número en formato internacional con `+`.

#### Escenario: Número local argentino
- DADO el teléfono `"11 5555-1234"`
- CUANDO se normaliza
- ENTONCES los dígitos para WhatsApp son `"5491155551234"`
- Y el enlace de WhatsApp es `https://wa.me/5491155551234`

#### Escenario: Número internacional
- DADO el teléfono `"+54 9 221 555 1234"`
- CUANDO se normaliza
- ENTONCES los dígitos para WhatsApp son `"5492215551234"`

#### Escenario: Número ya normalizado
- DADO el teléfono `"+5491155551234"`
- CUANDO se normaliza
- ENTONCES los dígitos para WhatsApp son `"5491155551234"`

#### Escenario: Teléfono con espacios y paréntesis antes del `+`
- DADO el teléfono `" (+54) 9 11 5555-1234 "`
- CUANDO se normaliza
- ENTONCES los dígitos para WhatsApp son `"5491155551234"`

#### Escenario: Número que ya empieza con 54 sin `+`
- DADO el teléfono `"5491155554444"`
- CUANDO se normaliza
- ENTONCES los dígitos para WhatsApp son `"5491155554444"`, sin un segundo prefijo
- Y el enlace de WhatsApp es `https://wa.me/5491155554444`

### Requirement: Tarjeta de contacto [Fase 1]

Los detalles de solicitud de ambos roles DEBEN mostrar, cuando corresponda según esta especificación, una tarjeta con nombre, teléfono y los botones "Llamar" y "WhatsApp". "Llamar" DEBE abrir el marcador del sistema con el número precargado, sin iniciar la llamada ni pedir permisos. "WhatsApp" DEBE abrir la conversación con ese número en la aplicación de WhatsApp o, si no está instalada, en el navegador. La acción PUEDE incluir un mensaje inicial "Hola, te contacto por la solicitud de FixIt: {categoría}"; ese mensaje lo agrega la interfaz como parámetro `?text=` al abrir el enlace y queda fuera de la URL probada (`whatsAppUrl` nunca lo incluye). Si el dispositivo no tiene una aplicación que resuelva la acción, DEBE mostrarse el mensaje "No hay una aplicación disponible para esta acción" y la app NO DEBE cerrarse. Ambas acciones DEBEN funcionar en dispositivos con Android 11 o superior.

#### Escenario: Llamar [manual]
- DADO la tarjeta de contacto visible en un trabajo `ACCEPTED`
- CUANDO el usuario toca "Llamar"
- ENTONCES se abre el marcador del sistema con el número del contacto precargado

#### Escenario: Enlace de WhatsApp
- DADO el teléfono `"11 5555-1234"`
- CUANDO se arma el enlace de WhatsApp
- ENTONCES `PhoneFormatter.whatsAppUrl` devuelve `https://wa.me/5491155551234`, sin parámetro `?text`

#### Escenario: Abrir WhatsApp [manual]
- DADO la tarjeta de contacto con el teléfono `"11 5555-1234"`
- CUANDO el usuario toca "WhatsApp"
- ENTONCES se abre `https://wa.me/5491155551234` en la aplicación de WhatsApp o, si no está instalada, en el navegador

#### Escenario: Sin aplicación disponible [manual]
- DADO un dispositivo sin ninguna aplicación que resuelva la acción de llamar
- CUANDO el usuario toca "Llamar"
- ENTONCES se muestra "No hay una aplicación disponible para esta acción"
- Y la app sigue abierta

### Requirement: Sin chat [Fase 1]

El sistema NO DEBE incluir mensajería dentro de la app.

#### Escenario: Sin pantalla de chat [manual]
- DADO el catálogo de pantallas de ambos roles
- CUANDO se revisa
- ENTONCES no existe ninguna pantalla de chat ni de mensajes
