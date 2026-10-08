# Delta para Perfiles y reputación (`profiles`)

> Cambio: `fixit-mvp`. Alcance: perfiles de cliente y profesional, edición de perfil, reputación y privacidad de los datos personales antes de aceptar un trabajo. Los límites de validación son los mismos del registro (ver `auth`).
>
> Convención de verificación: cada escenario está cubierto por un test JVM o por un ítem del checklist manual. Los escenarios cuyo título termina en `[manual]` se verifican a mano (ver `design.md` §15). El dominio devuelve números; el formato visual con el idioma del dispositivo se verifica a mano.

## ADDED Requirements

### Requirement: Perfil de cliente [Fase 1]

La pestaña Perfil del cliente DEBE mostrar nombre, email, teléfono y rol, y las acciones "Editar perfil" y "Cerrar sesión".

#### Escenario: Visualizar perfil de cliente [manual]
- DADO un cliente autenticado
- CUANDO abre la pestaña Perfil
- ENTONCES se muestran su nombre, email, teléfono y rol
- Y están disponibles "Editar perfil" y "Cerrar sesión"

### Requirement: Perfil de profesional [Fase 1]

La pestaña Perfil del profesional DEBE mostrar nombre, email, teléfono, categorías, radio en km, punto base (dirección o coordenadas) y reputación, y las acciones "Editar perfil" y "Cerrar sesión".

#### Escenario: Datos del perfil de profesional
- DADO un profesional con categoría `ELECTRICITY`, radio 15 km y 3 reseñas de 5, 4 y 4 estrellas
- CUANDO abre la pestaña Perfil
- ENTONCES el estado de la pantalla contiene las categorías `{ELECTRICITY}`, el radio 15, el promedio numérico 4.3 y 3 reseñas

#### Escenario: Formato visual del perfil de profesional [manual]
- DADO el perfil del escenario anterior en un dispositivo con idioma español
- CUANDO se muestra la pestaña Perfil
- ENTONCES se muestran "Electricidad", "15 km" y "3 reseñas"
- Y el promedio se muestra con el formato del idioma ("4,3")

### Requirement: Cálculo y formato de reputación [Fase 1]

El dominio DEBE calcular el promedio como suma de calificaciones dividida por cantidad de reseñas y devolverlo como número con 1 decimal, redondeando hacia arriba en el punto medio (`BigDecimal(ratingSum).divide(BigDecimal(reviewCount), 1, RoundingMode.HALF_UP)`); NO DEBE devolver texto. Si el profesional no tiene reseñas, el promedio DEBE ser ausente (`null`) y la interfaz DEBE mostrar "Sin reseñas" en lugar de un promedio. La interfaz DEBE dar formato al número con el idioma del dispositivo (en español se muestra "4,3"). El sistema DEBE mantener suma y cantidad de reseñas de forma que cada nueva reseña las actualice de forma incremental.

#### Escenario: Promedio con un decimal
- DADO una suma de calificaciones de 13 y 3 reseñas
- CUANDO se calcula el promedio
- ENTONCES el resultado numérico es 4.3

#### Escenario: Redondeo en el punto medio
- DADO una suma de calificaciones de 17 y 4 reseñas (promedio 4.25)
- CUANDO se calcula el promedio
- ENTONCES el resultado numérico es 4.3

#### Escenario: Sin reseñas
- DADO un profesional con 0 reseñas
- CUANDO se calcula su promedio
- ENTONCES el resultado es ausente (`null`)
- Y no se produce ninguna división por cero

#### Escenario: Formato del promedio según el idioma [manual]
- DADO un promedio de 4.3 y un dispositivo con idioma español, y otro profesional sin reseñas
- CUANDO se muestra la reputación
- ENTONCES se muestra "4,3" para el primero y "Sin reseñas" para el segundo
- Y un promedio de 4.0 se muestra "4,0" (siempre con un decimal)

### Requirement: Reputación del profesional en el detalle del cliente [Fase 1]

Cuando una solicitud está `ACCEPTED` o `COMPLETED`, el detalle del cliente DEBE mostrar el nombre del profesional asignado y su reputación.

#### Escenario: Detalle con profesional asignado
- DADO una solicitud `ACCEPTED` por un profesional con suma de calificaciones 45 y 10 reseñas
- CUANDO el cliente abre el detalle
- ENTONCES el estado de la pantalla contiene el nombre del profesional, el promedio numérico 4.5 y 10 reseñas

### Requirement: Privacidad de los datos del cliente antes de aceptar [Fase 1]

Mientras una solicitud esté `OPEN`, el sistema NO DEBE mostrar al profesional el teléfono ni el nombre del cliente, ni en el feed ni en el detalle. Las condiciones en que los datos de contacto sí se muestran están definidas en `contact`.

#### Escenario: Profesional ve una solicitud abierta
- DADO una solicitud `OPEN` visible en el feed de un profesional
- CUANDO el profesional ve la tarjeta del feed y abre el detalle
- ENTONCES ninguna de las dos pantallas muestra el teléfono ni el nombre del cliente

### Requirement: Edición de nombre y teléfono [Fase 1]

Ambos roles DEBEN poder editar su nombre y su teléfono, con las mismas reglas de validación del registro: nombre recortado de 2..60 caracteres y teléfono recortado, sin espacios, guiones ni paréntesis, con `+` inicial opcional y 8..15 dígitos. El email y el rol NO DEBEN poder editarse. Si hay un error de validación, NO DEBE persistirse ningún cambio. Los valores válidos DEBEN guardarse recortados.

#### Escenario: Edición exitosa
- DADO un cliente con nombre "Ana" y teléfono "1155551234"
- CUANDO cambia el nombre a "Ana Pérez" y el teléfono a "11 5555-9999" y guarda
- ENTONCES el perfil muestra "Ana Pérez" y el nuevo teléfono
- Y el email y el rol no cambian

#### Escenario: Nombre inválido
- DADO un nombre de 1 carácter
- CUANDO el usuario guarda
- ENTONCES el resultado es `Validation.InvalidName` y el perfil conserva los valores anteriores

#### Escenario: Teléfono inválido
- DADO un teléfono `"12ab"`
- CUANDO el usuario guarda
- ENTONCES el resultado es `Validation.InvalidPhone` y el perfil conserva los valores anteriores

#### Escenario: Email y rol no editables [manual]
- DADO la pantalla de edición de cualquier rol
- CUANDO se revisan los campos disponibles
- ENTONCES el email se muestra de solo lectura y no existe forma de editar el email ni el rol

### Requirement: Edición de categorías, punto base y radio [Fase 1]

El profesional DEBE poder editar además sus categorías, su punto base y su radio. Las reglas de validación son las del registro: al menos 1 categoría (`Validation.CategoriesRequired`), punto base obligatorio (`Validation.BaseLocationRequired`), coordenadas del punto base válidas (`Validation.InvalidCoordinates`) y radio entero de 1..50 km (`Validation.InvalidRadius`). El punto base DEBE obtenerse de la ubicación del dispositivo, no ingresarse como coordenadas. La edición NO DEBE modificar la reputación.

#### Escenario: Edición exitosa de la zona de trabajo
- DADO un profesional con categoría `PLUMBING`, radio 10 y 3 reseñas
- CUANDO cambia las categorías a `{PLUMBING, GAS}`, el radio a 25 y guarda
- ENTONCES el perfil muestra las dos categorías y "25 km"
- Y la reputación sigue siendo la misma (3 reseñas)

#### Escenario: Sin categorías
- DADO un conjunto de categorías vacío
- CUANDO el profesional guarda
- ENTONCES el resultado es `Validation.CategoriesRequired` y no se persiste ningún cambio

#### Escenario: Radio fuera de rango
- DADO un radio de 0 km, o de 51 km
- CUANDO el profesional guarda
- ENTONCES el resultado es `Validation.InvalidRadius` y no se persiste ningún cambio

#### Escenario: Actualizar el punto base
- DADO la pantalla de edición del profesional
- CUANDO toca "Usar mi ubicación como base"
- ENTONCES el punto base se completa con la ubicación obtenida y se muestra su dirección

### Requirement: Pantalla de edición de perfil [Fase 1]

La pantalla de edición DEBE mostrar los valores actuales precargados, deshabilitar "Guardar" mientras se guarda y mostrar los errores de validación junto al campo afectado. Si el guardado falla por otra causa, DEBE conservar lo ingresado y permitir reintentar. Salir sin guardar DEBE descartar los cambios.

#### Escenario: Valores precargados
- DADO un profesional con nombre "Luis" y radio 12
- CUANDO abre "Editar perfil"
- ENTONCES el campo nombre contiene "Luis" y el radio contiene 12

#### Escenario: Fallo al guardar
- DADO que el guardado devuelve el error `Network`
- CUANDO el usuario guarda
- ENTONCES se muestra un mensaje de error, los valores ingresados se conservan y puede reintentar

#### Escenario: Salir sin guardar
- DADO valores modificados y no guardados
- CUANDO el usuario vuelve atrás
- ENTONCES el perfil muestra los valores anteriores

### Requirement: Alcance de los cambios de perfil [Fase 1]

Editar el perfil NO DEBE alterar el estado de ninguna solicitud. Los datos de contacto que ve la contraparte DEBEN reflejar siempre los valores vigentes del perfil. Los efectos de editar categorías, punto base o radio se limitan al feed (ver `professional-feed`) y, en Fase 4, a las suscripciones a temas de avisos (ver `notifications`).

#### Escenario: Contacto con el teléfono vigente
- DADO una solicitud `ACCEPTED` cuyo cliente cambia su teléfono
- CUANDO el profesional asignado abre el detalle del trabajo
- ENTONCES ve el teléfono nuevo del cliente

#### Escenario: Las solicitudes no cambian de estado
- DADO un profesional con un trabajo `ACCEPTED`
- CUANDO edita sus categorías, su punto base o su radio
- ENTONCES el trabajo sigue `ACCEPTED` y asignado a él

### Requirement: Reputación en tiempo real [Fase 2]

Con el backend de Fase 2, la reputación del profesional DEBE actualizarse en pantalla sin recargar cuando un cliente califica uno de sus trabajos.

#### Escenario: Actualización en vivo de la reputación [manual]
- DADO un profesional con la pestaña Perfil abierta y 3 reseñas
- CUANDO un cliente completa y califica uno de sus trabajos
- ENTONCES el perfil pasa a mostrar 4 reseñas sin recargar

### Requirement: Edición de perfil persistente y protegida [Fase 2]

Con el backend de Fase 2, las ediciones de perfil DEBEN persistir entre dispositivos y sesiones. La edición de perfil NO DEBE poder modificar el email ni el rol, aunque se intente por una vía distinta de la pantalla; esos intentos DEBEN rechazarse con `Unauthorized`. La reputación acumulada (`ratingSum` y `reviewCount`) NO queda protegida por las reglas: puede escribirse a través de la cláusula permisiva del agregado (solo con `reviewCount + 1`), una debilidad aceptada por no usar Cloud Functions (ver `design.md` D18). La pantalla de edición nunca la modifica.

#### Escenario: La edición se ve en otro dispositivo [manual]
- DADO un usuario que cambió su nombre en un dispositivo
- CUANDO inicia sesión en otro dispositivo
- ENTONCES su perfil muestra el nombre actualizado

#### Escenario: Intento de cambiar el email o el rol [manual]
- DADO un usuario autenticado
- CUANDO intenta modificar su email o su rol mediante la edición de perfil
- ENTONCES el resultado es `Unauthorized` y los dos valores no cambian
