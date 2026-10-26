# Delta para Autenticación (`auth`)

> Cambio: `fixit-mvp`. Alcance: crear una cuenta con rol fijo (`CLIENT` o `PROFESSIONAL`), iniciar y cerrar sesión, y exponer el estado de sesión que decide qué experiencia ve la persona (ver `navigation`).
>
> Convención de verificación: cada escenario está cubierto por un test JVM o por un ítem del checklist manual. Los escenarios cuyo título termina en `[manual]` se verifican a mano (ver `design.md` §15).

## ADDED Requirements

### Requirement: Registro de cliente [Fase 1]

El sistema DEBE permitir registrar una cuenta con rol `CLIENT` a partir de email, contraseña, nombre y teléfono. Al registrarse correctamente, la sesión DEBE quedar iniciada con ese usuario.

#### Escenario: Registro exitoso de cliente
- DADO un email no registrado y datos válidos
- CUANDO la persona se registra como cliente
- ENTONCES se crea la cuenta con `role = CLIENT`
- Y la sesión queda iniciada con ese usuario

#### Escenario: Email ya registrado
- DADO un email que ya pertenece a una cuenta
- CUANDO otra persona intenta registrarse con ese email
- ENTONCES el resultado es el error `EmailAlreadyInUse`
- Y no se crea ninguna cuenta ni cambia la sesión

#### Escenario: Email con mayúsculas y espacios
- DADO una cuenta registrada con `ana@fixit.test`
- CUANDO otra persona intenta registrarse con `" Ana@Fixit.Test "`
- ENTONCES el resultado es el error `EmailAlreadyInUse`
- Y iniciar sesión con `" Ana@Fixit.Test "` y la contraseña correcta inicia la sesión de la cuenta existente

### Requirement: Registro de profesional [Fase 1]

El sistema DEBE permitir registrar una cuenta con rol `PROFESSIONAL` que, además de los datos comunes, incluya una o más categorías de servicio, un punto base (latitud y longitud) y un radio de trabajo en km. Un profesional recién registrado DEBE tener 0 reseñas.

#### Escenario: Registro exitoso de profesional
- DADO datos comunes válidos, categorías `{PLUMBING, GAS}`, punto base válido y radio 10
- CUANDO la persona se registra como profesional
- ENTONCES se crea la cuenta con `role = PROFESSIONAL` y un perfil con esas categorías, ese punto base y ese radio
- Y el perfil indica 0 reseñas (`ratingSum = 0`, `reviewCount = 0`)

### Requirement: Validación de los datos de registro [Fase 1]

El sistema DEBE validar los datos antes de crear la cuenta y devolver únicamente el primer error encontrado, evaluando las reglas en el orden de la tabla. Si hay un error, NO DEBE crearse ninguna cuenta. Los valores válidos de nombre y teléfono DEBEN guardarse recortados.

| Orden | Campo | Regla | Error |
|---|---|---|---|
| 1 | Email | Recortado y en minúsculas; no vacío; formato `local@dominio.tld` | `Validation.InvalidEmail` |
| 2 | Contraseña | Mínimo 6 caracteres | `Validation.WeakPassword` |
| 3 | Nombre | Recortado, 2..60 caracteres | `Validation.InvalidName` |
| 4 | Teléfono | Recortado; se quitan espacios, guiones y paréntesis; `+` inicial opcional y 8..15 dígitos | `Validation.InvalidPhone` |
| 5 | Rol | Obligatorio | `Validation.RoleRequired` |
| 6 | Categorías (profesional) | Al menos 1 | `Validation.CategoriesRequired` |
| 7 | Punto base (profesional) | Obligatorio | `Validation.BaseLocationRequired` |
| 8 | Punto base (profesional) | Coordenadas válidas: latitud en [-90, 90] y longitud en [-180, 180] | `Validation.InvalidCoordinates` |
| 9 | Radio (profesional) | Entero 1..50 km | `Validation.InvalidRadius` |

#### Escenario: Teléfono inválido
- DADO un teléfono `"12ab"` y el resto de los datos válidos
- CUANDO la persona intenta registrarse
- ENTONCES el resultado es `Validation.InvalidPhone`
- Y no se crea ninguna cuenta

#### Escenario: Teléfono con separadores válidos
- DADO un teléfono `" +54 (11) 5555-1234 "` y el resto de los datos válidos
- CUANDO se valida el teléfono
- ENTONCES es aceptado

#### Escenario: Contraseña demasiado corta
- DADO una contraseña de 5 caracteres
- CUANDO la persona intenta registrarse
- ENTONCES el resultado es `Validation.WeakPassword`

#### Escenario: Nombre fuera de rango
- DADO un nombre de 1 carácter, o de 61 caracteres
- CUANDO la persona intenta registrarse
- ENTONCES el resultado es `Validation.InvalidName`

#### Escenario: Límites exactos válidos
- DADO una contraseña de 6 caracteres, nombres de 2 y de 60 caracteres, teléfonos de 8 y de 15 dígitos, y radios de 1 y de 50 km
- CUANDO se valida cada dato
- ENTONCES todos son aceptados

#### Escenario: Profesional sin categorías
- DADO un conjunto de categorías vacío
- CUANDO la persona intenta registrarse como profesional
- ENTONCES el resultado es `Validation.CategoriesRequired`

#### Escenario: Profesional sin punto base
- DADO que no se obtuvo el punto base
- CUANDO la persona intenta registrarse como profesional
- ENTONCES el resultado es `Validation.BaseLocationRequired`

#### Escenario: Punto base con coordenadas inválidas
- DADO un punto base con latitud 91, o con longitud -181
- CUANDO la persona intenta registrarse como profesional
- ENTONCES el resultado es `Validation.InvalidCoordinates`

#### Escenario: Radio fuera de rango
- DADO un radio de 0 km, o de 51 km
- CUANDO la persona intenta registrarse como profesional
- ENTONCES el resultado es `Validation.InvalidRadius`

#### Escenario: Se informa solo el primer error
- DADO un email inválido y una contraseña de 5 caracteres
- CUANDO la persona intenta registrarse
- ENTONCES el resultado es `Validation.InvalidEmail`

### Requirement: Rol fijo [Fase 1]

El rol DEBE elegirse en el registro y NO DEBE poder modificarse después, ni siquiera desde la edición de perfil.

#### Escenario: Rol persistido
- DADO un usuario registrado como `CLIENT`
- CUANDO cierra sesión y vuelve a iniciarla
- ENTONCES la sesión expone `role = CLIENT`

#### Escenario: Sin acción para cambiar el rol [manual]
- DADO una sesión iniciada con cualquier rol
- CUANDO se revisan todas las acciones de cuenta y de perfil disponibles
- ENTONCES ninguna permite cambiar el rol

### Requirement: Inicio de sesión [Fase 1]

El sistema DEBE permitir iniciar sesión con email y contraseña. Antes de consultar la cuenta, DEBE validar en este orden: email (mismas reglas de recorte, minúsculas y formato del registro; `Validation.InvalidEmail`) y contraseña no vacía (`Validation.EmptyPassword`).

#### Escenario: Credenciales correctas
- DADO un usuario registrado
- CUANDO inicia sesión con su email y contraseña
- ENTONCES la sesión queda iniciada con ese usuario y su rol

#### Escenario: Credenciales incorrectas
- DADO un email registrado con una contraseña errónea, o un email inexistente
- CUANDO la persona intenta iniciar sesión
- ENTONCES el resultado es `InvalidCredentials`, sin indicar cuál de los dos datos falló

#### Escenario: Email vacío
- DADO un email vacío
- CUANDO la persona intenta iniciar sesión
- ENTONCES el resultado es `Validation.InvalidEmail`
- Y no se consulta la cuenta

#### Escenario: Contraseña vacía
- DADO un email válido y una contraseña vacía
- CUANDO la persona intenta iniciar sesión
- ENTONCES el resultado es `Validation.EmptyPassword`
- Y no se consulta la cuenta

### Requirement: Cierre de sesión [Fase 1]

El sistema DEBE permitir cerrar sesión desde la pantalla de perfil de ambos roles. En Fase 4, iniciar y cerrar sesión además suscriben y desuscriben al dispositivo de los temas de avisos (ver `notifications`).

#### Escenario: Cerrar sesión
- DADO una sesión iniciada
- CUANDO el usuario toca "Cerrar sesión"
- ENTONCES el estado de sesión pasa a "sin sesión"
- Y ya no se puede acceder a datos del usuario anterior sin volver a iniciar sesión

### Requirement: Estado y persistencia de sesión en memoria [Fase 1]

El sistema DEBE exponer el estado de sesión con tres valores: determinando, sin sesión y con sesión (incluyendo el rol). En Fase 1 la sesión DEBE mantenerse mientras viva el proceso de la app y NO DEBE sobrevivir a su cierre.

#### Escenario: Sesión estable durante el uso [manual]
- DADO una sesión iniciada
- CUANDO se rota el dispositivo o se navega entre pantallas
- ENTONCES el estado de sesión sigue "con sesión" con el mismo rol

#### Escenario: Reinicio de la app en Fase 1
- DADO una sesión iniciada
- CUANDO el proceso de la app se cierra y se vuelve a abrir
- ENTONCES el estado de sesión es "sin sesión"

### Requirement: Estados de las pantallas de autenticación [Fase 1]

Las pantallas de inicio de sesión y de registro DEBEN deshabilitar el botón de envío y mostrar progreso mientras la operación está en curso. Los errores de validación DEBEN mostrarse junto al campo afectado y los demás errores como mensaje que se muestra una sola vez.

#### Escenario: Envío en curso
- DADO un formulario válido
- CUANDO el usuario toca "Ingresar"
- ENTONCES el botón queda deshabilitado y se muestra progreso hasta recibir el resultado

#### Escenario: Error de validación junto al campo
- DADO un teléfono inválido en el registro
- CUANDO el usuario envía el formulario
- ENTONCES el error se muestra junto al campo "Teléfono" y el resto de los datos ingresados se conserva

#### Escenario: Mensaje mostrado una sola vez
- DADO un error `InvalidCredentials` ya mostrado al usuario
- CUANDO el mensaje se descarta y luego se rota el dispositivo
- ENTONCES el mensaje no vuelve a mostrarse

### Requirement: Sesión persistente y errores de red [Fase 2]

Con el backend de Fase 2, la sesión DEBE persistir entre reinicios de la app y el perfil del usuario DEBE recuperarse junto con ella. Los fallos del servicio DEBEN traducirse a los mismos errores de la Fase 1 (`EmailAlreadyInUse`, `InvalidCredentials`) y a `Network` cuando no hay conectividad. Una operación de escritura que no termine en 15 segundos DEBE devolver `Network`.

#### Escenario: Reinicio con sesión [manual]
- DADO un usuario con sesión iniciada
- CUANDO se cierra y se vuelve a abrir la app
- ENTONCES el estado es "con sesión" con su rol y sus datos de perfil

#### Escenario: Registro sin conectividad [manual]
- DADO un dispositivo sin conexión
- CUANDO la persona intenta registrarse
- ENTONCES el resultado es `Network`, como máximo a los 15 segundos
- Y el formulario conserva lo ingresado para reintentar

#### Escenario: Mismos errores que en Fase 1 [manual]
- DADO un email ya registrado en el servicio
- CUANDO otra persona intenta registrarse con él
- ENTONCES el resultado es `EmailAlreadyInUse`

### Requirement: Registro consistente con el servicio [Fase 2]

Con el backend de Fase 2, el registro DEBE crear primero la cuenta de autenticación y después el documento de perfil del usuario. Si falla la escritura del perfil, DEBE eliminarse la cuenta de autenticación recién creada (mejor esfuerzo) y devolverse el error. Mientras el registro o el inicio de sesión estén en curso, la sesión DEBE permanecer en "sin sesión", de modo que la pantalla de autenticación no se reemplace antes de recibir el resultado; la eliminación de la cuenta recién creada DEBE completarse aunque la pantalla se cierre. Al iniciar la app, mientras exista la cuenta de autenticación pero el perfil todavía no se haya leído, la sesión DEBE estar en "determinando". Iniciar sesión con una cuenta sin perfil DEBE cerrar la sesión y devolver `ProfileMissing` con un mensaje que indique que la cuenta no tiene perfil y que debe crearse nuevamente.

#### Escenario: Falla al escribir el perfil [manual]
- DADO que la cuenta de autenticación se creó y la escritura del perfil falla
- CUANDO la persona intenta registrarse
- ENTONCES el resultado es el error de la escritura (por ejemplo `Network`)
- Y la cuenta de autenticación recién creada se elimina y la sesión no queda iniciada

#### Escenario: Pantalla de registro estable durante la operación [manual]
- DADO un registro o un inicio de sesión en curso
- CUANDO la cuenta de autenticación ya existe pero el perfil todavía se está escribiendo o leyendo
- ENTONCES la pantalla de autenticación sigue visible y recibe el resultado de la operación
- Y solo después, si fue exitosa, se muestra la experiencia del rol

#### Escenario: Cuenta sin perfil [manual]
- DADO una cuenta de autenticación sin documento de perfil
- CUANDO la persona intenta iniciar sesión
- ENTONCES el resultado es `ProfileMissing` con el mensaje "Su cuenta no tiene un perfil asociado. Cree la cuenta nuevamente."
- Y la sesión se cierra

#### Escenario: Error inesperado sin relación con el perfil
- DADO cualquier operación que falla con `Unknown`
- CUANDO se muestra el error
- ENTONCES el mensaje es "Ocurrió un error inesperado. Intente nuevamente."
- Y no sugiere crear la cuenta nuevamente
