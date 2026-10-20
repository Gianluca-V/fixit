# Propuesta: ícono de la app, mapa y autocompletado de dirección (`icon-map-address`)

> **Restricción dura**: nada de este cambio requiere tarjeta de crédito ni claves de API. El mapa usa datos de OpenStreetMap; el autocompletado usa un servicio público sin clave.
>
> **Orden con `fixit-mvp`**: este cambio modifica requisitos que hoy existen solo como deltas de `fixit-mvp` (`openspec/specs/` está vacío). Por eso DEBE archivarse **después** de `fixit-mvp`. Las notificaciones push siguen en la Fase 4 de `fixit-mvp` y no forman parte de este cambio.

## Why

Hoy la ubicación de una solicitud y el punto base del profesional solo pueden obtenerse con "Usar mi ubicación" (GPS). Si el usuario no está en el lugar de la reparación (por ejemplo, pide un arreglo para la casa de un familiar) o el GPS falla, no hay forma de indicar el lugar correcto. Además, el cliente no tiene una zona propia en su perfil y la app no tiene ícono: muestra el ícono genérico de Android. El equipo pidió estas tres mejoras antes de avanzar con las notificaciones push.

## What Changes

- **Ícono de la app**: ícono adaptativo vectorial diseñado por el equipo (herramienta sobre el color primario del tema), con capa monocroma para los íconos temáticos de Android 13 o superior.
- **Selector de ubicación en un mapa** (nuevo): una pantalla completa con un mapa de OpenStreetMap y un pin fijo en el centro. El usuario mueve el mapa hasta el lugar y confirma; la dirección se obtiene de las coordenadas del centro y sigue siendo editable.
- **Autocompletado de dirección** (nuevo): un campo de búsqueda dentro del selector que sugiere direcciones mientras se escribe (desde 3 caracteres, con espera de 300 ms entre teclas). Elegir una sugerencia mueve el mapa a ese punto.
- El selector se ofrece junto a "Usar mi ubicación", con el botón "Elegir en el mapa", en tres pantallas:
  - Formulario de creación de solicitud.
  - Edición de perfil del cliente.
  - Edición de perfil del profesional (punto base).
- **Zona del cliente** (nuevo): el cliente PUEDE guardar en su perfil una ubicación opcional ("de dónde es"). Se muestra en su perfil y precarga la ubicación al crear una solicitud.
- Las coordenadas siguen sin poder escribirse como números: provienen del GPS, del pin del mapa o de una sugerencia elegida.

## Capabilities

### New Capabilities

- `address-search`: autocompletado de direcciones (reglas de la búsqueda, sugerencias, errores) y selector de ubicación en un mapa (pin central, dirección del punto elegido, confirmación y cancelación).
- `app-icon`: ícono adaptativo de la app con capa monocroma. Es un dominio nuevo fuera de la lista de `drafts/`: ningún dominio existente cubre la identidad visual de la app.

### Modified Capabilities

- `location`: el requisito "Dirección editable" se amplía para que las coordenadas también provengan del mapa o de una sugerencia, nunca de un número escrito a mano.
- `profiles`:
  - "Perfil de cliente" muestra la zona.
  - "Edición de categorías, punto base y radio" permite elegir el punto base en el mapa.
  - Se agrega la edición de la zona del cliente.
- `repair-requests`: el formulario de creación ofrece "Elegir en el mapa" y precarga la zona del cliente.

## Alcance

**Dentro**: lo listado en *What Changes*, con implementaciones simuladas para las pruebas y reales con red.

**Fuera**:
- El mapa en el detalle de una solicitud.
- El feed como mapa con pines.
- El selector en el registro: el registro del profesional sigue con "Usar mi ubicación"; se puede agregar en un cambio posterior.
- Mapas sin conexión y descarga anticipada de teselas.
- Rutas o indicaciones para llegar.
- Notificaciones push (siguen en `fixit-mvp`, Fase 4).

**Fase**: los requisitos nuevos llevan la etiqueta `[Fase 3]` porque extienden la ubicación real de la Fase 3 y necesitan red. Las pruebas JVM usan implementaciones simuladas, igual que en la Fase 1.

## Impact

| Módulo | Cambio |
|---|---|
| `:app` | Ícono adaptativo (`res/mipmap-anydpi-v26`, `res/drawable`) y `android:icon` / `android:roundIcon` en el manifest. |
| `:core:domain` | Nueva interfaz `AddressSearch` y caso de uso `SearchAddressUseCase`. `User` gana una ubicación opcional del cliente; `ProfileForm` y la validación de `UpdateProfileUseCase` la incluyen. |
| `:core:data` | `FakeAddressSearch` y `PhotonAddressSearch` (OkHttp, cliente compartido de `NetworkModule`). DTO de usuario con los campos de la zona del cliente. Binding de Hilt. |
| `:core:ui` | `MapPickerDialog` + `MapPickerViewModel` (gana Hilt y KSP) y el botón "Elegir en el mapa" en `ProfileFormFields`. Nueva dependencia: MapLibre Native Android con teselas de OpenFreeMap (datos de OpenStreetMap, sin clave). |
| `:features:client`, `:features:professional` | Integran el selector en creación de solicitud y en ambas ediciones de perfil. Sin dependencias nuevas entre módulos. |
| `firestore.rules` | La edición de perfil admite los campos de la zona del cliente. |

**Servicios externos (gratuitos, sin clave ni tarjeta)**:
- **OpenFreeMap**: teselas vectoriales con datos de OpenStreetMap.
- **Photon** (komoot): búsqueda de direcciones, con uso razonable y atribución.
- **Nominatim**: se descarta porque su política de uso prohíbe el autocompletado.

La dirección del punto elegido en el mapa se sigue obteniendo con el `Geocoder` del dispositivo (`GeocoderAddressResolver`, Fase 3).

## Riesgos

| Riesgo | Prob. | Mitigación |
|---|---|---|
| Photon o OpenFreeMap no responden o cambian su política | Media | El selector funciona igual sin sugerencias (mensaje de búsqueda no disponible) y "Usar mi ubicación" sigue disponible. La búsqueda queda detrás de una interfaz y se reemplaza con un binding. |
| Superar el uso razonable de Photon | Baja | Mínimo de 3 caracteres, espera de 300 ms, máximo de 5 resultados, cancelación de la búsqueda anterior y un `User-Agent` identificable. Es un TP con pocos usuarios. |
| Peso de MapLibre en el APK (varios MB por ABI) | Media | Aceptado en el alcance académico. Si molesta, se publica con *App Bundle*. |
| La zona del cliente queda legible para cualquier usuario autenticado | Media | Misma debilidad conocida que el teléfono (`firestore.rules`). La zona es opcional y el texto lo aclara. Se documenta junto a las otras debilidades aceptadas. |
| Conflicto con el trabajo de Gian en `main` (`ProfileFormFields`, pantallas de edición) | Media | Rama propia (`feat/icon-map-address`), con PR chico por unidad y rebase frecuente. |

## Plan de reversión

- **Ícono**: borrar los recursos y los atributos del manifest.
- **Mapa y autocompletado**: quitar el botón "Elegir en el mapa" de las tres pantallas y el binding de `AddressSearch`. "Usar mi ubicación" sigue funcionando igual que en la Fase 3.
- **Zona del cliente**: es un campo opcional. Los documentos sin esos campos se leen igual y las reglas viejas solo rechazan la escritura de esos campos.

## Supuestos (a confirmar en la revisión)

1. La zona del cliente es **opcional**: no se pide en el registro y puede quedar vacía.
2. Si el cliente tiene zona, el formulario de creación la **precarga**. El cliente puede cambiarla antes de publicar.
3. El mapa abre centrado en la ubicación ya cargada en el formulario. Si no hay ninguna, abre en Florencio Varela. Nunca pide el permiso de ubicación por sí solo.
