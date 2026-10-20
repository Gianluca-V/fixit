# Delta para Perfiles y reputación (`profiles`)

> Cambio: `icon-map-address`. Alcance: zona opcional del cliente y elección del punto base del profesional en el mapa. Se modifican requisitos que hoy existen como deltas de `fixit-mvp`: este cambio se archiva después de `fixit-mvp`.
>
> Convención de verificación: cada escenario está cubierto por un test JVM o por un ítem del checklist manual. Los escenarios cuyo título termina en `[manual]` se verifican a mano.

## MODIFIED Requirements

### Requirement: Perfil de cliente [Fase 1]

La pestaña Perfil del cliente DEBE mostrar nombre, email, teléfono, rol y, desde la Fase 3, su zona (la dirección guardada, o "Sin zona" si no tiene), y las acciones "Editar perfil" y "Cerrar sesión".

#### Escenario: Visualizar perfil de cliente [manual]
- DADO un cliente autenticado
- CUANDO abre la pestaña Perfil
- ENTONCES se muestran su nombre, email, teléfono y rol
- Y están disponibles "Editar perfil" y "Cerrar sesión"

#### Escenario: Perfil de cliente con zona [manual]
- DADO un cliente con la zona "Calle 123, Quilmes"
- CUANDO abre la pestaña Perfil
- ENTONCES se muestra "Calle 123, Quilmes" como su zona

#### Escenario: Perfil de cliente sin zona [manual]
- DADO un cliente sin zona guardada
- CUANDO abre la pestaña Perfil
- ENTONCES se muestra "Sin zona"

### Requirement: Edición de categorías, punto base y radio [Fase 1]

El profesional DEBE poder editar además sus categorías, su punto base y su radio. Las reglas de validación son las del registro: al menos 1 categoría (`Validation.CategoriesRequired`), punto base obligatorio (`Validation.BaseLocationRequired`), coordenadas del punto base válidas (`Validation.InvalidCoordinates`) y radio entero de 1..50 km (`Validation.InvalidRadius`). El punto base DEBE obtenerse de la ubicación del dispositivo o, desde la Fase 3, del selector del mapa ("Elegir en el mapa"); nunca se ingresa como coordenadas. La edición NO DEBE modificar la reputación.

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

#### Escenario: Punto base elegido en el mapa
- DADO un profesional con punto base en Florencio Varela
- CUANDO elige en el mapa el punto Q con la dirección "Av. Mitre 500, Quilmes" y guarda
- ENTONCES el punto base guardado es Q con esa dirección
- Y las categorías, el radio y la reputación no cambian

## ADDED Requirements

### Requirement: Zona del cliente [Fase 3]

El cliente PUEDE guardar en su perfil una zona opcional: una ubicación con coordenadas y dirección. La zona NO DEBE pedirse en el registro. En "Editar perfil", el cliente DEBE poder fijarla con "Usar mi ubicación" o con "Elegir en el mapa", editar su dirección y quitarla con "Quitar zona". Si la zona tiene coordenadas fuera de rango, el guardado DEBE devolver `Validation.InvalidCoordinates` y NO DEBE persistir ningún cambio. Una zona con dirección vacía DEBE rechazarse con `Validation.LocationRequired`. Guardar la zona NO DEBE modificar el nombre, el teléfono, el email ni el rol. La zona de un profesional no existe: su ubicación es el punto base.

#### Escenario: Guardar la zona desde el mapa
- DADO un cliente sin zona
- CUANDO en "Editar perfil" elige en el mapa el punto Q con la dirección "Calle 123, Quilmes" y guarda
- ENTONCES su perfil tiene la zona Q con la dirección "Calle 123, Quilmes"

#### Escenario: Guardar la zona con la ubicación actual
- DADO un cliente sin zona y la ubicación actual P con la dirección "Av. San Martín 2000, Florencio Varela"
- CUANDO en "Editar perfil" toca "Usar mi ubicación" y guarda
- ENTONCES su perfil tiene la zona P con esa dirección

#### Escenario: Quitar la zona
- DADO un cliente con la zona "Calle 123, Quilmes"
- CUANDO toca "Quitar zona" y guarda
- ENTONCES su perfil queda sin zona

#### Escenario: Zona con dirección vacía
- DADO un cliente que eligió el punto Q en el mapa y borró la dirección
- CUANDO guarda
- ENTONCES el resultado es `Validation.LocationRequired` y no se persiste ningún cambio

#### Escenario: Zona con coordenadas inválidas
- DADO una zona con latitud 91
- CUANDO el cliente guarda
- ENTONCES el resultado es `Validation.InvalidCoordinates` y no se persiste ningún cambio

#### Escenario: Zona precargada al editar
- DADO un cliente con la zona "Calle 123, Quilmes"
- CUANDO abre "Editar perfil"
- ENTONCES la zona del formulario contiene esa ubicación

#### Escenario: El registro no pide zona
- DADO la pantalla de registro con el rol cliente
- CUANDO el usuario se registra
- ENTONCES la cuenta se crea sin zona

### Requirement: Zona del cliente persistente [Fase 3]

Con el backend de la Fase 2, la zona del cliente DEBE guardarse en su documento de usuario y solo el propio cliente DEBE poder modificarla. Un usuario sin los campos de zona DEBE leerse como un cliente sin zona.

#### Escenario: Usuario anterior sin zona
- DADO un documento de usuario cliente creado antes de este cambio (sin campos de zona)
- CUANDO se lee el usuario
- ENTONCES es un cliente sin zona y no hay error

#### Escenario: La zona se ve en otro dispositivo [manual]
- DADO un cliente que guarda la zona "Calle 123, Quilmes" en el dispositivo A
- CUANDO abre su perfil en el dispositivo B
- ENTONCES ve la misma zona

#### Escenario: Otro usuario no puede cambiar la zona [manual]
- DADO un usuario autenticado distinto del dueño
- CUANDO intenta escribir los campos de zona en el documento de otro usuario
- ENTONCES las reglas de Firestore rechazan la escritura
