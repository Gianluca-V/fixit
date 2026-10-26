# Delta para Solicitudes de reparación (`repair-requests`)

> Cambio: `icon-map-address`. Alcance: el formulario de creación ofrece "Elegir en el mapa" y precarga la zona del cliente. Se modifica un requisito que hoy existe como delta de `fixit-mvp`: este cambio se archiva después de `fixit-mvp`.
>
> Convención de verificación: cada escenario está cubierto por un test JVM o por un ítem del checklist manual. Los escenarios cuyo título termina en `[manual]` se verifican a mano.

## MODIFIED Requirements

### Requirement: Formulario de creación [Fase 1]

La pantalla de creación DEBE ofrecer:
- Un selector de categoría de selección única.
- Un campo de descripción con contador `n/500`.
- Una sección de fotos (agregar y quitar, máximo 3).
- La ubicación: botón "Usar mi ubicación", desde la Fase 3 también el botón "Elegir en el mapa" (ver `address-search`), y la dirección editable.
- El botón "Publicar".

Desde la Fase 3, si el cliente tiene zona, el formulario DEBE abrir con la ubicación precargada con esa zona. El cliente PUEDE reemplazarla con cualquiera de los dos botones. Mientras se publica, el botón DEBE estar deshabilitado. Si la publicación falla, el formulario DEBE conservar lo cargado. Al publicar con éxito, el cliente DEBE ver el detalle de la solicitud recién creada.

#### Escenario: Selección única de categoría
- DADO `PLUMBING` seleccionado
- CUANDO el usuario toca `GAS`
- ENTONCES solo `GAS` queda seleccionado

#### Escenario: Contador de descripción [manual]
- DADO una descripción de 120 caracteres
- CUANDO se muestra el formulario
- ENTONCES el contador muestra "120/500"

#### Escenario: Error de validación visible
- DADO que el cliente publica sin fotos
- CUANDO el resultado es `Validation.PhotosRequired`
- ENTONCES el error se muestra en la sección de fotos y el formulario conserva lo cargado

#### Escenario: Ubicación precargada con la zona del cliente
- DADO un cliente con la zona Z y la dirección "Calle 123, Quilmes"
- CUANDO abre el formulario de creación
- ENTONCES la ubicación del formulario es Z con la dirección "Calle 123, Quilmes"

#### Escenario: Cliente sin zona
- DADO un cliente sin zona
- CUANDO abre el formulario de creación
- ENTONCES la ubicación está vacía, igual que antes de este cambio

#### Escenario: Publicar con una ubicación elegida en el mapa
- DADO un formulario válido en el que el cliente confirmó en el mapa el punto Q con la dirección "Calle 12 450, Berazategui"
- CUANDO publica
- ENTONCES la solicitud se crea con las coordenadas Q y esa dirección

#### Escenario: Elegir en el mapa no pide permiso [manual]
- DADO el permiso de ubicación no otorgado
- CUANDO el cliente toca "Elegir en el mapa", elige un punto y confirma
- ENTONCES no se muestra ninguna solicitud de permiso y la ubicación queda cargada
