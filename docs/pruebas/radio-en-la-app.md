# La radio la juega la app y solo la app avisa puzzles

Rama `feature/radio-en-la-app`, comparada contra `develop`. Raúl decidió el 8 de noviembre de 2026 que la radio se juega en el teléfono, igual que los otros tres puzzles, y que el juego ya no avisa puzzles (cierra P8b).

## 1. Veredicto

✅ 271 pruebas, 0 fallos, 0 errores, 0 omitidas (`./mvnw clean verify`, 5 pruebas más que las 266 anteriores).

## 2. Qué cambió en el contrato

| Antes | Ahora |
|---|---|
| La radio la resolvía el juego con `{sintonizada: true}` | La radio la juega la app: `{iniciado: true}` al abrirla (`en_curso`) y `{sintonizada: true}` al sintonizarla (`resuelto`) |
| Cualquier rol avisaba cualquier puzzle (P8b abierto) | Solo la app avisa puzzles. El token del juego recibe `403` con `codigo: "puzzle_solo_app"` en los cuatro |
| La radio aparece cuando el juego registra `pescador_dialogo_completo` | Igual: sigue bloqueada (`409`) hasta entonces y pasa a `disponible` cuando el juego lo registra |

El orden de validación: primero el id del puzzle y la forma del aviso (`400`), después el rol (`403`), después el bloqueo (`409`). Un aviso mal formado del juego da `400`, no `403`. El evento de un puzzle ahora siempre tiene origen `app`.

La API sigue en `/api/v1`. No cambia el esquema ni hay migración.

## 3. Pruebas nuevas y cambiadas

- `PuzzleServiceTest`: la radio acepta `iniciado` y `sintonizada` y rechaza `completada` (`400`); el juego recibe `403 puzzle_solo_app` en los cuatro puzzles sin tocar nada; un aviso mal formado del juego da `400`; los eventos llevan origen `app`.
- `PuzzlesIT`: la radio se juega por HTTP (`en_curso`, `resuelto`, `coordenada_a` con origen `servidor`, repetir no cambia nada); el juego recibe `403` con el cuerpo exacto en los cuatro puzzles y no cambia estados ni eventos; la cadena completa y los demás escenarios usan el token de la app.
- `EmisionWebSocketIT`: el WebSocket emite `puzzle.actualizado` con `en_curso` y luego `resuelto` para la radio.
- `EscriturasSalaIT`, `JsonDocumentadoIT`, `PuzzleControllerTest`: usan el token de la app y el mensaje nuevo. `JsonDocumentadoIT` comprueba que cada JSON de las guías y de Swagger coincide con una respuesta real, incluido el `403` nuevo.
- `scripts/smoke-test.sh`: la radio la avisa la app (`iniciado` y `sintonizada`); `SmokeTestIT` lo corre en `./mvnw verify`.

Ninguna prueba se desactivó ni se borró.

## 4. Documentación

`docs/api.md`, `docs/modelo-datos.md`, `docs/tiempo-real.md`, `docs/PLAN.md`, `README.md`, Swagger (`PuzzleController`, `EjemplosOpenApi`, `ReportarPuzzleRequest`, `OpenApiConfig`) y las guías de Unity (el paso 8 pasa a "el juego solo escucha") y de Flutter (la app juega la radio, con su burbuja, su `iniciado` y su `sintonizada`).

## 5. Lo que queda abierto

- **Unity y Flutter** deben confirmar el cambio: el juego deja de mandar `PUT radio`; la app lo manda. Hasta que Unity lo cambie, su `PUT radio` recibe `403`.
- **Valores de la radio en la app** (dial, rango): viven en la app; el backend no los mide.
- **Revisión independiente** por un modelo de otra familia: no se hizo en este cambio. Raúl puede pedirla antes de mergear.
