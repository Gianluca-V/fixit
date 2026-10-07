# Especificación: Autenticación (`auth`)

## Propósito

Permitir que una persona cree una cuenta con email y contraseña eligiendo un rol fijo
(`CLIENT` o `PROFESSIONAL`), inicie y cierre sesión, y que la aplicación determine al iniciar
qué grafo de navegación mostrar según la sesión y el rol.

Módulos: `:features:auth` (UI), `:core:domain` (`SignUpClientUseCase`, `SignUpProfessionalUseCase`,
`SignInUseCase`, `SignOutUseCase`, `ObserveSessionUseCase`), `:core:data` (`FakeAuthRepository`,
`FirebaseAuthRepository`).

## Reglas de validación

| Campo | Regla | Error |
|---|---|---|
| Email | No vacío, formato `local@dominio.tld` (regex simple en dominio, sin `android.util.Patterns`) | `Validation.InvalidEmail` |
| Contraseña | Mínimo 6 caracteres (alineado con Firebase Auth) | `Validation.WeakPassword` |
| Nombre | Recortado, 2..60 caracteres | `Validation.InvalidName` |
| Teléfono | Opcional `+` inicial y 8..15 dígitos tras quitar espacios y guiones | `Validation.InvalidPhone` |
| Rol | Obligatorio | `Validation.RoleRequired` |
| Categorías (profesional) | Al menos 1, sin duplicados | `Validation.CategoriesRequired` |
| Ubicación base (profesional) | Obligatoria | `Validation.BaseLocationRequired` |
| Radio (profesional) | Entero 1..50 km | `Validation.InvalidRadius` |

Las validaciones se evalúan en el caso de uso en el orden de la tabla y se devuelve el primer
error encontrado.

## Requisitos

### Requirement: Registro de cliente [Fase 1]

El sistema DEBE permitir registrar una cuenta con rol `CLIENT` a partir de email, contraseña,
nombre y teléfono. Al registrarse correctamente, la sesión DEBE quedar iniciada.

#### Escenario: Registro exitoso de cliente
- DADO un email no registrado y datos válidos
- CUANDO se ejecuta `SignUpClientUseCase`
- ENTONCES el resultado es `AppResult.Success(User)` con `role = CLIENT`
- Y `ObserveSessionUseCase` emite una sesión autenticada con ese usuario

#### Escenario: Email ya registrado
- DADO un email que ya existe en el repositorio
- CUANDO se ejecuta `SignUpClientUseCase` con ese email
- ENTONCES el resultado es `AppResult.Error(DomainError.EmailAlreadyInUse)`

#### Escenario: Datos inválidos
- DADO un teléfono `"12ab"`
- CUANDO se ejecuta `SignUpClientUseCase`
- ENTONCES el resultado es `AppResult.Error(DomainError.Validation.InvalidPhone)`
- Y el repositorio NO recibe ninguna llamada

### Requirement: Registro de profesional [Fase 1]

El sistema DEBE permitir registrar una cuenta con rol `PROFESSIONAL` que, además de los datos
comunes, incluya una o más categorías, una ubicación base (lat/lng) y un radio en km.

#### Escenario: Registro exitoso de profesional
- DADO datos comunes válidos, categorías `{PLUMBING, GAS}`, ubicación base válida y radio 10
- CUANDO se ejecuta `SignUpProfessionalUseCase`
- ENTONCES el resultado es `Success(User)` con `role = PROFESSIONAL`
- Y existe un `ProfessionalProfile` con esas categorías, ubicación y radio, `ratingAverage = 0.0` y `reviewCount = 0`

#### Escenario: Sin categorías
- DADO un conjunto de categorías vacío
- CUANDO se ejecuta `SignUpProfessionalUseCase`
- ENTONCES el resultado es `Error(Validation.CategoriesRequired)`

#### Escenario: Radio fuera de rango
- DADO un radio de 0 km o de 51 km
- CUANDO se ejecuta `SignUpProfessionalUseCase`
- ENTONCES el resultado es `Error(Validation.InvalidRadius)`

#### Escenario: Sin ubicación base
- DADO que no se obtuvo ubicación base
- CUANDO se ejecuta `SignUpProfessionalUseCase`
- ENTONCES el resultado es `Error(Validation.BaseLocationRequired)`

### Requirement: Rol fijo [Fase 1]

El rol DEBE elegirse en el registro y NO DEBE poder modificarse después. Ningún caso de uso
ni repositorio DEBE exponer una operación para cambiar el rol.

#### Escenario: Rol persistido
- DADO un usuario registrado como `CLIENT`
- CUANDO cierra sesión y vuelve a iniciarla
- ENTONCES la sesión expone `role = CLIENT`

### Requirement: Inicio de sesión [Fase 1]

El sistema DEBE permitir iniciar sesión con email y contraseña.

#### Escenario: Credenciales correctas
- DADO un usuario registrado
- CUANDO se ejecuta `SignInUseCase` con su email y contraseña
- ENTONCES el resultado es `Success(User)` y la sesión queda autenticada

#### Escenario: Credenciales incorrectas
- DADO un email registrado y una contraseña errónea, o un email inexistente
- CUANDO se ejecuta `SignInUseCase`
- ENTONCES el resultado es `Error(DomainError.InvalidCredentials)` (sin distinguir cuál de los dos falló)

#### Escenario: Campos vacíos
- DADO un email vacío
- CUANDO se ejecuta `SignInUseCase`
- ENTONCES el resultado es `Error(Validation.InvalidEmail)` sin llamar al repositorio

### Requirement: Cierre de sesión [Fase 1]

El sistema DEBE permitir cerrar sesión desde la pantalla de perfil de ambos roles.

#### Escenario: Cerrar sesión
- DADO una sesión autenticada
- CUANDO se ejecuta `SignOutUseCase`
- ENTONCES `ObserveSessionUseCase` emite `SessionState.SignedOut`
- Y la navegación muestra el grafo de autenticación con la pila de navegación vacía

### Requirement: Compuerta de sesión (auth gate) [Fase 1]

Al iniciar la app, `:app` DEBE observar `ObserveSessionUseCase` y mostrar: un indicador de carga
mientras el estado es `Loading`; el grafo de autenticación si es `SignedOut`; el grafo de cliente
si es `SignedIn` con rol `CLIENT`; el grafo de profesional si es `SignedIn` con rol `PROFESSIONAL`.

#### Escenario: Sesión previa de profesional
- DADO una sesión persistida de un usuario `PROFESSIONAL`
- CUANDO se abre la app
- ENTONCES se muestra el grafo de profesional con la pestaña Feed seleccionada

#### Escenario: Sin sesión
- DADO que no hay sesión
- CUANDO se abre la app
- ENTONCES se muestra la pantalla de inicio de sesión

### Requirement: Estados de UI de autenticación [Fase 1]

Las pantallas `SignIn` y `SignUp` DEBEN deshabilitar el botón de envío y mostrar progreso mientras
la operación está en curso, y DEBEN mostrar errores de validación junto al campo afectado y los
demás errores mediante snackbar (`userMessage` + `onUserMessageShown()`).

#### Escenario: Envío en curso
- DADO el formulario válido
- CUANDO el usuario pulsa "Ingresar"
- ENTONCES `uiState.isSubmitting` es `true` hasta recibir el resultado

#### Escenario: Error mostrado una sola vez
- DADO `userMessage = InvalidCredentials`
- CUANDO la UI muestra el snackbar y llama `onUserMessageShown()`
- ENTONCES `userMessage` pasa a `null`

### Requirement: Persistencia de sesión [Fase 1 / Fase 2]

En Fase 1 la sesión DEBE mantenerse en memoria mientras viva el proceso (se pierde al cerrar la app).
En Fase 2 la sesión DEBE persistir entre reinicios usando Firebase Auth, y el perfil de usuario
DEBE leerse de la colección `users`.

#### Escenario: Fase 2 — reinicio con sesión
- DADO un usuario autenticado en Firebase Auth
- CUANDO se reinicia la app
- ENTONCES `ObserveSessionUseCase` emite `SignedIn` con el `User` leído de `users/{uid}`

#### Escenario: Fase 2 — errores de Firebase
- DADO que Firebase lanza `FirebaseAuthUserCollisionException`
- CUANDO `FirebaseAuthRepository.signUp` la captura
- ENTONCES devuelve `Error(EmailAlreadyInUse)`; `FirebaseAuthInvalidCredentialsException` mapea a `InvalidCredentials` y `FirebaseNetworkException` a `Network`
