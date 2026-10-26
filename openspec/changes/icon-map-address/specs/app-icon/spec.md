# Delta para Ícono de la app (`app-icon`)

> Cambio: `icon-map-address`. Alcance: el ícono con el que la app aparece en el lanzador y en los ajustes del sistema.
>
> Convención de verificación: todos los escenarios son visuales y se verifican a mano (ver `design.md`, checklist manual).

## Purpose

Da a FixIt una identidad visual propia en el lanzador de Android, en lugar del ícono genérico, compatible con las formas adaptativas y con los íconos temáticos.

## ADDED Requirements

### Requirement: Ícono adaptativo de la app [Fase 3]

La app DEBE declarar un ícono adaptativo propio, con una capa de fondo y una capa de primer plano vectoriales, y una capa monocroma para los íconos temáticos de Android 13 o superior. El dibujo del primer plano DEBE quedar dentro de la zona segura del ícono adaptativo (círculo central de 66 dp sobre 108 dp), de modo que ninguna máscara del lanzador lo recorte. El mismo ícono DEBE usarse como ícono redondo. Como la versión mínima es Android 8.0 (API 26), no hacen falta versiones en mapa de bits.

#### Escenario: Ícono en el lanzador [manual]
- DADO la app instalada en un emulador con Android 14
- CUANDO se abre la lista de apps del lanzador
- ENTONCES FixIt muestra su ícono propio y no el ícono genérico de Android

#### Escenario: Distintas máscaras [manual]
- DADO un lanzador que permite cambiar la forma de los íconos (círculo, cuadrado redondeado, gota)
- CUANDO se cambia la forma
- ENTONCES el dibujo del ícono se ve completo, sin recortes, en todas las formas

#### Escenario: Ícono temático [manual]
- DADO Android 13 o superior con "Íconos temáticos" activado
- CUANDO se mira el ícono de FixIt en la pantalla de inicio
- ENTONCES se muestra la versión monocroma teñida con los colores del sistema

#### Escenario: Ícono en ajustes y recientes [manual]
- DADO la app instalada
- CUANDO se abre Ajustes > Apps > FixIt y la vista de apps recientes
- ENTONCES aparece el mismo ícono propio
