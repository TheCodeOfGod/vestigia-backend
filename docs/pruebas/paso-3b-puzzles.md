# Paso 3b · Los cuatro puzzles

Rama `feature/puzzles`, comparada contra `feature/descubrimientos-escena-cierre`. Agrega `PUT /salas/{codigo}/puzzles/{puzzle}` para `radio`, `photo`, `lookout` y `observatory`, los descubrimientos resultado que crea el servidor y el campo `codigo` en el cuerpo uniforme de error. La API sigue en `/api/v1`. No hay migraciones nuevas.

## 1. Veredicto

✅ 179 pruebas, 0 fallos, 0 errores, 0 omitidas.

| Total | Unitarias | Integración | Fallos | Errores | Omitidas | Fecha | Commit | Rama |
|---:|---:|---:|---:|---:|---:|---|---|---|
| 179 | 103 | 76 | 0 | 0 | 0 | 2026-11-06 00:29 | `60eae97` | `feature/puzzles` |

Las cifras son de `./mvnw clean verify` sobre `60eae97`, el último commit de código. El commit siguiente solo agrega esta evidencia. La rama partió de `b83d081` con 143 pruebas en verde; el 3b suma 36.

## 2. Cómo reproducirlo

Con Docker corriendo (las pruebas `*IT` levantan `postgres:17-alpine` con Testcontainers):

```sh
git checkout feature/puzzles
./mvnw clean verify
python3 scripts/resumen-pruebas.py               # secciones 1 y 4 de este archivo
python3 scripts/resumen-pruebas.py --solo-conteo # una línea
```

**Las pruebas detectan lo que dicen cubrir.** Se rompió el código a propósito tres veces, se corrieron las pruebas indicadas y se restauró el archivo. Las tres mutaciones fallaron como se esperaba:

| Mutación | Pruebas que se corrieron | Resultado con la mutación |
|---|---|---|
| `PuzzleService.aviso` acepta cualquier valor no nulo en lugar de `Boolean.TRUE.equals(...)` | `PuzzleServiceTest` y `PuzzlesIT#soloTrue` | ❌ las dos. La IT: `[{"completada":"true"}] expected: 400 but was: 200` |
| `ActividadService.bloquear` lee la sala con `findById` en lugar de `SELECT … FOR UPDATE` | `PuzzlesIT#completadaSimultaneos` | ❌ 9 de los 10 avisos simultáneos responden `500 Error interno del servidor` (choque contra `UNIQUE (sala_id, clave)` de `coordenada_b`); con el bloqueo, los 10 responden `200` |
| `PuzzleService.reportar` trata como cambio un aviso que no mueve el puzzle (`< 0` en lugar de `<= 0`) | `PuzzleServiceTest`, `PuzzlesIT#matrizDeEstados` y `PuzzlesIT#completadaSimultaneos` | ❌ las tres pruebas de idempotencia |

Comandos de la segunda, que es la que prueba el bloqueo por sala:

```sh
sed -i '' 's/return salas.bloquear(salaId)/return salas.findById(salaId)/' src/main/java/com/vestigia/service/ActividadService.java
./mvnw clean verify -Dtest='NoHay*' -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='PuzzlesIT#completadaSimultaneos'
git checkout src/main/java/com/vestigia/service/ActividadService.java
```

Prueba manual con la app real (puerto 18080, Postgres `vestigia-pg-3b` en 55432, los dos borrados al terminar; resumen en la sección 8):

```sh
docker run -d --name vestigia-pg-3b -e POSTGRES_DB=vestigia -e POSTGRES_PASSWORD=postgres -p 55432:5432 postgres:17-alpine
DB_URL=jdbc:postgresql://localhost:55432/vestigia PORT=18080 java -jar target/vestigia-0.0.1-SNAPSHOT.jar
# ... peticiones con curl ...
docker rm -f vestigia-pg-3b
```

## 3. Requisito → pruebas

`PuzzlesIT` y `EscriturasSalaIT` van por HTTP contra la app levantada y un Postgres real. `*ServiceTest` son unitarias con Mockito y `*ControllerTest` son `@WebMvcTest`.

| Requisito del paso | Pruebas | Resultado |
|---|---|:-:|
| `PUT /salas/{codigo}/puzzles/{puzzle}` con `{datos}`, juego o app, responde `200` con `{puzzle, estado, datos}` | `PuzzleControllerTest` · PUT puzzle responde 200 con {puzzle, estado, datos} y pasa al servicio el id de la ruta y los datos · `PuzzlesIT` · La cadena completa por HTTP (la radio la reporta el juego y el resto la app, y la respuesta de radio se compara entera) · `PuzzleServiceTest` · El evento lleva el origen del rol que avisó: juego o app | ✅ |
| `datos` válidos: `radio` `{sintonizada: true}`; `photo`, `lookout` y `observatory` `{completada: true}` o `{iniciado: true}` | `PuzzleServiceTest` · radio solo acepta {sintonizada: true}: iniciado o completada dan 400; los puzzles de la app no aceptan sintonizada · `PuzzlesIT` · La cadena completa por HTTP | ✅ |
| Solo `true`: texto `"true"`, `1`, `false`, `null`, claves extra o `datos` ausente dan `400` con `details` | `PuzzlesIT` · Solo el booleano true: texto "true", 1, false, null, claves extra, objeto vacío o datos ausente dan 400 con details en datos y no cambian nada; iniciado en radio también · `PuzzleServiceTest` · Solo vale una clave aceptada con el booleano true: texto, número, false, null, claves extra, vacío o una clave ajena dan 400 con details en datos · `PuzzleControllerTest` · Sin datos, con datos null o con el aviso fuera de datos da 400 con details en datos y codigo null, sin llamar al servicio · datos que no es un objeto JSON da 400, sin llamar al servicio · Los valores de datos llegan al servicio con su tipo JSON: "true" es texto, 1 es número y true es booleano. Una mutación que acepta cualquier valor no nulo hace fallar la unitaria y la IT (sección 2) | ✅ |
| `iniciado` en `radio` da `400` | `PuzzlesIT` · Solo el booleano true (… iniciado en radio también) · `PuzzleServiceTest` · radio solo acepta {sintonizada: true}: iniciado o completada dan 400 · `PuzzleControllerTest` · Un aviso inválido para el puzzle responde 400 con el details del servicio y codigo null | ✅ |
| Id de puzzle inexistente en la ruta: **se eligió `400`**, igual en todos los casos, incluidos `fotografia`, `mirador` y `observatorio` | `PuzzlesIT` · Un id de puzzle desconocido da 400 con la lista de ids, igual para los viejos fotografia, mirador y observatorio, y no toca la sala · `PuzzleServiceTest` · Un id de puzzle desconocido da 400 con la lista de ids, también los viejos fotografia, mirador y observatorio y las variantes de mayúsculas o espacios | ✅ |
| `bloqueado` responde `409` con `codigo: "puzzle_bloqueado"` | `PuzzleServiceTest` · Un puzzle bloqueado da 409 con codigo puzzle_bloqueado ante cualquier aviso válido y no cambia nada · `PuzzleControllerTest` · Un puzzle bloqueado responde 409 con codigo puzzle_bloqueado en el cuerpo uniforme · `PuzzlesIT` · Matriz de estados · Los errores reales de PUT puzzles traen codigo null en 400, 401, 403, 404 y 405, y puzzle_bloqueado en el 409 (compara el cuerpo entero del `409`) | ✅ |
| `disponible` + `iniciado` → `en_curso` | `PuzzleServiceTest` · disponible más iniciado pasa a en_curso, guarda los datos y puzzle.iniciado con {puzzle, estado, datos} y el origen del rol, sin crear descubrimiento · `PuzzlesIT` · Matriz de estados | ✅ |
| `disponible` o `en_curso` + `completada`/`sintonizada` → `resuelto` | `PuzzleServiceTest` · disponible o en_curso más completada pasa a resuelto: guarda puzzle.resuelto y después crea el resultado · `PuzzlesIT` · Matriz de estados · La cadena completa por HTTP (radio y observatory desde `disponible`, photo y lookout desde `en_curso`) | ✅ |
| `en_curso` + `iniciado`, o `resuelto` + cualquier aviso → `200` con el estado actual, sin cambios ni duplicados | `PuzzleServiceTest` · en_curso más iniciado y resuelto más cualquier aviso responden el estado actual, solo renuevan la sala y no guardan eventos ni descubrimientos · `PuzzlesIT` · Matriz de estados (cuenta eventos y filas de `coordenada_b`) | ✅ |
| Al resolver crea el descubrimiento resultado con origen `servidor` | `PuzzleServiceTest` · Cada puzzle crea su resultado con origen servidor: coordenada_a con AR "137", coordenada_b con DEC "+042" como texto, observatorio_visible y magnetar_localizado · `DescubrimientoServiceTest` · Un resultado del servidor se guarda con origen servidor y sus datos, guarda su evento y luego recalcula los puzzles · `PuzzlesIT` · La cadena completa por HTTP | ✅ |
| Al resolver recalcula la habilitación de dependientes | `DescubrimientoServiceTest` · Un resultado del servidor se guarda con origen servidor y sus datos, guarda su evento y luego recalcula los puzzles · `PuzzlesIT` · lookout_enabled y observatory_enabled que llegan antes que sus requisitos habilitan su puzzle en el PUT que completa el último requisito · observatory espera coordenada_a: con lookout resuelto y su bandera sigue bloqueado hasta que se resuelve radio, aunque llegue al final | ✅ |
| Al resolver guarda `evento` (`puzzle.iniciado`, `puzzle.resuelto`) | `PuzzlesIT` · Cada cambio de estado de un puzzle guarda {puzzle, estado, datos} y cada resultado guarda {clave, datos} con origen servidor · La cadena completa por HTTP (lista los 22 eventos en orden) · `PuzzleServiceTest` · disponible más iniciado pasa a en_curso, … · disponible o en_curso más completada pasa a resuelto, … | ✅ |
| Al resolver actualiza `ultima_actividad` y renueva `expira_en` | `PuzzlesIT` · Resolver un puzzle actualiza ultima_actividad y renueva expira_en; repetir el aviso también renueva, sin guardar eventos | ✅ |
| Todo en **una sola transacción** | `PuzzlesIT` · Resolver es una sola transacción: si crear el resultado falla, el puzzle no queda resuelto, no se guarda ningún evento y la sala no se renueva | ✅ |
| Coordenadas de `vestigia.puzzles.coordenadas.ar` y `.dec` (`137` y `+042`) como **texto**; `"+042"` intacto en la respuesta y en la base | `PuzzlesIT` · "+042" llega intacto como texto en GET estado y en la base (jsonb string), igual que "137" · `PuzzleServiceTest` · Las coordenadas salen de la configuración, sin convertirse a número (con `"0099"` y `"-007"`) · Cada puzzle crea su resultado con origen servidor: … | ✅ |
| `codigo` en el cuerpo uniforme de error. **Se eligió incluir siempre `"codigo": null`**, no omitirlo | `ApiExceptionHandlerTest` · Los errores sin código traen "codigo": null en 400, 401, 403, 404, 405, 409 y 500 · Un error con código lo lleva en codigo y sus detalles en details, con el mismo cuerpo uniforme · `PuzzlesIT` · Los errores reales de PUT puzzles traen codigo null en 400, 401, 403, 404 y 405, y puzzle_bloqueado en el 409 · Resolver es una sola transacción (un `500` real con `codigo` null) · `PuzzleControllerTest` · Sin token da 401 y con el token de otra sala da 403, con codigo null y sin llamar al servicio | ✅ |
| Prueba obligatoria: cadena completa de punta a punta con una sala real | `PuzzlesIT` · La cadena completa por HTTP: pescador → radio → coordenada_a; tres piezas → photo → coordenada_b; lookout_enabled → lookout; observatory_enabled → observatory → magnetar_localizado | ✅ |
| Prueba obligatoria: cada bandera del juego **antes** y **después** de sus requisitos | `PuzzlesIT` · lookout_enabled y observatory_enabled que llegan antes que sus requisitos habilitan su puzzle en el PUT que completa el último requisito · lookout_enabled y observatory_enabled que llegan después de sus requisitos habilitan su puzzle en ese mismo POST | ✅ |
| Prueba obligatoria: la matriz de estados | `PuzzlesIT` · Matriz de estados: bloqueado da 409; disponible+iniciado pasa a en_curso; en_curso+iniciado no cambia; en_curso+completada resuelve; resuelto+cualquier aviso no cambia ni duplica | ✅ |
| Prueba obligatoria: idempotencia y concurrencia (dos `completada` simultáneos crean un solo descubrimiento) | `PuzzlesIT` · Diez completada simultáneos sobre photo responden 200 y dejan una sola coordenada_b, un solo puzzle.resuelto y lookout habilitado una vez. Sin el bloqueo por sala, falla (sección 2) | ✅ |
| Prueba obligatoria: un cliente no puede fabricar un descubrimiento del servidor | `PuzzlesIT` · Un cliente no puede fabricar un resultado del servidor: POST de las cuatro claves da 403 y PUT de un puzzle bloqueado da 409, sin crear nada; el resultado real tiene origen servidor y los datos de la configuración · `PuzzleServiceTest` · Cada puzzle crea su resultado… (las cuatro claves que crea son exactamente `CLAVES_DEL_SERVIDOR`) | ✅ |
| Cada cambio de estado publica un `SalaEvento` con `{puzzle, estado, datos}`, incluido `bloqueado` → `disponible`; cada resultado del servidor publica el suyo con `{clave, datos}` y origen `servidor` | `HabilitacionPuzzlesServiceTest` · Recalcular pasa a disponible los puzzles habilitados y guarda un evento puzzle.habilitado de origen servidor con {puzzle, estado, datos} por cada uno · `EscriturasSalaIT` · Las tres piezas enviadas a la vez, repetidas, habilitan photo una sola vez · `PuzzlesIT` · Cada cambio de estado de un puzzle guarda {puzzle, estado, datos} … · `ActividadServiceTest` · Registrar renueva la sala, guarda el evento y lo publica para el WebSocket del Paso 4 (cada evento guardado se publica con el mismo payload) | ✅ |
| Una sala con `expira_en` vencido sigue dando `401` | `PuzzlesIT` · Con expira_en vencido y sin que corra el job, PUT puzzles responde 401 y no resuelve nada · `PuzzleServiceTest` · Si la sala terminó entre el token y la escritura, el 401 de bloquear se propaga sin leer ni tocar el puzzle | ✅ |
| Token en la ruta nueva (`401` sin token, `403` otra sala) | `PuzzleControllerTest` · Sin token da 401 y con el token de otra sala da 403, con codigo null y sin llamar al servicio · `PuzzlesIT` · Los errores reales de PUT puzzles traen codigo null en 400, 401, 403, 404 y 405, … | ✅ |
| Swagger documenta la ruta nueva | `AplicacionIT` · openApiDocumentaLosEndpointsDeSalasYElToken (ahora exige el `put` de `/puzzles/{puzzle}`) | ✅ |
| Documentación (`docs/api.md`, `docs/modelo-datos.md`, `README.md`, `docs/PLAN.md`) | Sin prueba: es documentación. Se actualizó en el mismo commit que cada cambio. Los ejemplos de `docs/api.md` salieron de la app real | — |

## 4. Lista completa de pruebas


| Tipo | Clase | Prueba | Tiempo (s) | Resultado |
|---|---|---|---:|---|
| unitaria | `config.ApiHttpConfigTest` | corsRechazaOrigenNoConfigurado | 0.022 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | corsPermiteOrigenConfigurado | 0.003 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | registraUnaLineaPorPeticionSinTokens(CapturedOutput) | 0.013 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | noRegistraRutasFueraDeApi(CapturedOutput) | 0.027 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Los tipos correctos se siguen leyendo, y en un objeto libre (Map) cada valor conserva su tipo JSON | 0.014 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un texto o un número en un campo booleano no se convierte: falla, también "true" entre comillas | 0.002 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un texto o un booleano en un campo numérico no se convierte: falla | 0.000 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un número o un booleano en un campo de texto no se convierte a texto: falla | 0.001 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | Los errores sin código traen "codigo": null en 400, 401, 403, 404, 405, 409 y 500 | 0.017 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | rutaInexistenteDevuelve404ConCuerpoUniforme | 0.001 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | jsonMalformadoDevuelve400ConCuerpoUniforme | 0.003 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | validacionDevuelve400ConDetallePorCampo | 0.003 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | errorInesperadoDevuelve500SinDetallesInternos | 0.002 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | Un error con código lo lleva en codigo y sus detalles en details, con el mismo cuerpo uniforme | 0.002 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | conflictoConservaSuCodigoYMensaje | 0.001 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Sin clave, con una clave que no es texto o con datos que no son un objeto JSON da 400 sin llamar al servicio | 0.014 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Unos datos de más de 4096 bytes en JSON dan 400 con detalle en datos, sin llamar al servicio | 0.003 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Sin token da 401 y con el token de otra sala da 403, sin llamar al servicio | 0.002 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Una clave del servidor responde 403 con el cuerpo de error uniforme | 0.002 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Repetir una clave responde 200 con el descubrimiento que ya existía | 0.003 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Una clave vacía, con mayúsculas, guiones, acentos, espacios o de más de 40 caracteres da 400 con detalle | 0.007 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Un descubrimiento nuevo responde 201 con clave, datos, origen y creado_en | 0.002 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Sin datos, con datos null o con el aviso fuera de datos da 400 con details en datos y codigo null, sin llamar al servicio | 0.015 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Sin token da 401 y con el token de otra sala da 403, con codigo null y sin llamar al servicio | 0.003 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | datos que no es un objeto JSON da 400, sin llamar al servicio | 0.004 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Un aviso inválido para el puzzle responde 400 con el details del servicio y codigo null | 0.002 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | PUT puzzle responde 200 con {puzzle, estado, datos} y pasa al servicio el id de la ruta y los datos | 0.004 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Un puzzle bloqueado responde 409 con codigo puzzle_bloqueado en el cuerpo uniforme | 0.002 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Los valores de datos llegan al servicio con su tipo JSON: "true" es texto, 1 es número y true es booleano | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | La app recibe 403 en PUT escena y en DELETE antes de validar el cuerpo, sin llamar al servicio | 0.025 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | pueblosPasaElFiltroDeDisponibles | 0.008 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeOtraSalaDa403 | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena sin token da 401 | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | unirseNoPideTokenYDevuelveElDeLaApp | 0.006 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearResponde201ConCodigoEstadoYToken | 0.028 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | DELETE de la sala responde 204 sin cuerpo y sin token da 401 | 0.004 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena sin escena o con escena vacía da 400 con detalle, sin llamar al servicio | 0.009 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDesconocidoDa401 | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearSinCodigoDa400ConDetalle | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeLaSalaDevuelveElEstadoEnSnakeCase | 0.011 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena responde 200 con escena_actual | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | El token de otra sala recibe 403 en PUT escena y DELETE, sin llamar al servicio | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearConPuebloOcupadoDa409ConCuerpoUniforme | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoSinTokenDa401 | 0.002 | ✅ pasó |
| unitaria | `dto.JsonAcotadoTest` | Los datos que caben en el límite, en bytes de su JSON compacto, son válidos; nulos también | 0.054 | ✅ pasó |
| unitaria | `dto.JsonAcotadoTest` | Un byte más del límite es inválido, y los acentos cuentan como dos bytes | 0.028 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear devuelve la sala si sigue viva | 0.040 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Renovar pone ultima_actividad en ahora y expira_en en ahora más la expiración configurada | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar el cierre de una sala finalizada guarda el evento y ultima_actividad, pero no renueva expira_en | 0.000 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Los tipos de evento se guardan con su punto: sala.creada, app.unida, descubrimiento.registrado... | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar renueva la sala, guarda el evento y lo publica para el WebSocket del Paso 4 | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear una sala finalizada, expirada o inexistente responde 401 | 0.000 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear una sala viva cuyo expira_en ya pasó responde 401 aunque el job no la haya marcado | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Si la sala terminó entre el token y la escritura, el 401 de bloquear se propaga sin guardar nada | 0.078 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | El descubrimiento se guarda en la sala del token con la clave recibida | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Repetir una clave devuelve el descubrimiento original sin guardar otro, sin evento y renovando la sala | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Un descubrimiento nuevo se guarda con el origen del rol, guarda su evento y luego recalcula los puzzles | 0.003 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Sin datos, el descubrimiento se guarda con un objeto vacío y origen juego si lo manda el juego | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Las cuatro claves del servidor dan 403 con cualquier rol y no tocan la sala | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Un resultado del servidor se guarda con origen servidor y sus datos, guarda su evento y luego recalcula los puzzles | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | radio se habilita con pescador_dialogo_completo y no antes | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | photo se habilita solo con las tres piezas, lleguen en el orden que lleguen | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Solo un puzzle bloqueado pasa a disponible: los que están disponibles, en curso o resueltos no cambian | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Recalcular pasa a disponible los puzzles habilitados y guarda un evento puzzle.habilitado de origen servidor con {puzzle, estado, datos} por cada uno | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | observatory pide coordenada_a, coordenada_b, observatory_enabled y lookout resuelto; falta uno y no se habilita | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Recalcular sin requisitos nuevos no cambia nada ni guarda eventos | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | lookout pide photo resuelto y lookout_enabled; la bandera puede llegar antes o después | 0.000 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | en_curso más iniciado y resuelto más cualquier aviso responden el estado actual, solo renuevan la sala y no guardan eventos ni descubrimientos | 0.028 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | El evento lleva el origen del rol que avisó: juego o app | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Si la sala terminó entre el token y la escritura, el 401 de bloquear se propaga sin leer ni tocar el puzzle | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Las coordenadas salen de la configuración, sin convertirse a número | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | disponible más iniciado pasa a en_curso, guarda los datos y puzzle.iniciado con {puzzle, estado, datos} y el origen del rol, sin crear descubrimiento | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Un puzzle bloqueado da 409 con codigo puzzle_bloqueado ante cualquier aviso válido y no cambia nada | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Cada puzzle crea su resultado con origen servidor: coordenada_a con AR "137", coordenada_b con DEC "+042" como texto, observatorio_visible y magnetar_localizado | 0.004 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | disponible o en_curso más completada pasa a resuelto: guarda puzzle.resuelto y después crea el resultado | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | radio solo acepta {sintonizada: true}: iniciado o completada dan 400; los puzzles de la app no aceptan sintonizada | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Solo vale una clave aceptada con el booleano true: texto, número, false, null, claves extra, vacío o una clave ajena dan 400 con details en datos | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Un id de puzzle desconocido da 400 con la lista de ids, también los viejos fotografia, mirador y observatorio y las variantes de mayúsculas o espacios | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearFueraDelCatalogoDa400SinTocarSalas | 0.050 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Cambiar la escena la guarda y registra escena.cambiada con la anterior y la nueva | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | expirarVencidasUsaLaHoraDelReloj | 0.000 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | otraViolacionDeIntegridadNoSeDisfrazaDe409 | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseSinSalaVivaDa404 | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | listarPueblosMarcaOcupadosLosQueTienenSalaViva | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseEnlazaConUnSoloUpdateYRenuevaLaExpiracion | 0.000 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearConPuebloOcupadoDa409 | 0.000 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Unirse guarda el evento app.unida con origen app sobre la sala enlazada | 0.000 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Cerrar la sala la pasa a finalizada y guarda sala.finalizada con origen juego | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Una escena fuera de introduccion, pueblo, faro, observatorio y desenlace da 400 con la lista de válidas | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Crear la sala guarda el evento sala.creada con origen juego y el código en el payload | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Unirse a una sala con expira_en vencido que el job aún no marcó responde 404 con el mensaje de sala inexistente | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Si unirse pierde la carrera no guarda ningún evento | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | estadoOrdenaLosPuzzlesYReportaDispositivosDesconectados | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Repetir la escena actual responde igual y solo renueva la sala, sin evento | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseASalaVivaQueYaTieneAppDa409 | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | La app no puede cerrar la sala: 403 sin tocarla | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | cadaSalaRecibeUnTokenDistinto | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearInsertaLosCuatroPuzzlesBloqueados | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearNormalizaElCodigoYGuardaSoloElHashDelToken | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | autenticarDistingueElRolPorElHash | 0.003 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | autenticarConTokenDesconocidoNoDaCredencial | 0.000 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | La app no puede cambiar la escena: 403 sin tocar la sala | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Autenticar con el token de una sala viva con expira_en vencido no da credencial; justo en expira_en sí | 0.001 | ✅ pasó |
| integración | `AplicacionIT` | rutaInexistenteBajoSalasDa404SinPedirToken | 0.003 | ✅ pasó |
| integración | `AplicacionIT` | elJobDeExpiracionEstaProgramadoCadaMinuto | 0.001 | ✅ pasó |
| integración | `AplicacionIT` | openApiDocumentaLosEndpointsDeSalasYElToken | 0.154 | ✅ pasó |
| integración | `AplicacionIT` | swaggerUiYOpenApiEstanDisponibles | 0.009 | ✅ pasó |
| integración | `AplicacionIT` | healthResponde200ConEstadoUp | 0.012 | ✅ pasó |
| integración | `CatalogoPueblosIT` | flywayAplicaV2YLosCodigosEstanEnLaTabla | 0.004 | ✅ pasó |
| integración | `CatalogoPueblosIT` | seedDeclara101CodigosValidosYSinDuplicados | 0.002 | ✅ pasó |
| integración | `EscriturasSalaIT` | Con expira_en vencido y sin que corra el job, unirse da 404 y GET estado da 401 a los dos tokens; la sala no revive | 0.026 | ✅ pasó |
| integración | `EscriturasSalaIT` | pescador_dialogo_completo habilita radio y GET estado lo muestra disponible | 0.019 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una sala en esperando_app también se puede cerrar | 0.015 | ✅ pasó |
| integración | `EscriturasSalaIT` | El token del juego de otra sala recibe 403 en PUT escena y DELETE, y la sala no cambia | 0.017 | ✅ pasó |
| integración | `EscriturasSalaIT` | lookout_enabled que llega después de photo resuelto habilita lookout en la misma petición | 0.014 | ✅ pasó |
| integración | `EscriturasSalaIT` | Diez peticiones simultáneas con la misma clave dan un 201 y nueve 200, una sola fila y un solo evento | 0.025 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una escena inválida, vacía, de otro tipo o ausente da 400 y no cambia nada | 0.028 | ✅ pasó |
| integración | `EscriturasSalaIT` | El juego cierra la sala: 204, queda finalizada sin renovar expira_en, guarda sala.finalizada y los dos tokens dan 401 | 0.033 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un descubrimiento, nuevo o repetido, renueva ultima_actividad y expira_en con la duración configurada | 0.020 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una sala con expira_en vencido que el job aún no marcó responde 401 a toda escritura y no revive | 0.016 | ✅ pasó |
| integración | `EscriturasSalaIT` | Cerrar la sala libera el código: se puede crear otra sala con el mismo pueblo y la cerrada queda como historial | 0.021 | ✅ pasó |
| integración | `EscriturasSalaIT` | Crear y unirse guardan los eventos sala.creada (origen juego) y app.unida (origen app) | 0.010 | ✅ pasó |
| integración | `EscriturasSalaIT` | Cada escritura deja su evento en orden: sala.creada, app.unida, descubrimiento.registrado, puzzle.habilitado, escena.cambiada y sala.finalizada | 0.024 | ✅ pasó |
| integración | `EscriturasSalaIT` | El juego cambia la escena: 200, GET estado la muestra, guarda escena.cambiada y renueva expira_en | 0.016 | ✅ pasó |
| integración | `EscriturasSalaIT` | Repetir la escena actual responde 200, renueva expira_en y no guarda otro escena.cambiada | 0.015 | ✅ pasó |
| integración | `EscriturasSalaIT` | Repetir una clave con otros datos responde 200 con los datos originales y no guarda otro evento | 0.018 | ✅ pasó |
| integración | `EscriturasSalaIT` | La app recibe 403 al cerrar la sala y la sala sigue activa | 0.017 | ✅ pasó |
| integración | `EscriturasSalaIT` | lookout y observatory se habilitan por servicio cuando se resuelve el puzzle previo, aunque la bandera haya llegado antes | 0.027 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un descubrimiento nuevo responde 201, aparece en GET estado y guarda descubrimiento.registrado con el origen del rol | 0.019 | ✅ pasó |
| integración | `EscriturasSalaIT` | Las tres piezas enviadas a la vez, repetidas, habilitan photo una sola vez | 0.033 | ✅ pasó |
| integración | `EscriturasSalaIT` | Las cuatro claves del servidor dan 403 al juego y a la app y no se guardan | 0.034 | ✅ pasó |
| integración | `EscriturasSalaIT` | Unos datos que pasan de 4096 bytes en JSON dan 400 y no se guardan; justo en el límite responden 201 | 0.022 | ✅ pasó |
| integración | `EscriturasSalaIT` | La app recibe 403 al cambiar la escena, con cuerpo válido, inválido o vacío, y la escena no cambia | 0.019 | ✅ pasó |
| integración | `EscriturasSalaIT` | DELETE simultáneo con nueve descubrimientos: los que pierden reciben 401 y no dejan filas ni eventos después del cierre | 0.074 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un número o booleano JSON donde va texto da 400 sin convertirse: clave, escena y código de pueblo | 0.028 | ✅ pasó |
| integración | `EsquemaIT` | salasTerminadasNoBloqueanUnaNuevaSalaViva | 0.005 | ✅ pasó |
| integración | `EsquemaIT` | indiceEventosPorSalaYTiempoExiste | 0.003 | ✅ pasó |
| integración | `EsquemaIT` | noPuedeHaberDosSalasVivasConElMismoCodigo | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | catalogoRechazaCodigosConMinusculasAcentosOEnie | 0.003 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaCodigoFueraDelCatalogo | 0.001 | ✅ pasó |
| integración | `EsquemaIT` | flywayAplicaV1YCreaLasCincoTablas | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | alFinalizarUnaSalaElCodigoSePuedeReclamarDeNuevo | 0.003 | ✅ pasó |
| integración | `EsquemaIT` | carreraPorElMismoPuebloDejaUnaSolaSalaViva | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaEstadoInvalido | 0.002 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | Tras V3, el CHECK de puzzles_estado rechaza fotografia, mirador y observatorio y acepta los ids nuevos | 0.290 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | V3 renombra a inglés los puzzles de una sala creada con V2, conserva su estado y sus datos, y ese esquema rechaza fotografia | 0.156 | ✅ pasó |
| integración | `PuzzlesIT` | Con expira_en vencido y sin que corra el job, PUT puzzles responde 401 y no resuelve nada | 0.242 | ✅ pasó |
| integración | `PuzzlesIT` | Cada cambio de estado de un puzzle guarda {puzzle, estado, datos} y cada resultado guarda {clave, datos} con origen servidor | 0.104 | ✅ pasó |
| integración | `PuzzlesIT` | Los errores reales de PUT puzzles traen codigo null en 400, 401, 403, 404 y 405, y puzzle_bloqueado en el 409 | 0.055 | ✅ pasó |
| integración | `PuzzlesIT` | Resolver es una sola transacción: si crear el resultado falla, el puzzle no queda resuelto, no se guarda ningún evento y la sala no se renueva | 0.087 | ✅ pasó |
| integración | `PuzzlesIT` | Un cliente no puede fabricar un resultado del servidor: POST de las cuatro claves da 403 y PUT de un puzzle bloqueado da 409, sin crear nada; el resultado real tiene origen servidor y los datos de la configuración | 0.093 | ✅ pasó |
| integración | `PuzzlesIT` | lookout_enabled y observatory_enabled que llegan antes que sus requisitos habilitan su puzzle en el PUT que completa el último requisito | 0.119 | ✅ pasó |
| integración | `PuzzlesIT` | lookout_enabled y observatory_enabled que llegan después de sus requisitos habilitan su puzzle en ese mismo POST | 0.114 | ✅ pasó |
| integración | `PuzzlesIT` | "+042" llega intacto como texto en GET estado y en la base (jsonb string), igual que "137" | 0.090 | ✅ pasó |
| integración | `PuzzlesIT` | Matriz de estados: bloqueado da 409; disponible+iniciado pasa a en_curso; en_curso+iniciado no cambia; en_curso+completada resuelve; resuelto+cualquier aviso no cambia ni duplica | 0.119 | ✅ pasó |
| integración | `PuzzlesIT` | observatory espera coordenada_a: con lookout resuelto y su bandera sigue bloqueado hasta que se resuelve radio, aunque llegue al final | 0.087 | ✅ pasó |
| integración | `PuzzlesIT` | Solo el booleano true: texto "true", 1, false, null, claves extra, objeto vacío o datos ausente dan 400 con details en datos y no cambian nada; iniciado en radio también | 0.087 | ✅ pasó |
| integración | `PuzzlesIT` | Resolver un puzzle actualiza ultima_actividad y renueva expira_en; repetir el aviso también renueva, sin guardar eventos | 0.036 | ✅ pasó |
| integración | `PuzzlesIT` | Un id de puzzle desconocido da 400 con la lista de ids, igual para los viejos fotografia, mirador y observatorio, y no toca la sala | 0.031 | ✅ pasó |
| integración | `PuzzlesIT` | Diez completada simultáneos sobre photo responden 200 y dejan una sola coordenada_b, un solo puzzle.resuelto y lookout habilitado una vez | 0.070 | ✅ pasó |
| integración | `PuzzlesIT` | La cadena completa por HTTP: pescador → radio → coordenada_a; tres piezas → photo → coordenada_b; lookout_enabled → lookout; observatory_enabled → observatory → magnetar_localizado | 0.108 | ✅ pasó |
| integración | `SalaIT` | crearDevuelveElTokenUnaVezYGuardaSoloSuHash | 0.015 | ✅ pasó |
| integración | `SalaIT` | carreraDeAppsPorUnirseDaUn200YSiete409 | 0.026 | ✅ pasó |
| integración | `SalaIT` | unirseSinSalaVivaDa404 | 0.007 | ✅ pasó |
| integración | `SalaIT` | flujoCrearUnirseEstado | 0.025 | ✅ pasó |
| integración | `SalaIT` | elJobNoTocaSalasFinalizadas | 0.013 | ✅ pasó |
| integración | `SalaIT` | unirseAceptaMinusculas | 0.013 | ✅ pasó |
| integración | `SalaIT` | pueblosReflejaLasSalasVivas | 0.026 | ✅ pasó |
| integración | `SalaIT` | estadoExigeElTokenDeEsaSala | 0.026 | ✅ pasó |
| integración | `SalaIT` | salaVencidaExpiraLiberaElNombreYSeConservaComoHistorial | 0.022 | ✅ pasó |
| integración | `SalaIT` | unirseRenuevaLaExpiracion | 0.012 | ✅ pasó |
| integración | `SalaIT` | carreraPorElMismoPuebloDaUn201YSiete409 | 0.010 | ✅ pasó |
| integración | `SalaIT` | crearFueraDelCatalogoDa400 | 0.003 | ✅ pasó |
| integración | `SalaIT` | crearAceptaMinusculasYEspacios | 0.007 | ✅ pasó |
| integración | `SalaIT` | crearInsertaLosCuatroPuzzlesBloqueados | 0.008 | ✅ pasó |
| integración | `SalaIT` | unirseDosVecesDa409 | 0.014 | ✅ pasó |
| integración | `SalaIT` | salaQueAunNoVenceSigueViva | 0.009 | ✅ pasó |
| integración | `repository.ModeloIT` | guardaYLeeSalaPuzzlesYDescubrimientosConJsonb | 0.031 | ✅ pasó |
| integración | `ws.WebSocketIT` | elServidorCierraLaConexionCon1008MientrasNoHayAutenticacion | 0.039 | ✅ pasó |

## 5. Salida de `./mvnw clean verify`

Últimas líneas de Maven sobre `60eae97` (sin los logs de Spring; rutas recortadas al repo):

```text
[INFO] Running com.vestigia.service.PuzzleServiceTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.045 s -- in com.vestigia.service.PuzzleServiceTest
[INFO] Running com.vestigia.service.SalaServiceTest
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.077 s -- in com.vestigia.service.SalaServiceTest
[INFO] Running com.vestigia.service.HabilitacionPuzzlesServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in com.vestigia.service.HabilitacionPuzzlesServiceTest
[INFO] Running com.vestigia.service.ActividadServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.045 s -- in com.vestigia.service.ActividadServiceTest
[INFO] Results:
[INFO] Tests run: 103, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- jar:3.5.1:jar (default-jar) @ vestigia ---
[INFO] Building jar: target/vestigia-0.0.1-SNAPSHOT.jar
[INFO] --- spring-boot:4.1.1:repackage (repackage) @ vestigia ---
[INFO] Replacing main artifact target/vestigia-0.0.1-SNAPSHOT.jar with repackaged archive, adding nested dependencies in BOOT-INF/.
[INFO] The original artifact has been renamed to target/vestigia-0.0.1-SNAPSHOT.jar.original
[INFO] --- failsafe:3.5.6:integration-test (default) @ vestigia ---
[INFO] Running com.vestigia.MigracionIdsPuzzlesIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.764 s -- in com.vestigia.MigracionIdsPuzzlesIT
[INFO] Running com.vestigia.PuzzlesIT
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.455 s -- in com.vestigia.PuzzlesIT
[INFO] Running com.vestigia.AplicacionIT
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.185 s -- in com.vestigia.AplicacionIT
[INFO] Running com.vestigia.repository.ModeloIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.039 s -- in com.vestigia.repository.ModeloIT
[INFO] Running com.vestigia.EsquemaIT
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.034 s -- in com.vestigia.EsquemaIT
[INFO] Running com.vestigia.CatalogoPueblosIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.010 s -- in com.vestigia.CatalogoPueblosIT
[INFO] Running com.vestigia.SalaIT
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.250 s -- in com.vestigia.SalaIT
[INFO] Running com.vestigia.EscriturasSalaIT
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.606 s -- in com.vestigia.EscriturasSalaIT
[INFO] Running com.vestigia.ws.WebSocketIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.047 s -- in com.vestigia.ws.WebSocketIT
[INFO] Results:
[INFO] Tests run: 76, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- failsafe:3.5.6:verify (default) @ vestigia ---
[INFO] BUILD SUCCESS
[INFO] Total time:  14.799 s
[INFO] Finished at: 2026-11-06T00:29:45-06:00
```

## 6. Pruebas adversariales de la API

Pendiente: las completa el orquestador.

## 7. Revisiones independientes

Pendiente: las completa el orquestador.

## 8. Lo que no se probó y por qué

| Qué | Por qué |
|---|---|
| Emisión por WebSocket de `puzzle.actualizado` y `descubrimiento.nuevo` | Es del Paso 4. Aquí cada cambio de estado y cada resultado guardan su evento y lo publican como `SalaEvento` dentro de la transacción. Las pruebas leen los eventos guardados; `ActividadServiceTest` comprueba que lo guardado se publica |
| Qué rol puede reportar cada puzzle | **Decisión pendiente de Raúl (P8).** El contrato dice "juego o app", así que cualquier rol puede reportar cualquier puzzle, aunque en el diseño la radio la manda el juego y los demás la app. No se restringió |
| Errores que no pasan por Spring MVC (por ejemplo, una petición que Tomcat rechaza antes del `DispatcherServlet`) | Esos los responde el `/error` de Spring Boot, no `ApiExceptionHandler`, así que su cuerpo no es el uniforme y no trae `codigo`. Ya pasaba antes del 3b. Es una inferencia por cómo funciona Spring; no se probó |
| Los números del mirador y del observatorio | Por diseño el servidor no los mide ni los valida: la app avisa `{completada: true}` |
| Reclamar el pueblo de una sala vencida que el job aún no marcó | Sigue igual que en el 3a (O5 en `docs/PLAN.md`) |

Prueba manual con la app real, con la sala `BACALAR`. Usó el jar de la última corrida antes de `b0ea64a`; lo único que cambió después en `src/main` fue hacer privados métodos y tipos de `PuzzleService`, sin efecto en el comportamiento (los tokens no aparecen en el log de la app; se comprobó con `grep`):

| Petición | Respuesta |
|---|---|
| `PUT radio {"sintonizada": true}` antes del pescador, juego | `409` con `"codigo": "puzzle_bloqueado"` |
| `PUT radio {"iniciado": true}` y `{"sintonizada": "true"}`, con radio disponible | `400 Datos inválidos`, `details` en `datos`: `en radio debe ser exactamente {"sintonizada": true}` |
| `PUT radio {"sintonizada": true}` dos veces | `200` `{"puzzle":"radio","estado":"resuelto","datos":{"sintonizada":true}}` las dos veces |
| `PUT fotografia` | `400` `'fotografia' no es un puzzle válido. Usa uno de: radio, photo, lookout, observatory` |
| `PUT photo` con `{}` | `400` con `details: [{"field":"datos","message":"es obligatorio"}]` |
| Tres piezas, luego `PUT photo` `iniciado`, con clave extra y `completada` | `200 en_curso`, `400` y `200 resuelto` |
| `PUT lookout` antes de `lookout_enabled` / después | `409` / `200 resuelto` |
| `observatory_enabled` y `PUT observatory {"completada": true}` | `200 resuelto` |
| `GET estado` al final | Los cuatro `resuelto`; `coordenada_a` `{"tipo":"ascension_recta","valor":"137"}`, `coordenada_b` `{"tipo":"declinacion","valor":"+042"}`, `observatorio_visible` y `magnetar_localizado`, los cuatro con origen `servidor` |
| Tabla `eventos` | 21 filas en orden: cada `puzzle.habilitado`, `puzzle.iniciado` y `puzzle.resuelto` con `{puzzle, estado, datos}` y cada resultado como `descubrimiento.registrado` de origen `servidor` |
| `GET /v3/api-docs` | La ruta `put` con los cuatro ids en `puzzle` y respuestas `200`, `400`, `401`, `403` y `409`; `ErrorBody` con `codigo` |
