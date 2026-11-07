# Paso 3.0 · Ids de puzzle en inglés

Rama `feature/ids-puzzles-ingles`, comparada contra `feature/plan-puzzles-adaptado`. La migración `V3__ids_puzzles_en_ingles.sql` renombra `fotografia` → `photo`, `mirador` → `lookout` y `observatorio` → `observatory`, y el enum `PuzzleEstadoModel.Puzzle` queda `radio, photo, lookout, observatory`. La API sigue en `/api/v1`.

## 1. Veredicto

✅ 68 pruebas, 0 fallos, 0 errores, 0 omitidas.

| Total | Unitarias | Integración | Fallos | Errores | Omitidas | Fecha | Commit | Rama |
|---:|---:|---:|---:|---:|---:|---|---|---|
| 68 | 32 | 36 | 0 | 0 | 0 | 2026-11-05 23:17 | `a8b8aea` | `feature/ids-puzzles-ingles` |

## 2. Cómo reproducirlo

Con Docker corriendo (las pruebas `*IT` levantan `postgres:17-alpine` con Testcontainers):

```sh
git checkout feature/ids-puzzles-ingles
./mvnw clean verify
python3 scripts/resumen-pruebas.py               # secciones 1 y 4 de este archivo
python3 scripts/resumen-pruebas.py --solo-conteo # una línea
```

Comprobación manual del nombre del `CHECK` en un Postgres real, antes y después de `V3`:

```sh
docker run -d --name vestigia-pg-3.0 -e POSTGRES_PASSWORD=postgres -p 55432:5432 postgres:17-alpine
docker exec -i vestigia-pg-3.0 psql -U postgres -v ON_ERROR_STOP=1 < src/main/resources/db/migration/V1__esquema.sql
docker exec vestigia-pg-3.0 psql -U postgres -c "SELECT conname FROM pg_constraint WHERE conrelid = 'puzzles_estado'::regclass AND contype = 'c'"
#   puzzles_estado_puzzle_check   <- el que borra y vuelve a crear V3
#   puzzles_estado_estado_check
docker exec -i vestigia-pg-3.0 psql -U postgres -v ON_ERROR_STOP=1 < src/main/resources/db/migration/V3__ids_puzzles_en_ingles.sql
docker rm -f vestigia-pg-3.0
```

En esa corrida manual, una sala con los cuatro puzzles viejos quedó con `radio`, `photo`, `lookout` y `observatory`, y un `INSERT` con `fotografia` dio `violates check constraint "puzzles_estado_puzzle_check"`.

## 3. Requisito → pruebas

| Requisito del paso | Pruebas | Resultado |
|---|---|:-:|
| `V3` borra el `CHECK`, actualiza las filas existentes y crea el `CHECK` nuevo | `MigracionIdsPuzzlesIT` · V3 renombra a inglés los puzzles de una sala creada con V2, conserva su estado y sus datos, y ese esquema rechaza fotografia | ✅ |
| Flyway hasta `V2`, sala con puzzles viejos, migrar a la última versión, filas con ids nuevos | `MigracionIdsPuzzlesIT` · la misma prueba: migra un esquema propio con `target("2")`, inserta `radio`, `fotografia`, `mirador` y `observatorio` con estados y `datos` distintos, migra con `target("3")` para no depender de que `V3` sea la última (B1) y comprueba `lookout`, `observatory`, `photo` y `radio` con el mismo estado y los mismos `datos` | ✅ |
| El `CHECK` rechaza `fotografia` (y los otros dos ids viejos) | `MigracionIdsPuzzlesIT` · Tras V3, el CHECK de puzzles_estado rechaza fotografia, mirador y observatorio y acepta los ids nuevos (esquema principal; además exige que la versión `3` de Flyway tenga `success`) · la prueba de la migración, que intenta `fotografia` en el esquema que vino de `V2` (B5) | ✅ |
| El nombre real de la restricción es `puzzles_estado_puzzle_check` | La misma prueba exige ese nombre en el error de Postgres. Además, Flyway aplica `V3` al arrancar el contexto de todas las `*IT`: si el `DROP CONSTRAINT` usara otro nombre, ninguna arrancaría. Comprobado también a mano (sección 2) | ✅ |
| Enum `radio, photo, lookout, observatory`, en ese orden | `SalaIT` · flujoCrearUnirseEstado (el estado devuelve los cuatro en ese orden) · `SalaServiceTest` · estadoOrdenaLosPuzzlesYReportaDispositivosDesconectados | ✅ |
| Al crear una sala se insertan los cuatro puzzles con los ids nuevos | `SalaIT` · crearInsertaLosCuatroPuzzlesBloqueados | ✅ |
| Hibernate guarda y lee el enum nuevo contra el esquema migrado | `ModeloIT` · guardaYLeeSalaPuzzlesYDescubrimientosConJsonb (guarda `lookout` y la coordenada AR `{"tipo": "ascension_recta", "valor": "137"}`, con `valor` como texto en el JSONB) · `AplicacionIT` (el contexto arranca con `ddl-auto=validate`) | ✅ |
| La API sigue en `/api/v1` | `SalaIT` (todas sus pruebas usan `/api/v1/...`) | ✅ |
| `docs/api.md`, `docs/modelo-datos.md` y `README.md` con los ids nuevos | Sin prueba: es documentación. Se revisó con `grep -rnE "fotografia\|mirador\|observatorio"`, que ya solo los encuentra en `V1__esquema.sql`, `V3__ids_puzzles_en_ingles.sql`, `MigracionIdsPuzzlesIT`, la fila de `V3` en `docs/modelo-datos.md`, este archivo y los documentos de plan (donde `observatorio` también es escena y parte de la clave `observatorio_visible`) | — |
| `scripts/resumen-pruebas.py` con `--solo-conteo` | Sin prueba automatizada: es una herramienta de la evidencia. Se comprobó corriéndolo: generó las secciones 1 y 4 de este archivo y `--solo-conteo` imprimió `68 pruebas, 0 fallos, 0 errores, 0 omitidas`. También se comprobaron a mano los avisos de B3: sin `failsafe-reports` el veredicto sale ⚠️ y el código de salida es 3; sin reportes, el código es 1 | — |

## 4. Lista completa de pruebas

| Tipo | Clase | Prueba | Tiempo (s) | Resultado |
|---|---|---|---:|---|
| unitaria | `config.ApiHttpConfigTest` | corsRechazaOrigenNoConfigurado | 0.031 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | corsPermiteOrigenConfigurado | 0.004 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | registraUnaLineaPorPeticionSinTokens(CapturedOutput) | 0.035 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | noRegistraRutasFueraDeApi(CapturedOutput) | 0.037 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | rutaInexistenteDevuelve404ConCuerpoUniforme | 0.006 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | jsonMalformadoDevuelve400ConCuerpoUniforme | 0.006 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | validacionDevuelve400ConDetallePorCampo | 0.008 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | errorInesperadoDevuelve500SinDetallesInternos | 0.005 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | conflictoConservaSuCodigoYMensaje | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | pueblosPasaElFiltroDeDisponibles | 0.030 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeOtraSalaDa403 | 0.007 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | unirseNoPideTokenYDevuelveElDeLaApp | 0.005 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearResponde201ConCodigoEstadoYToken | 0.037 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDesconocidoDa401 | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearSinCodigoDa400ConDetalle | 0.005 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeLaSalaDevuelveElEstadoEnSnakeCase | 0.009 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearConPuebloOcupadoDa409ConCuerpoUniforme | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoSinTokenDa401 | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearFueraDelCatalogoDa400SinTocarSalas | 0.161 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | expirarVencidasUsaLaHoraDelReloj | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | otraViolacionDeIntegridadNoSeDisfrazaDe409 | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseSinSalaVivaDa404 | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | listarPueblosMarcaOcupadosLosQueTienenSalaViva | 0.005 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseEnlazaConUnSoloUpdateYRenuevaLaExpiracion | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearConPuebloOcupadoDa409 | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | estadoOrdenaLosPuzzlesYReportaDispositivosDesconectados | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseASalaVivaQueYaTieneAppDa409 | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | cadaSalaRecibeUnTokenDistinto | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearInsertaLosCuatroPuzzlesBloqueados | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearNormalizaElCodigoYGuardaSoloElHashDelToken | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | autenticarDistingueElRolPorElHash | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | autenticarConTokenDesconocidoNoDaCredencial | 0.000 | ✅ pasó |
| integración | `AplicacionIT` | rutaInexistenteBajoSalasDa404SinPedirToken | 0.074 | ✅ pasó |
| integración | `AplicacionIT` | elJobDeExpiracionEstaProgramadoCadaMinuto | 0.002 | ✅ pasó |
| integración | `AplicacionIT` | openApiDocumentaLosEndpointsDeSalasYElToken | 0.148 | ✅ pasó |
| integración | `AplicacionIT` | swaggerUiYOpenApiEstanDisponibles | 0.010 | ✅ pasó |
| integración | `AplicacionIT` | healthResponde200ConEstadoUp | 0.016 | ✅ pasó |
| integración | `CatalogoPueblosIT` | flywayAplicaV2YLosCodigosEstanEnLaTabla | 0.005 | ✅ pasó |
| integración | `CatalogoPueblosIT` | seedDeclara101CodigosValidosYSinDuplicados | 0.002 | ✅ pasó |
| integración | `EsquemaIT` | salasTerminadasNoBloqueanUnaNuevaSalaViva | 0.008 | ✅ pasó |
| integración | `EsquemaIT` | indiceEventosPorSalaYTiempoExiste | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | noPuedeHaberDosSalasVivasConElMismoCodigo | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | catalogoRechazaCodigosConMinusculasAcentosOEnie | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaCodigoFueraDelCatalogo | 0.002 | ✅ pasó |
| integración | `EsquemaIT` | flywayAplicaV1YCreaLasCincoTablas | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | alFinalizarUnaSalaElCodigoSePuedeReclamarDeNuevo | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | carreraPorElMismoPuebloDejaUnaSolaSalaViva | 0.006 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaEstadoInvalido | 0.002 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | Tras V3, el CHECK de puzzles_estado rechaza fotografia, mirador y observatorio y acepta los ids nuevos | 0.289 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | V3 renombra a inglés los puzzles de una sala creada con V2, conserva su estado y sus datos, y ese esquema rechaza fotografia | 0.175 | ✅ pasó |
| integración | `SalaIT` | crearDevuelveElTokenUnaVezYGuardaSoloSuHash | 0.046 | ✅ pasó |
| integración | `SalaIT` | carreraDeAppsPorUnirseDaUn200YSiete409 | 0.039 | ✅ pasó |
| integración | `SalaIT` | unirseSinSalaVivaDa404 | 0.012 | ✅ pasó |
| integración | `SalaIT` | flujoCrearUnirseEstado | 0.045 | ✅ pasó |
| integración | `SalaIT` | elJobNoTocaSalasFinalizadas | 0.017 | ✅ pasó |
| integración | `SalaIT` | unirseAceptaMinusculas | 0.018 | ✅ pasó |
| integración | `SalaIT` | pueblosReflejaLasSalasVivas | 0.034 | ✅ pasó |
| integración | `SalaIT` | estadoExigeElTokenDeEsaSala | 0.041 | ✅ pasó |
| integración | `SalaIT` | salaVencidaExpiraLiberaElNombreYSeConservaComoHistorial | 0.036 | ✅ pasó |
| integración | `SalaIT` | unirseRenuevaLaExpiracion | 0.018 | ✅ pasó |
| integración | `SalaIT` | carreraPorElMismoPuebloDaUn201YSiete409 | 0.017 | ✅ pasó |
| integración | `SalaIT` | crearFueraDelCatalogoDa400 | 0.005 | ✅ pasó |
| integración | `SalaIT` | crearAceptaMinusculasYEspacios | 0.010 | ✅ pasó |
| integración | `SalaIT` | crearInsertaLosCuatroPuzzlesBloqueados | 0.013 | ✅ pasó |
| integración | `SalaIT` | unirseDosVecesDa409 | 0.018 | ✅ pasó |
| integración | `SalaIT` | salaQueAunNoVenceSigueViva | 0.012 | ✅ pasó |
| integración | `repository.ModeloIT` | guardaYLeeSalaPuzzlesYDescubrimientosConJsonb | 0.092 | ✅ pasó |
| integración | `ws.WebSocketIT` | elServidorCierraLaConexionCon1008MientrasNoHayAutenticacion | 0.025 | ✅ pasó |

## 5. Salida de `./mvnw clean verify`

Últimas 40 líneas de Maven sobre el commit de la sección 1 (`a8b8aea`, después de las correcciones de la sección 7) (sin los logs de Spring; rutas recortadas al repo):

```text
[INFO] Running com.vestigia.controller.ApiExceptionHandlerTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.136 s -- in com.vestigia.controller.ApiExceptionHandlerTest
[INFO] Running com.vestigia.service.SalaServiceTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.184 s -- in com.vestigia.service.SalaServiceTest
[INFO] Results:
[INFO] Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- jar:3.5.1:jar (default-jar) @ vestigia ---
[INFO] Building jar: target/vestigia-0.0.1-SNAPSHOT.jar
[INFO] --- spring-boot:4.1.1:repackage (repackage) @ vestigia ---
[INFO] Loaded 50 auto-discovered prefixes for remote repository ow2-snapshot (prefixes-ow2-snapshot.txt)
[INFO] Replacing main artifact target/vestigia-0.0.1-SNAPSHOT.jar with repackaged archive, adding nested dependencies in BOOT-INF/.
[INFO] The original artifact has been renamed to target/vestigia-0.0.1-SNAPSHOT.jar.original
[INFO] --- failsafe:3.5.6:integration-test (default) @ vestigia ---
[INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.vestigia.MigracionIdsPuzzlesIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.921 s -- in com.vestigia.MigracionIdsPuzzlesIT
[INFO] Running com.vestigia.AplicacionIT
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.280 s -- in com.vestigia.AplicacionIT
[INFO] Running com.vestigia.repository.ModeloIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.100 s -- in com.vestigia.repository.ModeloIT
[INFO] Running com.vestigia.EsquemaIT
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.044 s -- in com.vestigia.EsquemaIT
[INFO] Running com.vestigia.CatalogoPueblosIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.013 s -- in com.vestigia.CatalogoPueblosIT
[INFO] Running com.vestigia.SalaIT
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.398 s -- in com.vestigia.SalaIT
[INFO] Running com.vestigia.ws.WebSocketIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.031 s -- in com.vestigia.ws.WebSocketIT
[INFO] Results:
[INFO] Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- failsafe:3.5.6:verify (default) @ vestigia ---
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  12.646 s
[INFO] Finished at: 2026-11-05T23:17:25-06:00
[INFO] ------------------------------------------------------------------------
```

## 6. Pruebas adversariales de la API

El probador adversarial fue **gemini-3.8-flash** (Antigravity, familia Google). Levantó la app en el puerto 18080 con su propio `postgres:17-alpine` (`vestigia-pg-test-30`) y borró los dos al terminar; el orquestador comprobó que no quedó nada escuchando en 8080 ni en 18080. Corrió **56 escenarios y no encontró anomalías**. Esta es su tabla resumida (en algunas filas de su salida original decía `:8080` por error de transcripción; la app corrió en 18080).

| Escenario | Petición | Esperado | Real | |
|---|---|---|---|:-:|
| Ids y orden en `GET estado` | `GET /api/v1/salas/BACALAR/estado` con `Bearer <token>` | `radio, photo, lookout, observatory` | Igual, sin ids viejos en ninguna respuesta | ✅ |
| `CHECK` rechaza ids viejos | `INSERT ... 'fotografia'` / `'mirador'` / `'observatorio'` en psql | Violación 23514 | `puzzles_estado_puzzle_check` violado | ✅ |
| Token ausente | `GET .../estado` sin `Authorization` | 401 | 401 | ✅ |
| `Authorization` sin `Bearer` o con `Bearer ` vacío | `GET .../estado` | 401 | 401 | ✅ |
| Token inválido | `Bearer <token_invalido>` | 401 | 401 "Token inválido o de una sala que ya terminó" | ✅ |
| Token de otra sala | Token de CREEL en BACALAR | 403 | 403 | ✅ |
| JSON malformado, cuerpo vacío o `{}` | `POST /api/v1/salas` | 400 | 400 | ✅ |
| Tipos erróneos en `codigo` (999, true, null, arreglo, objeto) | `POST /api/v1/salas` | 400 | 400 | ✅ |
| `Content-Type: text/plain` | `POST /api/v1/salas` | 415 | 415 | ✅ |
| Claves extra y campos del servidor inyectados (`estado`, `token`) | `POST /api/v1/salas` | 201, ignorándolos | 201, ignorados | ✅ |
| Código con minúsculas y espacios | `{"codigo":"  bacalar  "}` y `/salas/bacalar/unirse` | Normaliza a BACALAR | 201 / 200 | ✅ |
| Fuera del catálogo | `{"codigo":"ATLANTIDA"}` | 400 | 400 | ✅ |
| 10 creaciones simultáneas del mismo pueblo | 10× `POST /api/v1/salas {"codigo":"CALVILLO"}` | 1×201, 9×409 | 1×201, 9×409 | ✅ |
| 10 uniones simultáneas | 10× `POST /api/v1/salas/CALVILLO/unirse` | 1×200, 9×409 | 1×200, 9×409 | ✅ |
| Sala expirada | Token y `unirse` sobre BATOPILAS expirada | 401 / 404, pueblo libre otra vez | 401 / 404, reclamable con 201 | ✅ |
| Endpoints de pasos posteriores (`PUT puzzles`, `POST descubrimientos`, `PUT escena`, `DELETE`) | Con `Bearer <token>` | Aún no existen | 404 | ✅ |

## 7. Revisiones independientes

| Revisor | Modelo y familia | Sustitución | Veredicto |
|---|---|---|---|
| A | **gemini-4-argon** (Antigravity, familia Google) | Sustituyó a gpt-oss-120b-medium, que falló dos veces: error del proveedor y salida de herramienta mal formada | Aprobado, 1 hallazgo menor |
| B | **claude-opus-5-5**, esfuerzo alto (Anthropic) | Sustituyó a claude-fable-5-1, que falló dos veces por el límite de uso de la API | Aprobado, 5 hallazgos menores |

El revisor B es de la misma familia que el constructor (Claude). La revisión de otra familia es la del revisor A.

| # | Hallazgo | Decisión | Commit |
|---|---|---|---|
| A1 | `docs/modelo-datos.md` daba como ejemplo de `datos` `{"tipo": "azimut", "valor": 60}`, que se descartó. El canon es `{"tipo": "ascension_recta", "valor": "137"}`, con `valor` en texto. `ModeloIT` usaba los mismos datos | Aceptado. Se cambiaron el documento y `ModeloIT`, que además comprueba que `valor` queda como texto en el JSONB | `563503c` |
| B1 | `MigracionIdsPuzzlesIT` migraba a `latest` y exigía que la última versión fuera `3`: fallaría en cuanto exista `V4` | Aceptado. Migra con `target("3")` y comprueba que la versión `3` tiene `success` | `f7b8a2b` |
| B2 | El SQL de `docs/plan-cambios-backend.md` no tiene el `WHERE puzzle IN (...)` del archivo real | Aceptado. Nota en el plan: la versión vigente es la del archivo | `74421c5` |
| B3 | El script no avisaba si faltaba `failsafe-reports`, y el README decía `./mvnw verify` sin `clean` | Aceptado. Aviso por stderr, veredicto ⚠️ y código de salida 3; el README y el script piden `./mvnw clean verify` | `a8b8aea` |
| B4 | El script corría `git` en el directorio actual, no en el repo de `--target` | Aceptado. Usa `git -C` con la carpeta padre de `target` | `3534a4d` |
| B5 | El rechazo de `fotografia` no se probaba en el esquema que vino de `V2` | Aceptado. La prueba de la migración intenta insertar `fotografia` en ese esquema | `8348ece` |

## 8. Lo que no se probó y por qué

| Qué | Por qué |
|---|---|
| `V3` sobre la base de Railway | No hay nada desplegado todavía (O1), así que no hay filas reales que mover. Cuando se despliegue, Flyway aplicará `V1`, `V2` y `V3` en orden sobre una base vacía |
| Deshacer `V3` | Flyway Community no tiene migraciones de reversa. Volver a los ids en español exigiría una `V4` |
| Que Unity y la app usen los ids nuevos | Es integración de los clientes. Los endpoints de puzzles todavía no existen (entrega 3b) |
| Pruebas automatizadas del script de resumen | Es una herramienta de la evidencia, no código del backend. Se verificó corriéndolo sobre los reportes reales, sobre un `target` sin `failsafe-reports`, sobre una carpeta vacía y desde otro directorio con `--target` (B3 y B4) |
