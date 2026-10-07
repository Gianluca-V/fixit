# Delta para Ubicación (`location`)

> Cambio: `fixit-mvp`. Alcance: coordenadas válidas, distancia Haversine, obtención de la ubicación para una solicitud y para el punto base del profesional, y dirección legible. Comportamiento por fase: simulado (Fase 1), búsqueda por proximidad con backend (Fase 2), GPS real con permisos (Fase 3).
>
> Convención de verificación: cada escenario está cubierto por un test JVM o por un ítem del checklist manual. Los escenarios cuyo título termina en `[manual]` se verifican a mano (ver `design.md` §15). El dominio devuelve números (distancias en km).

## ADDED Requirements

### Requirement: Coordenadas válidas [Fase 1]

Una coordenada DEBE tener latitud en [-90, 90] y longitud en [-180, 180]. Una ubicación DEBE agrupar una coordenada y una dirección en texto. Una coordenada fuera de rango DEBE rechazarse con `Validation.InvalidCoordinates`. La validación (`Validators.coordinates`) DEBE aplicarse al crear una solicitud y al fijar el punto base de un profesional (registro y edición de perfil).

#### Escenario: Latitud fuera de rango
- DADO una latitud de 91
- CUANDO se validan las coordenadas con `Validators.coordinates`
- ENTONCES el resultado es `Validation.InvalidCoordinates`

#### Escenario: Límites exactos válidos
- DADO coordenadas (-90, -180) y (90, 180)
- CUANDO se validan con `Validators.coordinates`
- ENTONCES ambas son aceptadas

### Requirement: Distancia Haversine [Fase 1]

El sistema DEBE calcular la distancia entre dos coordenadas con la fórmula de Haversine y un radio terrestre de 6371.0088 km. La distancia DEBE ser simétrica y valer 0 para puntos iguales. La distancia que se informa al usuario DEBE redondearse a 0.1 km en el dominio; la comparación con el radio usa la distancia sin redondear.

#### Escenario: Mismo punto
- DADO dos coordenadas iguales
- CUANDO se calcula la distancia
- ENTONCES es `0.0` km

#### Escenario: Distancia conocida
- DADO Florencio Varela centro (-34.8065, -58.2788) y Quilmes centro (-34.7206, -58.2546)
- CUANDO se calcula la distancia
- ENTONCES es aproximadamente 9.8 km (tolerancia de 0.2 km)

#### Escenario: Simetría
- DADO dos coordenadas distintas a y b
- CUANDO se calcula la distancia en ambos sentidos
- ENTONCES `distancia(a, b)` y `distancia(b, a)` son iguales (tolerancia 1e-9)

#### Escenario: Cruce del antimeridiano
- DADO las coordenadas (0, 179.9) y (0, -179.9)
- CUANDO se calcula la distancia
- ENTONCES es aproximadamente 22.2 km (tolerancia de 0.05 km)

#### Escenario: Un grado de latitud
- DADO las coordenadas (0, 0) y (1, 0)
- CUANDO se calcula la distancia
- ENTONCES es 111.195 km (tolerancia de 0.01 km)

#### Escenario: Redondeo de la distancia a 0.1 km
- DADO distancias calculadas de 2.04 km y de 2.06 km
- CUANDO se redondean para informarlas
- ENTONCES resultan 2.0 y 2.1 respectivamente

### Requirement: Obtención de la ubicación actual [Fase 1]

Las pantallas DEBEN obtener la ubicación a través de un único origen que combina coordenadas y dirección, de modo que las pantallas y los flujos no cambien entre fases. Si no se puede resolver la dirección, el sistema DEBE devolver las coordenadas con la dirección vacía para que el usuario la complete.

#### Escenario: Dirección no resoluble
- DADO que la resolución de la dirección falla con `Network`
- CUANDO se obtiene la ubicación actual
- ENTONCES el resultado es exitoso con las coordenadas y la dirección vacía

### Requirement: Ubicación simulada [Fase 1]

En Fase 1, "Usar mi ubicación" y "Usar mi ubicación como base" DEBEN completar una ubicación fija configurable, por defecto Florencio Varela (-34.8065, -58.2788) con la dirección "Av. San Martín 2000, Florencio Varela", sin acceder al hardware. PUEDE ofrecerse en modo de depuración un selector de ubicaciones de prueba (Florencio Varela, Quilmes, Berazategui) para verificar el matching. Si la obtención de la ubicación falla (`LocationUnavailable`), DEBE mostrarse un mensaje de ubicación no disponible, la dirección DEBE poder escribirse a mano, pero NO DEBE poder publicarse la solicitud hasta obtener coordenadas.

#### Escenario: Usar mi ubicación en Fase 1
- DADO la pantalla de creación de solicitud
- CUANDO el usuario toca "Usar mi ubicación"
- ENTONCES se completan las coordenadas y la dirección de la ubicación simulada

#### Escenario: Falla simulada de ubicación
- DADO que la ubicación simulada está configurada para fallar con `LocationUnavailable`
- CUANDO el usuario toca "Usar mi ubicación"
- ENTONCES se muestra el mensaje de ubicación no disponible y el campo de dirección sigue editable
- Y al intentar publicar el resultado es `Validation.LocationRequired`

### Requirement: Dirección editable [Fase 1]

El usuario DEBE poder editar el texto de la dirección. Las coordenadas NO DEBEN poder editarse manualmente; provienen siempre de la obtención de ubicación.

#### Escenario: Editar la dirección
- DADO una ubicación obtenida con coordenadas P
- CUANDO el usuario cambia la dirección a "Calle 123, Quilmes" y publica
- ENTONCES la solicitud se crea con esa dirección y las coordenadas P

### Requirement: Búsqueda por proximidad con backend [Fase 2]

Con el backend de Fase 2, las solicitudes cercanas DEBEN encontrarse sin recorrer todas las solicitudes, y el resultado final DEBE ser el mismo que con el filtro exacto de Haversine: NO DEBE omitir solicitudes dentro del radio ni incluir solicitudes fuera de él.

#### Escenario: Dentro del radio [manual]
- DADO un profesional con radio 10 km y una solicitud compatible a 9.9 km
- CUANDO abre el feed
- ENTONCES la solicitud aparece

#### Escenario: Cerca pero fuera del radio [manual]
- DADO un profesional con radio 10 km y una solicitud compatible a 10.4 km, dentro del área aproximada de búsqueda
- CUANDO abre el feed
- ENTONCES la solicitud no aparece

### Requirement: Permisos de ubicación [Fase 3]

La app DEBE solicitar el permiso de ubicación solo cuando el usuario toca "Usar mi ubicación" (o "Usar mi ubicación como base"), DEBE mostrar una justificación si el sistema lo indica y DEBE aceptar ubicación aproximada. Si el usuario deniega, DEBE mostrarse un mensaje explicativo, la dirección DEBE seguir editable y la publicación DEBE requerir reintentar la ubicación. Si el usuario deniega de forma permanente, DEBE ofrecerse un botón que abra los ajustes de la aplicación.

#### Escenario: Permiso concedido [manual]
- DADO el permiso de ubicación no otorgado
- CUANDO el usuario toca "Usar mi ubicación" y concede el permiso
- ENTONCES se obtiene la ubicación real del dispositivo

#### Escenario: Permiso denegado [manual]
- DADO el permiso de ubicación no otorgado
- CUANDO el usuario toca "Usar mi ubicación" y deniega el permiso
- ENTONCES se muestra un mensaje explicativo y la dirección sigue editable
- Y la publicación requiere reintentar la ubicación

#### Escenario: Permiso denegado de forma permanente [manual]
- DADO que el usuario marcó "No volver a preguntar"
- CUANDO toca "Usar mi ubicación"
- ENTONCES se ofrece un botón que abre los ajustes de la aplicación

#### Escenario: Solo se pide al usar la acción [manual]
- DADO el permiso no otorgado
- CUANDO el usuario abre el formulario de creación sin tocar "Usar mi ubicación"
- ENTONCES no se muestra ninguna solicitud de permiso

### Requirement: GPS real [Fase 3]

Con el hardware real, la obtención de la ubicación DEBE devolver `LocationUnavailable` si el sistema no entrega una ubicación dentro de 10 segundos o si la ubicación del dispositivo está desactivada, y DEBE resolver la dirección legible a partir de las coordenadas. El profesional DEBE poder usar la ubicación real como punto base, tanto en el registro como al editar su perfil.

#### Escenario: GPS desactivado [manual]
- DADO la ubicación del sistema desactivada
- CUANDO se solicita la ubicación
- ENTONCES el resultado es `LocationUnavailable`

#### Escenario: Tiempo de espera agotado [manual]
- DADO que el dispositivo no entrega ubicación en 10 segundos
- CUANDO se solicita la ubicación
- ENTONCES el resultado es `LocationUnavailable`

#### Escenario: Punto base real del profesional [manual]
- DADO el registro o la edición de perfil de un profesional con el permiso concedido
- CUANDO toca "Usar mi ubicación como base"
- ENTONCES el punto base se completa con la ubicación real y su dirección
