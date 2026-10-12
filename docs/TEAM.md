# Guía del equipo: cómo repartirse el trabajo

La app ya arranca con todos los módulos y todas las pantallas como **placeholders**. Cada integrante reemplaza los placeholders de sus módulos sin tocar los del otro. Esta guía dice quién hace qué, cómo ramificar y dónde se pueden pisar los cambios.

## Camino rápido

1. Verificar que `local.properties` apunta al SDK (ver `README.md`).
2. Compilar y probar, con el JBR de Android Studio **por comando** (no se toca la configuración global):
   ```bash
   JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew test
   JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
   ```
   En Android Studio: abrir la carpeta raíz y elegir ese JDK en *Settings > Build > Gradle > Gradle JDK*.
3. Ejecutar `:app` en un emulador. Pantalla de inicio: formulario de inicio de sesión; entrar con una cuenta de prueba (Ana o Bruno como cliente, Carlos, Diana o Esteban como profesional; contraseña `test1234`). Desde la Fase 2 la app usa Firebase: hace falta `app/google-services.json` y las cuentas tienen que existir en el proyecto (ver [Firebase](#firebase-reglas-índices-y-usuarios-de-prueba)).
4. Cada pantalla pendiente muestra "Pantalla pendiente de implementar (tarea X.Y.Z)": esa es la tarea de `tasks.md` que la reemplaza.

## Firebase: reglas, índices y usuarios de prueba

Desde la Fase 2, `RepositoryModule.kt` enlaza los repositorios de Firebase (Auth y Firestore; las fotos van a Cloudinary). Para volver a los Fakes de la Fase 1 alcanza con revertir **solo** ese archivo (enlazar `FakeAuthRepository`, `FakeUserRepository` y `FakeRepairRequestRepository`).

- **Configuración**: `app/google-services.json` es obligatorio para compilar y **no** se versiona; se comparte por fuera del repositorio. Las claves de Cloudinary van en `local.properties` (ver `README.md`).
- **Desplegar reglas e índices** (requiere el CLI de Firebase y haber iniciado sesión con `firebase login`):
  ```bash
  node scripts/prepare-firestore-rules.mjs
  firebase deploy --only firestore --project <project-id>
  ```
  `firestore.rules` tiene el marcador `<CLOUD_NAME>`; el script lo reemplaza por `CLOUDINARY_CLOUD_NAME` de `local.properties` y genera `firestore.deploy.rules`, que es lo que despliega `firebase.json`. Ese archivo está en `.gitignore`: el *cloud name* real nunca se versiona. Sin el script, el despliegue falla porque no encuentra el archivo.
- **Usuarios de prueba** (después de desplegar las reglas):
  ```bash
  node scripts/seed-test-users.mjs
  ```
  Crea las cuentas en Auth y su perfil en `users/{uid}` con los mismos campos que el registro de la app. Se puede ejecutar varias veces: las cuentas y los perfiles existentes se conservan. Al final imprime una tabla con email, rol y `uid`. No crea solicitudes ni reseñas, y la reputación empieza en 0.

| Email | Rol | Contraseña |
|---|---|---|
| `ana@fixit.test` | Cliente | `test1234` |
| `bruno@fixit.test` | Cliente | `test1234` |
| `carlos@fixit.test` | Profesional (electricidad, gas; Florencio Varela, 10 km) | `test1234` |
| `diana@fixit.test` | Profesional (plomería, gas; Quilmes, 8 km) | `test1234` |
| `esteban@fixit.test` | Profesional (carpintería, pintura, otros; Berazategui, 15 km) | `test1234` |

## Dónde está todo

Todo lo necesario está versionado en el repositorio; no hace falta ninguna herramienta extra. El proyecto sigue *Spec-Driven Development*: primero se especifica, después se implementa.

| Pregunta | Archivo |
|---|---|
| ¿Qué tarea hago y en qué orden? | `openspec/changes/fixit-mvp/tasks.md`: tareas numeradas, cada par RED (test) → GREEN (implementación), con los escenarios que cubre cada test |
| ¿Qué tiene que hacer exactamente la pantalla? | `openspec/changes/fixit-mvp/specs/<dominio>/spec.md`: requisitos y escenarios DADO / CUANDO / ENTONCES. Cada escenario es un criterio de aceptación y suele ser un test |
| ¿Cómo se construye? | `openspec/changes/fixit-mvp/design.md`: §8 patrón de pantalla (Route + Screen + ViewModel + UiState), §9 pantallas por rol y navegación, §7 casos de uso, §15 estrategia de tests |
| ¿Por qué se decidió así? | `design.md` §16 (decisiones D1–D28) y `proposal.md` (alcance) |
| Reglas del proyecto | `openspec/config.yaml` (sección `rules`) |
| Estado del cambio y temas abiertos | `openspec/changes/fixit-mvp/state.yaml` |

Las etiquetas de `tasks.md` indican el spec: `[auth]` auth, `[prof]` profiles, `[rr]` repair-requests, `[feed]` professional-feed, `[jc]` job-completion, `[contact]` contact, `[loc]` location, `[media]` media, `[nav]` navigation. Ejemplo: la tarea 1.9.1 (`FeedViewModelTest`) menciona `[feed] Nueva solicitud aparece`; ese test se escribe a partir del escenario "Nueva solicitud aparece" de `specs/professional-feed/spec.md`.

Si se usa un asistente de IA, alcanza con indicarle estos archivos y la tarea a implementar.

## Quién hace qué

| | Módulos | Tareas (`tasks.md`) | Pantallas |
|---|---|---|---|
| **Dev A** | `:features:auth`, `:features:client` | 1.7 (auth), 1.8 (cliente) | Iniciar sesión, Registro, Mis solicitudes, Nueva solicitud, Detalle, Completar y calificar, Perfil, Editar perfil |
| **Dev B** | `:features:professional` | 1.9 | Feed, Detalle del profesional, Mis trabajos, Perfil, Editar perfil |
| **Ambos** | `:app`, `:core:ui` | 1.10.11–1.10.13 y 1.11 (verificaciones y checklist manual) | Se reparten el checklist; cada uno verifica su rol |

Notas:

- Dev A tiene más pantallas. Sugerencia: Dev B toma primero las verificaciones 1.10.11–1.10.13 y el reparto de la sección 1.11 para equilibrar.
- Fases 2 a 4 (Firebase, hardware, push) tocan `:core:data` y `:app`. Se reparten al cerrar la Fase 1, por unidad de trabajo (U10 en adelante).

## Ramas

- **Una rama por unidad de trabajo**, con Conventional Commits en inglés y sin atribución de IA. Ejemplos: `feat/u6-auth`, `feat/u7-client`, `feat/u8-professional`.
- Crear la rama desde `main` (el shell ya está integrado) y mantenerla al día con `main` antes de integrarla.
- Commits chicos por unidad de comportamiento (ViewModel + su pantalla + sus tests). Si una rama pasa de unas 400 líneas, dividirla por pantalla.
- Marcar la tarea `[x]` en `openspec/changes/fixit-mvp/tasks.md` en el mismo commit que la completa.

## Dónde se pisan los cambios

| Archivo | Por qué conflictúa | Cómo evitarlo |
|---|---|---|
| `core/ui/.../res/values/strings.xml` | Lo editan ambos | Agregar al final de la sección que corresponde; los textos propios de una feature van en el `strings.xml` de **esa** feature (prefijos `auth_`, `client_`, `pro_`) |
| `app/.../navigation/AppNavDisplay.kt` | Un `entryProvider` por rol en un solo archivo | Cada uno edita solo el provider de su rol; si cambian los callbacks de una pantalla, avisar al otro |
| `gradle/libs.versions.toml` | Ambos agregan librerías | Pedir la dependencia por mensaje y agregarla en un commit aparte y chico |
| `openspec/changes/fixit-mvp/tasks.md` | Ambos marcan tareas | Cambiar solo las líneas propias; resolver conflictos conservando las dos marcas |
| `core/ui/.../components/` | Componentes compartidos | Crear archivos nuevos en lugar de editar los del otro |

## Reglas

- **Dependencias**: una feature depende solo de `:core:domain` y `:core:ui`. Nunca de `:core:data` ni de otra feature (Gradle no compila si se rompe).
- **Navegación**: las features **nunca navegan**. Cada `XxxRoute` recibe lambdas (`onBack`, `onRequestClick(id)`...) y `:app` decide qué hacen (`AppNavDisplay.kt`). Mantener el nombre y los parámetros del `Route`; el placeholder ya tiene los correctos.
- **Casos de uso**: se inyectan en el `@HiltViewModel` y el `Route` obtiene el ViewModel con `hiltViewModel()` (`androidx.hilt.lifecycle.viewmodel.compose`).
- **TDD estricto en ViewModels**: primero el test que falla (`XxxViewModelTest`, con `MainDispatcherRule` y los dobles de `testFixtures` de `:core:domain`), después el mínimo para que pase, después refactor. Nombres: `` `given X when Y then Z` ``. Sin librería de mocks.
- **Textos**: solo en `strings.xml`, en español neutro. Nada de textos fijos en el código. Los errores se muestran con `DomainError.asMessage()`.
- **Código y comentarios** en inglés. Eventos únicos (mensaje, navegar) como campo nullable más `onXxxShown()`; ver `design.md` §8.

## Cómo reemplazar un placeholder

1. Leer la fila de la pantalla en `design.md` §9 y las tareas RED/GREEN en `tasks.md`.
2. Escribir `XxxViewModelTest` (RED) y verlo fallar.
3. Crear `XxxViewModel.kt` con su `UiState` (GREEN) y refactorizar.
4. Reemplazar el cuerpo de `XxxScreen.kt` en el mismo archivo (sin moverlo) y agregar su `@Preview`.
5. Borrar el `TODO` y el uso de `PlaceholderScreen`. Cuando no quede ninguno, borrar `PlaceholderScreen.kt` y su string.
6. Dos placeholders con ViewModel temporal: `SignInViewModel` (entrar con usuarios de ejemplo, tarea 1.7.2) y los ViewModels de perfil (solo "Cerrar sesión", 1.8.10 y 1.9.8). Los reemplaza la versión con tests.

## Lista de verificación antes de abrir el PR

- [ ] `./gradlew test` y `./gradlew assembleDebug` en verde.
- [ ] La feature no importa nada de `:core:data` ni de otra feature.
- [ ] No quedan textos fijos ni `TODO` de la tarea completada.
- [ ] Tareas marcadas en `tasks.md`.
