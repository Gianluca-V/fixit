# Delta para Búsqueda de direcciones y selector en el mapa (`address-search`)

> Cambio: `icon-map-address`. Alcance: autocompletado de direcciones y selector de ubicación en un mapa. Se usa desde la creación de solicitud y desde la edición de perfil de ambos roles.
>
> Convención de verificación: cada escenario está cubierto por un test JVM o por un ítem del checklist manual. Los escenarios cuyo título termina en `[manual]` se verifican a mano (ver `design.md`, checklist manual).

## Purpose

Permite indicar una ubicación sin depender del GPS: el usuario busca una dirección con sugerencias o mueve un mapa de OpenStreetMap hasta el lugar, y la app obtiene coordenadas y una dirección legible.

## ADDED Requirements

### Requirement: Reglas de la búsqueda de direcciones [Fase 3]

La búsqueda DEBE recortar el texto ingresado. Con menos de 3 caracteres NO DEBE consultar el servicio y DEBE devolver una lista vacía. Con 3 o más caracteres DEBE devolver como máximo 5 sugerencias. Cada sugerencia tiene una dirección legible no vacía y coordenadas válidas. Las sugerencias sin dirección o con coordenadas fuera de rango DEBEN descartarse. La búsqueda DEBERÍA priorizar los resultados cercanos al centro actual del mapa. Si el servicio falla, el resultado DEBE ser un error de dominio (`Network` o `Unknown`) y NO DEBE lanzar una excepción.

#### Escenario: Texto demasiado corto
- DADO el texto "  Av " (2 caracteres tras recortar)
- CUANDO se buscan direcciones
- ENTONCES el resultado es una lista vacía y no se consulta el servicio

#### Escenario: Máximo de sugerencias
- DADO un servicio que devuelve 8 resultados válidos para "San Martín"
- CUANDO se buscan direcciones
- ENTONCES el resultado tiene 5 sugerencias, en el orden del servicio

#### Escenario: Sugerencias inválidas descartadas
- DADO un servicio que devuelve un resultado sin dirección y otro con latitud 91
- CUANDO se buscan direcciones
- ENTONCES ninguno de los dos aparece en el resultado

#### Escenario: Servicio no disponible
- DADO que el servicio de búsqueda falla por falta de red
- CUANDO se buscan direcciones
- ENTONCES el resultado es el error `Network`

### Requirement: Dirección legible de una sugerencia [Fase 3]

La dirección de una sugerencia DEBE armarse como "calle número, localidad" cuando el servicio informa calle. Sin calle, DEBE usarse el nombre del lugar con la localidad. Sin ninguno de ellos, DEBE usarse solo la localidad. Las partes vacías DEBEN omitirse sin dejar comas sueltas.

#### Escenario: Calle con número y localidad
- DADO un resultado con calle "Avenida San Martín", número "2000" y localidad "Florencio Varela"
- CUANDO se arma la dirección
- ENTONCES es "Avenida San Martín 2000, Florencio Varela"

#### Escenario: Lugar sin calle
- DADO un resultado con nombre "Universidad Nacional Arturo Jauretche", sin calle y con localidad "Florencio Varela"
- CUANDO se arma la dirección
- ENTONCES es "Universidad Nacional Arturo Jauretche, Florencio Varela"

#### Escenario: Calle sin número
- DADO un resultado con calle "Calle 10", sin número y sin localidad
- CUANDO se arma la dirección
- ENTONCES es "Calle 10"

### Requirement: Autocompletado mientras se escribe [Fase 3]

En el selector, las sugerencias DEBEN actualizarse mientras el usuario escribe, esperando 300 ms sin nuevas teclas antes de buscar. Una búsqueda nueva DEBE descartar el resultado de la anterior, de modo que nunca se muestren sugerencias de un texto viejo. Si la búsqueda falla, DEBE mostrarse un mensaje de búsqueda no disponible y el mapa DEBE seguir usable. Borrar el texto DEBE ocultar las sugerencias.

#### Escenario: Espera entre teclas
- DADO el selector abierto
- CUANDO el usuario escribe "Cal", "Call" y "Calle" con menos de 300 ms entre cada uno
- ENTONCES se hace una sola búsqueda, con "Calle"

#### Escenario: Resultado viejo descartado
- DADO una búsqueda de "Calle 1" que todavía no respondió
- CUANDO el usuario cambia el texto a "Calle 12" y esa búsqueda responde
- ENTONCES las sugerencias son las de "Calle 12" aunque la de "Calle 1" responda después

#### Escenario: Búsqueda fallida
- DADO que la búsqueda devuelve `Network`
- CUANDO el usuario escribe "San Martín"
- ENTONCES se muestra el mensaje de búsqueda no disponible y no hay sugerencias
- Y el usuario puede seguir moviendo el mapa y confirmar

### Requirement: Selector de ubicación en el mapa [Fase 3]

El selector DEBE mostrar un mapa con datos de OpenStreetMap, un pin fijo en el centro, un campo de búsqueda, la dirección del punto elegido (editable) y las acciones "Confirmar" y "Cancelar". DEBE abrir centrado en la ubicación ya cargada en el formulario; si no hay ninguna, DEBE abrir en Florencio Varela (-34.8065, -58.2788). NO DEBE pedir el permiso de ubicación. Cuando el mapa deja de moverse, el punto elegido DEBE pasar a ser el centro y su dirección DEBE resolverse a partir de las coordenadas. Si la dirección no se puede resolver, DEBE quedar vacía y editable. Elegir una sugerencia DEBE mover el mapa a ese punto y usar la dirección de la sugerencia, sin volver a resolverla. "Confirmar" DEBE devolver al formulario las coordenadas y la dirección del punto elegido. "Cancelar" o volver atrás NO DEBE modificar el formulario.

#### Escenario: Apertura sin ubicación previa
- DADO un formulario sin ubicación cargada
- CUANDO el usuario abre el selector
- ENTONCES el punto inicial es Florencio Varela (-34.8065, -58.2788) y no se pide ningún permiso

#### Escenario: Apertura con ubicación previa
- DADO un formulario con la ubicación P y la dirección "Calle 123, Quilmes"
- CUANDO el usuario abre el selector
- ENTONCES el punto inicial es P y la dirección mostrada es "Calle 123, Quilmes"

#### Escenario: Mover el mapa resuelve la dirección
- DADO el selector abierto
- CUANDO el mapa se detiene en el punto Q y la dirección de Q es "Av. Calchaquí 3950, Florencio Varela"
- ENTONCES el punto elegido es Q y la dirección mostrada es "Av. Calchaquí 3950, Florencio Varela"

#### Escenario: Dirección no resoluble en el mapa
- DADO que resolver la dirección falla con `Network`
- CUANDO el mapa se detiene en el punto Q
- ENTONCES el punto elegido es Q y la dirección queda vacía y editable

#### Escenario: Elegir una sugerencia
- DADO la sugerencia "Calle 12 450, Berazategui" en el punto S
- CUANDO el usuario la elige
- ENTONCES el punto elegido es S, la dirección es "Calle 12 450, Berazategui", las sugerencias se ocultan y no se resuelve la dirección de nuevo

#### Escenario: Confirmar
- DADO el punto elegido Q con la dirección editada a "Casa de mi madre, Calle 5 100"
- CUANDO el usuario toca "Confirmar"
- ENTONCES el formulario recibe las coordenadas Q y la dirección "Casa de mi madre, Calle 5 100"

#### Escenario: Cancelar
- DADO un formulario con la ubicación P y el selector movido al punto Q
- CUANDO el usuario toca "Cancelar" o vuelve atrás
- ENTONCES el formulario conserva la ubicación P

#### Escenario: Mapa y atribución visibles [manual]
- DADO el selector abierto con conexión
- CUANDO se muestra el mapa
- ENTONCES se ven las calles de la zona, el pin centrado y la atribución "© OpenStreetMap"

#### Escenario: Mapa sin conexión [manual]
- DADO el dispositivo sin conexión
- CUANDO el usuario abre el selector
- ENTONCES la pantalla no se cierra, el mapa muestra lo que tenga en caché o queda vacío y "Cancelar" funciona

#### Escenario: Gestos del mapa [manual]
- DADO el selector abierto
- CUANDO el usuario arrastra el mapa y hace zoom con dos dedos
- ENTONCES el mapa responde y la dirección se actualiza al soltar
