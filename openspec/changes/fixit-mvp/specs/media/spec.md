# Delta para Fotos (`media`)

> Cambio: `fixit-mvp`. Alcance: adjuntar entre 1 y 3 fotos a una solicitud y mostrarlas. Comportamiento por fase: fotos simuladas (Fase 1), subida a Cloudinary mediante un *upload preset* sin firma (Fase 2), cámara y selector de galería reales (Fase 3).
>
> Restricción dura: el proyecto NO DEBE requerir tarjeta de crédito. Por eso las fotos NO usan Cloud Storage for Firebase (exige el plan Blaze) y Firestore guarda solo las URLs de entrega.
>
> Convención de verificación: cada escenario está cubierto por un test JVM o por un ítem del checklist manual. Los escenarios cuyo título termina en `[manual]` se verifican a mano (ver `design.md` §15).

## ADDED Requirements

### Requirement: Cantidad de fotos [Fase 1]

Una solicitud DEBE tener entre 1 y 3 fotos (los errores de validación están en `repair-requests`). La interfaz DEBE impedir agregar una cuarta foto y DEBE permitir quitar las ya agregadas.

#### Escenario: Límite en la interfaz
- DADO 3 fotos seleccionadas
- CUANDO se muestra la sección de fotos
- ENTONCES las acciones para agregar fotos están deshabilitadas

#### Escenario: Quitar una foto
- DADO 3 fotos seleccionadas
- CUANDO el usuario toca la "X" de una miniatura
- ENTONCES quedan 2 fotos y las acciones para agregar vuelven a habilitarse

### Requirement: Selección simulada [Fase 1]

En Fase 1, el botón "Agregar foto de ejemplo" DEBE agregar una imagen de ejemplo sin acceder al hardware, para poder recorrer el flujo completo. Las fotos de la solicitud publicada DEBEN conservar esas mismas imágenes de ejemplo.

#### Escenario: Agregar foto de ejemplo
- DADO 0 fotos
- CUANDO el usuario toca "Agregar foto de ejemplo"
- ENTONCES se agrega una miniatura de ejemplo y quedan 1 foto

#### Escenario: Las fotos llegan al detalle
- DADO una solicitud publicada con 2 fotos de ejemplo
- CUANDO el cliente o un profesional abre su detalle
- ENTONCES se muestran 2 fotos

### Requirement: Visualización de fotos [Fase 1]

El formulario DEBE mostrar miniaturas de las fotos agregadas y el detalle DEBE mostrar todas las fotos de la solicitud. Cada foto DEBE tener una descripción accesible del estilo "Foto 1 de 3". Mientras una imagen carga DEBE mostrarse un indicador de carga, y si no puede cargarse DEBE mostrarse un indicador de error sin afectar el resto de la pantalla.

#### Escenario: Imagen que no carga [manual]
- DADO una foto cuya imagen no puede cargarse
- CUANDO se muestra el detalle
- ENTONCES esa foto muestra un ícono de imagen no disponible y el resto del detalle funciona

#### Escenario: Descripción accesible [manual]
- DADO una solicitud con 3 fotos
- CUANDO se muestra el detalle
- ENTONCES la segunda foto tiene la descripción "Foto 2 de 3"

### Requirement: Subida de fotos a Cloudinary [Fase 2]

Con el backend de Fase 2, las fotos DEBEN subirse a Cloudinary antes de que la solicitud quede publicada, mediante una subida sin firma (*unsigned*) con un *upload preset* configurado en Cloudinary; la app NO DEBE contener ninguna clave secreta de Cloudinary. Cada imagen DEBE rotarse según su orientación EXIF y comprimirse a JPEG con lado mayor de hasta 1600 px y calidad 80 antes de subirse; el cálculo del tamaño de destino es una función pura (`targetSize(width, height, maxSide = 1600)`) que no amplía las imágenes pequeñas. La solicitud DEBE guardar en Firestore únicamente las URLs de entrega de las fotos (`photoUrls`), nunca los bytes. Si falla la subida de alguna foto o la escritura posterior de la solicitud, la solicitud NO DEBE crearse y el resultado DEBE ser un error. Una subida que no termine en 15 segundos DEBE informar `Network`; una falla de red DEBE mapearse a `Network` y cualquier otra falla del servicio de imágenes DEBE mapearse a `Unknown`.

La app NO DEBE intentar borrar fotos ya subidas (el borrado exige la API firmada de Cloudinary). Las fotos subidas de una creación fallida o de una solicitud cancelada permanecen en Cloudinary (debilidad aceptada). El preset DEBE limitar la carpeta de destino, los formatos de imagen permitidos y el tamaño máximo.

#### Escenario: Subida exitosa [manual]
- DADO una solicitud con 2 fotos
- CUANDO el cliente la publica
- ENTONCES la solicitud queda con 2 fotos accesibles por URL desde otro dispositivo

#### Escenario: Firestore guarda solo las URLs [manual]
- DADO una solicitud publicada con 2 fotos
- CUANDO se inspecciona su documento en la consola de Firestore
- ENTONCES `photoUrls` contiene 2 URLs de Cloudinary y el documento no contiene datos binarios de imagen

#### Escenario: Cálculo del tamaño de compresión
- DADO una foto de 4000 x 3000 px, otra de 3000 x 4000 px y otra de 800 x 600 px
- CUANDO se calcula el tamaño de destino con `targetSize(width, height, maxSide = 1600)`
- ENTONCES resultan 1600 x 1200, 1200 x 1600 y 800 x 600 (sin ampliar)

#### Escenario: Calidad visual de la compresión [manual]
- DADO una foto de 4000 x 3000 px
- CUANDO se sube
- ENTONCES la imagen subida es JPEG de calidad 80, su lado mayor es de 1600 px o menos y se ve nítida en el detalle
- Y una foto vertical tomada con la cámara se ve derecha (no rotada)

#### Escenario: Mapeo de fallas de la subida
- DADO un servicio de imágenes de prueba que falla por red, por tiempo agotado (más de 15 segundos en tiempo virtual) o por otro motivo
- CUANDO se intenta subir una foto
- ENTONCES el resultado es `Network`, `Network` y `Unknown` respectivamente, sin lanzar excepciones

#### Escenario: Falla la segunda foto [manual]
- DADO que falla la subida de la segunda de 2 fotos
- CUANDO el cliente publica
- ENTONCES no se crea la solicitud y se informa un error
- Y la primera foto ya subida permanece en Cloudinary sin que la app intente borrarla

#### Escenario: Falla la escritura de la solicitud [manual]
- DADO que las 2 fotos se subieron y falla la escritura de la solicitud
- CUANDO el cliente publica
- ENTONCES no se crea la solicitud y se informa un error
- Y las 2 fotos permanecen en Cloudinary sin que la app intente borrarlas

#### Escenario: Preset sin firma y sin secretos en la app [manual]
- DADO el APK de Fase 2 y el *upload preset* configurado en Cloudinary
- CUANDO se revisan el código, los recursos y la configuración del preset
- ENTONCES no hay API secret de Cloudinary en el repositorio ni en el APK
- Y el preset limita carpeta, formatos de imagen y tamaño máximo

#### Escenario: Cuentas sin tarjeta [manual]
- DADO el proyecto Firebase y la cuenta de Cloudinary usados en Fase 2
- CUANDO se revisan sus paneles de facturación
- ENTONCES Firebase está en el plan Spark, Cloudinary está en su plan gratuito y ninguna cuenta tiene tarjeta cargada

### Requirement: Selector de galería [Fase 3]

"Galería" DEBE permitir elegir solo imágenes, sin pedir permisos de almacenamiento, y hasta la cantidad de fotos que falte para llegar a 3. Cuando falta exactamente 1 foto, DEBE usarse el selector de una sola imagen (`PickVisualMedia`); en los demás casos, el selector múltiple limitado a la cantidad que falta (`PickMultipleVisualMedia(faltantes)`), porque el selector múltiple exige un máximo de al menos 2. Si el usuario cierra el selector sin elegir, la lista de fotos NO DEBE cambiar.

#### Escenario: Selección múltiple [manual]
- DADO 1 foto ya cargada
- CUANDO el usuario abre la galería
- ENTONCES puede elegir hasta 2 imágenes

#### Escenario: 2 fotos ya cargadas [manual]
- DADO 2 fotos ya cargadas
- CUANDO el usuario abre la galería
- ENTONCES se abre el selector de una sola imagen y puede elegir 1

#### Escenario: Cancelar la selección [manual]
- DADO 1 foto ya cargada
- CUANDO el usuario cierra el selector sin elegir
- ENTONCES la lista sigue teniendo 1 foto

### Requirement: Cámara [Fase 3]

"Cámara" DEBE abrir la cámara del sistema para capturar una foto y agregarla a la solicitud, sin pedir un permiso de cámara a la persona. Si la captura se cancela, NO DEBE agregarse ninguna foto. Si el dispositivo no tiene cámara, la acción "Cámara" NO DEBE mostrarse.

#### Escenario: Foto tomada [manual]
- DADO 1 foto cargada y un dispositivo con cámara
- CUANDO el usuario captura una foto
- ENTONCES la lista pasa a tener 2 fotos

#### Escenario: Captura cancelada [manual]
- DADO 1 foto cargada
- CUANDO el usuario cancela la captura
- ENTONCES la lista sigue teniendo 1 foto

#### Escenario: Dispositivo sin cámara [manual]
- DADO un dispositivo sin cámara
- CUANDO se muestra la sección de fotos
- ENTONCES la acción "Cámara" no se muestra y "Galería" sigue disponible
