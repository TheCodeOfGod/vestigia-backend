# Paso 4 · WebSocket

Rama `feature/websocket`, comparada contra `feature/puzzles`. Reemplaza `PendienteWebSocketHandler` por el WebSocket de `/ws`: autenticación con el token en el primer mensaje, presencia (`dispositivo.conectado` y `desconectado`, y `dispositivos` reales en `GET estado`), errores que no cierran la conexión, límite de tamaño, pings y la emisión después del commit de `puzzle.actualizado`, `descubrimiento.nuevo`, `escena.cambiada` (solo a la app) y `sala.cerrada`. La API sigue en `/api/v1`. No hay migraciones nuevas: el tipo de evento `sala.expirada` cabe en la columna `eventos.tipo`, que no tiene `CHECK`.

## 1. Veredicto

✅ 206 pruebas, 0 fallos, 0 errores, 0 omitidas.

| Total | Unitarias | Integración | Fallos | Errores | Omitidas | Fecha | Commit | Rama |
|---:|---:|---:|---:|---:|---:|---|---|---|
| 206 | 110 | 96 | 0 | 0 | 0 | 2026-11-06 08:07 | `bce3f16` | `feature/websocket` |

Las cifras son de `./mvnw clean verify` sobre `bce3f16`, el último commit de código. El commit siguiente solo agrega esta evidencia y actualiza `docs/PLAN.md` y `docs/pruebas/README.md`. La rama partió de `d579235` con 185 pruebas; el paso suma 21.

## 2. Cómo reproducirlo

Con Docker corriendo (las pruebas `*IT` levantan `postgres:17-alpine` con Testcontainers):

```sh
git checkout feature/websocket
./mvnw clean verify
python3 scripts/resumen-pruebas.py               # secciones 1 y 4 de este archivo
python3 scripts/resumen-pruebas.py --solo-conteo # una línea
```

`WebSocketIT` y `EmisionWebSocketIT` usan un cliente WebSocket real (`java.net.http.WebSocket` de Java 21, en `ClienteWs`) contra la app levantada en un puerto aleatorio, con Postgres real. `WebSocketIT` tarda unos 6 s porque una prueba espera el plazo real de 5 s para `auth`.

**Las pruebas detectan lo que dicen cubrir.** Se rompió el código a propósito, se corrió la prueba indicada y se restauró el archivo:

| Mutación en `EmisorEventos` | Prueba | Resultado con la mutación |
|---|---|---|
| `@TransactionalEventListener(phase = BEFORE_COMMIT)` en lugar de `AFTER_COMMIT` | `EmisionWebSocketIT#losMensajesSalenDespuesDelCommit` | ❌ El `GET estado` que pide el cliente al recibir el mensaje no ve `pieza_1`: el mensaje salió antes de confirmar |
| `@EventListener` (emite dentro de la transacción) | La misma | ❌ `[ningún mensaje] expected: null`: el mensaje llega con la transacción todavía abierta |

La primera versión de esa prueba solo comprobaba que no llegara nada con la transacción abierta y que un rollback no emitiera. Con `BEFORE_COMMIT` seguía en verde, porque esa fase tampoco emite durante el cuerpo de la transacción ni en un rollback. Se reforzó antes del commit `bce3f16`: una sincronización registrada después del evento retiene el commit 1 s y otro hilo pide `GET estado` en cuanto llega el mensaje. Ahora las dos mutaciones fallan y el código real pasa.

```sh
sed -i '' 's/TransactionPhase.AFTER_COMMIT/TransactionPhase.BEFORE_COMMIT/' src/main/java/com/vestigia/ws/EmisorEventos.java
./mvnw -B verify -Dtest='NoHay*' -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='EmisionWebSocketIT#losMensajesSalenDespuesDelCommit'
git checkout src/main/java/com/vestigia/ws/EmisorEventos.java
```

## 3. Requisito → pruebas

| Requisito del paso | Pruebas | Resultado |
|---|---|:-:|
| Endpoint `/ws`, mensajes `{type, payload}`, sin STOMP ni SockJS | `WebSocketIT` · todas abren `ws://localhost:<puerto>/ws` con un cliente WebSocket simple y leen JSON `{type, payload}` · `EmisionWebSocketIT` · todas | ✅ |
| `auth` correcto responde `auth.ok` con `{rol, sala}` | `WebSocketIT` · auth con el token del juego o de la app responde auth.ok con su rol y la sala (compara el mensaje entero) | ✅ |
| Token inválido cierra la conexión | `WebSocketIT` · Un token desconocido, vacío, que no es texto o ausente cierra con 4001 sin auth.ok · El token de una sala cerrada o con expira_en vencido cierra con 4001, aunque el job no la haya marcado | ✅ |
| Sin `auth` en menos de 5 s se cierra la conexión | `WebSocketIT` · Sin auth en 5 s el servidor cierra con 4008; una conexión que se autenticó a tiempo sigue abierta (mide entre 4.5 y 9 s) | ✅ |
| Antes de autenticar, cualquier otro mensaje cierra la conexión | `WebSocketIT` · Antes de auth, otro tipo, JSON inválido o un mensaje binario cierran con 4000 (incluye `orientacion`) | ✅ |
| El token nunca va en la URL | Por diseño: el handler solo lee el token del payload de `auth` y nunca la URL. Todas las pruebas se conectan a `/ws` sin parámetros | ✅ |
| El token no se registra en logs (ni `Authorization`) | `WebSocketIT` · Ni los tokens ni Authorization aparecen en los logs durante auth, auth rechazada, errores y desconexión (captura la salida y comprueba primero que sí trae las líneas `[WS]`) | ✅ |
| `dispositivo.conectado` al otro dispositivo y `dispositivo.desconectado` `{rol}` al desconectarse | `WebSocketIT` · Presencia: cada conexión avisa dispositivo.conectado al otro rol, cada cierre dispositivo.desconectado, y GET estado lo refleja · Un mensaje de 4096 caracteres recibe su error; uno de 4097 cierra con 1009 y avisa desconectado al otro rol | ✅ |
| `GET estado` reporta `dispositivos` reales `{"juego": bool, "app": bool}` | `WebSocketIT` · Presencia (las cuatro combinaciones) · Reconexión · Una segunda conexión del mismo rol reemplaza a la primera · `SalaServiceTest` · El estado ordena los puzzles y reporta los dispositivos que da la presencia del WebSocket | ✅ |
| Pings cada ~25 s | `WebSocketIT` · El servidor manda pings a las conexiones autenticadas, programados cada 25 s (el cliente recibe el ping y la tarea está programada a 25 s) | ✅ |
| Límite de tamaño bajo y configurable | `WebSocketIT` · Un mensaje de 4096 caracteres recibe su error; uno de 4097 cierra con 1009 … (`vestigia.ws.mensaje-max-caracteres`) | ✅ |
| Una sola instancia, sesiones en memoria, documentado | Sin prueba: es una limitación. Está en `docs/tiempo-real.md` (Limitaciones conocidas), `README.md` y `docs/despliegue.md` | — |
| Emisión después del `commit` | `EmisionWebSocketIT` · Los mensajes salen después del commit: nada mientras la transacción sigue abierta, y un GET estado pedido al recibir el mensaje ya ve el cambio aunque el commit tarde; un rollback no emite nada. Las mutaciones de la sección 2 la hacen fallar | ✅ |
| `puzzle.actualizado` a todos con `{puzzle, estado, datos}` en cada cambio de estado, incluido `bloqueado` → `disponible` | `EmisionWebSocketIT` · Cada escritura REST de la cadena completa emite a los dos dispositivos su descubrimiento.nuevo y un puzzle.actualizado por cada cambio de estado, incluido bloqueado → disponible; otra sala no recibe nada · `EmisorEventosTest` · Cada tipo de evento tiene su emisión … | ✅ |
| `descubrimiento.nuevo` a todos con `{clave, datos, origen}` | `EmisionWebSocketIT` · Cada escritura REST de la cadena completa … (origen `juego`, `app` y `servidor`) · `EmisorEventosTest` · Cada tipo de evento tiene su emisión … | ✅ |
| `escena.cambiada` solo a la app | `EmisionWebSocketIT` · escena.cambiada llega solo a la app, con la escena nueva y la anterior; el juego no la recibe · `EmisorEventosTest` | ✅ |
| `sala.cerrada` a todos al cerrar la sala y luego se cierran las sesiones | `EmisionWebSocketIT` · Al cerrar la sala, los dos reciben sala.cerrada con estado finalizada y luego el servidor cierra con 1000 | ✅ |
| `sala.cerrada` al expirar; el job debe provocarlo después del commit | `EmisionWebSocketIT` · Al expirar la sala, el job hace que los dos reciban sala.cerrada con estado expirada y cierra con 1000 · `EscriturasSalaIT` · El job guarda sala.expirada con origen servidor y deja ultima_actividad en la última escritura · `SalaServiceTest` · El job bloquea las salas vivas vencidas según el reloj, las marca expirada y guarda sala.expirada de cada una · Sin salas vencidas, el job no registra nada · `ActividadServiceTest` · Registrar la expiración guarda y publica sala.expirada con origen servidor sin tocar ultima_actividad ni expira_en | ✅ |
| Las escrituras repetidas no emiten mensaje | `EmisionWebSocketIT` · Las escrituras repetidas (descubrimiento, aviso de puzzle, misma escena) responden con éxito y no emiten nada | ✅ |
| JSON inválido, tipo desconocido u `orientacion` no tumban al servidor: `error` con `{codigo, message}` y sigue | `WebSocketIT` · Ya autenticado, JSON inválido, binario, tipo desconocido, orientacion o un segundo auth responden error y la conexión sigue | ✅ |
| Una segunda conexión del mismo rol reemplaza a la anterior (decisión documentada) | `WebSocketIT` · Una segunda conexión del mismo rol reemplaza a la primera: la vieja cierra con 4009 y el otro rol no recibe desconectado | ✅ |
| Reconexión y `GET estado` consistente | `WebSocketIT` · Reconexión: la app vuelve con el mismo token, recibe auth.ok y no el mensaje que se perdió, y GET estado trae lo que pasó mientras no estaba | ✅ |
| `auth` usa `SalaService.autenticar`, con las mismas reglas que REST | `WebSocketIT` · El token de una sala cerrada o con expira_en vencido cierra con 4001, aunque el job no la haya marcado | ✅ |
| `orientacion` diferida: no se implementa | `WebSocketIT` · Antes de auth … (cierra con 4000) · Ya autenticado … orientacion … responde error `tipo_desconocido` con el mensaje de diferido | ✅ |
| Documentación (`docs/tiempo-real.md`, `docs/api.md`, `docs/modelo-datos.md`, `README.md`, `docs/PLAN.md`) | Sin prueba: es documentación. Cada archivo cambió en el mismo commit que su código | — |

## 4. Lista completa de pruebas

| Tipo | Clase | Prueba | Tiempo (s) | Resultado |
|---|---|---|---:|---|
| unitaria | `config.ApiHttpConfigTest` | corsRechazaOrigenNoConfigurado | 0.022 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | corsPermiteOrigenConfigurado | 0.004 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | registraUnaLineaPorPeticionSinTokens(CapturedOutput) | 0.012 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | noRegistraRutasFueraDeApi(CapturedOutput) | 0.022 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Los tipos correctos se siguen leyendo, y en un objeto libre (Map) cada valor conserva su tipo JSON | 0.015 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Una clave repetida en el mismo objeto falla en la raíz, anidada, en un Map o en un objeto libre, aunque los dos valores coincidan | 0.002 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un texto o un número en un campo booleano no se convierte: falla, también "true" entre comillas | 0.001 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un texto o un booleano en un campo numérico no se convierte: falla | 0.001 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un número o un booleano en un campo de texto no se convierte a texto: falla | 0.001 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | La misma clave en objetos distintos, hermanos o anidados, se sigue leyendo | 0.000 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | Los errores sin código traen "codigo": null en 400, 401, 403, 404, 405, 409 y 500 | 0.016 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | Los errores salen en JSON aunque el cliente pida otro formato: 406 con Accept XML y 409 con Accept HTML | 0.003 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | rutaInexistenteDevuelve404ConCuerpoUniforme | 0.001 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | jsonMalformadoDevuelve400ConCuerpoUniforme | 0.002 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | validacionDevuelve400ConDetallePorCampo | 0.003 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | errorInesperadoDevuelve500SinDetallesInternos | 0.002 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | Un error con código lo lleva en codigo y sus detalles en details, con el mismo cuerpo uniforme | 0.002 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | conflictoConservaSuCodigoYMensaje | 0.002 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Sin clave, con una clave que no es texto o con datos que no son un objeto JSON da 400 sin llamar al servicio | 0.013 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Unos datos de más de 4096 bytes en JSON dan 400 con detalle en datos, sin llamar al servicio | 0.003 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Sin token da 401 y con el token de otra sala da 403, sin llamar al servicio | 0.002 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Una clave del servidor responde 403 con el cuerpo de error uniforme | 0.002 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Repetir una clave responde 200 con el descubrimiento que ya existía | 0.003 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Una clave vacía, con mayúsculas, guiones, acentos, espacios o de más de 40 caracteres da 400 con detalle | 0.007 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Un descubrimiento nuevo responde 201 con clave, datos, origen y creado_en | 0.002 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Sin datos, con datos null o con el aviso fuera de datos da 400 con details en datos y codigo null, sin llamar al servicio | 0.013 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Sin token da 401 y con el token de otra sala da 403, con codigo null y sin llamar al servicio | 0.003 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Un aviso inválido para el puzzle responde 400 con el details del servicio y codigo null | 0.003 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | PUT puzzle responde 200 con {puzzle, estado, datos} y pasa al servicio el id de la ruta y los datos | 0.004 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Un puzzle bloqueado responde 409 con codigo puzzle_bloqueado en el cuerpo uniforme | 0.003 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | datos que no es un objeto JSON llega al servicio tal cual, para que responda 400 con details | 0.005 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Los valores de datos llegan al servicio con su tipo JSON: "true" es texto, 1 es número y true es booleano | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | La app recibe 403 en PUT escena y en DELETE antes de validar el cuerpo, sin llamar al servicio | 0.032 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | pueblosPasaElFiltroDeDisponibles | 0.014 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeOtraSalaDa403 | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena sin token da 401 | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | unirseNoPideTokenYDevuelveElDeLaApp | 0.009 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearResponde201ConCodigoEstadoYToken | 0.026 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | DELETE de la sala responde 204 sin cuerpo y sin token da 401 | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena sin escena o con escena vacía da 400 con detalle, sin llamar al servicio | 0.008 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDesconocidoDa401 | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearSinCodigoDa400ConDetalle | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeLaSalaDevuelveElEstadoEnSnakeCase | 0.008 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena responde 200 con escena_actual | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | El token de otra sala recibe 403 en PUT escena y DELETE, sin llamar al servicio | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearConPuebloOcupadoDa409ConCuerpoUniforme | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoSinTokenDa401 | 0.002 | ✅ pasó |
| unitaria | `dto.JsonAcotadoTest` | Los datos que caben en el límite, en bytes de su JSON compacto, son válidos; nulos también | 0.055 | ✅ pasó |
| unitaria | `dto.JsonAcotadoTest` | Un byte más del límite es inválido, y los acentos cuentan como dos bytes | 0.030 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear devuelve la sala si sigue viva | 0.040 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Renovar pone ultima_actividad en ahora y expira_en en ahora más la expiración configurada | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar el cierre de una sala finalizada guarda el evento y ultima_actividad, pero no renueva expira_en | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Los tipos de evento se guardan con su punto: sala.creada, app.unida, descubrimiento.registrado... | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar la expiración guarda y publica sala.expirada con origen servidor sin tocar ultima_actividad ni expira_en | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar renueva la sala, guarda el evento y lo publica para el WebSocket del Paso 4 | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear una sala finalizada, expirada o inexistente responde 401 | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear una sala viva cuyo expira_en ya pasó responde 401 aunque el job no la haya marcado | 0.000 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Si la sala terminó entre el token y la escritura, el 401 de bloquear se propaga sin guardar nada | 0.076 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | El descubrimiento se guarda en la sala del token con la clave recibida | 0.002 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Repetir una clave devuelve el descubrimiento original sin guardar otro, sin evento y renovando la sala | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Un descubrimiento nuevo se guarda con el origen del rol, guarda su evento y luego recalcula los puzzles | 0.002 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Sin datos, el descubrimiento se guarda con un objeto vacío y origen juego si lo manda el juego | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Las cuatro claves del servidor dan 403 con cualquier rol y no tocan la sala | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Un resultado del servidor se guarda con origen servidor y sus datos, guarda su evento y luego recalcula los puzzles | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | radio se habilita con pescador_dialogo_completo y no antes | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | photo se habilita solo con las tres piezas, lleguen en el orden que lleguen | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Solo un puzzle bloqueado pasa a disponible: los que están disponibles, en curso o resueltos no cambian | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Recalcular pasa a disponible los puzzles habilitados y guarda un evento puzzle.habilitado de origen servidor con {puzzle, estado, datos} por cada uno | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | observatory pide coordenada_a, coordenada_b, observatory_enabled y lookout resuelto; falta uno y no se habilita | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Recalcular sin requisitos nuevos no cambia nada ni guarda eventos | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | lookout pide photo resuelto y lookout_enabled; la bandera puede llegar antes o después | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | en_curso más iniciado y resuelto más cualquier aviso responden el estado actual, solo renuevan la sala y no guardan eventos ni descubrimientos | 0.032 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | El evento lleva el origen del rol que avisó: juego o app | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Si la sala terminó entre el token y la escritura, el 401 de bloquear se propaga sin leer ni tocar el puzzle | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Las coordenadas salen de la configuración, sin convertirse a número | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | disponible más iniciado pasa a en_curso, guarda los datos y puzzle.iniciado con {puzzle, estado, datos} y el origen del rol, sin crear descubrimiento | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Un puzzle bloqueado da 409 con codigo puzzle_bloqueado ante cualquier aviso válido y no cambia nada | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Cada puzzle crea su resultado con origen servidor: coordenada_a con AR "137", coordenada_b con DEC "+042" como texto, observatorio_visible y magnetar_localizado | 0.003 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | disponible o en_curso más completada pasa a resuelto: guarda puzzle.resuelto y después crea el resultado | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Si falta la fila del puzzle, un estado imposible, falla con un mensaje que nombra la sala y el puzzle, sin escribir nada | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | radio solo acepta {sintonizada: true}: iniciado o completada dan 400; los puzzles de la app no aceptan sintonizada | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Solo vale un objeto con una clave aceptada y el booleano true: un valor que no es objeto, texto, número, false, null, claves extra, vacío o una clave ajena dan 400 con details en datos | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Un id de puzzle desconocido da 400 con la lista de ids, también los viejos fotografia, mirador y observatorio y las variantes de mayúsculas o espacios | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearFueraDelCatalogoDa400SinTocarSalas | 0.065 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Cambiar la escena la guarda y registra escena.cambiada con la anterior y la nueva | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | otraViolacionDeIntegridadNoSeDisfrazaDe409 | 0.002 | ✅ pasó |
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
| unitaria | `service.SalaServiceTest` | El estado ordena los puzzles y reporta los dispositivos que da la presencia del WebSocket | 0.003 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Si unirse pierde la carrera no guarda ningún evento | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Repetir la escena actual responde igual y solo renueva la sala, sin evento | 0.000 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseASalaVivaQueYaTieneAppDa409 | 0.000 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | La app no puede cerrar la sala: 403 sin tocarla | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | cadaSalaRecibeUnTokenDistinto | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearInsertaLosCuatroPuzzlesBloqueados | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | El job bloquea las salas vivas vencidas según el reloj, las marca expirada y guarda sala.expirada de cada una | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearNormalizaElCodigoYGuardaSoloElHashDelToken | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | autenticarDistingueElRolPorElHash | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | autenticarConTokenDesconocidoNoDaCredencial | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | La app no puede cambiar la escena: 403 sin tocar la sala | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Autenticar con el token de una sala viva con expira_en vencido no da credencial; justo en expira_en sí | 0.000 | ✅ pasó |
| unitaria | `ws.EmisorEventosTest` | Cada tipo de evento tiene su emisión: puzzle.actualizado y descubrimiento.nuevo a todos, escena.cambiada solo a la app, sala.cerrada cierra la sala, crear y unirse no emiten | 0.021 | ✅ pasó |
| integración | `AplicacionIT` | rutaInexistenteBajoSalasDa404SinPedirToken | 0.003 | ✅ pasó |
| integración | `AplicacionIT` | elJobDeExpiracionEstaProgramadoCadaMinuto | 0.004 | ✅ pasó |
| integración | `AplicacionIT` | openApiDocumentaLosEndpointsDeSalasYElToken | 0.162 | ✅ pasó |
| integración | `AplicacionIT` | swaggerUiYOpenApiEstanDisponibles | 0.009 | ✅ pasó |
| integración | `AplicacionIT` | GET /pueblos con Accept: application/xml responde 406 con el error uniforme en JSON, no con un cuerpo vacío | 0.020 | ✅ pasó |
| integración | `AplicacionIT` | healthResponde200ConEstadoUp | 0.011 | ✅ pasó |
| integración | `CatalogoPueblosIT` | flywayAplicaV2YLosCodigosEstanEnLaTabla | 0.007 | ✅ pasó |
| integración | `CatalogoPueblosIT` | seedDeclara101CodigosValidosYSinDuplicados | 0.002 | ✅ pasó |
| integración | `EscriturasSalaIT` | Con expira_en vencido y sin que corra el job, unirse da 404 y GET estado da 401 a los dos tokens; la sala no revive | 0.024 | ✅ pasó |
| integración | `EscriturasSalaIT` | pescador_dialogo_completo habilita radio y GET estado lo muestra disponible | 0.020 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una sala en esperando_app también se puede cerrar | 0.014 | ✅ pasó |
| integración | `EscriturasSalaIT` | El token del juego de otra sala recibe 403 en PUT escena y DELETE, y la sala no cambia | 0.017 | ✅ pasó |
| integración | `EscriturasSalaIT` | lookout_enabled que llega después de photo resuelto habilita lookout en la misma petición | 0.013 | ✅ pasó |
| integración | `EscriturasSalaIT` | Diez peticiones simultáneas con la misma clave dan un 201 y nueve 200, una sola fila y un solo evento | 0.027 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una escena inválida, vacía, de otro tipo o ausente da 400 y no cambia nada | 0.030 | ✅ pasó |
| integración | `EscriturasSalaIT` | El juego cierra la sala: 204, queda finalizada sin renovar expira_en, guarda sala.finalizada y los dos tokens dan 401 | 0.030 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un descubrimiento, nuevo o repetido, renueva ultima_actividad y expira_en con la duración configurada | 0.023 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una sala con expira_en vencido que el job aún no marcó responde 401 a toda escritura y no revive | 0.033 | ✅ pasó |
| integración | `EscriturasSalaIT` | Cerrar la sala libera el código: se puede crear otra sala con el mismo pueblo y la cerrada queda como historial | 0.025 | ✅ pasó |
| integración | `EscriturasSalaIT` | Crear y unirse guardan los eventos sala.creada (origen juego) y app.unida (origen app) | 0.018 | ✅ pasó |
| integración | `EscriturasSalaIT` | Cada escritura deja su evento en orden: sala.creada, app.unida, descubrimiento.registrado, puzzle.habilitado, escena.cambiada y sala.finalizada | 0.040 | ✅ pasó |
| integración | `EscriturasSalaIT` | El juego cambia la escena: 200, GET estado la muestra, guarda escena.cambiada y renueva expira_en | 0.023 | ✅ pasó |
| integración | `EscriturasSalaIT` | Repetir la escena actual responde 200, renueva expira_en y no guarda otro escena.cambiada | 0.019 | ✅ pasó |
| integración | `EscriturasSalaIT` | Repetir una clave con otros datos responde 200 con los datos originales y no guarda otro evento | 0.023 | ✅ pasó |
| integración | `EscriturasSalaIT` | La app recibe 403 al cerrar la sala y la sala sigue activa | 0.021 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un JSON con una clave repetida da 400 JSON malformado en crear sala, descubrimientos, escena y puzzles, sin escribir nada; los mismos cuerpos sin repetir siguen funcionando | 0.059 | ✅ pasó |
| integración | `EscriturasSalaIT` | lookout y observatory se habilitan por servicio cuando se resuelve el puzzle previo, aunque la bandera haya llegado antes | 0.034 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un descubrimiento nuevo responde 201, aparece en GET estado y guarda descubrimiento.registrado con el origen del rol | 0.031 | ✅ pasó |
| integración | `EscriturasSalaIT` | Las tres piezas enviadas a la vez, repetidas, habilitan photo una sola vez | 0.039 | ✅ pasó |
| integración | `EscriturasSalaIT` | Las cuatro claves del servidor dan 403 al juego y a la app y no se guardan | 0.037 | ✅ pasó |
| integración | `EscriturasSalaIT` | El job guarda sala.expirada con origen servidor y deja ultima_actividad en la última escritura | 0.022 | ✅ pasó |
| integración | `EscriturasSalaIT` | Unos datos que pasan de 4096 bytes en JSON dan 400 y no se guardan; justo en el límite responden 201 | 0.025 | ✅ pasó |
| integración | `EscriturasSalaIT` | La app recibe 403 al cambiar la escena, con cuerpo válido, inválido o vacío, y la escena no cambia | 0.021 | ✅ pasó |
| integración | `EscriturasSalaIT` | DELETE simultáneo con nueve descubrimientos: los que pierden reciben 401 y no dejan filas ni eventos después del cierre | 0.075 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un número o booleano JSON donde va texto da 400 sin convertirse: clave, escena y código de pueblo | 0.035 | ✅ pasó |
| integración | `EsquemaIT` | salasTerminadasNoBloqueanUnaNuevaSalaViva | 0.005 | ✅ pasó |
| integración | `EsquemaIT` | indiceEventosPorSalaYTiempoExiste | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | noPuedeHaberDosSalasVivasConElMismoCodigo | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | catalogoRechazaCodigosConMinusculasAcentosOEnie | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaCodigoFueraDelCatalogo | 0.001 | ✅ pasó |
| integración | `EsquemaIT` | flywayAplicaV1YCreaLasCincoTablas | 0.003 | ✅ pasó |
| integración | `EsquemaIT` | alFinalizarUnaSalaElCodigoSePuedeReclamarDeNuevo | 0.002 | ✅ pasó |
| integración | `EsquemaIT` | carreraPorElMismoPuebloDejaUnaSolaSalaViva | 0.003 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaEstadoInvalido | 0.001 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | Tras V3, el CHECK de puzzles_estado rechaza fotografia, mirador y observatorio y acepta los ids nuevos | 0.281 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | V3 renombra a inglés los puzzles de una sala creada con V2, conserva su estado y sus datos, y ese esquema rechaza fotografia | 0.158 | ✅ pasó |
| integración | `PuzzlesIT` | Con expira_en vencido y sin que corra el job, PUT puzzles responde 401 y no resuelve nada | 0.242 | ✅ pasó |
| integración | `PuzzlesIT` | Cada cambio de estado de un puzzle guarda {puzzle, estado, datos} y cada resultado guarda {clave, datos} con origen servidor | 0.114 | ✅ pasó |
| integración | `PuzzlesIT` | Los errores reales de PUT puzzles traen codigo null en 400, 401, 403, 404 y 405, y puzzle_bloqueado en el 409 | 0.057 | ✅ pasó |
| integración | `PuzzlesIT` | Resolver es una sola transacción: si crear el resultado falla, el puzzle no queda resuelto, no se guarda ningún evento y la sala no se renueva | 0.086 | ✅ pasó |
| integración | `PuzzlesIT` | Un cliente no puede fabricar un resultado del servidor: POST de las cuatro claves da 403 y PUT de un puzzle bloqueado da 409, sin crear nada; el resultado real tiene origen servidor y los datos de la configuración | 0.094 | ✅ pasó |
| integración | `PuzzlesIT` | lookout_enabled y observatory_enabled que llegan antes que sus requisitos habilitan su puzzle en el PUT que completa el último requisito | 0.125 | ✅ pasó |
| integración | `PuzzlesIT` | lookout_enabled y observatory_enabled que llegan después de sus requisitos habilitan su puzzle en ese mismo POST | 0.110 | ✅ pasó |
| integración | `PuzzlesIT` | "+042" llega intacto como texto en GET estado y en la base (jsonb string), igual que "137" | 0.091 | ✅ pasó |
| integración | `PuzzlesIT` | Matriz de estados: bloqueado da 409; disponible+iniciado pasa a en_curso; en_curso+iniciado no cambia; en_curso+completada resuelve; resuelto+cualquier aviso no cambia ni duplica | 0.126 | ✅ pasó |
| integración | `PuzzlesIT` | observatory espera coordenada_a: con lookout resuelto y su bandera sigue bloqueado hasta que se resuelve radio, aunque llegue al final | 0.098 | ✅ pasó |
| integración | `PuzzlesIT` | Solo el booleano true: texto "true", 1, false, null, claves extra, objeto vacío, datos que no es objeto o datos ausente dan 400 con details en datos y no cambian nada; iniciado en radio también | 0.107 | ✅ pasó |
| integración | `PuzzlesIT` | Resolver un puzzle actualiza ultima_actividad y renueva expira_en; repetir el aviso también renueva, sin guardar eventos | 0.042 | ✅ pasó |
| integración | `PuzzlesIT` | Un id de puzzle desconocido da 400 con la lista de ids, igual para los viejos fotografia, mirador y observatorio, y no toca la sala | 0.027 | ✅ pasó |
| integración | `PuzzlesIT` | Diez completada simultáneos sobre photo responden 200 y dejan una sola coordenada_b, un solo puzzle.resuelto y lookout habilitado una vez | 0.075 | ✅ pasó |
| integración | `PuzzlesIT` | La cadena completa por HTTP: pescador → radio → coordenada_a; tres piezas → photo → coordenada_b; lookout_enabled → lookout; observatory_enabled → observatory → magnetar_localizado | 0.105 | ✅ pasó |
| integración | `SalaIT` | crearDevuelveElTokenUnaVezYGuardaSoloSuHash | 0.014 | ✅ pasó |
| integración | `SalaIT` | carreraDeAppsPorUnirseDaUn200YSiete409 | 0.024 | ✅ pasó |
| integración | `SalaIT` | unirseSinSalaVivaDa404 | 0.007 | ✅ pasó |
| integración | `SalaIT` | flujoCrearUnirseEstado | 0.024 | ✅ pasó |
| integración | `SalaIT` | elJobNoTocaSalasFinalizadas | 0.029 | ✅ pasó |
| integración | `SalaIT` | unirseAceptaMinusculas | 0.012 | ✅ pasó |
| integración | `SalaIT` | pueblosReflejaLasSalasVivas | 0.017 | ✅ pasó |
| integración | `SalaIT` | estadoExigeElTokenDeEsaSala | 0.021 | ✅ pasó |
| integración | `SalaIT` | salaVencidaExpiraLiberaElNombreYSeConservaComoHistorial | 0.025 | ✅ pasó |
| integración | `SalaIT` | unirseRenuevaLaExpiracion | 0.012 | ✅ pasó |
| integración | `SalaIT` | carreraPorElMismoPuebloDaUn201YSiete409 | 0.013 | ✅ pasó |
| integración | `SalaIT` | crearFueraDelCatalogoDa400 | 0.003 | ✅ pasó |
| integración | `SalaIT` | crearAceptaMinusculasYEspacios | 0.007 | ✅ pasó |
| integración | `SalaIT` | crearInsertaLosCuatroPuzzlesBloqueados | 0.008 | ✅ pasó |
| integración | `SalaIT` | unirseDosVecesDa409 | 0.025 | ✅ pasó |
| integración | `SalaIT` | salaQueAunNoVenceSigueViva | 0.010 | ✅ pasó |
| integración | `repository.ModeloIT` | guardaYLeeSalaPuzzlesYDescubrimientosConJsonb | 0.031 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Al cerrar la sala, los dos reciben sala.cerrada con estado finalizada y luego el servidor cierra con 1000 | 0.031 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | escena.cambiada llega solo a la app, con la escena nueva y la anterior; el juego no la recibe | 0.238 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Al expirar la sala, el job hace que los dos reciban sala.cerrada con estado expirada y cierra con 1000 | 0.043 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Los mensajes salen después del commit: nada mientras la transacción sigue abierta, y un GET estado pedido al recibir el mensaje ya ve el cambio aunque el commit tarde; un rollback no emite nada | 2.091 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Cada escritura REST de la cadena completa emite a los dos dispositivos su descubrimiento.nuevo y un puzzle.actualizado por cada cambio de estado, incluido bloqueado → disponible; otra sala no recibe nada | 0.334 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Las escrituras repetidas (descubrimiento, aviso de puzzle, misma escena) responden con éxito y no emiten nada | 0.068 | ✅ pasó |
| integración | `ws.WebSocketIT` | Antes de auth, otro tipo, JSON inválido o un mensaje binario cierran con 4000 | 0.029 | ✅ pasó |
| integración | `ws.WebSocketIT` | Ni los tokens ni Authorization aparecen en los logs durante auth, auth rechazada, errores y desconexión | 0.023 | ✅ pasó |
| integración | `ws.WebSocketIT` | Un token desconocido, vacío, que no es texto o ausente cierra con 4001 sin auth.ok | 0.007 | ✅ pasó |
| integración | `ws.WebSocketIT` | Una segunda conexión del mismo rol reemplaza a la primera: la vieja cierra con 4009 y el otro rol no recibe desconectado | 0.334 | ✅ pasó |
| integración | `ws.WebSocketIT` | Presencia: cada conexión avisa dispositivo.conectado al otro rol, cada cierre dispositivo.desconectado, y GET estado lo refleja | 0.257 | ✅ pasó |
| integración | `ws.WebSocketIT` | auth con el token del juego o de la app responde auth.ok con su rol y la sala | 0.030 | ✅ pasó |
| integración | `ws.WebSocketIT` | El token de una sala cerrada o con expira_en vencido cierra con 4001, aunque el job no la haya marcado | 0.032 | ✅ pasó |
| integración | `ws.WebSocketIT` | El servidor manda pings a las conexiones autenticadas, programados cada 25 s | 0.043 | ✅ pasó |
| integración | `ws.WebSocketIT` | Ya autenticado, JSON inválido, binario, tipo desconocido, orientacion o un segundo auth responden error y la conexión sigue | 0.022 | ✅ pasó |
| integración | `ws.WebSocketIT` | Sin auth en 5 s el servidor cierra con 4008; una conexión que se autenticó a tiempo sigue abierta | 5.539 | ✅ pasó |
| integración | `ws.WebSocketIT` | Reconexión: la app vuelve con el mismo token, recibe auth.ok y no el mensaje que se perdió, y GET estado trae lo que pasó mientras no estaba | 0.268 | ✅ pasó |
| integración | `ws.WebSocketIT` | Un mensaje de 4096 caracteres recibe su error; uno de 4097 cierra con 1009 y avisa desconectado al otro rol | 0.027 | ✅ pasó |

## 5. Salida de `./mvnw clean verify`

Últimas líneas de Maven sobre `bce3f16` (sin los logs de Spring; rutas recortadas al repo):

```text
[INFO] Running com.vestigia.ws.EmisorEventosTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.021 s -- in com.vestigia.ws.EmisorEventosTest
[INFO] Results:
[INFO] Tests run: 110, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- jar:3.5.1:jar (default-jar) @ vestigia ---
[INFO] Building jar: target/vestigia-0.0.1-SNAPSHOT.jar
[INFO] --- spring-boot:4.1.1:repackage (repackage) @ vestigia ---
[INFO] Replacing main artifact target/vestigia-0.0.1-SNAPSHOT.jar with repackaged archive, adding nested dependencies in BOOT-INF/.
[INFO] The original artifact has been renamed to target/vestigia-0.0.1-SNAPSHOT.jar.original
[INFO] --- failsafe:3.5.6:integration-test (default) @ vestigia ---
[INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
[INFO]  T E S T S
[INFO] Running com.vestigia.MigracionIdsPuzzlesIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.920 s -- in com.vestigia.MigracionIdsPuzzlesIT
[INFO] Running com.vestigia.PuzzlesIT
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.516 s -- in com.vestigia.PuzzlesIT
[INFO] Running com.vestigia.AplicacionIT
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.219 s -- in com.vestigia.AplicacionIT
[INFO] Running com.vestigia.repository.ModeloIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.039 s -- in com.vestigia.repository.ModeloIT
[INFO] Running com.vestigia.EsquemaIT
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.034 s -- in com.vestigia.EsquemaIT
[INFO] Running com.vestigia.CatalogoPueblosIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.012 s -- in com.vestigia.CatalogoPueblosIT
[INFO] Running com.vestigia.SalaIT
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.264 s -- in com.vestigia.SalaIT
[INFO] Running com.vestigia.EscriturasSalaIT
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.798 s -- in com.vestigia.EscriturasSalaIT
[INFO] Running com.vestigia.ws.WebSocketIT
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.627 s -- in com.vestigia.ws.WebSocketIT
[INFO] Running com.vestigia.ws.EmisionWebSocketIT
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.819 s -- in com.vestigia.ws.EmisionWebSocketIT
[INFO] Results:
[INFO] Tests run: 96, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- failsafe:3.5.6:verify (default) @ vestigia ---
[INFO] BUILD SUCCESS
[INFO] Total time:  25.169 s
[INFO] Finished at: 2026-11-06T08:07:39-06:00
```

## 6. Pruebas adversariales de la API

Pendiente: las completa el orquestador.

## 7. Revisiones independientes

Pendiente: las completa el orquestador.

## 8. Lo que no se probó y por qué

| Qué | Por qué |
|---|---|
| Un cliente que deja de leer y llena el búfer de TCP | Los envíos van en serie bajo un candado (limitación documentada en `docs/tiempo-real.md`). Reproducirlo pide saturar un socket real; con mensajes de menos de 1 KB y dos dispositivos por sala no ocurre en una partida |
| Un dispositivo que pierde la red sin cerrar la conexión | El servidor no cierra por pongs que no llegan (decisión documentada) y lo nota cuando falla un envío. Simularlo pide cortar la red a mitad de una conexión TCP |
| La carrera entre `auth` y el cierre de la sala | `SesionesWebSocket.conectar` autentica dentro del mismo candado que `cerrarSala`, así que no pueden intercalarse. No hay prueba que fuerce ese intercalado exacto |
| Varias instancias del backend | Fuera de alcance: una sola instancia es una limitación aceptada |
| Clientes reales de Unity y Flutter | Las pruebas usan el cliente WebSocket de Java 21. Falta confirmar con Unity y la app que sus bibliotecas responden los pings y no mandan el token en la URL (C3 y C2 de `docs/PLAN.md`) |
| La app levantada a mano con su Postgres | No se hizo en este paso: las dos `*IT` del WebSocket ya corren la app completa en un puerto aleatorio con Postgres real |
| `orientacion` y `observatorio.proximidad` | Diferidos (Paso 5). Solo se prueba que `orientacion` no tumba la conexión |
