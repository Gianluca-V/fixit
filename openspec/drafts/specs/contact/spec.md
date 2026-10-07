# Especificación: Contacto (`contact`)

## Propósito

Una vez aceptado un trabajo, permitir que cliente y profesional vean el nombre y teléfono de la
contraparte y se comuniquen mediante llamada telefónica o WhatsApp, usando intents del sistema.
No hay chat dentro de la app.

Módulos: `:core:domain` (`GetCounterpartContactUseCase`, `PhoneNumberNormalizer`),
`:core:ui` (`ContactCard`), `:features:client` y `:features:professional` (callbacks),
`:app` (`ContactLauncher` que construye y lanza los intents).

## Reglas

| Regla | Detalle |
|---|---|
| Visibilidad | Solo en `ACCEPTED` y `COMPLETED`, y solo para el cliente dueño y el profesional asignado. |
| Llamada | `Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))`. No requiere permiso `CALL_PHONE`. |
| WhatsApp | `Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))`; `digits` en formato E.164 sin `+`. |
| Normalización | Se quitan espacios, guiones y paréntesis. Si el número no empieza con `+`, se asume Argentina: se antepone `54` y, para móviles, `9` (p. ej. `11 5555-1234` → `5491155551234`). |
| Mensaje inicial (opcional) | `?text=` con texto URL-encoded: "Hola, te contacto por la solicitud de FixIt: {categoría}". |

## Requisitos

### Requirement: Datos de contacto de la contraparte [Fase 1]

`GetCounterpartContactUseCase(requestId)` DEBE devolver `Contact(name, phone)` de la contraparte
del usuario autenticado si la solicitud está `ACCEPTED` o `COMPLETED` y el usuario es el cliente
dueño o el profesional asignado. En cualquier otro caso DEBE devolver error.

#### Escenario: Cliente obtiene contacto del profesional
- DADO una solicitud `ACCEPTED` del cliente C asignada a P
- CUANDO C ejecuta el caso de uso
- ENTONCES obtiene `Contact(name = P.name, phone = P.phone)`

#### Escenario: Profesional obtiene contacto del cliente
- DADO la misma solicitud
- CUANDO P ejecuta el caso de uso
- ENTONCES obtiene `Contact(name = C.name, phone = C.phone)`

#### Escenario: Solicitud abierta
- DADO una solicitud `OPEN`
- ENTONCES el resultado es `Error(Unauthorized)`

#### Escenario: Tercero
- DADO un profesional distinto de P
- ENTONCES el resultado es `Error(Unauthorized)`

### Requirement: Normalización de teléfono [Fase 1]

`PhoneNumberNormalizer` (dominio, puro) DEBE producir el número para `tel:` y los dígitos para
`wa.me` según las reglas de esta especificación.

#### Escenario: Número local argentino
- DADO `"11 5555-1234"`
- ENTONCES `waDigits = "5491155551234"`

#### Escenario: Número internacional
- DADO `"+54 9 221 555 1234"`
- ENTONCES `waDigits = "5492215551234"`

#### Escenario: Número ya normalizado
- DADO `"+5491155551234"`
- ENTONCES `waDigits = "5491155551234"`

### Requirement: Tarjeta de contacto [Fase 1]

Los detalles de solicitud de ambos roles DEBEN mostrar `ContactCard` con nombre, teléfono y dos
botones: "Llamar" y "WhatsApp". La feature DEBE exponer `onCallClick(phone)` y
`onWhatsAppClick(waDigits)`; el lanzamiento del intent ocurre en `:app` (o en el `Route` mediante
`LocalContext`), nunca en el ViewModel.

#### Escenario: Llamar
- CUANDO el usuario toca "Llamar"
- ENTONCES se abre el marcador del sistema con el número precargado

#### Escenario: WhatsApp
- CUANDO el usuario toca "WhatsApp"
- ENTONCES se abre `https://wa.me/{digits}` (app de WhatsApp si está instalada o el navegador)

#### Escenario: Sin aplicación para el intent
- DADO que no hay app que resuelva `ACTION_DIAL`
- CUANDO se lanza el intent y ocurre `ActivityNotFoundException`
- ENTONCES se muestra el snackbar "No hay una aplicación disponible para esta acción" y la app no se cierra

### Requirement: Visibilidad de paquetes [Fase 1]

El `AndroidManifest.xml` de `:app` DEBE declarar en `<queries>` los intents `ACTION_DIAL` (scheme
`tel`) y `ACTION_VIEW` (scheme `https`) para compatibilidad con la visibilidad de paquetes de
Android 11+.

#### Escenario: Manifest
- DADO el manifest fusionado
- ENTONCES contiene las entradas `<queries>` indicadas

### Requirement: Sin chat [Fase 1]

El sistema NO DEBE incluir mensajería dentro de la app.

#### Escenario: Sin pantalla de chat
- DADO el catálogo de pantallas
- ENTONCES no existe ninguna pantalla de chat
