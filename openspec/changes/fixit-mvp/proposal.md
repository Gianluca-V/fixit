# Propuesta: FixIt MVP — marketplace de reparaciones (Fases 1–3)

## Intención

- **Problema**: quien tiene algo roto en su hogar no tiene una forma rápida de encontrar a un profesional cercano y disponible del rubro adecuado.
- **Solución**: una app Android donde el cliente publica una solicitud (categoría, descripción, fotos, ubicación) y el primer profesional cercano y compatible que la acepta se queda con el trabajo.
- **Por qué ahora**: es la entrega del TP2 (UNAJ, Aplicaciones Móviles). Fase 1 DEBE funcionar sin backend; las Fases 2 y 3 se suman después sin reescribir lógica ni pantallas.
- **Fuente**: `openspec/drafts/` (solo lectura), las decisiones confirmadas por el usuario (Engram `architecture/repair-app-decisions`) y la ronda de preguntas de esta propuesta.

## Alcance

### Incluido
- [Fase 1] Registro e inicio de sesión con email y contraseña; rol fijo (`CLIENT` / `PROFESSIONAL`).
- [Fase 1] Perfil del profesional: categorías (1 o más), punto base GPS + radio en km, reputación.
- [Fase 1] Edición de perfil: ambos roles editan nombre y teléfono; el profesional además edita categorías, punto base y radio. El email y el rol NO son editables.
- [Fase 1] Solicitud: 1 categoría, descripción, 1..3 fotos, ubicación; listado y detalle. Solo el cliente dueño puede cancelarla, en `OPEN` o `ACCEPTED`. Las solicitudes `OPEN` no vencen.
- [Fase 1] Feed en tiempo real (categoría + distancia Haversine ≤ radio); aceptación atómica: el primero gana.
- [Fase 1] Tras `ACCEPTED`: teléfono de la contraparte + botones de llamada y WhatsApp.
- [Fase 1] El cliente marca `COMPLETED` y califica (1–5 + comentario opcional) en el mismo flujo; una reseña por trabajo.
- [Fase 2] Firebase Auth, Firestore, Storage, geohash, transacciones y Security Rules.
- [Fase 3] GPS real con permisos, cámara y Photo Picker.

### Excluido
- Pagos, chat, notificaciones push, panel de administración, calificación mutua, verificación de identidad o matrícula, cambio de rol, límites de cantidad.
- Edición de solicitudes publicadas, recuperación de contraseña y verificación de email.
- Vencimiento automático de solicitudes y verificación previa de que haya profesionales disponibles.

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
| `media` | 1..3 fotos: simuladas (F1), Storage (F2), cámara y Photo Picker (F3). |
| `navigation` | Compuerta por sesión y rol, pestañas por rol con Navigation 3, resuelta en `:app`; incluye la pantalla de edición de perfil. |

### Modificadas (Modified Capabilities)
- Ninguna (`openspec/specs/` está vacío; proyecto nuevo).

## Enfoque por fases

| Fase | Qué se construye | Cómo se intercambia |
|---|---|---|
| **1 (TP2)** | Proyecto multi-módulo; dominio puro con TDD (validaciones, estados, matching, aceptación con `Mutex`); repositorios Fake con datos de ejemplo; UI completa de ambos roles. | — |
| **2** | `Firebase*Repository` sobre las mismas interfaces; transacciones para aceptar y reseñar; geohash; reglas e índices. Dependencias y configuración de build: plugin `google-services` y permiso `INTERNET` en `:app`; `coil-network-okhttp` en `:core:ui` (para mostrar las fotos por URL); `androidx.exifinterface` en `:core:data`. | Bindings de Hilt; el resto son cambios de build y manifest en `:app` y `:core:ui`, sin tocar `core/domain` ni `features/*`. |
| **3** | `FusedLocationProvider`, cámara (`TakePicture`), Photo Picker, permisos en tiempo de ejecución. | Ubicación: bindings de Hilt en `RepositoryModule`. Fotos y permisos: cambios de UI (`:core:ui`, `features/*`), sin `LocationModule`. |

Motivo: Clean Architecture con Fakes permite entregar y testear sin red, y demuestra la regla de dependencia. Replica la estructura del repositorio de la cátedra (`mauromarod/unaj-am`).

## Módulos afectados

| Área | Impacto | Fase |
|---|---|---|
| `settings.gradle.kts`, `gradle/libs.versions.toml` | Nuevo | 1 |
| `core/domain` (Kotlin/JVM puro) | Nuevo | 1 |
| `core/data` (`fake/`, `firebase/`, `di/`) | Nuevo; Fase 2 agrega `firebase/` y `exifinterface` | 1, 2, 3 |
| `core/ui` (tema, componentes compartidos) | Nuevo; Fase 2 agrega `coil-network-okhttp`; Fase 3 cambia `PhotoPickerButtons` | 1, 2, 3 |
| `features/auth`, `features/client`, `features/professional` | Nuevo | 1 (3: permisos y cámara) |
| `app` (navegación, Hilt, manifest) | Nuevo; Fase 2 agrega el plugin `google-services` y el permiso `INTERNET`; Fase 3 agrega permisos y `FileProvider` | 1, 2, 3 |
| `firestore.rules`, `firestore.indexes.json`, `storage.rules` | Nuevo | 2 |

## Riesgos

| Riesgo | Prob. | Mitigación |
|---|---|---|
| Sin experiencia previa en Android + APIs recientes (Navigation 3, AGP 9) con pocos ejemplos | Alta | Replicar el repositorio de la cátedra; andamiaje primero; verificar versiones al implementar. |
| El proyecto no es un repositorio git: no hay rollback real | Alta | `git init` antes de la primera tarea de apply. |
| Cambio muy grande (~120 tareas en el borrador) | Alta | PRs encadenados; cerrar Fase 1 antes de empezar Fase 2. |
| El Fake no reproduce Firestore (latencia, errores, transacciones) | Media | Checklist manual de Fase 2 sobre Firebase (opcional: Firebase Emulator Suite). |
| Privacidad en Fase 2: teléfono y dirección exacta legibles por cualquier usuario autenticado; agregado de reputación manipulable | Media | Aceptado en el alcance académico; mejora: `users/{uid}/private` y una Cloud Function. |
| Editar el perfil en Fase 2 podría permitir modificar campos protegidos | Media | Las reglas de Firestore solo permiten cambiar los campos editables: `email` y `role` quedan bloqueados. `ratingSum` y `reviewCount` siguen siendo escribibles a través de la cláusula del agregado (debilidad aceptada, D18 del diseño). |

## Plan de rollback

- **Fase 1**: proyecto nuevo; se revierten commits (requiere git).
- **Fase 2**: los Fakes NO se borran. El único selector son los `@Binds` de `core/data/di/RepositoryModule.kt`, que eligen Fake o Firebase. Para volver atrás: revertir ese archivo y recompilar. Dominio y features no cambian. Los datos de Firestore se conservan, pero no se ven en modo Fake. Las reglas se versionan en el repositorio y se puede volver a desplegar la versión anterior.
- **Fase 3**: volver a la ubicación simulada en `RepositoryModule` y revertir los cambios de UI de fotos y permisos.

## Criterios de éxito

- [ ] Fase 1: ambos roles completan el flujo con datos Fake: registro → publicar → feed → aceptar → contactar → completar y calificar.
- [ ] Cada escenario `[Fase 1]` está cubierto por un test JVM en verde (`./gradlew test`) o por un ítem de checklist manual (los escenarios con título `[manual]`; ver `design.md` §15).
- [ ] 10 profesionales distintos aceptan a la vez → exactamente 1 éxito.
- [ ] Si un profesional edita sus categorías, zona o radio, su feed se actualiza.
- [ ] `core/domain` compila sin dependencias de Android, Firebase, Hilt ni Compose.
- [ ] Fase 2: el diff del cambio a Firebase no toca `core/domain` ni `features/*`.
- [ ] Fase 3: GPS, cámara y Photo Picker funcionan en un dispositivo, y los casos de permiso denegado están cubiertos.

## Supuestos confirmados

Aprobados por el usuario tal como se propusieron.

| Supuesto | Valor | Impacto si cambia |
|---|---|---|
| Nombre y paquete | "FixIt", `ar.edu.unaj.fixit` | Renombrar paquetes; en Fase 2 el `applicationId` queda atado al proyecto Firebase. |
| Límites de validación | Nombre 2..60; contraseña ≥6 (mínimo de Firebase); teléfono 8..15 dígitos; radio entero 1..50 km; descripción 10..500; comentario ≤300 | Bajo en código (constantes en `Limits`); cambian escenarios y tests. |
| Aceptar fuera de sus categorías | Prohibido → `Unauthorized` | Si se permite, se eliminan la validación y la regla de Firestore. |
| Aceptar valida la distancia | No: el radio solo filtra el feed | Si se exige, se agrega un chequeo Haversine al aceptar (difícil de reforzar en reglas de Firestore). |
| Prefijo de WhatsApp | Con `+` → dígitos tal cual; sin `+` y empezando con `54` → tal cual; si no → se asume celular argentino (`549`) | Limitación documentada: números fijos o extranjeros sin `+` generan enlaces inválidos; la alternativa es exigir formato internacional. |
| Granularidad SDD | Un solo cambio con requisitos etiquetados por fase | Si se separa por fase: Fase 1 se archiva con el TP2 y las Fases 2–3 pasan a ser cambios nuevos (deltas). |
| Edición de perfil y recuperación de contraseña | Edición de perfil: en alcance (ver respuesta 4); recuperación de contraseña: fuera de alcance | La edición impacta `profiles`, `navigation` y las reglas de Firestore. |
| Agregado de reputación | `ratingSum` + `reviewCount` (promedio derivado) | La alternativa (recalcular leyendo reseñas) implica más lecturas, pero es más simple de proteger. |
| Teléfono visible solo tras aceptar | Lo controla la app, no Firestore (debilidad conocida) | Privacidad real: subcolección privada con reglas por participante. |
| Reglas del agregado sin Cloud Functions | Permisivas; evita el plan Blaze (pago) | Con una Cloud Function el agregado queda seguro, pero exige el plan Blaze. |

## Decisiones de la ronda de preguntas

1. **Trabajo aceptado sin salida**: el cliente dueño PUEDE cancelar una solicitud en `OPEN` o en `ACCEPTED`. Se agrega la transición `ACCEPTED → CANCELLED`, y nadie más puede cancelar.
2. **Vencimiento**: las solicitudes `OPEN` no vencen; siguen abiertas hasta que alguien las acepta o el cliente las cancela.
3. **Sin profesionales compatibles**: la solicitud se publica igual y el cliente ve "Esperando que un profesional acepte". No se verifica antes si hay profesionales disponibles.
4. **Edición de perfil**: queda en alcance. Ambos roles editan nombre y teléfono, y el profesional también edita categorías, punto base y radio. El email y el rol no son editables. Esto impacta `profiles` y `navigation`.

## Conceptos clave

- **Repositorio Fake**: imitación en memoria de la base de datos; la app funciona y se testea sin internet.
- **Hilt**: define en un solo lugar qué implementación (Fake o Firebase) recibe cada pantalla.
- **Clean Architecture**: las reglas de negocio (`core/domain`) no conocen Android ni Firebase; por eso se puede cambiar el backend sin tocarlas.
- **Firestore**: base de datos en la nube de Firebase que avisa los cambios en tiempo real.
- **Transacción atómica**: se aplica completa o no se aplica; garantiza que solo un profesional gane la aceptación.
- **Geohash / Haversine**: el geohash codifica coordenadas como texto para buscar puntos cercanos en Firestore; Haversine calcula la distancia exacta.
- **UiState**: describe lo que muestra una pantalla (cargando, datos o error); la pantalla solo dibuja ese estado.
