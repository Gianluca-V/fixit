# Especificación: Perfiles y reputación (`profiles`)

## Propósito

Mostrar los datos del usuario autenticado y, en el caso de los profesionales, su configuración
de trabajo (categorías, ubicación base, radio) y su reputación (promedio y cantidad de reseñas).
También define cómo se expone el perfil de la contraparte una vez aceptado un trabajo.

Módulos: `:features:client` (`ClientProfile`), `:features:professional` (`ProfessionalProfile`),
`:core:ui` (`ReputationSummary`, `RatingStars`, `CategoryChip`), `:core:domain`
(`GetCurrentUserUseCase`, `GetProfessionalProfileUseCase`, `ObserveProfessionalProfileUseCase`,
`FormatRatingUseCase` opcional), `:core:data` (`UserRepository`).

## Requisitos

### Requirement: Perfil de cliente [Fase 1]

La pestaña Perfil del cliente DEBE mostrar nombre, email, teléfono y rol, y un botón "Cerrar sesión".

#### Escenario: Visualizar perfil de cliente
- DADO un cliente autenticado
- CUANDO abre la pestaña Perfil
- ENTONCES `ClientProfileUiState.Success` contiene su `User`
- Y la pantalla muestra nombre, email y teléfono

### Requirement: Perfil de profesional [Fase 1]

La pestaña Perfil del profesional DEBE mostrar nombre, email, teléfono, categorías (como
`CategoryChip`), radio en km, dirección o coordenadas de la ubicación base y la reputación.

#### Escenario: Visualizar perfil de profesional
- DADO un profesional con categorías `{ELECTRICITY}`, radio 15 y 3 reseñas de 5, 4 y 4 estrellas
- CUANDO abre la pestaña Perfil
- ENTONCES se muestra "Electricidad", "15 km", "4.3" y "3 reseñas"

### Requirement: Cálculo y formato de reputación [Fase 1]

El promedio DEBE calcularse como `ratingSum / reviewCount` y mostrarse redondeado a 1 decimal
(`HALF_UP`). Si `reviewCount = 0`, la UI DEBE mostrar "Sin reseñas" en lugar de un promedio.
El dominio DEBE almacenar `ratingSum: Int` y `reviewCount: Int` y exponer `ratingAverage: Double`
derivado, para que el agregado sea actualizable de forma incremental.

#### Escenario: Promedio con un decimal
- DADO `ratingSum = 13` y `reviewCount = 3`
- CUANDO se obtiene `ratingAverage`
- ENTONCES el valor formateado es `"4.3"`

#### Escenario: Sin reseñas
- DADO `reviewCount = 0`
- CUANDO se renderiza `ReputationSummary`
- ENTONCES se muestra el texto de recurso `reputation_no_reviews` y no se divide por cero

### Requirement: Reputación visible en el detalle para el cliente [Fase 1]

Cuando una solicitud está `ACCEPTED` o `COMPLETED`, el detalle de solicitud del cliente DEBE
mostrar el nombre del profesional asignado y su reputación.

#### Escenario: Detalle con profesional asignado
- DADO una solicitud `ACCEPTED` por un profesional con promedio 4.5 y 10 reseñas
- CUANDO el cliente abre el detalle
- ENTONCES se muestra el nombre del profesional, "4.5" y "10 reseñas"

### Requirement: Privacidad de datos de contacto [Fase 1]

El teléfono de un usuario NO DEBE mostrarse a la contraparte mientras la solicitud esté `OPEN`
o `CANCELLED`. Solo DEBE mostrarse cuando la solicitud esté `ACCEPTED` o `COMPLETED` y quien
consulta sea el cliente dueño o el profesional asignado (ver `specs/contact/spec.md`).

#### Escenario: Profesional ve una solicitud abierta
- DADO una solicitud `OPEN` en el feed
- CUANDO el profesional abre el detalle
- ENTONCES el `UiState` no contiene el teléfono ni el nombre completo del cliente

### Requirement: Perfil no editable en este alcance [Fase 1]

La edición de perfil (nombre, teléfono, categorías, radio, ubicación base) NO DEBE implementarse
en Fase 1. PUEDE agregarse en una fase posterior sin cambiar los contratos existentes.

#### Escenario: Sin acción de edición
- DADO la pantalla de perfil de cualquier rol
- CUANDO se renderiza
- ENTONCES no existe acción "Editar"

### Requirement: Perfil observable [Fase 2]

En Fase 2 el perfil profesional DEBE leerse de `users/{uid}` mediante un listener, de modo que
la reputación se actualice en tiempo real cuando un cliente califica un trabajo.

#### Escenario: Actualización en vivo de reputación
- DADO un profesional con la pestaña Perfil abierta
- CUANDO un cliente completa y califica uno de sus trabajos
- ENTONCES `ObserveProfessionalProfileUseCase` emite el perfil con `reviewCount` incrementado
