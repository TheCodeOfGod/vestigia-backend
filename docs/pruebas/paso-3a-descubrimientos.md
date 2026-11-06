# Paso 3a · Descubrimientos, escena y cierre

Rama `feature/descubrimientos-escena-cierre`, comparada contra `feature/ids-puzzles-ingles`. Agrega `POST /salas/{codigo}/descubrimientos`, `PUT /salas/{codigo}/escena` y `DELETE /salas/{codigo}`. También agrega la bitácora de eventos, la renovación de `expira_en` en cada escritura y el servicio de habilitación de puzzles que reutilizará el Paso 3b. La API sigue en `/api/v1`. No hay migraciones nuevas.

## 1. Veredicto

✅ 140 pruebas, 0 fallos, 0 errores, 0 omitidas.

| Total | Unitarias | Integración | Fallos | Errores | Omitidas | Fecha | Commit | Rama |
|---:|---:|---:|---:|---:|---:|---|---|---|
| 140 | 80 | 60 | 0 | 0 | 0 | 2026-11-05 23:58 | `ac37926` | `feature/descubrimientos-escena-cierre` |

Las cifras son de `./mvnw clean verify` sobre `ac37926`, el último commit de código (incluye las correcciones de la sección 7). El commit siguiente solo actualiza esta evidencia, `docs/pruebas/README.md` y `docs/PLAN.md`.

La primera entrega, antes de la revisión, terminó en `ca4a98f` con 123 pruebas en verde.

## 2. Cómo reproducirlo

Con Docker corriendo (las pruebas `*IT` levantan `postgres:17-alpine` con Testcontainers):

```sh
git checkout feature/descubrimientos-escena-cierre
./mvnw clean verify
python3 scripts/resumen-pruebas.py               # secciones 1 y 4 de este archivo
python3 scripts/resumen-pruebas.py --solo-conteo # una línea
```

Comprobación de que las pruebas concurrentes miden el bloqueo por sala. Se cambia `salas.bloquear(salaId)` por `salas.findById(salaId)` en `ActividadService.bloquear` y se corren solo esas dos pruebas:

```sh
./mvnw clean verify -Dtest='NoHay*' -Dsurefire.failIfNoSpecifiedTests=false \
	-Dit.test='EscriturasSalaIT#idempotenciaConcurrente+piezasSimultaneasHabilitanPhoto'
git checkout src/main/java/com/vestigia/service/ActividadService.java
```

Sin el bloqueo, las dos fallan: `Expected size: 9 but was: 0` y `Expected size: 6 but was: 0`. Ninguna de las peticiones repetidas recibe `200`, porque chocan contra `UNIQUE (sala_id, clave)`. Con el bloqueo, las dos pasan.

Prueba manual con la app real (puerto 18080, Postgres `vestigia-pg-3a` en 55432, los dos borrados al terminar):

```sh
docker run -d --name vestigia-pg-3a -e POSTGRES_DB=vestigia -e POSTGRES_PASSWORD=postgres -p 55432:5432 postgres:17-alpine
DB_URL=jdbc:postgresql://localhost:55432/vestigia PORT=18080 java -jar target/vestigia-0.0.1-SNAPSHOT.jar
# ... peticiones con curl (resumen en la sección 8) ...
docker rm -f vestigia-pg-3a
```

## 3. Requisito → pruebas

Las pruebas de `EscriturasSalaIT` van por HTTP contra la app levantada y un Postgres real. `*ServiceTest` son unitarias con Mockito y `*ControllerTest` son `@WebMvcTest`.

| Requisito del paso | Pruebas | Resultado |
|---|---|:-:|
| `POST /descubrimientos` con `{clave, datos}`, roles juego o app, primera vez `201` | `EscriturasSalaIT` · Un descubrimiento nuevo responde 201, aparece en GET estado y guarda descubrimiento.registrado con el origen del rol · `DescubrimientoControllerTest` · Un descubrimiento nuevo responde 201 con clave, datos, origen y creado_en · `DescubrimientoServiceTest` · Un descubrimiento nuevo se guarda con el origen del rol, guarda su evento y luego recalcula los puzzles | ✅ |
| Repetir la misma clave devuelve `200` y no duplica | `EscriturasSalaIT` · Repetir una clave con otros datos responde 200 con los datos originales y no guarda otro evento · `DescubrimientoServiceTest` · Repetir una clave devuelve el descubrimiento original sin guardar otro, sin evento y renovando la sala · `DescubrimientoControllerTest` · Repetir una clave responde 200 con el descubrimiento que ya existía | ✅ |
| Idempotencia **bajo concurrencia** (`UNIQUE (sala_id, clave)`) | `EscriturasSalaIT` · Diez peticiones simultáneas con la misma clave dan un 201 y nueve 200, una sola fila y un solo evento · Las tres piezas enviadas a la vez, repetidas, habilitan photo una sola vez. Sin el bloqueo por sala las dos fallan (sección 2) | ✅ |
| Clave `^[a-z0-9_]{1,40}$` y `datos` objeto JSON opcional | `DescubrimientoControllerTest` · Una clave vacía, con mayúsculas, guiones, acentos, espacios o de más de 40 caracteres da 400 con detalle (y 40 caracteres pasa) · Sin clave, con una clave que no es texto o con datos que no son un objeto JSON da 400 sin llamar al servicio · `DescubrimientoServiceTest` · Sin datos, el descubrimiento se guarda con un objeto vacío y origen juego si lo manda el juego | ✅ |
| Las cuatro claves del servidor dan `403` | `EscriturasSalaIT` · Las cuatro claves del servidor dan 403 al juego y a la app y no se guardan · `DescubrimientoServiceTest` · Las cuatro claves del servidor dan 403 con cualquier rol y no tocan la sala · `DescubrimientoControllerTest` · Una clave del servidor responde 403 con el cuerpo de error uniforme | ✅ |
| `PUT /escena` solo rol `juego` (`403` para `app`, mande el cuerpo que mande) | `EscriturasSalaIT` · La app recibe 403 al cambiar la escena, con cuerpo válido, inválido o vacío, y la escena no cambia · `SalaControllerTest` · La app recibe 403 en PUT escena y en DELETE antes de validar el cuerpo, sin llamar al servicio (B2) · `SalaServiceTest` · La app no puede cambiar la escena: 403 sin tocar la sala | ✅ |
| Escenas válidas `introduccion`, `pueblo`, `faro`, `observatorio`, `desenlace`; otro valor `400` | `EscriturasSalaIT` · El juego cambia la escena: 200, GET estado la muestra, guarda escena.cambiada y renueva expira_en · Una escena inválida, vacía, de otro tipo o ausente da 400 y no cambia nada · `SalaServiceTest` · Una escena fuera de introduccion, pueblo, faro, observatorio y desenlace da 400 con la lista de válidas · Cambiar la escena la guarda y registra escena.cambiada con la anterior y la nueva · Repetir la escena actual responde igual y solo renueva la sala, sin evento · `SalaControllerTest` · PUT escena responde 200 con escena_actual · PUT escena sin escena o con escena vacía da 400 con detalle, sin llamar al servicio | ✅ |
| `DELETE /salas/{codigo}` solo `juego`, pasa a `finalizada` | `EscriturasSalaIT` · El juego cierra la sala: 204, queda finalizada sin renovar expira_en, guarda sala.finalizada y los dos tokens dan 401 · La app recibe 403 al cerrar la sala y la sala sigue activa · Una sala en esperando_app también se puede cerrar · `SalaServiceTest` · La app no puede cerrar la sala: 403 sin tocarla · Cerrar la sala la pasa a finalizada y guarda sala.finalizada con origen juego · `SalaControllerTest` · DELETE de la sala responde 204 sin cuerpo y sin token da 401 · `EscriturasSalaIT` · DELETE simultáneo con nueve descubrimientos: los que pierden reciben 401 y no dejan filas ni eventos después del cierre (B7) | ✅ |
| El cierre libera el código (otra sala con el mismo pueblo) | `EscriturasSalaIT` · Cerrar la sala libera el código: se puede crear otra sala con el mismo pueblo y la cerrada queda como historial | ✅ |
| Token inválido (`401`) tras el cierre | `EscriturasSalaIT` · El juego cierra la sala: 204, queda finalizada sin renovar expira_en, guarda sala.finalizada y los dos tokens dan 401 (estado, descubrimientos, escena y otro `DELETE`, con el token del juego y el de la app) · `ActividadServiceTest` · Bloquear una sala finalizada, expirada o inexistente responde 401 | ✅ |
| Cada escritura exitosa guarda un `evento`; también `crear` y `unirse` | `EscriturasSalaIT` · Cada escritura deja su evento en orden: sala.creada, app.unida, descubrimiento.registrado, puzzle.habilitado, escena.cambiada y sala.finalizada · Crear y unirse guardan los eventos sala.creada (origen juego) y app.unida (origen app) · `SalaServiceTest` · Crear la sala guarda el evento sala.creada con origen juego y el código en el payload · Unirse guarda el evento app.unida con origen app sobre la sala enlazada · Si unirse pierde la carrera no guarda ningún evento · `ActividadServiceTest` · Registrar renueva la sala, guarda el evento y lo publica para el WebSocket del Paso 4 · Los tipos de evento se guardan con su punto | ✅ |
| Cada escritura actualiza `ultima_actividad` y **renueva `expira_en`** con la duración de `unirse` | `EscriturasSalaIT` · Un descubrimiento, nuevo o repetido, renueva ultima_actividad y expira_en con la duración configurada · El juego cambia la escena: 200, GET estado la muestra, guarda escena.cambiada y renueva expira_en · Repetir la escena actual responde 200, renueva expira_en y no guarda otro escena.cambiada (A2) · `ActividadServiceTest` · Renovar pone ultima_actividad en ahora y expira_en en ahora más la expiración configurada | ✅ |
| El cierre guarda su evento sin renovar `expira_en` (B5) | `ActividadServiceTest` · Registrar el cierre de una sala finalizada guarda el evento y ultima_actividad, pero no renueva expira_en · `EscriturasSalaIT` · El juego cierra la sala: 204, queda finalizada sin renovar expira_en, guarda sala.finalizada y los dos tokens dan 401 | ✅ |
| Una escritura no revive una sala con `expira_en` vencido (B1) | `ActividadServiceTest` · Bloquear una sala viva cuyo expira_en ya pasó responde 401 aunque el job no la haya marcado · `EscriturasSalaIT` · Una sala con expira_en vencido que el job aún no marcó responde 401 a toda escritura y no revive | ✅ |
| Tras un descubrimiento se recalcula la habilitación: `radio` y `photo` por REST | `EscriturasSalaIT` · pescador_dialogo_completo habilita radio y GET estado lo muestra disponible · Las tres piezas enviadas a la vez, repetidas, habilitan photo una sola vez · `HabilitacionPuzzlesServiceTest` · radio se habilita con pescador_dialogo_completo y no antes · photo se habilita solo con las tres piezas, lleguen en el orden que lleguen | ✅ |
| `lookout` y `observatory` cubiertos por las reglas, a nivel de servicio | `HabilitacionPuzzlesServiceTest` · lookout pide photo resuelto y lookout_enabled; la bandera puede llegar antes o después · observatory pide coordenada_a, coordenada_b, observatory_enabled y lookout resuelto; falta uno y no se habilita · `EscriturasSalaIT` · lookout y observatory se habilitan por servicio cuando se resuelve el puzzle previo, aunque la bandera haya llegado antes · lookout_enabled que llega después de photo resuelto habilita lookout en la misma petición. `resuelto` se escribe con SQL porque `PUT puzzles` es del Paso 3b | ✅ |
| Servicio de habilitación reutilizable por 3b | `HabilitacionPuzzlesServiceTest` · Recalcular pasa a disponible los puzzles habilitados y guarda un evento puzzle.habilitado de origen servidor por cada uno · Recalcular sin requisitos nuevos no cambia nada ni guarda eventos · Solo un puzzle bloqueado pasa a disponible: los que están disponibles, en curso o resueltos no cambian. `recalcular(sala)` lee el estado completo, así que 3b solo tiene que llamarlo tras resolver un puzzle | ✅ |
| Roles y token en las rutas nuevas (`401` sin token, `403` otra sala) | `DescubrimientoControllerTest` · Sin token da 401 y con el token de otra sala da 403, sin llamar al servicio · `SalaControllerTest` · PUT escena sin token da 401 · DELETE de la sala responde 204 sin cuerpo y sin token da 401 · El token de otra sala recibe 403 en PUT escena y DELETE, sin llamar al servicio (B6) · `EscriturasSalaIT` · El token del juego de otra sala recibe 403 en PUT escena y DELETE, y la sala no cambia (B6) | ✅ |
| Tipos estrictos: número o booleano en un campo de texto da `400` (P1/P2) | `JacksonConfigTest` · Un número o un booleano en un campo de texto no se convierte a texto: falla · Un texto o un número en un campo booleano no se convierte: falla, también "true" entre comillas · Un texto o un booleano en un campo numérico no se convierte: falla · Los tipos correctos se siguen leyendo, y en un objeto libre (Map) cada valor conserva su tipo JSON · `EscriturasSalaIT` · Un número o booleano JSON donde va texto da 400 sin convertirse: clave, escena y código de pueblo | ✅ |
| `datos` con tamaño acotado, `400` con `details` si se pasa (B4) | `JsonAcotadoTest` · Los datos que caben en el límite, en bytes de su JSON compacto, son válidos; nulos también · Un byte más del límite es inválido, y los acentos cuentan como dos bytes · `DescubrimientoControllerTest` · Unos datos de más de 4096 bytes en JSON dan 400 con detalle en datos, sin llamar al servicio · `EscriturasSalaIT` · Unos datos que pasan de 4096 bytes en JSON dan 400 y no se guardan; justo en el límite responden 201 | ✅ |
| Gancho para el WebSocket del Paso 4 | `ActividadServiceTest` · Registrar renueva la sala, guarda el evento y lo publica para el WebSocket del Paso 4 (verifica el `SalaEvento` publicado) | ✅ |
| Swagger documenta las rutas nuevas | `AplicacionIT` · openApiDocumentaLosEndpointsDeSalasYElToken (ahora exige `/descubrimientos`, `/escena` y el `delete` de `/salas/{codigo}`) | ✅ |
| Documentación (`docs/api.md`, `docs/modelo-datos.md`, `README.md`, `docs/PLAN.md`) | Sin prueba: es documentación. Se actualizó en el mismo commit que cada endpoint | — |

## 4. Lista completa de pruebas

| Tipo | Clase | Prueba | Tiempo (s) | Resultado |
|---|---|---|---:|---|
| unitaria | `config.ApiHttpConfigTest` | corsRechazaOrigenNoConfigurado | 0.021 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | corsPermiteOrigenConfigurado | 0.003 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | registraUnaLineaPorPeticionSinTokens(CapturedOutput) | 0.011 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | noRegistraRutasFueraDeApi(CapturedOutput) | 0.022 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Los tipos correctos se siguen leyendo, y en un objeto libre (Map) cada valor conserva su tipo JSON | 0.014 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un texto o un número en un campo booleano no se convierte: falla, también "true" entre comillas | 0.002 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un texto o un booleano en un campo numérico no se convierte: falla | 0.001 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un número o un booleano en un campo de texto no se convierte a texto: falla | 0.002 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | rutaInexistenteDevuelve404ConCuerpoUniforme | 0.004 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | jsonMalformadoDevuelve400ConCuerpoUniforme | 0.005 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | validacionDevuelve400ConDetallePorCampo | 0.007 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | errorInesperadoDevuelve500SinDetallesInternos | 0.004 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | conflictoConservaSuCodigoYMensaje | 0.002 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Sin clave, con una clave que no es texto o con datos que no son un objeto JSON da 400 sin llamar al servicio | 0.015 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Unos datos de más de 4096 bytes en JSON dan 400 con detalle en datos, sin llamar al servicio | 0.004 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Sin token da 401 y con el token de otra sala da 403, sin llamar al servicio | 0.003 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Una clave del servidor responde 403 con el cuerpo de error uniforme | 0.003 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Repetir una clave responde 200 con el descubrimiento que ya existía | 0.004 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Una clave vacía, con mayúsculas, guiones, acentos, espacios o de más de 40 caracteres da 400 con detalle | 0.008 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Un descubrimiento nuevo responde 201 con clave, datos, origen y creado_en | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | La app recibe 403 en PUT escena y en DELETE antes de validar el cuerpo, sin llamar al servicio | 0.024 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | pueblosPasaElFiltroDeDisponibles | 0.009 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeOtraSalaDa403 | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena sin token da 401 | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | unirseNoPideTokenYDevuelveElDeLaApp | 0.005 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearResponde201ConCodigoEstadoYToken | 0.026 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | DELETE de la sala responde 204 sin cuerpo y sin token da 401 | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena sin escena o con escena vacía da 400 con detalle, sin llamar al servicio | 0.008 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDesconocidoDa401 | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearSinCodigoDa400ConDetalle | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeLaSalaDevuelveElEstadoEnSnakeCase | 0.009 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena responde 200 con escena_actual | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | El token de otra sala recibe 403 en PUT escena y DELETE, sin llamar al servicio | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearConPuebloOcupadoDa409ConCuerpoUniforme | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoSinTokenDa401 | 0.003 | ✅ pasó |
| unitaria | `dto.JsonAcotadoTest` | Los datos que caben en el límite, en bytes de su JSON compacto, son válidos; nulos también | 0.053 | ✅ pasó |
| unitaria | `dto.JsonAcotadoTest` | Un byte más del límite es inválido, y los acentos cuentan como dos bytes | 0.029 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear devuelve la sala si sigue viva | 0.047 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Renovar pone ultima_actividad en ahora y expira_en en ahora más la expiración configurada | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar el cierre de una sala finalizada guarda el evento y ultima_actividad, pero no renueva expira_en | 0.000 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Los tipos de evento se guardan con su punto: sala.creada, app.unida, descubrimiento.registrado... | 0.000 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar renueva la sala, guarda el evento y lo publica para el WebSocket del Paso 4 | 0.002 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear una sala finalizada, expirada o inexistente responde 401 | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear una sala viva cuyo expira_en ya pasó responde 401 aunque el job no la haya marcado | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Si la sala terminó entre el token y la escritura, el 401 de bloquear se propaga sin guardar nada | 0.077 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | El descubrimiento se guarda en la sala del token con la clave recibida | 0.002 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Repetir una clave devuelve el descubrimiento original sin guardar otro, sin evento y renovando la sala | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Un descubrimiento nuevo se guarda con el origen del rol, guarda su evento y luego recalcula los puzzles | 0.003 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Sin datos, el descubrimiento se guarda con un objeto vacío y origen juego si lo manda el juego | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Las cuatro claves del servidor dan 403 con cualquier rol y no tocan la sala | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | radio se habilita con pescador_dialogo_completo y no antes | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | photo se habilita solo con las tres piezas, lleguen en el orden que lleguen | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Solo un puzzle bloqueado pasa a disponible: los que están disponibles, en curso o resueltos no cambian | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Recalcular pasa a disponible los puzzles habilitados y guarda un evento puzzle.habilitado de origen servidor por cada uno | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | observatory pide coordenada_a, coordenada_b, observatory_enabled y lookout resuelto; falta uno y no se habilita | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Recalcular sin requisitos nuevos no cambia nada ni guarda eventos | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | lookout pide photo resuelto y lookout_enabled; la bandera puede llegar antes o después | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearFueraDelCatalogoDa400SinTocarSalas | 0.078 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Cambiar la escena la guarda y registra escena.cambiada con la anterior y la nueva | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | expirarVencidasUsaLaHoraDelReloj | 0.000 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | otraViolacionDeIntegridadNoSeDisfrazaDe409 | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseSinSalaVivaDa404 | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | listarPueblosMarcaOcupadosLosQueTienenSalaViva | 0.005 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseEnlazaConUnSoloUpdateYRenuevaLaExpiracion | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearConPuebloOcupadoDa409 | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Unirse guarda el evento app.unida con origen app sobre la sala enlazada | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Cerrar la sala la pasa a finalizada y guarda sala.finalizada con origen juego | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Una escena fuera de introduccion, pueblo, faro, observatorio y desenlace da 400 con la lista de válidas | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Crear la sala guarda el evento sala.creada con origen juego y el código en el payload | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Si unirse pierde la carrera no guarda ningún evento | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | estadoOrdenaLosPuzzlesYReportaDispositivosDesconectados | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Repetir la escena actual responde igual y solo renueva la sala, sin evento | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseASalaVivaQueYaTieneAppDa409 | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | La app no puede cerrar la sala: 403 sin tocarla | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | cadaSalaRecibeUnTokenDistinto | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearInsertaLosCuatroPuzzlesBloqueados | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearNormalizaElCodigoYGuardaSoloElHashDelToken | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | autenticarDistingueElRolPorElHash | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | autenticarConTokenDesconocidoNoDaCredencial | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | La app no puede cambiar la escena: 403 sin tocar la sala | 0.001 | ✅ pasó |
| integración | `AplicacionIT` | rutaInexistenteBajoSalasDa404SinPedirToken | 0.072 | ✅ pasó |
| integración | `AplicacionIT` | elJobDeExpiracionEstaProgramadoCadaMinuto | 0.003 | ✅ pasó |
| integración | `AplicacionIT` | openApiDocumentaLosEndpointsDeSalasYElToken | 0.174 | ✅ pasó |
| integración | `AplicacionIT` | swaggerUiYOpenApiEstanDisponibles | 0.009 | ✅ pasó |
| integración | `AplicacionIT` | healthResponde200ConEstadoUp | 0.013 | ✅ pasó |
| integración | `CatalogoPueblosIT` | flywayAplicaV2YLosCodigosEstanEnLaTabla | 0.005 | ✅ pasó |
| integración | `CatalogoPueblosIT` | seedDeclara101CodigosValidosYSinDuplicados | 0.003 | ✅ pasó |
| integración | `EscriturasSalaIT` | pescador_dialogo_completo habilita radio y GET estado lo muestra disponible | 0.046 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una sala en esperando_app también se puede cerrar | 0.023 | ✅ pasó |
| integración | `EscriturasSalaIT` | El token del juego de otra sala recibe 403 en PUT escena y DELETE, y la sala no cambia | 0.026 | ✅ pasó |
| integración | `EscriturasSalaIT` | lookout_enabled que llega después de photo resuelto habilita lookout en la misma petición | 0.023 | ✅ pasó |
| integración | `EscriturasSalaIT` | Diez peticiones simultáneas con la misma clave dan un 201 y nueve 200, una sola fila y un solo evento | 0.040 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una escena inválida, vacía, de otro tipo o ausente da 400 y no cambia nada | 0.040 | ✅ pasó |
| integración | `EscriturasSalaIT` | El juego cierra la sala: 204, queda finalizada sin renovar expira_en, guarda sala.finalizada y los dos tokens dan 401 | 0.048 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un descubrimiento, nuevo o repetido, renueva ultima_actividad y expira_en con la duración configurada | 0.032 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una sala con expira_en vencido que el job aún no marcó responde 401 a toda escritura y no revive | 0.026 | ✅ pasó |
| integración | `EscriturasSalaIT` | Cerrar la sala libera el código: se puede crear otra sala con el mismo pueblo y la cerrada queda como historial | 0.032 | ✅ pasó |
| integración | `EscriturasSalaIT` | Crear y unirse guardan los eventos sala.creada (origen juego) y app.unida (origen app) | 0.015 | ✅ pasó |
| integración | `EscriturasSalaIT` | Cada escritura deja su evento en orden: sala.creada, app.unida, descubrimiento.registrado, puzzle.habilitado, escena.cambiada y sala.finalizada | 0.040 | ✅ pasó |
| integración | `EscriturasSalaIT` | El juego cambia la escena: 200, GET estado la muestra, guarda escena.cambiada y renueva expira_en | 0.022 | ✅ pasó |
| integración | `EscriturasSalaIT` | Repetir la escena actual responde 200, renueva expira_en y no guarda otro escena.cambiada | 0.021 | ✅ pasó |
| integración | `EscriturasSalaIT` | Repetir una clave con otros datos responde 200 con los datos originales y no guarda otro evento | 0.024 | ✅ pasó |
| integración | `EscriturasSalaIT` | La app recibe 403 al cerrar la sala y la sala sigue activa | 0.021 | ✅ pasó |
| integración | `EscriturasSalaIT` | lookout y observatory se habilitan por servicio cuando se resuelve el puzzle previo, aunque la bandera haya llegado antes | 0.037 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un descubrimiento nuevo responde 201, aparece en GET estado y guarda descubrimiento.registrado con el origen del rol | 0.028 | ✅ pasó |
| integración | `EscriturasSalaIT` | Las tres piezas enviadas a la vez, repetidas, habilitan photo una sola vez | 0.035 | ✅ pasó |
| integración | `EscriturasSalaIT` | Las cuatro claves del servidor dan 403 al juego y a la app y no se guardan | 0.034 | ✅ pasó |
| integración | `EscriturasSalaIT` | Unos datos que pasan de 4096 bytes en JSON dan 400 y no se guardan; justo en el límite responden 201 | 0.022 | ✅ pasó |
| integración | `EscriturasSalaIT` | La app recibe 403 al cambiar la escena, con cuerpo válido, inválido o vacío, y la escena no cambia | 0.019 | ✅ pasó |
| integración | `EscriturasSalaIT` | DELETE simultáneo con nueve descubrimientos: los que pierden reciben 401 y no dejan filas ni eventos después del cierre | 0.077 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un número o booleano JSON donde va texto da 400 sin convertirse: clave, escena y código de pueblo | 0.032 | ✅ pasó |
| integración | `EsquemaIT` | salasTerminadasNoBloqueanUnaNuevaSalaViva | 0.005 | ✅ pasó |
| integración | `EsquemaIT` | indiceEventosPorSalaYTiempoExiste | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | noPuedeHaberDosSalasVivasConElMismoCodigo | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | catalogoRechazaCodigosConMinusculasAcentosOEnie | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaCodigoFueraDelCatalogo | 0.002 | ✅ pasó |
| integración | `EsquemaIT` | flywayAplicaV1YCreaLasCincoTablas | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | alFinalizarUnaSalaElCodigoSePuedeReclamarDeNuevo | 0.003 | ✅ pasó |
| integración | `EsquemaIT` | carreraPorElMismoPuebloDejaUnaSolaSalaViva | 0.006 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaEstadoInvalido | 0.002 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | Tras V3, el CHECK de puzzles_estado rechaza fotografia, mirador y observatorio y acepta los ids nuevos | 0.298 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | V3 renombra a inglés los puzzles de una sala creada con V2, conserva su estado y sus datos, y ese esquema rechaza fotografia | 0.162 | ✅ pasó |
| integración | `SalaIT` | crearDevuelveElTokenUnaVezYGuardaSoloSuHash | 0.053 | ✅ pasó |
| integración | `SalaIT` | carreraDeAppsPorUnirseDaUn200YSiete409 | 0.039 | ✅ pasó |
| integración | `SalaIT` | unirseSinSalaVivaDa404 | 0.009 | ✅ pasó |
| integración | `SalaIT` | flujoCrearUnirseEstado | 0.049 | ✅ pasó |
| integración | `SalaIT` | elJobNoTocaSalasFinalizadas | 0.020 | ✅ pasó |
| integración | `SalaIT` | unirseAceptaMinusculas | 0.017 | ✅ pasó |
| integración | `SalaIT` | pueblosReflejaLasSalasVivas | 0.037 | ✅ pasó |
| integración | `SalaIT` | estadoExigeElTokenDeEsaSala | 0.042 | ✅ pasó |
| integración | `SalaIT` | salaVencidaExpiraLiberaElNombreYSeConservaComoHistorial | 0.035 | ✅ pasó |
| integración | `SalaIT` | unirseRenuevaLaExpiracion | 0.021 | ✅ pasó |
| integración | `SalaIT` | carreraPorElMismoPuebloDaUn201YSiete409 | 0.018 | ✅ pasó |
| integración | `SalaIT` | crearFueraDelCatalogoDa400 | 0.005 | ✅ pasó |
| integración | `SalaIT` | crearAceptaMinusculasYEspacios | 0.014 | ✅ pasó |
| integración | `SalaIT` | crearInsertaLosCuatroPuzzlesBloqueados | 0.013 | ✅ pasó |
| integración | `SalaIT` | unirseDosVecesDa409 | 0.021 | ✅ pasó |
| integración | `SalaIT` | salaQueAunNoVenceSigueViva | 0.013 | ✅ pasó |
| integración | `repository.ModeloIT` | guardaYLeeSalaPuzzlesYDescubrimientosConJsonb | 0.089 | ✅ pasó |
| integración | `ws.WebSocketIT` | elServidorCierraLaConexionCon1008MientrasNoHayAutenticacion | 0.025 | ✅ pasó |

## 5. Salida de `./mvnw clean verify`

Últimas líneas de Maven sobre `ac37926` (sin los logs de Spring; rutas recortadas al repo):

```text
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.024 s -- in com.vestigia.config.JacksonConfigTest
[INFO] Running com.vestigia.controller.SalaControllerTest
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.246 s -- in com.vestigia.controller.SalaControllerTest
[INFO] Running com.vestigia.controller.ApiExceptionHandlerTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.125 s -- in com.vestigia.controller.ApiExceptionHandlerTest
[INFO] Running com.vestigia.controller.DescubrimientoControllerTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.135 s -- in com.vestigia.controller.DescubrimientoControllerTest
[INFO] Running com.vestigia.service.DescubrimientoServiceTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.085 s -- in com.vestigia.service.DescubrimientoServiceTest
[INFO] Running com.vestigia.service.SalaServiceTest
[INFO] Tests run: 23, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.109 s -- in com.vestigia.service.SalaServiceTest
[INFO] Running com.vestigia.service.HabilitacionPuzzlesServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.006 s -- in com.vestigia.service.HabilitacionPuzzlesServiceTest
[INFO] Running com.vestigia.service.ActividadServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.053 s -- in com.vestigia.service.ActividadServiceTest
[INFO] Results:
[INFO] Tests run: 80, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- jar:3.5.1:jar (default-jar) @ vestigia ---
[INFO] Building jar: target/vestigia-0.0.1-SNAPSHOT.jar
[INFO] --- spring-boot:4.1.1:repackage (repackage) @ vestigia ---
[INFO] Loaded 50 auto-discovered prefixes for remote repository ow2-snapshot (prefixes-ow2-snapshot.txt)
[INFO] Replacing main artifact target/vestigia-0.0.1-SNAPSHOT.jar with repackaged archive, adding nested dependencies in BOOT-INF/.
[INFO] The original artifact has been renamed to target/vestigia-0.0.1-SNAPSHOT.jar.original
[INFO] --- failsafe:3.5.6:integration-test (default) @ vestigia ---
[INFO] Running com.vestigia.MigracionIdsPuzzlesIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.828 s -- in com.vestigia.MigracionIdsPuzzlesIT
[INFO] Running com.vestigia.AplicacionIT
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.300 s -- in com.vestigia.AplicacionIT
[INFO] Running com.vestigia.repository.ModeloIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.096 s -- in com.vestigia.repository.ModeloIT
[INFO] Running com.vestigia.EsquemaIT
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.042 s -- in com.vestigia.EsquemaIT
[INFO] Running com.vestigia.CatalogoPueblosIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.012 s -- in com.vestigia.CatalogoPueblosIT
[INFO] Running com.vestigia.SalaIT
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.420 s -- in com.vestigia.SalaIT
[INFO] Running com.vestigia.EscriturasSalaIT
[INFO] Tests run: 24, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.776 s -- in com.vestigia.EscriturasSalaIT
[INFO] Running com.vestigia.ws.WebSocketIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.035 s -- in com.vestigia.ws.WebSocketIT
[INFO] Results:
[INFO] Tests run: 60, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- failsafe:3.5.6:verify (default) @ vestigia ---
[INFO] BUILD SUCCESS
[INFO] Total time:  13.640 s
[INFO] Finished at: 2026-11-05T23:58:08-06:00
```

## 6. Pruebas adversariales de la API

El probador adversarial fue **gemini-3.8-flash** (Antigravity, familia Google). Sustituyó a gpt-oss-120b-medium, el probador planeado, que falló con un error del proveedor y se retiró de la corrida. Levantó la app en el puerto 18080 con su propio Postgres (`vestigia-pg-test-3a`) y borró los dos al terminar. Corrió **67 escenarios: 65 ✅ y 2 ❌** (P1 y P2, la clave número y la clave booleana), sobre `ca4a98f`. Esta es su tabla resumida. Los tokens se escriben como `<token>`.

| Escenario | Petición | Esperado | Real | |
|---|---|---|---|:-:|
| Sin token / sin `Bearer` / token inválido | `POST .../descubrimientos`, `PUT .../escena`, `DELETE /salas/BACALAR` | 401 | 401 | ✅ |
| Token de otra sala | Las tres rutas con `Bearer <token_otra_sala>` | 403 | 403 | ✅ |
| Rol app en escena y `DELETE` | `Bearer <token_app>` | 403 | 403 | ✅ |
| Las 4 claves del servidor | `{"clave":"coordenada_a"}` etc., juego y app | 403 | 403 | ✅ |
| Clave con mayúsculas, guion, espacio, acento, `#`, 41 caracteres, vacía, `null`, ausente | `POST .../descubrimientos` | 400 | 400 | ✅ |
| Clave de 40 caracteres | `POST .../descubrimientos` | 201 | 201 | ✅ |
| Clave número `12345` / booleano `true` (P1/P2) | `POST .../descubrimientos` | 400 | 201 (Jackson la convertía a texto) | ❌ falló, se corrigió en el commit `6b3754f` |
| `datos` no objeto (`[1,2]`, `"x"`, `1`, `true`) | `POST .../descubrimientos` | 400 | 400 | ✅ |
| JSON malformado / cuerpo vacío | `POST .../descubrimientos` | 400 | 400 | ✅ |
| `datos: null` y claves extra | `POST .../descubrimientos` | 201 | 201 (`datos: {}`, la clave extra se ignora) | ✅ |
| Repetida con otros datos | Misma clave, otros `datos` | 200 con los datos originales | 200 | ✅ |
| 10 descubrimientos iguales en paralelo | 10× `{"clave":"llave_dorada"}` | 1×201, 9×200, 1 fila | 1×201, 9×200, 1 fila (SQL) | ✅ |
| `pieza_1..3` en paralelo | 3× `POST` y `GET estado` | `photo` disponible | `photo` disponible | ✅ |
| `pescador_dialogo_completo` | `POST` y `GET estado` | `radio` disponible | `radio` disponible | ✅ |
| Escena `FARO`, `bosque`, en blanco, `123`, `true`, `null` | `PUT .../escena` | 400 | 400 | ✅ |
| Escena válida y repetida | `{"escena":"faro"}` ×2 | 200 | 200 `{"escena_actual":"faro"}` | ✅ |
| Cierre | `DELETE` con el juego | 204 | 204 | ✅ |
| Tras el cierre: `GET estado`, escena, descubrimiento, `DELETE` | Los dos tokens | 401 | 401 | ✅ |
| Pueblo liberado | `POST /salas {"codigo":"BACALAR"}` | 201 | 201 | ✅ |
| Eventos en la base | `SELECT DISTINCT tipo FROM eventos` | Los tipos del 3a | `sala.creada`, `app.unida`, `descubrimiento.registrado`, `puzzle.habilitado`, `escena.cambiada`, `sala.finalizada` | ✅ |

Después de `6b3754f`, `{"clave": 12345}` y `{"clave": true}` responden `400 JSON malformado o ilegible` y no se guardan (`EscriturasSalaIT` · Un número o booleano JSON donde va texto da 400 sin convertirse: clave, escena y código de pueblo). `{"codigo": 999}` en `POST /salas` sigue dando `400`.

## 7. Revisiones independientes

| Revisor | Modelo y familia | Sustitución | Veredicto |
|---|---|---|---|
| A | **gemini-4-argon** (Antigravity, familia Google) | Ninguna | Aprobado, 2 hallazgos menores |
| B | **claude-opus-5-5** (Anthropic) | Sustituyó a claude-fable-5-1, que falló por el límite de uso de la API | Aprobable, sin bloqueantes, 8 hallazgos |
| Probador | **gemini-3.8-flash** (Antigravity, familia Google) | Sustituyó a gpt-oss-120b-medium, que falló con un error del proveedor | 2 escenarios ❌ de 67 (sección 6) |

El revisor B es de la misma familia que el constructor (Claude). Las revisiones de otra familia son las del revisor A y el probador. El triage lo hizo el orquestador. Las correcciones las hizo el constructor (claude-opus-5-5).

| # | Hallazgo | Decisión | Commit |
|---|---|---|---|
| A1 | `SalaService.unirse` podría lanzar `NoSuchElementException` si la sala se cierra entre `unirApp` y la lectura | Rechazado. El `UPDATE` de `unirApp` deja la fila bloqueada hasta el commit, la lectura ocurre en la misma transacción y ve la sala `activa`, y un `DELETE` concurrente espera ese bloqueo. La carrera no puede ocurrir | — |
| A2 | Faltaba una prueba HTTP de que repetir la escena renueva `expira_en` y no duplica `escena.cambiada` | Aceptado. Prueba nueva en `EscriturasSalaIT` | `3ae24cf` |
| B1 | `bloquear` no miraba `expira_en`: una escritura sobre una sala vencida que el job aún no marcó la revivía | Aceptado. Una sala viva con `expira_en` en el pasado cuenta como terminada y la escritura responde `401` sin tocarla. Documentado en `api.md` y `modelo-datos.md` | `7a1de93` |
| B2 | `@Valid` corría antes de comprobar el rol: la app recibía `400` o `403` en `PUT escena` según el cuerpo | Aceptado. `@SoloJuego` hace que `TokenInterceptor` responda `403` antes de leer el cuerpo, en escena y en cierre. El servicio conserva su comprobación para el WebSocket del Paso 4 | `28f9ea3` |
| B3 | La app puede mandar `pieza_*`, `pescador_dialogo_completo`, `lookout_enabled` y `observatory_enabled`, que son del juego | No se cambia: decisión de Raúl. El contrato vigente dice "juego o app". Queda como pendiente en `docs/PLAN.md` y en la sección 8 | — |
| B4 | `datos` no tenía límite de tamaño | Aceptado. `@JsonAcotado` limita a `vestigia.descubrimientos.datos-max-bytes` (4096 bytes del JSON compacto en UTF-8) y responde `400` con `details` en `datos` | `3cedff9` |
| B5 | `finalizar` renovaba `expira_en` | Aceptado. `renovar` solo mueve `expira_en` si la sala sigue viva. El cierre guarda su evento y `ultima_actividad` | `cc8d478` |
| B6 | Faltaban pruebas del token de otra sala en `PUT escena` y `DELETE` | Aceptado. Pruebas en `SalaControllerTest` y `EscriturasSalaIT` | `b156563` |
| B7 | Faltaba una prueba de integración de `DELETE` simultáneo con varios descubrimientos | Aceptado. Prueba en tres salas. En la corrida de `ac37926` hubo mezcla real (en una sala ganaron 8 y perdió 1) y las filas coinciden con los `201` | `ac37926` |
| B8 | `CLAUDE.md` §6 dice que toda escritura exitosa guarda un evento; las repetidas no | No se toca el código. Lo anota el orquestador en su reporte, porque `CLAUDE.md` es local y no va en el PR | — |
| P1/P2 | `{"clave": 12345}` y `{"clave": true}` respondían `201` | Aceptado como importante. `JacksonConfig` desactiva `MapperFeature.ALLOW_COERCION_OF_SCALARS` y pone `CoercionAction.Fail` para número o booleano en texto, texto o número en booleano y texto o booleano en número. Pruebas en `JacksonConfigTest` y `EscriturasSalaIT` | `6b3754f` |

## 8. Lo que no se probó y por qué

| Qué | Por qué |
|---|---|
| Que `lookout` y `observatory` pasen a `disponible` por REST de punta a punta | Necesitan un puzzle `resuelto`, y `PUT puzzles` es del Paso 3b. Se probaron con el servicio y con `resuelto` escrito por SQL (sección 3) |
| Emisión por WebSocket | Es del Paso 4. Aquí solo se publica el `SalaEvento` de Spring dentro de la transacción, y `ActividadServiceTest` comprueba que se publica |
| Qué rol puede mandar cada clave (B3) | **Decisión pendiente de Raúl.** Hoy la app puede registrar claves que en el diseño manda el juego (`pieza_*`, `pescador_dialogo_completo`, `lookout_enabled`, `observatory_enabled`) y así habilitar puzzles sin pasar por el juego. El contrato vigente dice "juego o app", así que no se restringió. Está en los pendientes de `docs/PLAN.md` |
| Que el `GET estado` de una sala con `expira_en` vencido responda `401` | B1 solo cubre escrituras. La lectura sigue funcionando hasta que corre el job, como ya decía O5 |
| `unirse` a una sala vencida que el job aún no marcó | El `UPDATE` de `unirApp` no mira `expira_en`, así que una app podría unirse en ese minuto y renovar la sala. Es el mismo hueco de B1 en un endpoint del Paso 2. No se tocó porque no estaba en el triage |

Prueba manual con la app real sobre `ca4a98f`, antes de las correcciones de la sección 7 (los tokens no aparecen en el log; se comprobó con `grep`):

| Petición | Respuesta |
|---|---|
| `POST /descubrimientos` `pieza_1` con `datos`, token del juego | `201`, `origen: "juego"` |
| Lo mismo con otros `datos` y el token de la app | `200`, datos y origen originales |
| `{"clave":"pieza_3","origen":"servidor"}` | `201`, `origen: "juego"` (el campo extra se ignora) |
| `{"clave":"coordenada_a"}`, token de la app | `403` con el mensaje de clave del servidor |
| Cuerpo vacío / `Content-Type: text/plain` / `GET /descubrimientos` | `400` / `415` / `405` |
| `PUT /escena` con la app / `"bosque"` / `"faro"` | `403` / `400` con la lista / `200` |
| `pieza_1..3` registradas | `GET estado` muestra `photo: disponible` y la tabla `eventos` tiene `puzzle.habilitado` de origen `servidor` |
| `DELETE` con la app / con el juego / otra vez | `403` / `204` / `401` |
| `GET estado` con el token de la app tras el cierre | `401` |
| `POST /salas` con el mismo pueblo | `201`, y la cerrada queda `finalizada` |
