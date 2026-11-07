# P8 · Las claves del juego solo las manda el juego

Rama `feature/p8-claves-del-juego`, comparada contra `feature/listo-para-railway` (`e2a2c10`). Raúl decidió (P8) que la app no manda `pescador_dialogo_completo`, `pieza_1`, `pieza_2`, `pieza_3`, `lookout_enabled` ni `observatory_enabled`: esas claves las manda el juego, y la app solo avisa sus puzzles con `PUT puzzles`. Hasta ahora el backend las aceptaba de cualquier rol, así que la app podía habilitar puzzles sin pasar por el juego. Ahora `POST /api/v1/salas/{codigo}/descubrimientos` con el token de la app y una de esas claves responde `403` con `codigo: "clave_solo_juego"`, sin tocar la sala. `PUT puzzles` no cambia. La API sigue en `/api/v1`. No hay migraciones nuevas ni dependencias nuevas.

## 1. Veredicto

✅ 266 pruebas, 0 fallos, 0 errores, 0 omitidas.

| Total | Unitarias | Integración | Fallos | Errores | Omitidas | Fecha | Commit | Rama |
|---:|---:|---:|---:|---:|---:|---|---|---|
| 266 | 139 | 127 | 0 | 0 | 0 | 2026-11-07 10:20 | `9e87a11` | `feature/p8-claves-del-juego` |

Las cifras salen de los reportes de `./mvnw clean verify` sobre `9e87a11`, con `python3 scripts/resumen-pruebas.py`. El commit siguiente solo agrega este archivo y su fila en `docs/pruebas/README.md`, que ninguna prueba lee. La rama partió de `e2a2c10` con 249 pruebas (124 unitarias y 125 de integración). Suma 17: 15 unitarias (`DescubrimientoServiceTest` +13: 6 de la app con `403`, 6 del juego con `201` y la lista exacta; `HabilitacionPuzzlesServiceTest` +1; `DescubrimientoControllerTest` +1) y 2 de integración (`EscriturasSalaIT`). `./mvnw clean verify` también terminó en `BUILD SUCCESS` con las mismas 266 sobre el primer commit (`d8e3808`).

**Una corrida en rojo, por el reposo del equipo.** Antes del commit de este archivo, `./mvnw clean verify` sobre el mismo código falló una vez: `SmokeTestIT.cadenaCompleta` esperó 305 s el `GET /actuator/health` (`curl: (28) Operation timed out after 305374 milliseconds`) y el log de Spring dice `DataSourceHealthIndicator (db) took 305336ms to respond`. `pmset -g log` muestra que el Mac, con batería, entró en reposo a las 10:57:00 (`Entering Sleep state due to 'Maintenance Sleep'`) y despertó a las 11:02:07: son los mismos 305 s, y la siguiente prueba corrió normal al despertar. No es del código: las otras cuatro corridas completas pasaron con el mismo orden de pruebas. La corrida siguiente, con `caffeinate -i ./mvnw clean verify`, terminó en `BUILD SUCCESS` con 266 pruebas y 0 fallos.

## 2. Cómo reproducirlo

Con Docker corriendo (las pruebas `*IT` levantan `postgres:17-alpine` con Testcontainers):

```sh
git checkout feature/p8-claves-del-juego
./mvnw clean verify
python3 scripts/resumen-pruebas.py               # secciones 1 y 4 de este archivo
python3 scripts/resumen-pruebas.py --solo-conteo # una línea
```

Las pruebas adversariales de la sección 6 corrieron contra el JAR real y un Postgres propio:

```sh
docker run -d --name vestigia-pg-p8 -e POSTGRES_PASSWORD=<contraseña-local> -e POSTGRES_DB=vestigia -p 55439:5432 postgres:17-alpine
./mvnw -q package -DskipTests
DB_URL=jdbc:postgresql://localhost:55439/vestigia DB_USER=postgres DB_PASSWORD=<contraseña-local> PORT=18088 \
	java -jar target/vestigia-0.0.1-SNAPSHOT.jar
curl -X POST http://localhost:18088/api/v1/salas/BACALAR/descubrimientos \
	-H "Authorization: Bearer $TOKEN_APP" -H 'Content-Type: application/json' -d '{"clave": "pieza_1"}'
docker rm -f vestigia-pg-p8
```

### 2.1 Las pruebas detectan lo que dicen cubrir

Se rompió el código a propósito, se corrieron las pruebas afectadas y se restauró con `git checkout`.

| Mutación | Pruebas que fallaron |
|---|---|
| M1. Quitar el chequeo (`if (false)`) | Unitarias: `DescubrimientoServiceTest.claveDelJuegoDesdeLaAppDa403`, las 6 claves. Integración (corrida aparte, porque las unitarias cortan el build antes de failsafe): `EscriturasSalaIT.clavesDelJuegoSoloDelJuego` (falla en la primera clave, `pescador_dialogo_completo`), `EscriturasSalaIT.claveDelJuegoRepetidaPorLaAppDa403` y `EmisionWebSocketIT.escriturasRepetidasNoEmiten` |
| M2. Sacar `pieza_3` de `CLAVES_DEL_JUEGO` | `DescubrimientoServiceTest.clavesDelJuego`, `DescubrimientoServiceTest.claveDelJuegoDesdeLaAppDa403` con `pieza_3` y `HabilitacionPuzzlesServiceTest.requisitosSincronizadosConLasClavesReservadas` |
| M3. Mover el chequeo después de `actividad.bloquear` | `DescubrimientoServiceTest.claveDelJuegoDesdeLaAppDa403`, las 6 claves (`verifyNoInteractions` ve el bloqueo) |

M3 no cambia lo que ve un cliente: la excepción revierte la transacción, así que la integración no la distingue. La prueba unitaria fija el orden pedido (el `403` no toma el candado de la sala).

## 3. Requisito → pruebas

| Requisito | Pruebas | Resultado |
|---|---|:-:|
| `CLAVES_DEL_JUEGO` pública en `DescubrimientoService`, junto a `CLAVES_DEL_SERVIDOR`, con las seis claves | `service.DescubrimientoServiceTest` · CLAVES_DEL_JUEGO son exactamente las seis claves que el diseño reserva al juego | ✅ |
| Rol app con una clave del juego: `403`, `codigo: "clave_solo_juego"`, mensaje, `details` vacío | `service.DescubrimientoServiceTest` · Una clave del juego enviada por la app da 403 clave_solo_juego sin bloquear la sala ni leer el repositorio (6 claves) · `controller.DescubrimientoControllerTest` · Una clave del juego enviada por la app responde 403 con codigo clave_solo_juego en el cuerpo uniforme · `EscriturasSalaIT` · Cada clave del juego enviada por la app da 403 clave_solo_juego y no cambia la sala… | ✅ |
| El `403` va antes de `actividad.bloquear`: no toca la sala ni renueva la expiración | `service.DescubrimientoServiceTest` (la misma, con `verifyNoInteractions(actividad, descubrimientos, habilitacion)`) · `EscriturasSalaIT` (la misma: `GET estado`, `expira_en`, `ultima_actividad` y eventos iguales tras avanzar el reloj 10 minutos). Mutación M3 | ✅ |
| Rol juego: igual que antes | `service.DescubrimientoServiceTest` · Una clave del juego enviada por el juego se registra con origen juego (6 claves) · `EscriturasSalaIT` (la misma: las seis dan `201` con origen `juego` y habilitan `radio` y `photo`) · `PuzzlesIT`, `EmisionWebSocketIT`, `SmokeTestIT` sin cambios | ✅ |
| Una clave del juego que ya existe también da `403` a la app | `EscriturasSalaIT` · Una clave del juego que ya existe da 403 a la app, no 200: no devuelve el descubrimiento ni renueva la expiración | ✅ |
| Tras el `403` no se guarda evento | `EscriturasSalaIT` (las dos: la lista de eventos de la sala es la misma) | ✅ |
| Tras el `403` no se emite nada por WebSocket | `ws.EmisionWebSocketIT` · Las escrituras repetidas … no emiten nada; una clave del juego enviada por la app da 403 y tampoco emite | ✅ |
| Las claves del servidor siguen con su `403` sin `codigo` | `service.DescubrimientoServiceTest` · Las cuatro claves del servidor dan 403 con cualquier rol y no tocan la sala · `controller.DescubrimientoControllerTest` · Una clave del servidor responde 403 con el cuerpo de error uniforme | ✅ |
| Los requisitos de `HabilitacionPuzzlesService` no se desincronizan de las claves reservadas | `service.HabilitacionPuzzlesServiceTest` · Cada descubrimiento que piden los requisitos es una clave del juego o del servidor, y cada clave del juego es requisito de algún puzzle. Mutación M2 | ✅ |
| `PUT puzzles` no se restringe por rol | Sin prueba nueva: no cambió. Adversarial: la app reporta `radio` y recibe `200` (sección 6) | ✅ sin cambio |
| Swagger: el `403` menciona `clave_solo_juego`, con un ejemplo de una respuesta real | `JsonDocumentadoIT` · Cada ejemplo de respuesta de Swagger (/v3/api-docs) tiene los campos y tipos de una respuesta real · `DocumentacionIT` · Cada operación tiene resumen, descripción y ejemplos; cada error usa ErrorBody con codigo en su ejemplo. El ejemplo es la respuesta real de la sección 6 | ✅ |
| `docs/api.md` con el bloque del `403` | `JsonDocumentadoIT` · Cada bloque de respuesta de la documentación tiene los campos y tipos de una respuesta real | ✅ |
| Pruebas que mandaban claves del juego con el token de la app | `EscriturasSalaIT.descubrimientoNuevoResponde201`, `DescubrimientoServiceTest.nuevoGuardaEventoYRecalcula` y `EmisionWebSocketIT.escriturasRepetidasNoEmiten` usaban `pieza_1` para probar el origen `app`: ahora usan `nota_de_prueba`. `DescubrimientoServiceTest.repetidoDevuelveElOriginal` probaba la idempotencia con la app: ahora con el juego. Ninguna probaba que la app pudiera mandar esas claves | ✅ |
| `/prueba-ws.html` | Sin cambio: la página no manda descubrimientos (crear, unirse, `GET estado` y WebSocket) | ✅ revisado |
| `scripts/smoke-test.sh` manda las claves como juego | `SmokeTestIT` · smoke-test.sh recorre la partida completa contra el backend real… | ✅ |

## 4. Lista completa de pruebas

| Tipo | Clase | Prueba | Tiempo (s) | Resultado |
|---|---|---|---:|---|
| unitaria | `config.ApiHttpConfigTest` | corsRechazaOrigenNoConfigurado | 0.026 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | corsPermiteOrigenConfigurado | 0.004 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | registraUnaLineaPorPeticionSinTokens(CapturedOutput) | 0.014 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | noRegistraRutasFueraDeApi(CapturedOutput) | 0.017 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Los tipos correctos se siguen leyendo, y en un objeto libre (Map) cada valor conserva su tipo JSON | 0.017 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Una clave repetida en el mismo objeto falla en la raíz, anidada, en un Map o en un objeto libre, aunque los dos valores coincidan | 0.003 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un texto o un número en un campo booleano no se convierte: falla, también "true" entre comillas | 0.002 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un texto o un booleano en un campo numérico no se convierte: falla | 0.001 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un número o un booleano en un campo de texto no se convierte a texto: falla | 0.001 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | La misma clave en objetos distintos, hermanos o anidados, se sigue leyendo | 0.001 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | Los errores sin código traen "codigo": null en 400, 401, 403, 404, 405, 409 y 500 | 0.019 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | Los errores salen en JSON aunque el cliente pida otro formato: 406 con Accept XML y 409 con Accept HTML | 0.004 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | rutaInexistenteDevuelve404ConCuerpoUniforme | 0.002 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | jsonMalformadoDevuelve400ConCuerpoUniforme | 0.003 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | validacionDevuelve400ConDetallePorCampo | 0.004 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | errorInesperadoDevuelve500SinDetallesInternos | 0.002 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | Un error con código lo lleva en codigo y sus detalles en details, con el mismo cuerpo uniforme | 0.003 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | conflictoConservaSuCodigoYMensaje | 0.002 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Sin clave, con una clave que no es texto o con datos que no son un objeto JSON da 400 sin llamar al servicio | 0.016 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Unos datos de más de 4096 bytes en JSON dan 400 con detalle en datos, sin llamar al servicio | 0.004 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Sin token da 401 y con el token de otra sala da 403, sin llamar al servicio | 0.003 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Una clave del servidor responde 403 con el cuerpo de error uniforme | 0.003 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Repetir una clave responde 200 con el descubrimiento que ya existía | 0.005 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Una clave vacía, con mayúsculas, guiones, acentos, espacios o de más de 40 caracteres da 400 con detalle | 0.008 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Un descubrimiento nuevo responde 201 con clave, datos, origen y creado_en | 0.002 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Una clave del juego enviada por la app responde 403 con codigo clave_solo_juego en el cuerpo uniforme | 0.003 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Sin datos, con datos null o con el aviso fuera de datos da 400 con details en datos y codigo null, sin llamar al servicio | 0.016 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Sin token da 401 y con el token de otra sala da 403, con codigo null y sin llamar al servicio | 0.003 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Un aviso inválido para el puzzle responde 400 con el details del servicio y codigo null | 0.003 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | PUT puzzle responde 200 con {puzzle, estado, datos} y pasa al servicio el id de la ruta y los datos | 0.005 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Un puzzle bloqueado responde 409 con codigo puzzle_bloqueado en el cuerpo uniforme | 0.003 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | datos que no es un objeto JSON llega al servicio tal cual, para que responda 400 con details | 0.005 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Los valores de datos llegan al servicio con su tipo JSON: "true" es texto, 1 es número y true es booleano | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | La app recibe 403 en PUT escena y en DELETE antes de validar el cuerpo, sin llamar al servicio | 0.027 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | pueblosPasaElFiltroDeDisponibles | 0.015 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeOtraSalaDa403 | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena sin token da 401 | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | unirseNoPideTokenYDevuelveElDeLaApp | 0.010 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearResponde201ConCodigoEstadoYToken | 0.031 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | DELETE de la sala responde 204 sin cuerpo y sin token da 401 | 0.004 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena sin escena o con escena vacía da 400 con detalle, sin llamar al servicio | 0.010 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDesconocidoDa401 | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearSinCodigoDa400ConDetalle | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeLaSalaDevuelveElEstadoEnSnakeCase | 0.009 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena responde 200 con escena_actual | 0.004 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | El token de otra sala recibe 403 en PUT escena y DELETE, sin llamar al servicio | 0.008 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearConPuebloOcupadoDa409ConCuerpoUniforme | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoSinTokenDa401 | 0.002 | ✅ pasó |
| unitaria | `dto.JsonAcotadoTest` | Los datos que caben en el límite, en bytes de su JSON compacto, son válidos; nulos también | 0.058 | ✅ pasó |
| unitaria | `dto.JsonAcotadoTest` | Un byte más del límite es inválido, y los acentos cuentan como dos bytes | 0.029 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear devuelve la sala si sigue viva | 0.053 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Renovar pone ultima_actividad en ahora y expira_en en ahora más la expiración configurada | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar el cierre de una sala finalizada guarda el evento y ultima_actividad, pero no renueva expira_en | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Los tipos de evento se guardan con su punto: sala.creada, app.unida, descubrimiento.registrado... | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar la expiración guarda y publica sala.expirada con origen servidor sin tocar ultima_actividad ni expira_en | 0.002 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar renueva la sala, guarda el evento y lo publica para el WebSocket del Paso 4 | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear una sala finalizada, expirada o inexistente responde 401 | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear una sala viva cuyo expira_en ya pasó responde 401 aunque el job no la haya marcado | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Una clave del juego enviada por el juego se registra con origen juego "pescador_dialogo_completo" | 0.093 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Una clave del juego enviada por el juego se registra con origen juego "pieza_1" | 0.002 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Una clave del juego enviada por el juego se registra con origen juego "pieza_2" | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Una clave del juego enviada por el juego se registra con origen juego "pieza_3" | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Una clave del juego enviada por el juego se registra con origen juego "lookout_enabled" | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Una clave del juego enviada por el juego se registra con origen juego "observatory_enabled" | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Si la sala terminó entre el token y la escritura, el 401 de bloquear se propaga sin guardar nada | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | CLAVES_DEL_JUEGO son exactamente las seis claves que el diseño reserva al juego | 0.004 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | El descubrimiento se guarda en la sala del token con la clave recibida | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Una clave del juego enviada por la app da 403 clave_solo_juego sin bloquear la sala ni leer el repositorio "pescador_dialogo_completo" | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Una clave del juego enviada por la app da 403 clave_solo_juego sin bloquear la sala ni leer el repositorio "pieza_1" | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Una clave del juego enviada por la app da 403 clave_solo_juego sin bloquear la sala ni leer el repositorio "pieza_2" | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Una clave del juego enviada por la app da 403 clave_solo_juego sin bloquear la sala ni leer el repositorio "pieza_3" | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Una clave del juego enviada por la app da 403 clave_solo_juego sin bloquear la sala ni leer el repositorio "lookout_enabled" | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Una clave del juego enviada por la app da 403 clave_solo_juego sin bloquear la sala ni leer el repositorio "observatory_enabled" | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Repetir una clave devuelve el descubrimiento original sin guardar otro, sin evento y renovando la sala | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Un descubrimiento nuevo se guarda con el origen del rol, guarda su evento y luego recalcula los puzzles | 0.003 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Sin datos, el descubrimiento se guarda con un objeto vacío y origen juego si lo manda el juego | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Las cuatro claves del servidor dan 403 con cualquier rol y no tocan la sala | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Un resultado del servidor se guarda con origen servidor y sus datos, guarda su evento y luego recalcula los puzzles | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | radio se habilita con pescador_dialogo_completo y no antes | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | photo se habilita solo con las tres piezas, lleguen en el orden que lleguen | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Solo un puzzle bloqueado pasa a disponible: los que están disponibles, en curso o resueltos no cambian | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Recalcular pasa a disponible los puzzles habilitados y guarda un evento puzzle.habilitado de origen servidor con {puzzle, estado, datos} por cada uno | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | observatory pide coordenada_a, coordenada_b, observatory_enabled y lookout resuelto; falta uno y no se habilita | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Recalcular sin requisitos nuevos no cambia nada ni guarda eventos | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | lookout pide photo resuelto y lookout_enabled; la bandera puede llegar antes o después | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Cada descubrimiento que piden los requisitos es una clave del juego o del servidor, y cada clave del juego es requisito de algún puzzle | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | en_curso más iniciado y resuelto más cualquier aviso responden el estado actual, solo renuevan la sala y no guardan eventos ni descubrimientos | 0.038 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | El evento lleva el origen del rol que avisó: juego o app | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Si la sala terminó entre el token y la escritura, el 401 de bloquear se propaga sin leer ni tocar el puzzle | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Las coordenadas salen de la configuración, sin convertirse a número | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | disponible más iniciado pasa a en_curso, guarda los datos y puzzle.iniciado con {puzzle, estado, datos} y el origen del rol, sin crear descubrimiento | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Un puzzle bloqueado da 409 con codigo puzzle_bloqueado ante cualquier aviso válido y no cambia nada | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Cada puzzle crea su resultado con origen servidor: coordenada_a con AR "137", coordenada_b con DEC "+042" como texto, observatorio_visible y magnetar_localizado | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | disponible o en_curso más completada pasa a resuelto: guarda puzzle.resuelto y después crea el resultado | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Si falta la fila del puzzle, un estado imposible, falla con un mensaje que nombra la sala y el puzzle, sin escribir nada | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | radio solo acepta {sintonizada: true}: iniciado o completada dan 400; los puzzles de la app no aceptan sintonizada | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Solo vale un objeto con una clave aceptada y el booleano true: un valor que no es objeto, texto, número, false, null, claves extra, vacío o una clave ajena dan 400 con details en datos | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Un id de puzzle desconocido da 400 con la lista de ids, también los viejos fotografia, mirador y observatorio y las variantes de mayúsculas o espacios | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearFueraDelCatalogoDa400SinTocarSalas | 0.085 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Cambiar la escena la guarda y registra escena.cambiada con la anterior y la nueva | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | otraViolacionDeIntegridadNoSeDisfrazaDe409 | 0.003 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseSinSalaVivaDa404 | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | listarPueblosMarcaOcupadosLosQueTienenSalaViva | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseEnlazaConUnSoloUpdateYRenuevaLaExpiracion | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearConPuebloOcupadoDa409 | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Unirse guarda el evento app.unida con origen app sobre la sala enlazada | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Cerrar la sala la pasa a finalizada y guarda sala.finalizada con origen juego | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Sin salas vencidas, el job no registra nada | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Una escena fuera de introduccion, pueblo, faro, observatorio y desenlace da 400 con la lista de válidas | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Crear la sala guarda el evento sala.creada con origen juego y el código en el payload | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Unirse a una sala con expira_en vencido que el job aún no marcó responde 404 con el mensaje de sala inexistente | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | El estado ordena los puzzles y reporta los dispositivos que da la presencia del WebSocket | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Si unirse pierde la carrera no guarda ningún evento | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Repetir la escena actual responde igual y solo renueva la sala, sin evento | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseASalaVivaQueYaTieneAppDa409 | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | La app no puede cerrar la sala: 403 sin tocarla | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | cadaSalaRecibeUnTokenDistinto | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearInsertaLosCuatroPuzzlesBloqueados | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | El job bloquea las salas vivas vencidas según el reloj, las marca expirada y guarda sala.expirada de cada una | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearNormalizaElCodigoYGuardaSoloElHashDelToken | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | autenticarDistingueElRolPorElHash | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | autenticarConTokenDesconocidoNoDaCredencial | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | La app no puede cambiar la escena: 403 sin tocar la sala | 0.000 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Autenticar con el token de una sala viva con expira_en vencido no da credencial; justo en expira_en sí | 0.000 | ✅ pasó |
| unitaria | `ws.EmisorEventosTest` | Cada tipo de evento tiene su emisión: puzzle.actualizado y descubrimiento.nuevo a todos, escena.cambiada solo a la app, sala.cerrada cierra la sala, crear y unirse no emiten | 0.031 | ✅ pasó |
| unitaria | `ws.SalaWebSocketHandlerTest` | Al autenticarse, el cierre programado por el plazo de auth se cancela | 0.078 | ✅ pasó |
| unitaria | `ws.SalaWebSocketHandlerTest` | Un auth rechazado no cancela el plazo: la sesión se cierra con 4001 y el plazo ya no hace nada | 0.001 | ✅ pasó |
| unitaria | `ws.SalaWebSocketHandlerTest` | Al abrir la conexión fija BLOCKING_SEND_TIMEOUT de Tomcat en la sesión con vestigia.ws.envio-limite, en milisegundos | 0.029 | ✅ pasó |
| unitaria | `ws.SalaWebSocketHandlerTest` | Al cerrarse una conexión sin autenticar, el cierre programado por el plazo de auth se cancela | 0.001 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | desconectar olvida una sesión rechazada, así que no queda guardada para siempre | 0.026 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Una sala que se cerró después de validar el token no registra la conexión: conectar devuelve false y no envía nada | 0.002 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | auth.ok es el primer mensaje de una conexión y los demás salen en el orden en que se encolaron | 0.004 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | La conexión reemplazada sigue autenticada hasta que Tomcat la cierra: un auth o un mensaje suyo no crean otra conexión ni otro close, y al desconectarse no avisa | 0.002 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Si la cola de una conexión pasa del límite de bytes, se descarta y la conexión se cierra con 4500 | 0.005 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Al desconectarse, lo que quedaba en la cola se descarta: no hay otro envío ni otro close sobre la sesión cerrada | 0.205 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Tras cerrarSala, la sesión sigue autenticada hasta que Tomcat la cierra: responder, conectar o cerrarSiNoSeAutentico no producen otro close ni otro envío | 0.002 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Si un envío falla, la conexión se cierra con 4500 y lo que seguía en su cola se descarta | 0.001 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Una sesión que el servidor ya decidió cerrar sin auth no se registra aunque su auth llegue después | 0.001 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Un envío que se bloquea no frena a nadie: enviar, dispositivos y la otra sala responden, y al soltarse sale todo en orden | 0.019 | ✅ pasó |
| integración | `AplicacionIT` | El programador que comparten el plazo de auth, los pings y el job de expiración tiene dos hilos | 0.002 | ✅ pasó |
| integración | `AplicacionIT` | rutaInexistenteBajoSalasDa404SinPedirToken | 0.005 | ✅ pasó |
| integración | `AplicacionIT` | elJobDeExpiracionEstaProgramadoCadaMinuto | 0.002 | ✅ pasó |
| integración | `AplicacionIT` | openApiDocumentaLosEndpointsDeSalasYElToken | 0.371 | ✅ pasó |
| integración | `AplicacionIT` | swaggerUiYOpenApiEstanDisponibles | 0.018 | ✅ pasó |
| integración | `AplicacionIT` | GET /pueblos con Accept: application/xml responde 406 con el error uniforme en JSON, no con un cuerpo vacío | 0.014 | ✅ pasó |
| integración | `AplicacionIT` | healthResponde200ConEstadoUp | 0.007 | ✅ pasó |
| integración | `CatalogoPueblosIT` | flywayAplicaV2YLosCodigosEstanEnLaTabla | 0.008 | ✅ pasó |
| integración | `CatalogoPueblosIT` | seedDeclara101CodigosValidosYSinDuplicados | 0.004 | ✅ pasó |
| integración | `ConfiguracionDespliegueIT` | Actuator publica solo health: info, env, beans, metrics, heapdump y los demás responden 404 | 0.039 | ✅ pasó |
| integración | `ConfiguracionDespliegueIT` | Hibernate solo valida el esquema (ddl-auto=validate) y Flyway lo crea al arrancar | 0.002 | ✅ pasó |
| integración | `ConfiguracionDespliegueIT` | Los errores fuera de /api no muestran stacktrace, excepción ni versión del servidor | 0.013 | ✅ pasó |
| integración | `ConfiguracionDespliegueIT` | application.properties toma DB_URL, DB_USER, DB_PASSWORD, PORT y CORS_ALLOWED_ORIGINS del entorno | 0.002 | ✅ pasó |
| integración | `DocumentacionIT` | La página de prueba /prueba-ws.html se sirve como HTML, manda el token en auth y no lo pone en la URL ni en el navegador | 0.006 | ✅ pasó |
| integración | `DocumentacionIT` | Cada operación tiene resumen, descripción y ejemplos; cada error usa ErrorBody con codigo en su ejemplo | 0.016 | ✅ pasó |
| integración | `DocumentacionIT` | Swagger UI se sirve en /swagger-ui/index.html | 0.005 | ✅ pasó |
| integración | `DocumentacionIT` | Las rutas con token declaran el esquema Bearer y las públicas no; el esquema es HTTP bearer | 0.011 | ✅ pasó |
| integración | `DocumentacionIT` | /v3/api-docs documenta exactamente las rutas y métodos reales de los controladores de la API | 0.010 | ✅ pasó |
| integración | `EscriturasSalaIT` | Con expira_en vencido y sin que corra el job, unirse da 404 y GET estado da 401 a los dos tokens; la sala no revive | 0.063 | ✅ pasó |
| integración | `EscriturasSalaIT` | pescador_dialogo_completo habilita radio y GET estado lo muestra disponible | 0.038 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una sala en esperando_app también se puede cerrar | 0.032 | ✅ pasó |
| integración | `EscriturasSalaIT` | El token del juego de otra sala recibe 403 en PUT escena y DELETE, y la sala no cambia | 0.037 | ✅ pasó |
| integración | `EscriturasSalaIT` | lookout_enabled que llega después de photo resuelto habilita lookout en la misma petición | 0.029 | ✅ pasó |
| integración | `EscriturasSalaIT` | Diez peticiones simultáneas con la misma clave dan un 201 y nueve 200, una sola fila y un solo evento | 0.056 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una clave del juego que ya existe da 403 a la app, no 200: no devuelve el descubrimiento ni renueva la expiración | 0.052 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una escena inválida, vacía, de otro tipo o ausente da 400 y no cambia nada | 0.053 | ✅ pasó |
| integración | `EscriturasSalaIT` | El juego cierra la sala: 204, queda finalizada sin renovar expira_en, guarda sala.finalizada y los dos tokens dan 401 | 0.060 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un descubrimiento, nuevo o repetido, renueva ultima_actividad y expira_en con la duración configurada | 0.041 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una sala con expira_en vencido que el job aún no marcó responde 401 a toda escritura y no revive | 0.052 | ✅ pasó |
| integración | `EscriturasSalaIT` | Cerrar la sala libera el código: se puede crear otra sala con el mismo pueblo y la cerrada queda como historial | 0.043 | ✅ pasó |
| integración | `EscriturasSalaIT` | Crear y unirse guardan los eventos sala.creada (origen juego) y app.unida (origen app) | 0.025 | ✅ pasó |
| integración | `EscriturasSalaIT` | Cada escritura deja su evento en orden: sala.creada, app.unida, descubrimiento.registrado, puzzle.habilitado, escena.cambiada y sala.finalizada | 0.054 | ✅ pasó |
| integración | `EscriturasSalaIT` | El juego cambia la escena: 200, GET estado la muestra, guarda escena.cambiada y renueva expira_en | 0.032 | ✅ pasó |
| integración | `EscriturasSalaIT` | Repetir la escena actual responde 200, renueva expira_en y no guarda otro escena.cambiada | 0.031 | ✅ pasó |
| integración | `EscriturasSalaIT` | Repetir una clave con otros datos responde 200 con los datos originales y no guarda otro evento | 0.034 | ✅ pasó |
| integración | `EscriturasSalaIT` | La app recibe 403 al cerrar la sala y la sala sigue activa | 0.033 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un JSON con una clave repetida da 400 JSON malformado en crear sala, descubrimientos, escena y puzzles, sin escribir nada; los mismos cuerpos sin repetir siguen funcionando | 0.095 | ✅ pasó |
| integración | `EscriturasSalaIT` | lookout y observatory se habilitan por servicio cuando se resuelve el puzzle previo, aunque la bandera haya llegado antes | 0.054 | ✅ pasó |
| integración | `EscriturasSalaIT` | Cada clave del juego enviada por la app da 403 clave_solo_juego y no cambia la sala: ni descubrimiento, ni puzzles, ni expiración, ni evento; el juego las registra con 201 | 0.132 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un descubrimiento nuevo responde 201, aparece en GET estado y guarda descubrimiento.registrado con el origen del rol | 0.048 | ✅ pasó |
| integración | `EscriturasSalaIT` | Las tres piezas enviadas a la vez, repetidas, habilitan photo una sola vez | 0.051 | ✅ pasó |
| integración | `EscriturasSalaIT` | Las cuatro claves del servidor dan 403 al juego y a la app y no se guardan | 0.053 | ✅ pasó |
| integración | `EscriturasSalaIT` | El job guarda sala.expirada con origen servidor y deja ultima_actividad en la última escritura | 0.038 | ✅ pasó |
| integración | `EscriturasSalaIT` | Unos datos que pasan de 4096 bytes en JSON dan 400 y no se guardan; justo en el límite responden 201 | 0.035 | ✅ pasó |
| integración | `EscriturasSalaIT` | La app recibe 403 al cambiar la escena, con cuerpo válido, inválido o vacío, y la escena no cambia | 0.031 | ✅ pasó |
| integración | `EscriturasSalaIT` | DELETE simultáneo con nueve descubrimientos: los que pierden reciben 401 y no dejan filas ni eventos después del cierre | 0.103 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un número o booleano JSON donde va texto da 400 sin convertirse: clave, escena y código de pueblo | 0.060 | ✅ pasó |
| integración | `EsperarHealthIT` | esperar-health.sh sin URL (la variable RAILWAY_PUBLIC_URL no existe) avisa con ::warning:: y sale con 0 | 0.013 | ✅ pasó |
| integración | `EsperarHealthIT` | esperar-health.sh sale con 0 cuando el backend real responde UP | 0.036 | ✅ pasó |
| integración | `EsperarHealthIT` | esperar-health.sh sale con 1 y un ::error:: si el backend no responde UP en ningún intento | 0.065 | ✅ pasó |
| integración | `EsperarHealthIT` | esperar-health.sh reintenta mientras el health responde DOWN y sale con 0 cuando llega UP | 0.068 | ✅ pasó |
| integración | `EsquemaIT` | salasTerminadasNoBloqueanUnaNuevaSalaViva | 0.008 | ✅ pasó |
| integración | `EsquemaIT` | indiceEventosPorSalaYTiempoExiste | 0.005 | ✅ pasó |
| integración | `EsquemaIT` | noPuedeHaberDosSalasVivasConElMismoCodigo | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | catalogoRechazaCodigosConMinusculasAcentosOEnie | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaCodigoFueraDelCatalogo | 0.002 | ✅ pasó |
| integración | `EsquemaIT` | flywayAplicaV1YCreaLasCincoTablas | 0.005 | ✅ pasó |
| integración | `EsquemaIT` | alFinalizarUnaSalaElCodigoSePuedeReclamarDeNuevo | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | carreraPorElMismoPuebloDejaUnaSolaSalaViva | 0.006 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaEstadoInvalido | 0.002 | ✅ pasó |
| integración | `JsonDocumentadoIT` | Cada ejemplo de respuesta de Swagger (/v3/api-docs) tiene los campos y tipos de una respuesta real | 0.195 | ✅ pasó |
| integración | `JsonDocumentadoIT` | Cada bloque JSON de respuesta de la documentación tiene los campos y tipos de una respuesta real | 0.022 | ✅ pasó |
| integración | `JsonDocumentadoIT` | La comparación detecta campos de más o de menos, tipos distintos, null fuera de codigo, coordenadas numéricas, avisos que no son booleanos y valores fuera de los enums | 0.012 | ✅ pasó |
| integración | `JsonDocumentadoIT` | Cada bloque JSON de api.md, tiempo-real.md y las tres guías está marcado como respuesta o como petición | 0.006 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | Tras V3, el CHECK de puzzles_estado rechaza fotografia, mirador y observatorio y acepta los ids nuevos | 0.406 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | V3 renombra a inglés los puzzles de una sala creada con V2, conserva su estado y sus datos, y ese esquema rechaza fotografia | 0.269 | ✅ pasó |
| integración | `PuzzlesIT` | Con expira_en vencido y sin que corra el job, PUT puzzles responde 401 y no resuelve nada | 0.129 | ✅ pasó |
| integración | `PuzzlesIT` | Cada cambio de estado de un puzzle guarda {puzzle, estado, datos} y cada resultado guarda {clave, datos} con origen servidor | 0.105 | ✅ pasó |
| integración | `PuzzlesIT` | Los errores reales de PUT puzzles traen codigo null en 400, 401, 403, 404 y 405, y puzzle_bloqueado en el 409 | 0.084 | ✅ pasó |
| integración | `PuzzlesIT` | Resolver es una sola transacción: si crear el resultado falla, el puzzle no queda resuelto, no se guarda ningún evento y la sala no se renueva | 0.111 | ✅ pasó |
| integración | `PuzzlesIT` | Un cliente no puede fabricar un resultado del servidor: POST de las cuatro claves da 403 y PUT de un puzzle bloqueado da 409, sin crear nada; el resultado real tiene origen servidor y los datos de la configuración | 0.122 | ✅ pasó |
| integración | `PuzzlesIT` | lookout_enabled y observatory_enabled que llegan antes que sus requisitos habilitan su puzzle en el PUT que completa el último requisito | 0.147 | ✅ pasó |
| integración | `PuzzlesIT` | lookout_enabled y observatory_enabled que llegan después de sus requisitos habilitan su puzzle en ese mismo POST | 0.118 | ✅ pasó |
| integración | `PuzzlesIT` | "+042" llega intacto como texto en GET estado y en la base (jsonb string), igual que "137" | 0.087 | ✅ pasó |
| integración | `PuzzlesIT` | Matriz de estados: bloqueado da 409; disponible+iniciado pasa a en_curso; en_curso+iniciado no cambia; en_curso+completada resuelve; resuelto+cualquier aviso no cambia ni duplica | 0.133 | ✅ pasó |
| integración | `PuzzlesIT` | observatory espera coordenada_a: con lookout resuelto y su bandera sigue bloqueado hasta que se resuelve radio, aunque llegue al final | 0.114 | ✅ pasó |
| integración | `PuzzlesIT` | Solo el booleano true: texto "true", 1, false, null, claves extra, objeto vacío, datos que no es objeto o datos ausente dan 400 con details en datos y no cambian nada; iniciado en radio también | 0.158 | ✅ pasó |
| integración | `PuzzlesIT` | Resolver un puzzle actualiza ultima_actividad y renueva expira_en; repetir el aviso también renueva, sin guardar eventos | 0.052 | ✅ pasó |
| integración | `PuzzlesIT` | Un id de puzzle desconocido da 400 con la lista de ids, igual para los viejos fotografia, mirador y observatorio, y no toca la sala | 0.041 | ✅ pasó |
| integración | `PuzzlesIT` | Diez completada simultáneos sobre photo responden 200 y dejan una sola coordenada_b, un solo puzzle.resuelto y lookout habilitado una vez | 0.128 | ✅ pasó |
| integración | `PuzzlesIT` | La cadena completa por HTTP: pescador → radio → coordenada_a; tres piezas → photo → coordenada_b; lookout_enabled → lookout; observatory_enabled → observatory → magnetar_localizado | 0.199 | ✅ pasó |
| integración | `SalaIT` | crearDevuelveElTokenUnaVezYGuardaSoloSuHash | 0.022 | ✅ pasó |
| integración | `SalaIT` | carreraDeAppsPorUnirseDaUn200YSiete409 | 0.041 | ✅ pasó |
| integración | `SalaIT` | unirseSinSalaVivaDa404 | 0.016 | ✅ pasó |
| integración | `SalaIT` | flujoCrearUnirseEstado | 0.045 | ✅ pasó |
| integración | `SalaIT` | elJobNoTocaSalasFinalizadas | 0.060 | ✅ pasó |
| integración | `SalaIT` | unirseAceptaMinusculas | 0.026 | ✅ pasó |
| integración | `SalaIT` | pueblosReflejaLasSalasVivas | 0.030 | ✅ pasó |
| integración | `SalaIT` | estadoExigeElTokenDeEsaSala | 0.052 | ✅ pasó |
| integración | `SalaIT` | salaVencidaExpiraLiberaElNombreYSeConservaComoHistorial | 0.053 | ✅ pasó |
| integración | `SalaIT` | unirseRenuevaLaExpiracion | 0.030 | ✅ pasó |
| integración | `SalaIT` | carreraPorElMismoPuebloDaUn201YSiete409 | 0.023 | ✅ pasó |
| integración | `SalaIT` | crearFueraDelCatalogoDa400 | 0.008 | ✅ pasó |
| integración | `SalaIT` | crearAceptaMinusculasYEspacios | 0.018 | ✅ pasó |
| integración | `SalaIT` | crearInsertaLosCuatroPuzzlesBloqueados | 0.016 | ✅ pasó |
| integración | `SalaIT` | unirseDosVecesDa409 | 0.027 | ✅ pasó |
| integración | `SalaIT` | salaQueAunNoVenceSigueViva | 0.019 | ✅ pasó |
| integración | `SmokeTestFalloIT` | smoke-test.sh sin pueblo elige uno libre; si un paso falla a mitad, sale con 1, dice cuál y cierra la sala que creó | 0.484 | ✅ pasó |
| integración | `SmokeTestIT` | smoke-test.sh sale con 1 si el backend no responde y con 2 si falta la URL | 0.055 | ✅ pasó |
| integración | `SmokeTestIT` | smoke-test.sh recorre la partida completa contra el backend real, sale con 0, cierra la sala y no imprime tokens | 1.669 | ✅ pasó |
| integración | `SmokeTestServidorFalsoIT` | smoke-test.sh guarda el token antes de comprobar la respuesta de POST /salas: si la comprobación falla, igual cierra la sala | 0.132 | ✅ pasó |
| integración | `SmokeTestServidorFalsoIT` | smoke-test.sh muestra el cuerpo de una respuesta que no es JSON, como el 502 en HTML de un proxy | 0.053 | ✅ pasó |
| integración | `SmokeTestServidorFalsoIT` | smoke-test.sh interrumpido con HUP sale con 129, dice que se interrumpió y cierra la sala | 1.635 | ✅ pasó |
| integración | `repository.ModeloIT` | guardaYLeeSalaPuzzlesYDescubrimientosConJsonb | 0.052 | ✅ pasó |
| integración | `ws.ConcurrenciaWebSocketIT` | Una conexión cuyo envío se queda bloqueado no frena GET estado, ni las escrituras de su sala, ni al otro dispositivo, ni a otra sala | 0.220 | ✅ pasó |
| integración | `ws.ConcurrenciaWebSocketIT` | auth contra la expiración: si el job expira la sala entre validar el token y registrar la conexión, no queda conexión viva | 0.055 | ✅ pasó |
| integración | `ws.ConcurrenciaWebSocketIT` | auth contra DELETE: si la sala se cierra entre validar el token y registrar la conexión, no queda conexión viva | 0.037 | ✅ pasó |
| integración | `ws.ConcurrenciaWebSocketIT` | El job no expira una sala que una escritura simultánea renueva: espera su candado y vuelve a evaluar expira_en | 0.025 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Al cerrar la sala, los dos reciben sala.cerrada con estado finalizada y luego el servidor cierra con 1000 | 0.030 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | escena.cambiada llega solo a la app, con la escena nueva y la anterior; el juego no la recibe | 0.241 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Al expirar la sala, el job hace que los dos reciban sala.cerrada con estado expirada y cierra con 1000 | 0.047 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Los mensajes salen después del commit: nada mientras la transacción sigue abierta, y un GET estado pedido al recibir el mensaje ya ve el cambio aunque el commit tarde; un rollback no emite nada | 2.072 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Cada escritura REST de la cadena completa emite a los dos dispositivos su descubrimiento.nuevo y un puzzle.actualizado por cada cambio de estado, incluido bloqueado → disponible; otra sala no recibe nada | 0.330 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Las escrituras repetidas (descubrimiento, aviso de puzzle, misma escena) responden con éxito y no emiten nada; una clave del juego enviada por la app da 403 y tampoco emite | 0.066 | ✅ pasó |
| integración | `ws.WebSocketIT` | Antes de auth, otro tipo, JSON inválido o un mensaje binario cierran con 4000 | 0.012 | ✅ pasó |
| integración | `ws.WebSocketIT` | Ni los tokens ni Authorization aparecen en los logs durante auth, auth rechazada, errores y desconexión | 0.032 | ✅ pasó |
| integración | `ws.WebSocketIT` | Un token desconocido, vacío, que no es texto o ausente cierra con 4001 sin auth.ok | 0.010 | ✅ pasó |
| integración | `ws.WebSocketIT` | Origin: un navegador de un origen de CORS_ALLOWED_ORIGINS se conecta; otro origen recibe 403 en el handshake; sin Origin se conecta | 0.025 | ✅ pasó |
| integración | `ws.WebSocketIT` | Una segunda conexión del mismo rol reemplaza a la primera: la vieja cierra con 4009 y el otro rol no recibe desconectado | 0.352 | ✅ pasó |
| integración | `ws.WebSocketIT` | Presencia: cada conexión avisa dispositivo.conectado al otro rol, cada cierre dispositivo.desconectado, y GET estado lo refleja | 0.265 | ✅ pasó |
| integración | `ws.WebSocketIT` | auth con el token del juego o de la app responde auth.ok con su rol y la sala | 0.027 | ✅ pasó |
| integración | `ws.WebSocketIT` | El token de una sala cerrada o con expira_en vencido cierra con 4001, aunque el job no la haya marcado | 0.025 | ✅ pasó |
| integración | `ws.WebSocketIT` | El servidor manda pings a las conexiones autenticadas, programados cada 25 s | 0.042 | ✅ pasó |
| integración | `ws.WebSocketIT` | Ya autenticado, JSON inválido, binario, tipo desconocido, orientacion o un segundo auth responden error y la conexión sigue | 0.020 | ✅ pasó |
| integración | `ws.WebSocketIT` | Sin auth en 5 s el servidor cierra con 4008; una conexión que se autenticó a tiempo sigue abierta | 5.539 | ✅ pasó |
| integración | `ws.WebSocketIT` | Reconexión: la app vuelve con el mismo token, recibe auth.ok y no el mensaje que se perdió, y GET estado trae lo que pasó mientras no estaba | 0.261 | ✅ pasó |
| integración | `ws.WebSocketIT` | Un mensaje de 4096 caracteres recibe su error; uno de 4097 cierra con 1009 y avisa desconectado al otro rol | 0.027 | ✅ pasó |

## 5. Salida de `./mvnw clean verify`

Pruebas unitarias (`surefire`), línea 520 del log:

```
[INFO] Tests run: 139, Failures: 0, Errors: 0, Skipped: 0
```

Pruebas de integración (`failsafe`) y cierre:

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.vestigia.MigracionIdsPuzzlesIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 11.26 s -- in com.vestigia.MigracionIdsPuzzlesIT
[INFO] Running com.vestigia.SmokeTestIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.735 s -- in com.vestigia.SmokeTestIT
[INFO] Running com.vestigia.PuzzlesIT
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.751 s -- in com.vestigia.PuzzlesIT
[INFO] Running com.vestigia.AplicacionIT
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.436 s -- in com.vestigia.AplicacionIT
[INFO] Running com.vestigia.repository.ModeloIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.066 s -- in com.vestigia.repository.ModeloIT
[INFO] Running com.vestigia.SmokeTestFalloIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.747 s -- in com.vestigia.SmokeTestFalloIT
[INFO] Running com.vestigia.ConfiguracionDespliegueIT
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.072 s -- in com.vestigia.ConfiguracionDespliegueIT
[INFO] Running com.vestigia.EsquemaIT
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.053 s -- in com.vestigia.EsquemaIT
[INFO] Running com.vestigia.CatalogoPueblosIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.020 s -- in com.vestigia.CatalogoPueblosIT
[INFO] Running com.vestigia.EsperarHealthIT
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.193 s -- in com.vestigia.EsperarHealthIT
[INFO] Running com.vestigia.DocumentacionIT
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.061 s -- in com.vestigia.DocumentacionIT
[INFO] Running com.vestigia.SalaIT
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.512 s -- in com.vestigia.SalaIT
[INFO] Running com.vestigia.JsonDocumentadoIT
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.249 s -- in com.vestigia.JsonDocumentadoIT
[INFO] Running com.vestigia.EscriturasSalaIT
[INFO] Tests run: 29, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.500 s -- in com.vestigia.EscriturasSalaIT
[INFO] Running com.vestigia.SmokeTestServidorFalsoIT
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.822 s -- in com.vestigia.SmokeTestServidorFalsoIT
[INFO] Running com.vestigia.ws.WebSocketIT
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.658 s -- in com.vestigia.ws.WebSocketIT
[INFO] Running com.vestigia.ws.EmisionWebSocketIT
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.797 s -- in com.vestigia.ws.EmisionWebSocketIT
[INFO] Running com.vestigia.ws.ConcurrenciaWebSocketIT
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.346 s -- in com.vestigia.ws.ConcurrenciaWebSocketIT
[INFO] Results:
[INFO] Tests run: 127, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  05:48 min
[INFO] Finished at: 2026-11-07T10:20:39-06:00
```

## 6. Pruebas adversariales

Contra el JAR real y un Postgres propio (`vestigia-pg-p8`, borrado al final), con dos salas: `BACALAR` (la probada) y `TULUM` (para el token de otra sala). Los tokens no se imprimen: en este archivo van como `<token>`. Cada fila es una petición `POST /api/v1/salas/BACALAR/descubrimientos` salvo donde se indica.

| Escenario | Esperado | Llegó | Detalle | |
|---|:-:|:-:|---|:-:|
| app pescador_dialogo_completo | 403 | 403 | `codigo: "clave_solo_juego"` | ✅ |
| app pescador_dialogo_completo con datos | 403 | 403 | `codigo: "clave_solo_juego"` | ✅ |
| app pieza_1 | 403 | 403 | `codigo: "clave_solo_juego"` | ✅ |
| app pieza_1 con datos | 403 | 403 | `codigo: "clave_solo_juego"` | ✅ |
| app pieza_2 | 403 | 403 | `codigo: "clave_solo_juego"` | ✅ |
| app pieza_2 con datos | 403 | 403 | `codigo: "clave_solo_juego"` | ✅ |
| app pieza_3 | 403 | 403 | `codigo: "clave_solo_juego"` | ✅ |
| app pieza_3 con datos | 403 | 403 | `codigo: "clave_solo_juego"` | ✅ |
| app lookout_enabled | 403 | 403 | `codigo: "clave_solo_juego"` | ✅ |
| app lookout_enabled con datos | 403 | 403 | `codigo: "clave_solo_juego"` | ✅ |
| app observatory_enabled | 403 | 403 | `codigo: "clave_solo_juego"` | ✅ |
| app observatory_enabled con datos | 403 | 403 | `codigo: "clave_solo_juego"` | ✅ |
| app lookout_enabled con origen juego en la raíz | 403 | 403 | `codigo: "clave_solo_juego"` | ✅ |
| app clave repetida que esconde una del juego | 400 | 400 | `codigo: null`, `JSON malformado o ilegible` | ✅ |
| app PIEZA_1 en mayúsculas | 400 | 400 | `codigo: null`, `Datos inválidos` | ✅ |
| app ' pieza_1' con espacio | 400 | 400 | `codigo: null`, `Datos inválidos` | ✅ |
| app pieza_1 con escape unicode \u0070 | 403 | 403 | `codigo: "clave_solo_juego"` | ✅ |
| app pieza con i cirílica | 400 | 400 | `codigo: null`, `Datos inválidos` | ✅ |
| app clave como arreglo | 400 | 400 | `codigo: null`, `JSON malformado o ilegible` | ✅ |
| app coordenada_a (servidor) | 403 | 403 | `codigo: null`, `La clave 'coordenada_a' la genera el servidor al resolver un puzzle; los clientes no pueden enviarla` | ✅ |
| `GET estado` igual tras los `403` | | | | ✅ |
| Eventos de la sala sin cambio tras los `403` (siguen siendo 2: `sala.creada` y `app.unida`) | | | | ✅ |
| `ultima_actividad` y `expira_en` sin cambio tras los `403` | | | | ✅ |
| app de otra sala pieza_1 | 403 | 403 | `codigo: null`, `El token no pertenece a esta sala` | ✅ |
| sin token pieza_1 | 401 | 401 | `codigo: null`, `Falta el token: envía el encabezado Authorization: Bearer <token>` | ✅ |
| app pieza_4 (no reservada) | 201 | 201 | `origen: app` | ✅ |
| app nota_de_prueba | 201 | 201 | `origen: app` | ✅ |
| juego pescador_dialogo_completo | 201 | 201 | `origen: juego` | ✅ |
| juego pieza_1 | 201 | 201 | `origen: juego` | ✅ |
| juego pieza_2 | 201 | 201 | `origen: juego` | ✅ |
| juego pieza_3 | 201 | 201 | `origen: juego` | ✅ |
| juego lookout_enabled | 201 | 201 | `origen: juego` | ✅ |
| juego observatory_enabled | 201 | 201 | `origen: juego` | ✅ |
| juego pieza_1 repetida | 200 | 200 | `origen: juego` | ✅ |
| app pieza_1 que ya existe | 403 | 403 | `codigo: "clave_solo_juego"` | ✅ |
| eventos sin cambio tras el 403 de una clave existente | | | | ✅ |
| `PUT /puzzles/radio` `{"datos": {"sintonizada": true}}` con el token de la app (no se restringe por rol) | 200 | 200 | `radio` queda `resuelto` y el servidor crea `coordenada_a` | ✅ |
| app de sala cerrada pieza_1 | 401 | 401 | `codigo: null`, `Token inválido o de una sala que ya terminó` | ✅ |

Origen de lo que quedó guardado en `BACALAR`: pieza_4=app, nota_de_prueba=app, pescador_dialogo_completo=juego, pieza_1=juego, pieza_2=juego, pieza_3=juego, lookout_enabled=juego, observatory_enabled=juego, coordenada_a=servidor. Apariciones de los cuatro tokens en el log del backend: 0.

Notas:

- `pieza_4` y `nota_de_prueba` no son claves reservadas: la app las registra con `201`. La regla solo cubre las seis claves de la partida.
- `\u0070ieza_1` llega como `pieza_1` tras leer el JSON y da `403`. Una `і` cirílica no pasa el patrón de la clave (`400`).
- `PUT /puzzles/radio` con el token de la app responde `200` y resuelve la radio: `PUT puzzles` no se restringe por rol (pregunta P8b para Raúl).

## 7. Revisiones independientes

| Quién | Familia | Qué revisó | Resultado |
|---|---|---|---|
| Orquestador (Claude Opus 5.5) | Claude | El diff del código y una corrida propia de `./mvnw clean verify` (266 pruebas, 0 fallos, 0 errores, 0 omitidas) | Sin hallazgos. No cuenta como revisión independiente |
| gemini-3.8-flash (Antigravity) | Gemini, otra familia | El diff completo, los archivos clave y las guías: saltos del 403, orden de los chequeos, regresiones, pruebas y documentación | **APROBADO**, sin hallazgos bloqueantes ni importantes. Su informe se cortó después del veredicto, así que no lista hallazgos menores |

## 8. Lo que no se probó y por qué

| Qué | Por qué |
|---|---|
| La app real (Flutter) y el juego real (Unity) | No forman parte de este repositorio. Se simularon con `curl` y con `ClienteApi` en las pruebas |
| Restringir `PUT puzzles` por rol | Fuera del alcance: no se pidió. Queda como P8b en `docs/PLAN.md` |
| El despliegue en Railway | Prohibido tocar Railway. El cambio no agrega variables ni migraciones |
| El ejemplo nuevo en Swagger UI dentro de un navegador | Se comprobó en `/v3/api-docs` con `JsonDocumentadoIT` y `DocumentacionIT`, no visualmente |
