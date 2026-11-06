# Paso 3a · Descubrimientos, escena y cierre

Rama `feature/descubrimientos-escena-cierre`, comparada contra `feature/ids-puzzles-ingles`. Agrega `POST /salas/{codigo}/descubrimientos`, `PUT /salas/{codigo}/escena` y `DELETE /salas/{codigo}`. También agrega la bitácora de eventos, la renovación de `expira_en` en cada escritura y el servicio de habilitación de puzzles que reutilizará el Paso 3b. La API sigue en `/api/v1`. No hay migraciones nuevas.

## 1. Veredicto

✅ 123 pruebas, 0 fallos, 0 errores, 0 omitidas.

| Total | Unitarias | Integración | Fallos | Errores | Omitidas | Fecha | Commit | Rama |
|---:|---:|---:|---:|---:|---:|---|---|---|
| 123 | 69 | 54 | 0 | 0 | 0 | 2026-11-05 23:37 | `ca4a98f` | `feature/descubrimientos-escena-cierre` |

Las cifras son de `./mvnw clean verify` sobre `ca4a98f`, el último commit de código. El commit siguiente solo agrega esta evidencia, `docs/pruebas/README.md` y `docs/PLAN.md`.

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
| Clave `^[a-z0-9_]{1,40}$` y `datos` objeto JSON opcional | `DescubrimientoControllerTest` · Una clave vacía, con mayúsculas, guiones, acentos, espacios o de más de 40 caracteres da 400 con detalle (y 40 caracteres pasa) · Sin clave o con datos que no son un objeto JSON da 400 sin llamar al servicio · `DescubrimientoServiceTest` · Sin datos, el descubrimiento se guarda con un objeto vacío y origen juego si lo manda el juego | ✅ |
| Las cuatro claves del servidor dan `403` | `EscriturasSalaIT` · Las cuatro claves del servidor dan 403 al juego y a la app y no se guardan · `DescubrimientoServiceTest` · Las cuatro claves del servidor dan 403 con cualquier rol y no tocan la sala · `DescubrimientoControllerTest` · Una clave del servidor responde 403 con el cuerpo de error uniforme | ✅ |
| `PUT /escena` solo rol `juego` (`403` para `app`) | `EscriturasSalaIT` · La app recibe 403 al cambiar la escena y la escena no cambia · `SalaServiceTest` · La app no puede cambiar la escena: 403 sin tocar la sala | ✅ |
| Escenas válidas `introduccion`, `pueblo`, `faro`, `observatorio`, `desenlace`; otro valor `400` | `EscriturasSalaIT` · El juego cambia la escena: 200, GET estado la muestra, guarda escena.cambiada y renueva expira_en · Una escena inválida, vacía, de otro tipo o ausente da 400 y no cambia nada · `SalaServiceTest` · Una escena fuera de introduccion, pueblo, faro, observatorio y desenlace da 400 con la lista de válidas · Cambiar la escena la guarda y registra escena.cambiada con la anterior y la nueva · Repetir la escena actual responde igual y solo renueva la sala, sin evento · `SalaControllerTest` · PUT escena responde 200 con escena_actual · PUT escena sin escena o con escena vacía da 400 con detalle, sin llamar al servicio | ✅ |
| `DELETE /salas/{codigo}` solo `juego`, pasa a `finalizada` | `EscriturasSalaIT` · El juego cierra la sala: 204, queda finalizada, guarda sala.finalizada y los dos tokens dan 401 · La app recibe 403 al cerrar la sala y la sala sigue activa · Una sala en esperando_app también se puede cerrar · `SalaServiceTest` · La app no puede cerrar la sala: 403 sin tocarla · Cerrar la sala la pasa a finalizada y guarda sala.finalizada con origen juego · `SalaControllerTest` · DELETE de la sala responde 204 sin cuerpo y sin token da 401 | ✅ |
| El cierre libera el código (otra sala con el mismo pueblo) | `EscriturasSalaIT` · Cerrar la sala libera el código: se puede crear otra sala con el mismo pueblo y la cerrada queda como historial | ✅ |
| Token inválido (`401`) tras el cierre | `EscriturasSalaIT` · El juego cierra la sala: 204, queda finalizada, guarda sala.finalizada y los dos tokens dan 401 (estado, descubrimientos, escena y otro `DELETE`, con el token del juego y el de la app) · `ActividadServiceTest` · Bloquear una sala finalizada, expirada o inexistente responde 401 | ✅ |
| Cada escritura exitosa guarda un `evento`; también `crear` y `unirse` | `EscriturasSalaIT` · Cada escritura deja su evento en orden: sala.creada, app.unida, descubrimiento.registrado, puzzle.habilitado, escena.cambiada y sala.finalizada · Crear y unirse guardan los eventos sala.creada (origen juego) y app.unida (origen app) · `SalaServiceTest` · Crear la sala guarda el evento sala.creada con origen juego y el código en el payload · Unirse guarda el evento app.unida con origen app sobre la sala enlazada · Si unirse pierde la carrera no guarda ningún evento · `ActividadServiceTest` · Registrar renueva la sala, guarda el evento y lo publica para el WebSocket del Paso 4 · Los tipos de evento se guardan con su punto | ✅ |
| Cada escritura actualiza `ultima_actividad` y **renueva `expira_en`** con la duración de `unirse` | `EscriturasSalaIT` · Un descubrimiento, nuevo o repetido, renueva ultima_actividad y expira_en con la duración configurada · El juego cambia la escena: 200, GET estado la muestra, guarda escena.cambiada y renueva expira_en · `ActividadServiceTest` · Renovar pone ultima_actividad en ahora y expira_en en ahora más la expiración configurada | ✅ |
| Tras un descubrimiento se recalcula la habilitación: `radio` y `photo` por REST | `EscriturasSalaIT` · pescador_dialogo_completo habilita radio y GET estado lo muestra disponible · Las tres piezas enviadas a la vez, repetidas, habilitan photo una sola vez · `HabilitacionPuzzlesServiceTest` · radio se habilita con pescador_dialogo_completo y no antes · photo se habilita solo con las tres piezas, lleguen en el orden que lleguen | ✅ |
| `lookout` y `observatory` cubiertos por las reglas, a nivel de servicio | `HabilitacionPuzzlesServiceTest` · lookout pide photo resuelto y lookout_enabled; la bandera puede llegar antes o después · observatory pide coordenada_a, coordenada_b, observatory_enabled y lookout resuelto; falta uno y no se habilita · `EscriturasSalaIT` · lookout y observatory se habilitan por servicio cuando se resuelve el puzzle previo, aunque la bandera haya llegado antes · lookout_enabled que llega después de photo resuelto habilita lookout en la misma petición. `resuelto` se escribe con SQL porque `PUT puzzles` es del Paso 3b | ✅ |
| Servicio de habilitación reutilizable por 3b | `HabilitacionPuzzlesServiceTest` · Recalcular pasa a disponible los puzzles habilitados y guarda un evento puzzle.habilitado de origen servidor por cada uno · Recalcular sin requisitos nuevos no cambia nada ni guarda eventos · Solo un puzzle bloqueado pasa a disponible: los que están disponibles, en curso o resueltos no cambian. `recalcular(sala)` lee el estado completo, así que 3b solo tiene que llamarlo tras resolver un puzzle | ✅ |
| Roles y token en las rutas nuevas (`401` sin token, `403` otra sala) | `DescubrimientoControllerTest` · Sin token da 401 y con el token de otra sala da 403, sin llamar al servicio · `SalaControllerTest` · PUT escena sin token da 401 · DELETE de la sala responde 204 sin cuerpo y sin token da 401 | ✅ |
| Gancho para el WebSocket del Paso 4 | `ActividadServiceTest` · Registrar renueva la sala, guarda el evento y lo publica para el WebSocket del Paso 4 (verifica el `SalaEvento` publicado) | ✅ |
| Swagger documenta las rutas nuevas | `AplicacionIT` · openApiDocumentaLosEndpointsDeSalasYElToken (ahora exige `/descubrimientos`, `/escena` y el `delete` de `/salas/{codigo}`) | ✅ |
| Documentación (`docs/api.md`, `docs/modelo-datos.md`, `README.md`, `docs/PLAN.md`) | Sin prueba: es documentación. Se actualizó en el mismo commit que cada endpoint | — |

## 4. Lista completa de pruebas

| Tipo | Clase | Prueba | Tiempo (s) | Resultado |
|---|---|---|---:|---|
| unitaria | `config.ApiHttpConfigTest` | corsRechazaOrigenNoConfigurado | 0.029 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | corsPermiteOrigenConfigurado | 0.004 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | registraUnaLineaPorPeticionSinTokens(CapturedOutput) | 0.030 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | noRegistraRutasFueraDeApi(CapturedOutput) | 0.034 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | rutaInexistenteDevuelve404ConCuerpoUniforme | 0.005 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | jsonMalformadoDevuelve400ConCuerpoUniforme | 0.006 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | validacionDevuelve400ConDetallePorCampo | 0.008 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | errorInesperadoDevuelve500SinDetallesInternos | 0.005 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | conflictoConservaSuCodigoYMensaje | 0.002 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Sin clave o con datos que no son un objeto JSON da 400 sin llamar al servicio | 0.014 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Sin token da 401 y con el token de otra sala da 403, sin llamar al servicio | 0.004 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Una clave del servidor responde 403 con el cuerpo de error uniforme | 0.003 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Repetir una clave responde 200 con el descubrimiento que ya existía | 0.004 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Una clave vacía, con mayúsculas, guiones, acentos, espacios o de más de 40 caracteres da 400 con detalle | 0.008 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Un descubrimiento nuevo responde 201 con clave, datos, origen y creado_en | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | pueblosPasaElFiltroDeDisponibles | 0.030 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeOtraSalaDa403 | 0.006 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena sin token da 401 | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | unirseNoPideTokenYDevuelveElDeLaApp | 0.005 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearResponde201ConCodigoEstadoYToken | 0.034 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | DELETE de la sala responde 204 sin cuerpo y sin token da 401 | 0.004 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena sin escena o con escena vacía da 400 con detalle, sin llamar al servicio | 0.009 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDesconocidoDa401 | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearSinCodigoDa400ConDetalle | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeLaSalaDevuelveElEstadoEnSnakeCase | 0.010 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena responde 200 con escena_actual | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearConPuebloOcupadoDa409ConCuerpoUniforme | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoSinTokenDa401 | 0.002 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear devuelve la sala si sigue viva | 0.046 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Renovar pone ultima_actividad en ahora y expira_en en ahora más la expiración configurada | 0.000 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Los tipos de evento se guardan con su punto: sala.creada, app.unida, descubrimiento.registrado... | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar renueva la sala, guarda el evento y lo publica para el WebSocket del Paso 4 | 0.002 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear una sala finalizada, expirada o inexistente responde 401 | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Si la sala terminó entre el token y la escritura, el 401 de bloquear se propaga sin guardar nada | 0.111 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | El descubrimiento se guarda en la sala del token con la clave recibida | 0.003 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Repetir una clave devuelve el descubrimiento original sin guardar otro, sin evento y renovando la sala | 0.003 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Un descubrimiento nuevo se guarda con el origen del rol, guarda su evento y luego recalcula los puzzles | 0.002 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Sin datos, el descubrimiento se guarda con un objeto vacío y origen juego si lo manda el juego | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Las cuatro claves del servidor dan 403 con cualquier rol y no tocan la sala | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | radio se habilita con pescador_dialogo_completo y no antes | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | photo se habilita solo con las tres piezas, lleguen en el orden que lleguen | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Solo un puzzle bloqueado pasa a disponible: los que están disponibles, en curso o resueltos no cambian | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Recalcular pasa a disponible los puzzles habilitados y guarda un evento puzzle.habilitado de origen servidor por cada uno | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | observatory pide coordenada_a, coordenada_b, observatory_enabled y lookout resuelto; falta uno y no se habilita | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Recalcular sin requisitos nuevos no cambia nada ni guarda eventos | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | lookout pide photo resuelto y lookout_enabled; la bandera puede llegar antes o después | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearFueraDelCatalogoDa400SinTocarSalas | 0.083 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Cambiar la escena la guarda y registra escena.cambiada con la anterior y la nueva | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | expirarVencidasUsaLaHoraDelReloj | 0.001 | ✅ pasó |
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
| unitaria | `service.SalaServiceTest` | La app no puede cambiar la escena: 403 sin tocar la sala | 0.000 | ✅ pasó |
| integración | `AplicacionIT` | rutaInexistenteBajoSalasDa404SinPedirToken | 0.070 | ✅ pasó |
| integración | `AplicacionIT` | elJobDeExpiracionEstaProgramadoCadaMinuto | 0.002 | ✅ pasó |
| integración | `AplicacionIT` | openApiDocumentaLosEndpointsDeSalasYElToken | 0.170 | ✅ pasó |
| integración | `AplicacionIT` | swaggerUiYOpenApiEstanDisponibles | 0.010 | ✅ pasó |
| integración | `AplicacionIT` | healthResponde200ConEstadoUp | 0.013 | ✅ pasó |
| integración | `CatalogoPueblosIT` | flywayAplicaV2YLosCodigosEstanEnLaTabla | 0.006 | ✅ pasó |
| integración | `CatalogoPueblosIT` | seedDeclara101CodigosValidosYSinDuplicados | 0.002 | ✅ pasó |
| integración | `EscriturasSalaIT` | pescador_dialogo_completo habilita radio y GET estado lo muestra disponible | 0.048 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una sala en esperando_app también se puede cerrar | 0.026 | ✅ pasó |
| integración | `EscriturasSalaIT` | lookout_enabled que llega después de photo resuelto habilita lookout en la misma petición | 0.028 | ✅ pasó |
| integración | `EscriturasSalaIT` | Diez peticiones simultáneas con la misma clave dan un 201 y nueve 200, una sola fila y un solo evento | 0.046 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una escena inválida, vacía, de otro tipo o ausente da 400 y no cambia nada | 0.042 | ✅ pasó |
| integración | `EscriturasSalaIT` | El juego cierra la sala: 204, queda finalizada, guarda sala.finalizada y los dos tokens dan 401 | 0.050 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un descubrimiento, nuevo o repetido, renueva ultima_actividad y expira_en con la duración configurada | 0.045 | ✅ pasó |
| integración | `EscriturasSalaIT` | Cerrar la sala libera el código: se puede crear otra sala con el mismo pueblo y la cerrada queda como historial | 0.036 | ✅ pasó |
| integración | `EscriturasSalaIT` | Crear y unirse guardan los eventos sala.creada (origen juego) y app.unida (origen app) | 0.016 | ✅ pasó |
| integración | `EscriturasSalaIT` | Cada escritura deja su evento en orden: sala.creada, app.unida, descubrimiento.registrado, puzzle.habilitado, escena.cambiada y sala.finalizada | 0.037 | ✅ pasó |
| integración | `EscriturasSalaIT` | El juego cambia la escena: 200, GET estado la muestra, guarda escena.cambiada y renueva expira_en | 0.025 | ✅ pasó |
| integración | `EscriturasSalaIT` | Repetir una clave con otros datos responde 200 con los datos originales y no guarda otro evento | 0.028 | ✅ pasó |
| integración | `EscriturasSalaIT` | La app recibe 403 al cerrar la sala y la sala sigue activa | 0.023 | ✅ pasó |
| integración | `EscriturasSalaIT` | lookout y observatory se habilitan por servicio cuando se resuelve el puzzle previo, aunque la bandera haya llegado antes | 0.040 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un descubrimiento nuevo responde 201, aparece en GET estado y guarda descubrimiento.registrado con el origen del rol | 0.028 | ✅ pasó |
| integración | `EscriturasSalaIT` | Las tres piezas enviadas a la vez, repetidas, habilitan photo una sola vez | 0.038 | ✅ pasó |
| integración | `EscriturasSalaIT` | Las cuatro claves del servidor dan 403 al juego y a la app y no se guardan | 0.036 | ✅ pasó |
| integración | `EscriturasSalaIT` | La app recibe 403 al cambiar la escena y la escena no cambia | 0.018 | ✅ pasó |
| integración | `EsquemaIT` | salasTerminadasNoBloqueanUnaNuevaSalaViva | 0.006 | ✅ pasó |
| integración | `EsquemaIT` | indiceEventosPorSalaYTiempoExiste | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | noPuedeHaberDosSalasVivasConElMismoCodigo | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | catalogoRechazaCodigosConMinusculasAcentosOEnie | 0.005 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaCodigoFueraDelCatalogo | 0.002 | ✅ pasó |
| integración | `EsquemaIT` | flywayAplicaV1YCreaLasCincoTablas | 0.003 | ✅ pasó |
| integración | `EsquemaIT` | alFinalizarUnaSalaElCodigoSePuedeReclamarDeNuevo | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | carreraPorElMismoPuebloDejaUnaSolaSalaViva | 0.006 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaEstadoInvalido | 0.002 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | Tras V3, el CHECK de puzzles_estado rechaza fotografia, mirador y observatorio y acepta los ids nuevos | 0.318 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | V3 renombra a inglés los puzzles de una sala creada con V2, conserva su estado y sus datos, y ese esquema rechaza fotografia | 0.169 | ✅ pasó |
| integración | `SalaIT` | crearDevuelveElTokenUnaVezYGuardaSoloSuHash | 0.056 | ✅ pasó |
| integración | `SalaIT` | carreraDeAppsPorUnirseDaUn200YSiete409 | 0.043 | ✅ pasó |
| integración | `SalaIT` | unirseSinSalaVivaDa404 | 0.011 | ✅ pasó |
| integración | `SalaIT` | flujoCrearUnirseEstado | 0.050 | ✅ pasó |
| integración | `SalaIT` | elJobNoTocaSalasFinalizadas | 0.021 | ✅ pasó |
| integración | `SalaIT` | unirseAceptaMinusculas | 0.024 | ✅ pasó |
| integración | `SalaIT` | pueblosReflejaLasSalasVivas | 0.041 | ✅ pasó |
| integración | `SalaIT` | estadoExigeElTokenDeEsaSala | 0.044 | ✅ pasó |
| integración | `SalaIT` | salaVencidaExpiraLiberaElNombreYSeConservaComoHistorial | 0.034 | ✅ pasó |
| integración | `SalaIT` | unirseRenuevaLaExpiracion | 0.021 | ✅ pasó |
| integración | `SalaIT` | carreraPorElMismoPuebloDaUn201YSiete409 | 0.021 | ✅ pasó |
| integración | `SalaIT` | crearFueraDelCatalogoDa400 | 0.004 | ✅ pasó |
| integración | `SalaIT` | crearAceptaMinusculasYEspacios | 0.013 | ✅ pasó |
| integración | `SalaIT` | crearInsertaLosCuatroPuzzlesBloqueados | 0.013 | ✅ pasó |
| integración | `SalaIT` | unirseDosVecesDa409 | 0.022 | ✅ pasó |
| integración | `SalaIT` | salaQueAunNoVenceSigueViva | 0.015 | ✅ pasó |
| integración | `repository.ModeloIT` | guardaYLeeSalaPuzzlesYDescubrimientosConJsonb | 0.089 | ✅ pasó |
| integración | `ws.WebSocketIT` | elServidorCierraLaConexionCon1008MientrasNoHayAutenticacion | 0.024 | ✅ pasó |

## 5. Salida de `./mvnw clean verify`

Últimas líneas de Maven sobre `ca4a98f` (sin los logs de Spring; rutas recortadas al repo):

```text
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.268 s -- in com.vestigia.controller.SalaControllerTest
[INFO] Running com.vestigia.controller.ApiExceptionHandlerTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.137 s -- in com.vestigia.controller.ApiExceptionHandlerTest
[INFO] Running com.vestigia.controller.DescubrimientoControllerTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.128 s -- in com.vestigia.controller.DescubrimientoControllerTest
[INFO] Running com.vestigia.service.DescubrimientoServiceTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.122 s -- in com.vestigia.service.DescubrimientoServiceTest
[INFO] Running com.vestigia.service.SalaServiceTest
[INFO] Tests run: 23, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.114 s -- in com.vestigia.service.SalaServiceTest
[INFO] Running com.vestigia.service.HabilitacionPuzzlesServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.006 s -- in com.vestigia.service.HabilitacionPuzzlesServiceTest
[INFO] Running com.vestigia.service.ActividadServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.052 s -- in com.vestigia.service.ActividadServiceTest
[INFO] Results:
[INFO] Tests run: 69, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- jar:3.5.1:jar (default-jar) @ vestigia ---
[INFO] Building jar: target/vestigia-0.0.1-SNAPSHOT.jar
[INFO] --- spring-boot:4.1.1:repackage (repackage) @ vestigia ---
[INFO] Loaded 50 auto-discovered prefixes for remote repository ow2-snapshot (prefixes-ow2-snapshot.txt)
[INFO] Replacing main artifact target/vestigia-0.0.1-SNAPSHOT.jar with repackaged archive, adding nested dependencies in BOOT-INF/.
[INFO] The original artifact has been renamed to target/vestigia-0.0.1-SNAPSHOT.jar.original
[INFO] --- failsafe:3.5.6:integration-test (default) @ vestigia ---
[INFO] Running com.vestigia.MigracionIdsPuzzlesIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.833 s -- in com.vestigia.MigracionIdsPuzzlesIT
[INFO] Running com.vestigia.AplicacionIT
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.296 s -- in com.vestigia.AplicacionIT
[INFO] Running com.vestigia.repository.ModeloIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.097 s -- in com.vestigia.repository.ModeloIT
[INFO] Running com.vestigia.EsquemaIT
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.044 s -- in com.vestigia.EsquemaIT
[INFO] Running com.vestigia.CatalogoPueblosIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.013 s -- in com.vestigia.CatalogoPueblosIT
[INFO] Running com.vestigia.SalaIT
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.453 s -- in com.vestigia.SalaIT
[INFO] Running com.vestigia.EscriturasSalaIT
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.628 s -- in com.vestigia.EscriturasSalaIT
[INFO] Running com.vestigia.ws.WebSocketIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.032 s -- in com.vestigia.ws.WebSocketIT
[INFO] Results:
[INFO] Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- failsafe:3.5.6:verify (default) @ vestigia ---
[INFO] BUILD SUCCESS
[INFO] Total time:  13.860 s
[INFO] Finished at: 2026-11-05T23:37:36-06:00
```

## 6. Pruebas adversariales de la API

Pendiente: las completa el orquestador.

## 7. Revisiones independientes

Pendiente: las completa el orquestador.

## 8. Lo que no se probó y por qué

| Qué | Por qué |
|---|---|
| Que `lookout` y `observatory` pasen a `disponible` por REST de punta a punta | Necesitan un puzzle `resuelto`, y `PUT puzzles` es del Paso 3b. Se probaron con el servicio y con `resuelto` escrito por SQL (sección 3) |
| Emisión por WebSocket | Es del Paso 4. Aquí solo se publica el `SalaEvento` de Spring dentro de la transacción, y `ActividadServiceTest` comprueba que se publica |
| Carrera entre `DELETE` y otra escritura de la misma sala | No hay prueba automatizada. El diseño la cubre: las dos bloquean la fila y la segunda ve la sala `finalizada` y responde `401` (`ActividadServiceTest` · Bloquear una sala finalizada, expirada o inexistente responde 401) |
| Límite de tamaño de `datos` | La especificación no fija uno. Un `datos` muy grande se guarda tal cual en el JSONB |
| `clave` con un número o booleano JSON (`123`, `true`) | Jackson los convierte a texto (`"123"`, `"true"`), que cumplen el formato, y responde `201`. Visto en la prueba manual. Es el mismo comportamiento que ya tenía `codigo` en `POST /salas`. No se cambió porque tocaría la configuración global de Jackson |

Prueba manual con la app real sobre `ca4a98f` (los tokens no aparecen en el log; se comprobó con `grep`):

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
