# Especificación: Fotos (`media`)

## Propósito

Permitir adjuntar entre 1 y 3 fotos a una solicitud, desde la cámara o el Photo Picker de Android,
mostrarlas en miniaturas y en el detalle, y (Fase 2) subirlas a Firebase Storage.

Módulos: `:core:domain` (`PhotoRef`, reglas de cantidad en `CreateRepairRequestUseCase`),
`:core:data` (`PhotoUploader` interno: `FakePhotoUploader`, `FirebaseStoragePhotoUploader`; la subida
es un detalle de `RepairRequestRepository.create`),
`:core:ui` (`PhotoThumbnails`, `PhotoPager` con Coil 3), `:features:client` (selección),
`:app` (`FileProvider` para la cámara en Fase 3).

## Modelo

- `PhotoRef(uri: String)`: referencia local elegida por el usuario (`content://...`) antes de publicar. El dominio la trata como `String` opaco, sin `android.net.Uri`.
- `RepairRequest.photoUrls: List<String>`: URLs remotas (Fase 2) o referencias mock (Fase 1) ya persistidas.

## Requisitos

### Requirement: Cantidad de fotos [Fase 1]

Una solicitud DEBE tener entre 1 y 3 fotos. La validación vive en `CreateRepairRequestUseCase`
(`PhotosRequired`, `TooManyPhotos`) y la UI DEBE impedir agregar una cuarta.

#### Escenario: Límite en UI
- DADO 3 fotos seleccionadas
- ENTONCES las acciones "Cámara" y "Galería" están deshabilitadas

#### Escenario: Quitar foto
- DADO 2 fotos seleccionadas
- CUANDO el usuario toca la "X" de una miniatura
- ENTONCES quedan 1 foto y las acciones vuelven a habilitarse

### Requirement: Selección simulada [Fase 1]

En Fase 1, "Agregar foto" DEBE agregar una imagen mock (recurso drawable de ejemplo o URL estática
de una imagen de prueba) sin acceder a hardware, para poder recorrer el flujo completo.
`FakePhotoUploader` DEBE devolver las mismas referencias como "URLs".

#### Escenario: Agregar foto mock
- DADO 0 fotos
- CUANDO el usuario toca "Agregar foto"
- ENTONCES se agrega una miniatura de ejemplo

### Requirement: Visualización con Coil 3 [Fase 1]

Las miniaturas en el formulario y el carrusel del detalle DEBEN cargarse con Coil 3 (`AsyncImage`)
con placeholder y estado de error, `contentScale = Crop` y `contentDescription` descriptivo
("Foto 1 de 3").

#### Escenario: Imagen que no carga
- DADO una URL inválida
- ENTONCES se muestra el ícono de imagen rota y el resto del detalle funciona

### Requirement: Photo Picker [Fase 3]

"Galería" DEBE usar `ActivityResultContracts.PickMultipleVisualMedia(maxItems = 3 - actuales)` con
`PickVisualMediaRequest(ImageOnly)`, que no requiere permisos de almacenamiento. Si `maxItems`
calculado es 1, DEBE usarse `PickVisualMedia`.

#### Escenario: Selección múltiple
- DADO 1 foto ya cargada
- CUANDO el usuario abre la galería
- ENTONCES puede elegir hasta 2 imágenes

#### Escenario: Cancelar selección
- CUANDO el usuario cierra el picker sin elegir
- ENTONCES la lista de fotos no cambia

### Requirement: Cámara [Fase 3]

"Cámara" DEBE usar `ActivityResultContracts.TakePicture()` con una URI de destino generada por
`FileProvider` en el directorio de caché. La app NO DEBE declarar el permiso `CAMERA` si solo usa
el intent de captura del sistema. Si la captura devuelve `false`, el archivo temporal DEBE
descartarse.

#### Escenario: Foto tomada
- CUANDO el usuario captura una foto
- ENTONCES se agrega a la lista como `PhotoRef(uri)`

#### Escenario: Captura cancelada
- CUANDO el resultado es `false`
- ENTONCES no se agrega ninguna foto

#### Escenario: Sin cámara
- DADO un dispositivo sin cámara (`PackageManager.FEATURE_CAMERA_ANY` ausente)
- ENTONCES la acción "Cámara" no se muestra

### Requirement: Subida a Storage [Fase 2]

`FirebaseStoragePhotoUploader.upload(requestId, photos)` DEBE comprimir cada imagen a JPEG (lado
mayor ≤ 1600 px, calidad 80), subirla a `requests/{requestId}/photo_{index}.jpg` y devolver las URLs
de descarga. El repositorio DEBE generar el `requestId` antes de subir (`collection.document().id`),
subir las fotos y luego escribir el documento. Si una subida falla, DEBE intentar borrar las ya
subidas y devolver `Error(Network)` o `Error(Unknown)`.

#### Escenario: Subida exitosa
- DADO 2 fotos
- ENTONCES el documento queda con 2 `photoUrls` de `firebasestorage.googleapis.com`

#### Escenario: Fallo en la segunda foto
- DADO que falla la subida de la foto 2
- ENTONCES se borra la foto 1, no se crea el documento y se devuelve error

#### Escenario: Reglas de Storage
- DADO un usuario no autenticado o que no es dueño de la solicitud
- ENTONCES las reglas rechazan la escritura en `requests/{requestId}/`
