# Delta para Ubicación (`location`)

> Cambio: `icon-map-address`. Alcance: las coordenadas de una ubicación pueden provenir también del selector en el mapa o de una sugerencia de dirección. Se modifica un requisito que hoy existe como delta de `fixit-mvp`: este cambio se archiva después de `fixit-mvp`.
>
> Convención de verificación: cada escenario está cubierto por un test JVM o por un ítem del checklist manual. Los escenarios cuyo título termina en `[manual]` se verifican a mano.

## MODIFIED Requirements

### Requirement: Dirección editable [Fase 1]

El usuario DEBE poder editar el texto de la dirección. Las coordenadas NO DEBEN poder editarse manualmente como números: provienen siempre de la obtención de ubicación ("Usar mi ubicación") o, desde la Fase 3, del punto elegido en el selector del mapa o de una sugerencia de dirección (ver `address-search`). Elegir una ubicación en el mapa reemplaza juntas las coordenadas y la dirección.

#### Escenario: Editar la dirección
- DADO una ubicación obtenida con coordenadas P
- CUANDO el usuario cambia la dirección a "Calle 123, Quilmes" y publica
- ENTONCES la solicitud se crea con esa dirección y las coordenadas P

#### Escenario: Ubicación elegida en el mapa
- DADO una ubicación obtenida con "Usar mi ubicación" en el punto P
- CUANDO el usuario confirma en el selector el punto Q con la dirección "Calle 12 450, Berazategui"
- ENTONCES la ubicación del formulario pasa a ser Q con la dirección "Calle 12 450, Berazategui"
