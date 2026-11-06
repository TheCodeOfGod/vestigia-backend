# Paso 4 · WebSocket

Rama `feature/websocket`, comparada contra `feature/puzzles`. Reemplaza `PendienteWebSocketHandler` por el WebSocket de `/ws`: autenticación con el token en el primer mensaje, presencia (`dispositivo.conectado` y `desconectado`, y `dispositivos` reales en `GET estado`), errores que no cierran la conexión, límite de tamaño, pings y la emisión después del commit de `puzzle.actualizado`, `descubrimiento.nuevo`, `escena.cambiada` (solo a la app) y `sala.cerrada`. Incluye las correcciones de la ronda 1 de revisión (H1 a H6, sección 7). La API sigue en `/api/v1`. No hay migraciones nuevas: el tipo de evento `sala.expirada` cabe en la columna `eventos.tipo`, que no tiene `CHECK`.

## 1. Veredicto

✅ 221 pruebas, 0 fallos, 0 errores, 0 omitidas.

| Total | Unitarias | Integración | Fallos | Errores | Omitidas | Fecha | Commit | Rama |
|---:|---:|---:|---:|---:|---:|---|---|---|
| 221 | 119 | 102 | 0 | 0 | 0 | 2026-11-06 09:21 | `805360e` | `feature/websocket` |

Las cifras son de `./mvnw clean verify` sobre `805360e`, el último commit de código. El commit siguiente solo actualiza esta evidencia y `docs/pruebas/README.md`. La rama partió de `d579235` con 185 pruebas. La primera entrega, antes de la revisión, terminó en `bce3f16` con 206; las correcciones de la ronda 1 suman 15.

## 2. Cómo reproducirlo

Con Docker corriendo (las pruebas `*IT` levantan `postgres:17-alpine` con Testcontainers):

```sh
git checkout feature/websocket
./mvnw clean verify
python3 scripts/resumen-pruebas.py               # secciones 1 y 4 de este archivo
python3 scripts/resumen-pruebas.py --solo-conteo # una línea
```

`WebSocketIT` y `EmisionWebSocketIT` usan un cliente WebSocket real (`java.net.http.WebSocket` de Java 21, en `ClienteWs`) contra la app levantada en un puerto aleatorio, con Postgres real. `WebSocketIT` tarda unos 6 s porque una prueba espera el plazo real de 5 s para `auth`. `ConcurrenciaWebSocketIT` simula con Mockito una sesión cuyo envío se bloquea y detiene con latches un `auth` entre validar el token y registrar la conexión.

**Las pruebas detectan lo que dicen cubrir.** Se rompió el código a propósito, se corrió la prueba indicada y se restauró el archivo:

| Mutación | Prueba | Resultado con la mutación |
|---|---|---|
| `@TransactionalEventListener(phase = BEFORE_COMMIT)` en lugar de `AFTER_COMMIT` | `EmisionWebSocketIT#losMensajesSalenDespuesDelCommit` | ❌ El `GET estado` que pide el cliente al recibir el mensaje no ve `pieza_1`: el mensaje salió antes de confirmar |
| `@EventListener` (emite dentro de la transacción) | La misma | ❌ `[ningún mensaje] expected: null`: el mensaje llega con la transacción todavía abierta |
| `SesionesWebSocket` escribe en el mismo hilo y dentro del candado (`Runnable::run` en lugar del ejecutor de hilos virtuales), como antes de H1 | `ConcurrenciaWebSocketIT#clienteQueNoLeeNoFrenaANadie` | ❌ `timed out after 20 seconds`: con un envío bloqueado, todo lo demás espera |
| `conectar` sin la marca de salas cerradas (`cerradas.containsKey`) | `ConcurrenciaWebSocketIT#authContraDelete` y `#authContraExpiracion` | ❌ las dos: `[conectar registra la sesión] Expecting value to be false but was true` |
| `bloquearVencidas` sin `@Lock(PESSIMISTIC_WRITE)` | `ConcurrenciaWebSocketIT#jobContraEscrituraQueRenueva` | ❌ `expected: "esperando_app" but was: "expirada"`: el job pisa la renovación |

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
| H1 · Un cliente que no lee no frena `GET estado`, las escrituras ni otra sala | `ConcurrenciaWebSocketIT` · Una conexión cuyo envío se queda bloqueado no frena GET estado, ni las escrituras de su sala, ni al otro dispositivo, ni a otra sala · `SesionesWebSocketTest` · Un envío que se bloquea no frena a nadie: enviar, dispositivos y la otra sala responden, y al soltarse sale todo en orden | ✅ |
| H1 · Límites de envío configurables: cola de más de 64 KB o envío fallido cierran solo esa conexión | `SesionesWebSocketTest` · Si la cola de una conexión pasa del límite de bytes, se descarta y la conexión se cierra con 4500 · Si un envío falla, la conexión se cierra con 4500 y lo que seguía en su cola se descarta. El corte a los 5 s lo hace Tomcat con `BLOCKING_SEND_TIMEOUT`, sin prueba propia (sección 8) | ✅ |
| H1 · Orden: `auth.ok` primero y luego en el orden encolado | `SesionesWebSocketTest` · auth.ok es el primer mensaje de una conexión y los demás salen en el orden en que se encolaron · `EmisionWebSocketIT` · la cadena completa compara el orden exacto | ✅ |
| H1 y H4 · Sin base de datos dentro del candado, y ninguna conexión viva en una sala que se cerró a la vez | `ConcurrenciaWebSocketIT` · auth contra DELETE … · auth contra la expiración … · `SesionesWebSocketTest` · Una sala que se cerró después de validar el token no registra la conexión … · Una sesión que el servidor ya decidió cerrar sin auth no se registra aunque su auth llegue después | ✅ |
| H2 · El plazo de `auth` se cancela al autenticar y al cerrar | `SalaWebSocketHandlerTest` · Al autenticarse, el cierre programado por el plazo de auth se cancela · Al cerrarse una conexión sin autenticar, … se cancela · Un auth rechazado no cancela el plazo … | ✅ |
| H3 · El programador de tareas tiene dos hilos | `AplicacionIT` · El programador que comparten el plazo de auth, los pings y el job de expiración tiene dos hilos | ✅ |
| H4 · El job no expira una sala que una escritura simultánea renueva | `ConcurrenciaWebSocketIT` · El job no expira una sala que una escritura simultánea renueva: espera su candado y vuelve a evaluar expira_en | ✅ |
| H5 · Documentación para clientes: presencia al conectarse y `401` por REST | Sin prueba: es documentación (`docs/tiempo-real.md`, sección Conectarse) | — |
| H6 · `Origin` del handshake: los de `CORS_ALLOWED_ORIGINS`, y sin `Origin` siempre | `WebSocketIT` · Origin: un navegador de un origen de CORS_ALLOWED_ORIGINS se conecta; otro origen recibe 403 en el handshake; sin Origin se conecta | ✅ |
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
| unitaria | `config.ApiHttpConfigTest` | corsRechazaOrigenNoConfigurado | 0.023 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | corsPermiteOrigenConfigurado | 0.004 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | registraUnaLineaPorPeticionSinTokens(CapturedOutput) | 0.014 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | noRegistraRutasFueraDeApi(CapturedOutput) | 0.022 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Los tipos correctos se siguen leyendo, y en un objeto libre (Map) cada valor conserva su tipo JSON | 0.016 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Una clave repetida en el mismo objeto falla en la raíz, anidada, en un Map o en un objeto libre, aunque los dos valores coincidan | 0.003 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un texto o un número en un campo booleano no se convierte: falla, también "true" entre comillas | 0.002 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un texto o un booleano en un campo numérico no se convierte: falla | 0.001 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un número o un booleano en un campo de texto no se convierte a texto: falla | 0.001 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | La misma clave en objetos distintos, hermanos o anidados, se sigue leyendo | 0.000 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | Los errores sin código traen "codigo": null en 400, 401, 403, 404, 405, 409 y 500 | 0.017 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | Los errores salen en JSON aunque el cliente pida otro formato: 406 con Accept XML y 409 con Accept HTML | 0.003 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | rutaInexistenteDevuelve404ConCuerpoUniforme | 0.003 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | jsonMalformadoDevuelve400ConCuerpoUniforme | 0.003 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | validacionDevuelve400ConDetallePorCampo | 0.003 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | errorInesperadoDevuelve500SinDetallesInternos | 0.002 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | Un error con código lo lleva en codigo y sus detalles en details, con el mismo cuerpo uniforme | 0.002 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | conflictoConservaSuCodigoYMensaje | 0.002 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Sin clave, con una clave que no es texto o con datos que no son un objeto JSON da 400 sin llamar al servicio | 0.014 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Unos datos de más de 4096 bytes en JSON dan 400 con detalle en datos, sin llamar al servicio | 0.004 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Sin token da 401 y con el token de otra sala da 403, sin llamar al servicio | 0.002 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Una clave del servidor responde 403 con el cuerpo de error uniforme | 0.003 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Repetir una clave responde 200 con el descubrimiento que ya existía | 0.004 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Una clave vacía, con mayúsculas, guiones, acentos, espacios o de más de 40 caracteres da 400 con detalle | 0.008 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Un descubrimiento nuevo responde 201 con clave, datos, origen y creado_en | 0.002 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Sin datos, con datos null o con el aviso fuera de datos da 400 con details en datos y codigo null, sin llamar al servicio | 0.014 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Sin token da 401 y con el token de otra sala da 403, con codigo null y sin llamar al servicio | 0.004 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Un aviso inválido para el puzzle responde 400 con el details del servicio y codigo null | 0.003 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | PUT puzzle responde 200 con {puzzle, estado, datos} y pasa al servicio el id de la ruta y los datos | 0.004 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Un puzzle bloqueado responde 409 con codigo puzzle_bloqueado en el cuerpo uniforme | 0.003 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | datos que no es un objeto JSON llega al servicio tal cual, para que responda 400 con details | 0.004 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Los valores de datos llegan al servicio con su tipo JSON: "true" es texto, 1 es número y true es booleano | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | La app recibe 403 en PUT escena y en DELETE antes de validar el cuerpo, sin llamar al servicio | 0.030 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | pueblosPasaElFiltroDeDisponibles | 0.016 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeOtraSalaDa403 | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena sin token da 401 | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | unirseNoPideTokenYDevuelveElDeLaApp | 0.011 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearResponde201ConCodigoEstadoYToken | 0.033 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | DELETE de la sala responde 204 sin cuerpo y sin token da 401 | 0.004 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena sin escena o con escena vacía da 400 con detalle, sin llamar al servicio | 0.011 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDesconocidoDa401 | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearSinCodigoDa400ConDetalle | 0.004 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeLaSalaDevuelveElEstadoEnSnakeCase | 0.009 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena responde 200 con escena_actual | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | El token de otra sala recibe 403 en PUT escena y DELETE, sin llamar al servicio | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearConPuebloOcupadoDa409ConCuerpoUniforme | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoSinTokenDa401 | 0.002 | ✅ pasó |
| unitaria | `dto.JsonAcotadoTest` | Los datos que caben en el límite, en bytes de su JSON compacto, son válidos; nulos también | 0.062 | ✅ pasó |
| unitaria | `dto.JsonAcotadoTest` | Un byte más del límite es inválido, y los acentos cuentan como dos bytes | 0.031 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear devuelve la sala si sigue viva | 0.045 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Renovar pone ultima_actividad en ahora y expira_en en ahora más la expiración configurada | 0.000 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar el cierre de una sala finalizada guarda el evento y ultima_actividad, pero no renueva expira_en | 0.000 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Los tipos de evento se guardan con su punto: sala.creada, app.unida, descubrimiento.registrado... | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar la expiración guarda y publica sala.expirada con origen servidor sin tocar ultima_actividad ni expira_en | 0.002 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar renueva la sala, guarda el evento y lo publica para el WebSocket del Paso 4 | 0.000 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear una sala finalizada, expirada o inexistente responde 401 | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear una sala viva cuyo expira_en ya pasó responde 401 aunque el job no la haya marcado | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Si la sala terminó entre el token y la escritura, el 401 de bloquear se propaga sin guardar nada | 0.086 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | El descubrimiento se guarda en la sala del token con la clave recibida | 0.002 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Repetir una clave devuelve el descubrimiento original sin guardar otro, sin evento y renovando la sala | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Un descubrimiento nuevo se guarda con el origen del rol, guarda su evento y luego recalcula los puzzles | 0.002 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Sin datos, el descubrimiento se guarda con un objeto vacío y origen juego si lo manda el juego | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Las cuatro claves del servidor dan 403 con cualquier rol y no tocan la sala | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Un resultado del servidor se guarda con origen servidor y sus datos, guarda su evento y luego recalcula los puzzles | 0.002 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | radio se habilita con pescador_dialogo_completo y no antes | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | photo se habilita solo con las tres piezas, lleguen en el orden que lleguen | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Solo un puzzle bloqueado pasa a disponible: los que están disponibles, en curso o resueltos no cambian | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Recalcular pasa a disponible los puzzles habilitados y guarda un evento puzzle.habilitado de origen servidor con {puzzle, estado, datos} por cada uno | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | observatory pide coordenada_a, coordenada_b, observatory_enabled y lookout resuelto; falta uno y no se habilita | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Recalcular sin requisitos nuevos no cambia nada ni guarda eventos | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | lookout pide photo resuelto y lookout_enabled; la bandera puede llegar antes o después | 0.000 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | en_curso más iniciado y resuelto más cualquier aviso responden el estado actual, solo renuevan la sala y no guardan eventos ni descubrimientos | 0.031 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | El evento lleva el origen del rol que avisó: juego o app | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Si la sala terminó entre el token y la escritura, el 401 de bloquear se propaga sin leer ni tocar el puzzle | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Las coordenadas salen de la configuración, sin convertirse a número | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | disponible más iniciado pasa a en_curso, guarda los datos y puzzle.iniciado con {puzzle, estado, datos} y el origen del rol, sin crear descubrimiento | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Un puzzle bloqueado da 409 con codigo puzzle_bloqueado ante cualquier aviso válido y no cambia nada | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Cada puzzle crea su resultado con origen servidor: coordenada_a con AR "137", coordenada_b con DEC "+042" como texto, observatorio_visible y magnetar_localizado | 0.004 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | disponible o en_curso más completada pasa a resuelto: guarda puzzle.resuelto y después crea el resultado | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Si falta la fila del puzzle, un estado imposible, falla con un mensaje que nombra la sala y el puzzle, sin escribir nada | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | radio solo acepta {sintonizada: true}: iniciado o completada dan 400; los puzzles de la app no aceptan sintonizada | 0.003 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Solo vale un objeto con una clave aceptada y el booleano true: un valor que no es objeto, texto, número, false, null, claves extra, vacío o una clave ajena dan 400 con details en datos | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Un id de puzzle desconocido da 400 con la lista de ids, también los viejos fotografia, mirador y observatorio y las variantes de mayúsculas o espacios | 0.000 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearFueraDelCatalogoDa400SinTocarSalas | 0.075 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Cambiar la escena la guarda y registra escena.cambiada con la anterior y la nueva | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | otraViolacionDeIntegridadNoSeDisfrazaDe409 | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseSinSalaVivaDa404 | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | listarPueblosMarcaOcupadosLosQueTienenSalaViva | 0.001 | ✅ pasó |
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
| unitaria | `service.SalaServiceTest` | El job bloquea las salas vivas vencidas según el reloj, las marca expirada y guarda sala.expirada de cada una | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearNormalizaElCodigoYGuardaSoloElHashDelToken | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | autenticarDistingueElRolPorElHash | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | autenticarConTokenDesconocidoNoDaCredencial | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | La app no puede cambiar la escena: 403 sin tocar la sala | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Autenticar con el token de una sala viva con expira_en vencido no da credencial; justo en expira_en sí | 0.000 | ✅ pasó |
| unitaria | `ws.EmisorEventosTest` | Cada tipo de evento tiene su emisión: puzzle.actualizado y descubrimiento.nuevo a todos, escena.cambiada solo a la app, sala.cerrada cierra la sala, crear y unirse no emiten | 0.025 | ✅ pasó |
| unitaria | `ws.SalaWebSocketHandlerTest` | Al autenticarse, el cierre programado por el plazo de auth se cancela | 0.048 | ✅ pasó |
| unitaria | `ws.SalaWebSocketHandlerTest` | Un auth rechazado no cancela el plazo: la sesión se cierra con 4001 y el plazo ya no hace nada | 0.002 | ✅ pasó |
| unitaria | `ws.SalaWebSocketHandlerTest` | Al cerrarse una conexión sin autenticar, el cierre programado por el plazo de auth se cancela | 0.000 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Una sala que se cerró después de validar el token no registra la conexión: conectar devuelve false y no envía nada | 0.024 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | auth.ok es el primer mensaje de una conexión y los demás salen en el orden en que se encolaron | 0.004 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Si la cola de una conexión pasa del límite de bytes, se descarta y la conexión se cierra con 4500 | 0.005 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Si un envío falla, la conexión se cierra con 4500 y lo que seguía en su cola se descarta | 0.001 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Una sesión que el servidor ya decidió cerrar sin auth no se registra aunque su auth llegue después | 0.001 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Un envío que se bloquea no frena a nadie: enviar, dispositivos y la otra sala responden, y al soltarse sale todo en orden | 0.018 | ✅ pasó |
| integración | `AplicacionIT` | El programador que comparten el plazo de auth, los pings y el job de expiración tiene dos hilos | 0.001 | ✅ pasó |
| integración | `AplicacionIT` | rutaInexistenteBajoSalasDa404SinPedirToken | 0.004 | ✅ pasó |
| integración | `AplicacionIT` | elJobDeExpiracionEstaProgramadoCadaMinuto | 0.003 | ✅ pasó |
| integración | `AplicacionIT` | openApiDocumentaLosEndpointsDeSalasYElToken | 0.170 | ✅ pasó |
| integración | `AplicacionIT` | swaggerUiYOpenApiEstanDisponibles | 0.010 | ✅ pasó |
| integración | `AplicacionIT` | GET /pueblos con Accept: application/xml responde 406 con el error uniforme en JSON, no con un cuerpo vacío | 0.021 | ✅ pasó |
| integración | `AplicacionIT` | healthResponde200ConEstadoUp | 0.013 | ✅ pasó |
| integración | `CatalogoPueblosIT` | flywayAplicaV2YLosCodigosEstanEnLaTabla | 0.005 | ✅ pasó |
| integración | `CatalogoPueblosIT` | seedDeclara101CodigosValidosYSinDuplicados | 0.002 | ✅ pasó |
| integración | `EscriturasSalaIT` | Con expira_en vencido y sin que corra el job, unirse da 404 y GET estado da 401 a los dos tokens; la sala no revive | 0.024 | ✅ pasó |
| integración | `EscriturasSalaIT` | pescador_dialogo_completo habilita radio y GET estado lo muestra disponible | 0.019 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una sala en esperando_app también se puede cerrar | 0.016 | ✅ pasó |
| integración | `EscriturasSalaIT` | El token del juego de otra sala recibe 403 en PUT escena y DELETE, y la sala no cambia | 0.019 | ✅ pasó |
| integración | `EscriturasSalaIT` | lookout_enabled que llega después de photo resuelto habilita lookout en la misma petición | 0.015 | ✅ pasó |
| integración | `EscriturasSalaIT` | Diez peticiones simultáneas con la misma clave dan un 201 y nueve 200, una sola fila y un solo evento | 0.027 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una escena inválida, vacía, de otro tipo o ausente da 400 y no cambia nada | 0.031 | ✅ pasó |
| integración | `EscriturasSalaIT` | El juego cierra la sala: 204, queda finalizada sin renovar expira_en, guarda sala.finalizada y los dos tokens dan 401 | 0.046 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un descubrimiento, nuevo o repetido, renueva ultima_actividad y expira_en con la duración configurada | 0.025 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una sala con expira_en vencido que el job aún no marcó responde 401 a toda escritura y no revive | 0.029 | ✅ pasó |
| integración | `EscriturasSalaIT` | Cerrar la sala libera el código: se puede crear otra sala con el mismo pueblo y la cerrada queda como historial | 0.021 | ✅ pasó |
| integración | `EscriturasSalaIT` | Crear y unirse guardan los eventos sala.creada (origen juego) y app.unida (origen app) | 0.011 | ✅ pasó |
| integración | `EscriturasSalaIT` | Cada escritura deja su evento en orden: sala.creada, app.unida, descubrimiento.registrado, puzzle.habilitado, escena.cambiada y sala.finalizada | 0.027 | ✅ pasó |
| integración | `EscriturasSalaIT` | El juego cambia la escena: 200, GET estado la muestra, guarda escena.cambiada y renueva expira_en | 0.017 | ✅ pasó |
| integración | `EscriturasSalaIT` | Repetir la escena actual responde 200, renueva expira_en y no guarda otro escena.cambiada | 0.016 | ✅ pasó |
| integración | `EscriturasSalaIT` | Repetir una clave con otros datos responde 200 con los datos originales y no guarda otro evento | 0.017 | ✅ pasó |
| integración | `EscriturasSalaIT` | La app recibe 403 al cerrar la sala y la sala sigue activa | 0.017 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un JSON con una clave repetida da 400 JSON malformado en crear sala, descubrimientos, escena y puzzles, sin escribir nada; los mismos cuerpos sin repetir siguen funcionando | 0.045 | ✅ pasó |
| integración | `EscriturasSalaIT` | lookout y observatory se habilitan por servicio cuando se resuelve el puzzle previo, aunque la bandera haya llegado antes | 0.027 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un descubrimiento nuevo responde 201, aparece en GET estado y guarda descubrimiento.registrado con el origen del rol | 0.020 | ✅ pasó |
| integración | `EscriturasSalaIT` | Las tres piezas enviadas a la vez, repetidas, habilitan photo una sola vez | 0.029 | ✅ pasó |
| integración | `EscriturasSalaIT` | Las cuatro claves del servidor dan 403 al juego y a la app y no se guardan | 0.028 | ✅ pasó |
| integración | `EscriturasSalaIT` | El job guarda sala.expirada con origen servidor y deja ultima_actividad en la última escritura | 0.017 | ✅ pasó |
| integración | `EscriturasSalaIT` | Unos datos que pasan de 4096 bytes en JSON dan 400 y no se guardan; justo en el límite responden 201 | 0.018 | ✅ pasó |
| integración | `EscriturasSalaIT` | La app recibe 403 al cambiar la escena, con cuerpo válido, inválido o vacío, y la escena no cambia | 0.015 | ✅ pasó |
| integración | `EscriturasSalaIT` | DELETE simultáneo con nueve descubrimientos: los que pierden reciben 401 y no dejan filas ni eventos después del cierre | 0.082 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un número o booleano JSON donde va texto da 400 sin convertirse: clave, escena y código de pueblo | 0.031 | ✅ pasó |
| integración | `EsquemaIT` | salasTerminadasNoBloqueanUnaNuevaSalaViva | 0.006 | ✅ pasó |
| integración | `EsquemaIT` | indiceEventosPorSalaYTiempoExiste | 0.003 | ✅ pasó |
| integración | `EsquemaIT` | noPuedeHaberDosSalasVivasConElMismoCodigo | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | catalogoRechazaCodigosConMinusculasAcentosOEnie | 0.003 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaCodigoFueraDelCatalogo | 0.001 | ✅ pasó |
| integración | `EsquemaIT` | flywayAplicaV1YCreaLasCincoTablas | 0.002 | ✅ pasó |
| integración | `EsquemaIT` | alFinalizarUnaSalaElCodigoSePuedeReclamarDeNuevo | 0.002 | ✅ pasó |
| integración | `EsquemaIT` | carreraPorElMismoPuebloDejaUnaSolaSalaViva | 0.003 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaEstadoInvalido | 0.001 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | Tras V3, el CHECK de puzzles_estado rechaza fotografia, mirador y observatorio y acepta los ids nuevos | 0.293 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | V3 renombra a inglés los puzzles de una sala creada con V2, conserva su estado y sus datos, y ese esquema rechaza fotografia | 0.162 | ✅ pasó |
| integración | `PuzzlesIT` | Con expira_en vencido y sin que corra el job, PUT puzzles responde 401 y no resuelve nada | 0.253 | ✅ pasó |
| integración | `PuzzlesIT` | Cada cambio de estado de un puzzle guarda {puzzle, estado, datos} y cada resultado guarda {clave, datos} con origen servidor | 0.118 | ✅ pasó |
| integración | `PuzzlesIT` | Los errores reales de PUT puzzles traen codigo null en 400, 401, 403, 404 y 405, y puzzle_bloqueado en el 409 | 0.068 | ✅ pasó |
| integración | `PuzzlesIT` | Resolver es una sola transacción: si crear el resultado falla, el puzzle no queda resuelto, no se guarda ningún evento y la sala no se renueva | 0.102 | ✅ pasó |
| integración | `PuzzlesIT` | Un cliente no puede fabricar un resultado del servidor: POST de las cuatro claves da 403 y PUT de un puzzle bloqueado da 409, sin crear nada; el resultado real tiene origen servidor y los datos de la configuración | 0.095 | ✅ pasó |
| integración | `PuzzlesIT` | lookout_enabled y observatory_enabled que llegan antes que sus requisitos habilitan su puzzle en el PUT que completa el último requisito | 0.130 | ✅ pasó |
| integración | `PuzzlesIT` | lookout_enabled y observatory_enabled que llegan después de sus requisitos habilitan su puzzle en ese mismo POST | 0.134 | ✅ pasó |
| integración | `PuzzlesIT` | "+042" llega intacto como texto en GET estado y en la base (jsonb string), igual que "137" | 0.098 | ✅ pasó |
| integración | `PuzzlesIT` | Matriz de estados: bloqueado da 409; disponible+iniciado pasa a en_curso; en_curso+iniciado no cambia; en_curso+completada resuelve; resuelto+cualquier aviso no cambia ni duplica | 0.142 | ✅ pasó |
| integración | `PuzzlesIT` | observatory espera coordenada_a: con lookout resuelto y su bandera sigue bloqueado hasta que se resuelve radio, aunque llegue al final | 0.109 | ✅ pasó |
| integración | `PuzzlesIT` | Solo el booleano true: texto "true", 1, false, null, claves extra, objeto vacío, datos que no es objeto o datos ausente dan 400 con details en datos y no cambian nada; iniciado en radio también | 0.119 | ✅ pasó |
| integración | `PuzzlesIT` | Resolver un puzzle actualiza ultima_actividad y renueva expira_en; repetir el aviso también renueva, sin guardar eventos | 0.046 | ✅ pasó |
| integración | `PuzzlesIT` | Un id de puzzle desconocido da 400 con la lista de ids, igual para los viejos fotografia, mirador y observatorio, y no toca la sala | 0.027 | ✅ pasó |
| integración | `PuzzlesIT` | Diez completada simultáneos sobre photo responden 200 y dejan una sola coordenada_b, un solo puzzle.resuelto y lookout habilitado una vez | 0.092 | ✅ pasó |
| integración | `PuzzlesIT` | La cadena completa por HTTP: pescador → radio → coordenada_a; tres piezas → photo → coordenada_b; lookout_enabled → lookout; observatory_enabled → observatory → magnetar_localizado | 0.121 | ✅ pasó |
| integración | `SalaIT` | crearDevuelveElTokenUnaVezYGuardaSoloSuHash | 0.016 | ✅ pasó |
| integración | `SalaIT` | carreraDeAppsPorUnirseDaUn200YSiete409 | 0.032 | ✅ pasó |
| integración | `SalaIT` | unirseSinSalaVivaDa404 | 0.010 | ✅ pasó |
| integración | `SalaIT` | flujoCrearUnirseEstado | 0.028 | ✅ pasó |
| integración | `SalaIT` | elJobNoTocaSalasFinalizadas | 0.033 | ✅ pasó |
| integración | `SalaIT` | unirseAceptaMinusculas | 0.014 | ✅ pasó |
| integración | `SalaIT` | pueblosReflejaLasSalasVivas | 0.017 | ✅ pasó |
| integración | `SalaIT` | estadoExigeElTokenDeEsaSala | 0.023 | ✅ pasó |
| integración | `SalaIT` | salaVencidaExpiraLiberaElNombreYSeConservaComoHistorial | 0.027 | ✅ pasó |
| integración | `SalaIT` | unirseRenuevaLaExpiracion | 0.013 | ✅ pasó |
| integración | `SalaIT` | carreraPorElMismoPuebloDaUn201YSiete409 | 0.011 | ✅ pasó |
| integración | `SalaIT` | crearFueraDelCatalogoDa400 | 0.004 | ✅ pasó |
| integración | `SalaIT` | crearAceptaMinusculasYEspacios | 0.007 | ✅ pasó |
| integración | `SalaIT` | crearInsertaLosCuatroPuzzlesBloqueados | 0.008 | ✅ pasó |
| integración | `SalaIT` | unirseDosVecesDa409 | 0.014 | ✅ pasó |
| integración | `SalaIT` | salaQueAunNoVenceSigueViva | 0.009 | ✅ pasó |
| integración | `repository.ModeloIT` | guardaYLeeSalaPuzzlesYDescubrimientosConJsonb | 0.036 | ✅ pasó |
| integración | `ws.ConcurrenciaWebSocketIT` | Una conexión cuyo envío se queda bloqueado no frena GET estado, ni las escrituras de su sala, ni al otro dispositivo, ni a otra sala | 0.272 | ✅ pasó |
| integración | `ws.ConcurrenciaWebSocketIT` | auth contra la expiración: si el job expira la sala entre validar el token y registrar la conexión, no queda conexión viva | 0.058 | ✅ pasó |
| integración | `ws.ConcurrenciaWebSocketIT` | auth contra DELETE: si la sala se cierra entre validar el token y registrar la conexión, no queda conexión viva | 0.050 | ✅ pasó |
| integración | `ws.ConcurrenciaWebSocketIT` | El job no expira una sala que una escritura simultánea renueva: espera su candado y vuelve a evaluar expira_en | 0.025 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Al cerrar la sala, los dos reciben sala.cerrada con estado finalizada y luego el servidor cierra con 1000 | 0.040 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | escena.cambiada llega solo a la app, con la escena nueva y la anterior; el juego no la recibe | 0.255 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Al expirar la sala, el job hace que los dos reciban sala.cerrada con estado expirada y cierra con 1000 | 0.055 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Los mensajes salen después del commit: nada mientras la transacción sigue abierta, y un GET estado pedido al recibir el mensaje ya ve el cambio aunque el commit tarde; un rollback no emite nada | 2.079 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Cada escritura REST de la cadena completa emite a los dos dispositivos su descubrimiento.nuevo y un puzzle.actualizado por cada cambio de estado, incluido bloqueado → disponible; otra sala no recibe nada | 0.340 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Las escrituras repetidas (descubrimiento, aviso de puzzle, misma escena) responden con éxito y no emiten nada | 0.098 | ✅ pasó |
| integración | `ws.WebSocketIT` | Antes de auth, otro tipo, JSON inválido o un mensaje binario cierran con 4000 | 0.032 | ✅ pasó |
| integración | `ws.WebSocketIT` | Ni los tokens ni Authorization aparecen en los logs durante auth, auth rechazada, errores y desconexión | 0.027 | ✅ pasó |
| integración | `ws.WebSocketIT` | Un token desconocido, vacío, que no es texto o ausente cierra con 4001 sin auth.ok | 0.008 | ✅ pasó |
| integración | `ws.WebSocketIT` | Origin: un navegador de un origen de CORS_ALLOWED_ORIGINS se conecta; otro origen recibe 403 en el handshake; sin Origin se conecta | 0.017 | ✅ pasó |
| integración | `ws.WebSocketIT` | Una segunda conexión del mismo rol reemplaza a la primera: la vieja cierra con 4009 y el otro rol no recibe desconectado | 0.339 | ✅ pasó |
| integración | `ws.WebSocketIT` | Presencia: cada conexión avisa dispositivo.conectado al otro rol, cada cierre dispositivo.desconectado, y GET estado lo refleja | 0.261 | ✅ pasó |
| integración | `ws.WebSocketIT` | auth con el token del juego o de la app responde auth.ok con su rol y la sala | 0.030 | ✅ pasó |
| integración | `ws.WebSocketIT` | El token de una sala cerrada o con expira_en vencido cierra con 4001, aunque el job no la haya marcado | 0.041 | ✅ pasó |
| integración | `ws.WebSocketIT` | El servidor manda pings a las conexiones autenticadas, programados cada 25 s | 0.042 | ✅ pasó |
| integración | `ws.WebSocketIT` | Ya autenticado, JSON inválido, binario, tipo desconocido, orientacion o un segundo auth responden error y la conexión sigue | 0.024 | ✅ pasó |
| integración | `ws.WebSocketIT` | Sin auth en 5 s el servidor cierra con 4008; una conexión que se autenticó a tiempo sigue abierta | 5.539 | ✅ pasó |
| integración | `ws.WebSocketIT` | Reconexión: la app vuelve con el mismo token, recibe auth.ok y no el mensaje que se perdió, y GET estado trae lo que pasó mientras no estaba | 0.277 | ✅ pasó |
| integración | `ws.WebSocketIT` | Un mensaje de 4096 caracteres recibe su error; uno de 4097 cierra con 1009 y avisa desconectado al otro rol | 0.042 | ✅ pasó |

## 5. Salida de `./mvnw clean verify`

Últimas líneas de Maven sobre `805360e` (sin los logs de Spring; rutas recortadas al repo):

```text
[INFO] Running com.vestigia.ws.SalaWebSocketHandlerTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.051 s -- in com.vestigia.ws.SalaWebSocketHandlerTest
[INFO] Results:
[INFO] Tests run: 119, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- jar:3.5.1:jar (default-jar) @ vestigia ---
[INFO] Building jar: target/vestigia-0.0.1-SNAPSHOT.jar
[INFO] --- spring-boot:4.1.1:repackage (repackage) @ vestigia ---
[INFO] Replacing main artifact target/vestigia-0.0.1-SNAPSHOT.jar with repackaged archive, adding nested dependencies in BOOT-INF/.
[INFO] The original artifact has been renamed to target/vestigia-0.0.1-SNAPSHOT.jar.original
[INFO] --- failsafe:3.5.6:integration-test (default) @ vestigia ---
[INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
[INFO]  T E S T S
[INFO] Running com.vestigia.MigracionIdsPuzzlesIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.093 s -- in com.vestigia.MigracionIdsPuzzlesIT
[INFO] Running com.vestigia.PuzzlesIT
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.671 s -- in com.vestigia.PuzzlesIT
[INFO] Running com.vestigia.AplicacionIT
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.231 s -- in com.vestigia.AplicacionIT
[INFO] Running com.vestigia.repository.ModeloIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.044 s -- in com.vestigia.repository.ModeloIT
[INFO] Running com.vestigia.EsquemaIT
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.033 s -- in com.vestigia.EsquemaIT
[INFO] Running com.vestigia.CatalogoPueblosIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.012 s -- in com.vestigia.CatalogoPueblosIT
[INFO] Running com.vestigia.SalaIT
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.280 s -- in com.vestigia.SalaIT
[INFO] Running com.vestigia.EscriturasSalaIT
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.710 s -- in com.vestigia.EscriturasSalaIT
[INFO] Running com.vestigia.ws.WebSocketIT
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.699 s -- in com.vestigia.ws.WebSocketIT
[INFO] Running com.vestigia.ws.EmisionWebSocketIT
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.880 s -- in com.vestigia.ws.EmisionWebSocketIT
[INFO] Running com.vestigia.ws.ConcurrenciaWebSocketIT
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.420 s -- in com.vestigia.ws.ConcurrenciaWebSocketIT
[INFO] Results:
[INFO] Tests run: 102, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- failsafe:3.5.6:verify (default) @ vestigia ---
[INFO] BUILD SUCCESS
[INFO] Total time:  26.246 s
[INFO] Finished at: 2026-11-06T09:21:58-06:00
```

## 6. Pruebas adversariales de la API

Un probador atacó la primera entrega, que terminaba en `bce3f16`: **gemini-3.8-flash** (Antigravity, familia Google), en sustitución de gpt-oss-120b-medium. 45 escenarios, 0 anomalías. Tabla del probador, resumida por el orquestador. Los tokens se escriben como `<token>`.

| Escenario | Petición | Esperado | Real | |
|---|---|---|---|:-:|
| `auth` correcto juego/app | `{"type":"auth","payload":{"token":"<token>"}}` | `auth.ok {rol, sala}` | igual | ✅ |
| Token inválido, ausente, número, booleano, `null`, vacío, de sala cerrada | `auth` | cierre `4001` | `4001` | ✅ |
| Sin `auth` en 5 s | conectar y esperar | cierre `4008` | `4008` a los 5.3 s | ✅ |
| Otro mensaje, `orientacion`, JSON roto, texto o binario antes de `auth` | primer mensaje | cierre `4000` | `4000` | ✅ |
| Token en la URL | `/ws?token=<token>` + `ping` | no autentica | `4000` | ✅ |
| JSON roto, array, sin `type`, binario, tipo desconocido, `orientacion`, segundo `auth` ya autenticado | mensaje | `error` y sigue abierta | `json_invalido`/`tipo_desconocido`/`ya_autenticado`, abierta | ✅ |
| Mensaje > 4096 caracteres | 4537 bytes | cierre `1009` | `1009` | ✅ |
| Presencia | conectar/desconectar app | `dispositivo.conectado/desconectado` al otro rol; `dispositivos` en `GET estado` | igual | ✅ |
| Segunda conexión del mismo rol | otro `auth` con el mismo token | la anterior cierra `4009`; el otro rol no ve `desconectado` | igual | ✅ |
| Descubrimiento y habilitación | `POST pescador_dialogo_completo` | `descubrimiento.nuevo` + `puzzle.actualizado` a ambos | igual | ✅ |
| Resolver radio | `PUT radio {sintonizada:true}` | `puzzle.actualizado` resuelto + `coordenada_a` | igual | ✅ |
| Escena | `PUT escena faro` | `escena.cambiada` solo a la app | solo la app | ✅ |
| Cierre | `DELETE` | `sala.cerrada {estado: finalizada}` y cierre `1000` en ambos | igual | ✅ |
| Escrituras repetidas o rechazadas (403, 400) | descubrimiento repetido, misma escena, clave del servidor, rol app, `fotografia` | 0 mensajes | 0 mensajes | ✅ |
| 10 conexiones simultáneas | 5 salas | 10 `auth.ok` | 10 | ✅ |
| Tokens en el log | grep en `app.log` | 0 | 0 | ✅ |

El probador no cubrió un cliente que deja de leer; ese caso lo encontraron los revisores (H1) y ahora lo cubre `ConcurrenciaWebSocketIT`.

## 7. Revisiones independientes

| Quién | Modelo y familia | Sustitución | Veredicto |
|---|---|---|---|
| Revisor A | **gemini-3.8-flash** (Antigravity, familia Google) | Sustituyó a gemini-4-argon, sin cuota en Antigravity | Rechazado con correcciones (A1 a A5) |
| Revisor B | **claude-opus-5-5** (Anthropic) | Sustituyó a claude-fable-5-1 | Sin bloqueantes, el mismo problema de fondo que A (B1 a B5) |
| Probador | **gemini-3.8-flash** (Antigravity, familia Google) | Sustituyó a gpt-oss-120b-medium | 45 escenarios, 0 anomalías (sección 6) |

El revisor B es de la misma familia que el constructor (Claude); la revisión de otra familia es la del revisor A, y también el probador. El triage lo hizo el orquestador. Las correcciones las hizo el constructor (claude-opus-5-5). Todos los hallazgos se aceptaron:

| # | Origen | Hallazgo | Decisión | Commit |
|---|---|---|---|---|
| H1 | A1, A2, A4, B1 (bloqueante) | El candado global de `SesionesWebSocket` envolvía envíos de red bloqueantes y la consulta de `autenticar`: un cliente con el búfer lleno frenaba hasta 20 s a todas las salas, los pings, cada `auth` y cada `GET estado`, y un pool de Hikari saturado podía bloquearlo todo | Aceptado. El candado solo ordena trabajo en memoria (registrar, encolar, cerrar). Cada conexión tiene una cola que un hilo virtual escribe en orden, con un solo escritor. El token se valida antes del candado y `conectar` rechaza las salas que `cerrarSala` marcó. `dispositivos()` sin candado. Envío bloqueado cortado a los 5 s por Tomcat y cola de más de 64 KB descartada, los dos configurables; cierran solo esa conexión con `4500` | `74db226`; la carrera de su prueba unitaria, en `755c7fe` |
| H2 | A3 | El plazo de 5 s de `auth` no se cancelaba | Aceptado. El handler guarda el `ScheduledFuture` y lo cancela al autenticar y al cerrar | `99466e9` |
| H3 | B2 | `TaskScheduler` de un solo hilo compartido por el plazo, los pings y el job | Aceptado. `spring.task.scheduling.pool.size=2`; además esas tareas ya solo encolan | `7b22ebd` |
| H4 | B3 | Carreras sin prueba: `auth` contra `DELETE` o la expiración, y el job contra una escritura que renueva | Aceptado. Tres pruebas con latch en `ConcurrenciaWebSocketIT`; las mutaciones de la sección 2 las hacen fallar | `2d52717` |
| H5 | B4, A5 | Faltaba decir a los clientes que un `401` por REST es una sala terminada y que al conectarse deben pedir `GET estado` para conocer la presencia del otro | Aceptado. `docs/tiempo-real.md`, sección Conectarse | `3653626` |
| H6 | B5 | Spring rechazaba con `403` los orígenes distintos del propio (Unity WebGL, Flutter web) | Aceptado. `/ws` acepta los de `CORS_ALLOWED_ORIGINS`; sin `Origin` sigue entrando | `805360e` |

## 8. Lo que no se probó y por qué

| Qué | Por qué |
|---|---|
| Un cliente real que deja de leer y llena el búfer de TCP | Se simula con una sesión cuyo envío se bloquea (`ConcurrenciaWebSocketIT`). El corte a los 5 s lo hace Tomcat con `BLOCKING_SEND_TIMEOUT`, que se fija por sesión; comprobarlo pide llenar el búfer de un socket real con megabytes de mensajes |
| Un dispositivo que pierde la red sin cerrar la conexión | El servidor no cierra por pongs que no llegan (decisión documentada) y lo nota cuando falla un envío. Simularlo pide cortar la red a mitad de una conexión TCP |
| Varias instancias del backend | Fuera de alcance: una sola instancia es una limitación aceptada |
| Clientes reales de Unity y Flutter | Las pruebas usan el cliente WebSocket de Java 21. Falta confirmar con Unity y la app que sus bibliotecas responden los pings y no mandan el token en la URL (C3 y C2 de `docs/PLAN.md`) |
| La app levantada a mano con su Postgres | No se hizo en este paso: las tres `*IT` del WebSocket ya corren la app completa en un puerto aleatorio con Postgres real |
| `orientacion` y `observatorio.proximidad` | Diferidos (Paso 5). Solo se prueba que `orientacion` no tumba la conexión |
