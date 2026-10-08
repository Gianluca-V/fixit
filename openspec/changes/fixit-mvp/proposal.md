# Propuesta: FixIt MVP — marketplace de reparaciones (Fases 1–4)

> **Restricción dura**: el proyecto NO DEBE requerir tarjeta de crédito. Firebase queda en el plan Spark (Auth + Firestore + FCM); no se usan Cloud Storage for Firebase, Cloud Functions ni el plan Blaze. Actualizada el 2026-10-08.

## Intención

- **Problema**: quien tiene algo roto en su hogar no tiene una forma rápida de encontrar a un profesional cercano y disponible del rubro adecuado.
- **Solución**: una app Android donde el cliente publica una solicitud (categoría, descripción, fotos, ubicación) y el primer profesional cercano y compatible que la acepta se queda con el trabajo.
- **Por qué ahora**: es la entrega del TP2 (UNAJ, Aplicaciones Móviles). Fase 1 DEBE funcionar sin backend; las Fases 2, 3 y 4 se suman después sin reescribir lógica ni pantallas.
- **Por qué se actualiza**: desde el 2026-02-03, Cloud Storage for Firebase exige el plan Blaze (con tarjeta). Las fotos pasan a Cloudinary (plan gratuito, sin tarjeta), y las notificaciones push se agregan como Fase 4 opcional por decisión explícita del usuario.
- **Fuente**: `openspec/drafts/` (solo lectura), las decisiones confirmadas por el usuario (Engram `architecture/repair-app-decisions`) y las rondas de preguntas de esta propuesta.

## Alcance

### Incluido
- [Fase 1] Registro e inicio de sesión con email y contraseña; rol fijo (`CLIENT` / `PROFESSIONAL`).
- [Fase 1] Perfil del profesional: categorías (1 o más), punto base GPS + radio en km, reputación.
- [Fase 1] Edición de perfil: ambos roles editan nombre y teléfono; el profesional además edita categorías, punto base y radio. El email y el rol NO son editables.
- [Fase 1] Solicitud: 1 categoría, descripción, 1..3 fotos, ubicación; listado y detalle. Solo el cliente dueño puede cancelarla, en `OPEN` o `ACCEPTED`. Las solicitudes `OPEN` no vencen.
- [Fase 1] Feed en tiempo real (categoría + distancia Haversine ≤ radio); aceptación atómica: el primero gana.
- [Fase 1] Tras `ACCEPTED`: teléfono de la contraparte + botones de llamada y WhatsApp.
- [Fase 1] El cliente marca `COMPLETED` y califica (1–5 + comentario opcional) en el mismo flujo; una reseña por trabajo.
- [Fase 2] Firebase Auth y Firestore (plan Spark), geohash, transacciones y Security Rules de Firestore.
- [Fase 2] Fotos en **Cloudinary** con un *upload preset* sin firma (*unsigned*); Firestore guarda solo las URLs (`photoUrls`).
- [Fase 3] GPS real con permisos, cámara y Photo Picker.
- [Fase 4, opcional] Notificaciones push con FCM, enviadas por un **Cloudflare Worker**:
  - El cliente recibe un aviso cuando un profesional acepta su solicitud.
  - El profesional recibe un aviso de cada solicitud `OPEN` nueva de sus categorías que cae dentro de su radio.
  - El profesional asignado recibe un aviso cuando el cliente cancela un trabajo `ACCEPTED`.
  - Los avisos se muestran siempre, también con la app en primer plano. Al tocarlos se abre el detalle de la solicitud según el rol.

### Excluido
- Pagos, chat, panel de administración, calificación mutua, verificación de identidad o matrícula, cambio de rol, límites de cantidad.
- Edición de solicitudes publicadas, recuperación de contraseña y verificación de email.
- Vencimiento automático de solicitudes y verificación previa de que haya profesionales disponibles.
- Cloud Storage for Firebase, Cloud Functions y cualquier servicio que exija el plan Blaze o una tarjeta.
- Borrado de fotos en Cloudinary desde la app (exige la API firmada).
- [Fase 4] Otros avisos: trabajo completado, reseña recibida, recordatorios. Tampoco hay bandeja de notificaciones dentro de la app ni preferencias por usuario (silenciar, horarios).

## Capacidades (Capabilities)

### Nuevas (New Capabilities)
| Capacidad | Resumen |
|---|---|
| `auth` | Registro con rol fijo, login, logout, compuerta y persistencia de sesión. |
| `profiles` | Perfiles de cliente y profesional; **edición** (nombre y teléfono; el profesional también edita categorías, zona y radio; email y rol fijos); reputación; privacidad del contacto. |
| `repair-requests` | Crear, validar, listar, ver y cancelar (`OPEN` o `ACCEPTED`, solo el dueño); estado "Esperando que un profesional acepte"; máquina de estados pura. |
| `professional-feed` | Feed compatible en tiempo real, detalle, aceptación atómica, "Mis trabajos". |
| `job-completion` | Completar + reseñar en una operación; actualización del agregado de reputación. |
| `contact` | Contacto de la contraparte tras aceptar; normalización del número; llamada y WhatsApp. |
| `location` | Coordenadas, Haversine en dominio, proveedor abstracto (mock → geohash → GPS real). |
| `media` | 1..3 fotos: simuladas (F1), **Cloudinary con preset sin firma** (F2), cámara y Photo Picker (F3). |
| `navigation` | Compuerta por sesión y rol, pestañas por rol con Navigation 3, resuelta en `:app`; incluye la pantalla de edición de perfil. |
| `notifications` | **Nueva, [Fase 4]**: permiso de notificaciones; suscripción a `user_{uid}` y `category_*` al iniciar sesión, resuscripción al editar categorías y desuscripción total al cerrar sesión; tres eventos (aceptación, nueva solicitud, cancelación de un `ACCEPTED`) enviados por el Worker; filtro de distancia en el dispositivo; aviso visible también en primer plano; al tocarlo se abre el detalle según el rol. |

### Modificadas (Modified Capabilities)
- Ninguna en `openspec/specs/` (está vacío). Dentro de este cambio, los specs ya escritos de `media` (Storage → Cloudinary) y `profiles` / `auth` (suscripciones al editar categorías y al cerrar sesión) quedan pendientes de revisión.

## Enfoque por fases

| Fase | Qué se construye | Cómo se intercambia |
|---|---|---|
| **1 (TP2)** | Proyecto multi-módulo; dominio puro con TDD (validaciones, estados, matching, aceptación con `Mutex`); repositorios Fake con datos de ejemplo; UI completa de ambos roles. | — |
| **2** | `Firebase*Repository` (Auth + Firestore) sobre las mismas interfaces; transacciones para aceptar y reseñar; geohash; reglas e índices. Subida de fotos a Cloudinary con un POST multipart de OkHttp (`unsigned` + preset; ver D21 del diseño). Build: plugin `google-services` y permiso `INTERNET` en `:app`; `coil-network-okhttp` en `:core:ui`; `androidx.exifinterface` y OkHttp (`okhttp-coroutines`) en `:core:data`. | Bindings de Hilt; el resto son cambios de build y manifest, sin tocar `core/domain` ni `features/*`. |
| **3** | `FusedLocationProvider`, cámara (`TakePicture`), Photo Picker, permisos en tiempo de ejecución. | Ubicación: bindings de Hilt en `RepositoryModule`. Fotos y permisos: cambios de UI (`:core:ui`, `features/*`). |
| **4 (opcional)** | La app llama al Worker con el ID token de Firebase tras crear, aceptar o cancelar una solicitud. El Worker valida el token, relee la solicitud en Firestore (no confía en el cuerpo del pedido) y envía por FCM HTTP v1. Al crear, envía un mensaje de datos al tema `category_{CATEGORY}`. Al aceptar, avisa al tema `user_{clientId}`. Al cancelar, avisa al tema `user_{professionalId}`, solo si el estado es `CANCELLED`, quien llama es el cliente dueño y hay un `professionalId`. El profesional se suscribe a los temas de sus categorías y filtra la distancia en el dispositivo antes de mostrar el aviso. | Un decorador del repositorio de solicitudes en `:core:data` (binding de Hilt) más el servicio FCM y el permiso en `:app`. Sin cambios en `core/domain` ni `features/*`. |

**Motivo**:
- Clean Architecture con Fakes permite entregar y testear sin red, y demuestra la regla de dependencia. Replica la estructura del repositorio de la cátedra (`mauromarod/unaj-am`).
- Cloudinary con preset sin firma no requiere servidor ni secretos en el APK.
- El Worker es el único lugar que guarda la cuenta de servicio de FCM. Se usa porque Cloud Functions exigiría el plan Blaze.
- **Regla de negocio**: el aviso es de mejor esfuerzo. Si el Worker falla, la creación, la aceptación o la cancelación igual se consideran exitosas.

## Módulos afectados

| Área | Impacto | Fase |
|---|---|---|
| `settings.gradle.kts`, `gradle/libs.versions.toml` | Nuevo | 1 (2 y 4 agregan dependencias) |
| `core/domain` (Kotlin/JVM puro) | Nuevo | 1 |
| `core/data` (`fake/`, `firebase/`, `cloudinary/`, `push/`, `di/`) | Nuevo. Fase 2 agrega Firebase, Cloudinary y `exifinterface`; Fase 4 agrega el cliente del Worker, el decorador y las suscripciones a temas. | 1, 2, 3, 4 |
| `core/ui` (tema, componentes compartidos) | Nuevo; Fase 2 agrega `coil-network-okhttp`; Fase 3 cambia `PhotoPickerButtons` | 1, 2, 3 |
| `features/auth`, `features/client`, `features/professional` | Nuevo | 1 (3: permisos y cámara) |
| `app` (navegación, Hilt, manifest) | Nuevo. Fase 2: `google-services` e `INTERNET`. Fase 3: permisos y `FileProvider`. Fase 4: `FirebaseMessagingService`, `POST_NOTIFICATIONS` y el canal de notificaciones. | 1, 2, 3, 4 |
| `firestore.rules`, `firestore.indexes.json` | Nuevo (`storage.rules` se elimina) | 2 |
| `worker/` (raíz del repositorio, fuera de Gradle) | Nuevo: Cloudflare Worker en TypeScript con `wrangler.toml`. La cuenta de servicio va como secreto del Worker, nunca en el repositorio ni en el APK. | 4 |

Se usa la carpeta `worker/` porque tiene otro runtime (JS/TS con `wrangler`), otro ciclo de despliegue y secretos propios. Estar fuera de Gradle evita mezclar toolchains y deja claro que no forma parte del APK.

## Riesgos

| Riesgo | Prob. | Mitigación |
|---|---|---|
| Sin experiencia previa en Android + APIs recientes (Navigation 3, AGP 9) con pocos ejemplos | Alta | Replicar el repositorio de la cátedra; andamiaje primero; verificar versiones al implementar. |
| Cambio muy grande (~160 tareas) | Alta | PRs encadenados; cerrar cada fase antes de empezar la siguiente. La Fase 4 es opcional. |
| El Fake no reproduce Firestore (latencia, errores, transacciones) | Media | Checklist manual de Fase 2 sobre Firebase (opcional: Firebase Emulator Suite). |
| Privacidad en Fase 2: teléfono y dirección exacta legibles por cualquier usuario autenticado; agregado de reputación manipulable | Media | Aceptado en el alcance académico. Una mejora futura es `users/{uid}/private` o mover el agregado al Worker; Cloud Functions queda descartado. |
| Editar el perfil en Fase 2 podría permitir modificar campos protegidos | Media | Las reglas solo permiten cambiar los campos editables (`email` y `role` bloqueados); `ratingSum` y `reviewCount` siguen escribibles (debilidad aceptada, D18). |
| Cambian los planes gratuitos o sus límites (Cloudinary ~25 créditos/mes; Worker 100.000 pedidos/día y 10 ms de CPU) | Media | Fotos comprimidas antes de subir; presupuesto de uso de un TP. El Worker cachea el token OAuth de Google. El avance se verifica en el panel de cada servicio. |
| Fase 4: la firma RS256 o la validación del ID token superan los 10 ms de CPU | Baja | Usar WebCrypto nativo, cachear el token de acceso y las claves públicas de Google, y medirlo en el primer despliegue. |
| Fase 4: Android limita mensajes en segundo plano (Doze, permiso denegado en Android 13+) | Media | Mensajes de alta prioridad. Si el permiso se deniega, la app funciona igual, sin avisos. |
| Fase 4: la creación de claves de cuenta de servicio está bloqueada por política | Baja | Un proyecto personal (sin organización) no la bloquea; se verifica al configurar. |

### Debilidades aceptadas
- **Preset de Cloudinary**: el nombre del preset se puede extraer del APK, así que cualquiera puede subir imágenes a la cuenta. Se mitiga con los límites del preset: carpeta fija, solo formatos de imagen, tamaño máximo y transformación de entrada.
- **URLs públicas**: cualquiera con la URL ve la foto; no hay reglas de lectura por usuario.
- **Fotos huérfanas**: borrar requiere la API firmada. Las fotos de solicitudes canceladas o de una creación fallida quedan en Cloudinary.
- **Worker**: el abuso se limita porque se valida el ID token y se relee Firestore; no hay rate limiting propio.
- **Temas FCM**: cualquier app con el `google-services.json` puede suscribirse a un tema. Por eso los mensajes no llevan datos personales ni coordenadas: solo `requestId` y categoría. Para calcular la distancia, el dispositivo lee la ubicación de la solicitud en Firestore con la sesión del usuario.

## Plan de rollback

- **Fase 1**: se revierten commits.
- **Fase 2**: los Fakes NO se borran. Los `@Binds` de `core/data/di/RepositoryModule.kt` eligen Fake, Firebase o Cloudinary; para volver atrás se revierte ese archivo y se recompila. Las reglas están versionadas. Las fotos ya subidas se conservan en Cloudinary.
- **Fase 3**: volver a la ubicación simulada en `RepositoryModule` y revertir los cambios de UI de fotos y permisos.
- **Fase 4**: quitar el binding del decorador (vuelve el repositorio sin avisos) y el servicio FCM del manifest. Después, `wrangler delete` y revocar la clave de la cuenta de servicio en Google Cloud.

## Criterios de éxito

- [ ] Fase 1: ambos roles completan el flujo con datos Fake: registro → publicar → feed → aceptar → contactar → completar y calificar.
- [ ] Cada escenario `[Fase 1]` está cubierto por un test JVM en verde (`./gradlew test`) o por un ítem de checklist manual (`[manual]`; ver `design.md` §15).
- [ ] 10 profesionales distintos aceptan a la vez → exactamente 1 éxito.
- [ ] Si un profesional edita sus categorías, zona o radio, su feed se actualiza.
- [ ] `core/domain` compila sin dependencias de Android, Firebase, Cloudinary, Hilt ni Compose.
- [ ] Fase 2: el proyecto Firebase está en el plan Spark y ninguna cuenta (Firebase, Cloudinary) tiene tarjeta cargada.
- [ ] Fase 2: las fotos se suben a Cloudinary y se ven por URL; el diff no toca `core/domain` ni `features/*`.
- [ ] Fase 3: GPS, cámara y Photo Picker funcionan en un dispositivo, y los casos de permiso denegado están cubiertos.
- [ ] Fase 4: al aceptar, el cliente recibe el aviso; al publicar, solo lo reciben los profesionales de esa categoría que están dentro de su radio; al cancelar un `ACCEPTED`, lo recibe el profesional asignado.
- [ ] Fase 4: el aviso aparece también con la app abierta y, al tocarlo, abre el detalle de la solicitud del rol correspondiente.
- [ ] Fase 4: tras cerrar sesión, el dispositivo no recibe avisos de esa cuenta. Cerrar la app no desuscribe.
- [ ] Fase 4: si el Worker no responde, crear, aceptar y cancelar siguen funcionando.
- [ ] Fase 4: el APK y el repositorio no contienen la cuenta de servicio.

## Supuestos confirmados

Aprobados por el usuario tal como se propusieron.

| Supuesto | Valor | Impacto si cambia |
|---|---|---|
| Nombre y paquete | "FixIt", `ar.edu.unaj.fixit` | Renombrar paquetes; en Fase 2 el `applicationId` queda atado al proyecto Firebase. |
| Límites de validación | Nombre 2..60; contraseña ≥6 (mínimo de Firebase); teléfono 8..15 dígitos; radio entero 1..50 km; descripción 10..500; comentario ≤300 | Bajo en código (constantes en `Limits`); cambian escenarios y tests. |
| Aceptar fuera de sus categorías | Prohibido → `Unauthorized` | Si se permite, se eliminan la validación y la regla de Firestore. |
| Aceptar valida la distancia | No: el radio solo filtra el feed | Si se exige, se agrega un chequeo Haversine al aceptar (difícil de reforzar en reglas de Firestore). |
| Prefijo de WhatsApp | Con `+` → dígitos tal cual; sin `+` y empezando con `54` → tal cual; si no → se asume celular argentino (`549`) | Limitación documentada: números fijos o extranjeros sin `+` generan enlaces inválidos. |
| Granularidad SDD | Un solo cambio con requisitos etiquetados por fase | Si se separa por fase, las Fases 2–4 pasan a ser cambios nuevos (deltas). |
| Edición de perfil y recuperación de contraseña | Edición en alcance; recuperación fuera de alcance | La edición impacta `profiles`, `navigation` y las reglas de Firestore. |
| Agregado de reputación | `ratingSum` + `reviewCount` (promedio derivado) | La alternativa (recalcular leyendo reseñas) implica más lecturas, pero es más simple de proteger. |
| Teléfono visible solo tras aceptar | Lo controla la app, no Firestore (debilidad conocida) | Privacidad real: subcolección privada con reglas por participante. |
| Sin tarjeta de crédito | Spark + Cloudinary gratuito + Worker gratuito; sin Cloud Functions | Con Blaze se podrían usar Storage y Cloud Functions, pero se viola la restricción dura. |

## Decisiones de la ronda de preguntas

1. **Trabajo aceptado sin salida**: el cliente dueño PUEDE cancelar una solicitud en `OPEN` o en `ACCEPTED` (transición `ACCEPTED → CANCELLED`). Nadie más puede cancelar.
2. **Vencimiento**: las solicitudes `OPEN` no vencen.
3. **Sin profesionales compatibles**: la solicitud se publica igual y el cliente ve "Esperando que un profesional acepte".
4. **Edición de perfil**: en alcance (ver Alcance).
5. **Sin tarjeta (2026-10-08)**: las fotos van a Cloudinary con preset sin firma; Firebase queda en Spark.
6. **Push (2026-10-08)**: Fase 4 opcional con FCM + Cloudflare Worker, con tres eventos: aceptación → cliente; nueva solicitud → profesionales; cancelación de un `ACCEPTED` → profesional asignado.

### Decisiones de la Fase 4 (2026-10-08)

| # | Pregunta | Decisión |
|---|---|---|
| 1 | ¿Se avisa al profesional asignado si el cliente cancela un `ACCEPTED`? | Sí, al tema `user_{professionalId}`. El Worker relee la solicitud y verifica estado `CANCELLED`, que quien llama sea el cliente dueño y que haya `professionalId`. |
| 2 | ¿Avisos siempre o solo en segundo plano? | Siempre: con la app en primer plano también se muestra una notificación del sistema desde `onMessageReceived`. |
| 3 | ¿Qué abre el aviso? | El detalle de la solicitud según el rol (detalle del cliente o del profesional). |
| 4 | ¿Qué datos lleva el aviso? | Ningún dato personal: solo el id de la solicitud y la categoría. La distancia se calcula en el dispositivo. |
| 5 | ¿Cuándo cambian las suscripciones? | Al cerrar sesión se desuscribe de todos los temas (`user_{uid}` y `category_*`). Al iniciar sesión se suscribe de nuevo, y al editar categorías se actualizan los temas. Cerrar la app NO desuscribe. |

## Conceptos clave

- **Repositorio Fake**: imitación en memoria de la base de datos; la app funciona y se testea sin internet.
- **Hilt**: define en un solo lugar qué implementación (Fake o real) recibe cada pantalla.
- **Clean Architecture**: las reglas de negocio (`core/domain`) no conocen Android ni Firebase; por eso se puede cambiar el backend sin tocarlas.
- **Firestore**: base de datos en la nube de Firebase que avisa los cambios en tiempo real.
- **Transacción atómica**: se aplica completa o no se aplica; garantiza que solo un profesional gane la aceptación.
- **Geohash / Haversine**: el geohash codifica coordenadas como texto para buscar puntos cercanos; Haversine calcula la distancia exacta.
- **UiState**: describe lo que muestra una pantalla (cargando, datos o error); la pantalla solo dibuja ese estado.
- **Cloudinary / upload preset sin firma**: servicio de imágenes; el preset define en el servidor qué se puede subir, así la app no necesita una clave secreta.
- **FCM / tema**: servicio de Google que entrega notificaciones; un tema es un canal al que se suscriben varios dispositivos.
- **Cloudflare Worker**: función pequeña en la nube que guarda la clave de FCM y envía los avisos en nombre de la app.
- **Decorador**: clase que envuelve un repositorio y le agrega un comportamiento (enviar el aviso) sin modificarlo.
