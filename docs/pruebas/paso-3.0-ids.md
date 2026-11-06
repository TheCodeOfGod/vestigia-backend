# Paso 3.0 · Ids de puzzle en inglés

Rama `feature/ids-puzzles-ingles`, comparada contra `feature/plan-puzzles-adaptado`. La migración `V3__ids_puzzles_en_ingles.sql` renombra `fotografia` → `photo`, `mirador` → `lookout` y `observatorio` → `observatory`, y el enum `PuzzleEstadoModel.Puzzle` queda `radio, photo, lookout, observatory`. La API sigue en `/api/v1`.

## 1. Veredicto

✅ 68 pruebas, 0 fallos, 0 errores, 0 omitidas.

| Total | Unitarias | Integración | Fallos | Errores | Omitidas | Fecha | Commit | Rama |
|---:|---:|---:|---:|---:|---:|---|---|---|
| 68 | 32 | 36 | 0 | 0 | 0 | 2026-11-05 22:58 | `e887e79` | `feature/ids-puzzles-ingles` |

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
| `V3` borra el `CHECK`, actualiza las filas existentes y crea el `CHECK` nuevo | `MigracionIdsPuzzlesIT` · V3 renombra a inglés los puzzles de una sala creada con V2 y conserva su estado y sus datos | ✅ |
| Flyway hasta `V2`, sala con puzzles viejos, migrar a la última versión, filas con ids nuevos | `MigracionIdsPuzzlesIT` · la misma prueba: migra un esquema propio con `target("2")`, inserta `radio`, `fotografia`, `mirador` y `observatorio` con estados y `datos` distintos, migra a `latest` (versión `3`) y comprueba `lookout`, `observatory`, `photo` y `radio` con el mismo estado y los mismos `datos` | ✅ |
| El `CHECK` rechaza `fotografia` (y los otros dos ids viejos) | `MigracionIdsPuzzlesIT` · Tras V3, el CHECK de puzzles_estado rechaza fotografia, mirador y observatorio y acepta los ids nuevos | ✅ |
| El nombre real de la restricción es `puzzles_estado_puzzle_check` | La misma prueba exige ese nombre en el error de Postgres. Además, Flyway aplica `V3` al arrancar el contexto de todas las `*IT`: si el `DROP CONSTRAINT` usara otro nombre, ninguna arrancaría. Comprobado también a mano (sección 2) | ✅ |
| Enum `radio, photo, lookout, observatory`, en ese orden | `SalaIT` · flujoCrearUnirseEstado (el estado devuelve los cuatro en ese orden) · `SalaServiceTest` · estadoOrdenaLosPuzzlesYReportaDispositivosDesconectados | ✅ |
| Al crear una sala se insertan los cuatro puzzles con los ids nuevos | `SalaIT` · crearInsertaLosCuatroPuzzlesBloqueados | ✅ |
| Hibernate guarda y lee el enum nuevo contra el esquema migrado | `ModeloIT` · guardaYLeeSalaPuzzlesYDescubrimientosConJsonb (guarda `lookout`) · `AplicacionIT` (el contexto arranca con `ddl-auto=validate`) | ✅ |
| La API sigue en `/api/v1` | `SalaIT` (todas sus pruebas usan `/api/v1/...`) | ✅ |
| `docs/api.md`, `docs/modelo-datos.md` y `README.md` con los ids nuevos | Sin prueba: es documentación. Se revisó con `grep -rnE "fotografia\|mirador\|observatorio"`, que ya solo los encuentra en `V1__esquema.sql`, `V3__ids_puzzles_en_ingles.sql`, `MigracionIdsPuzzlesIT`, la fila de `V3` en `docs/modelo-datos.md`, este archivo y los documentos de plan (donde `observatorio` también es escena y parte de la clave `observatorio_visible`) | — |
| `scripts/resumen-pruebas.py` con `--solo-conteo` | Sin prueba automatizada: es una herramienta de la evidencia. Se comprobó corriéndolo: generó las secciones 1 y 4 de este archivo y `--solo-conteo` imprimió `68 pruebas, 0 fallos, 0 errores, 0 omitidas` | — |

## 4. Lista completa de pruebas

| Tipo | Clase | Prueba | Tiempo (s) | Resultado |
|---|---|---|---:|---|
| unitaria | `config.ApiHttpConfigTest` | corsRechazaOrigenNoConfigurado | 0.029 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | corsPermiteOrigenConfigurado | 0.004 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | registraUnaLineaPorPeticionSinTokens(CapturedOutput) | 0.029 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | noRegistraRutasFueraDeApi(CapturedOutput) | 0.034 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | rutaInexistenteDevuelve404ConCuerpoUniforme | 0.006 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | jsonMalformadoDevuelve400ConCuerpoUniforme | 0.005 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | validacionDevuelve400ConDetallePorCampo | 0.008 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | errorInesperadoDevuelve500SinDetallesInternos | 0.004 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | conflictoConservaSuCodigoYMensaje | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | pueblosPasaElFiltroDeDisponibles | 0.028 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeOtraSalaDa403 | 0.006 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | unirseNoPideTokenYDevuelveElDeLaApp | 0.005 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearResponde201ConCodigoEstadoYToken | 0.034 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDesconocidoDa401 | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearSinCodigoDa400ConDetalle | 0.005 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeLaSalaDevuelveElEstadoEnSnakeCase | 0.011 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearConPuebloOcupadoDa409ConCuerpoUniforme | 0.004 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoSinTokenDa401 | 0.003 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearFueraDelCatalogoDa400SinTocarSalas | 0.152 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | expirarVencidasUsaLaHoraDelReloj | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | otraViolacionDeIntegridadNoSeDisfrazaDe409 | 0.003 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseSinSalaVivaDa404 | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | listarPueblosMarcaOcupadosLosQueTienenSalaViva | 0.005 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseEnlazaConUnSoloUpdateYRenuevaLaExpiracion | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearConPuebloOcupadoDa409 | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | estadoOrdenaLosPuzzlesYReportaDispositivosDesconectados | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseASalaVivaQueYaTieneAppDa409 | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | cadaSalaRecibeUnTokenDistinto | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearInsertaLosCuatroPuzzlesBloqueados | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearNormalizaElCodigoYGuardaSoloElHashDelToken | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | autenticarDistingueElRolPorElHash | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | autenticarConTokenDesconocidoNoDaCredencial | 0.000 | ✅ pasó |
| integración | `AplicacionIT` | rutaInexistenteBajoSalasDa404SinPedirToken | 0.069 | ✅ pasó |
| integración | `AplicacionIT` | elJobDeExpiracionEstaProgramadoCadaMinuto | 0.002 | ✅ pasó |
| integración | `AplicacionIT` | openApiDocumentaLosEndpointsDeSalasYElToken | 0.149 | ✅ pasó |
| integración | `AplicacionIT` | swaggerUiYOpenApiEstanDisponibles | 0.010 | ✅ pasó |
| integración | `AplicacionIT` | healthResponde200ConEstadoUp | 0.012 | ✅ pasó |
| integración | `CatalogoPueblosIT` | flywayAplicaV2YLosCodigosEstanEnLaTabla | 0.005 | ✅ pasó |
| integración | `CatalogoPueblosIT` | seedDeclara101CodigosValidosYSinDuplicados | 0.002 | ✅ pasó |
| integración | `EsquemaIT` | salasTerminadasNoBloqueanUnaNuevaSalaViva | 0.006 | ✅ pasó |
| integración | `EsquemaIT` | indiceEventosPorSalaYTiempoExiste | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | noPuedeHaberDosSalasVivasConElMismoCodigo | 0.005 | ✅ pasó |
| integración | `EsquemaIT` | catalogoRechazaCodigosConMinusculasAcentosOEnie | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaCodigoFueraDelCatalogo | 0.002 | ✅ pasó |
| integración | `EsquemaIT` | flywayAplicaV1YCreaLasCincoTablas | 0.005 | ✅ pasó |
| integración | `EsquemaIT` | alFinalizarUnaSalaElCodigoSePuedeReclamarDeNuevo | 0.005 | ✅ pasó |
| integración | `EsquemaIT` | carreraPorElMismoPuebloDejaUnaSolaSalaViva | 0.008 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaEstadoInvalido | 0.002 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | Tras V3, el CHECK de puzzles_estado rechaza fotografia, mirador y observatorio y acepta los ids nuevos | 0.298 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | V3 renombra a inglés los puzzles de una sala creada con V2 y conserva su estado y sus datos | 0.158 | ✅ pasó |
| integración | `SalaIT` | crearDevuelveElTokenUnaVezYGuardaSoloSuHash | 0.059 | ✅ pasó |
| integración | `SalaIT` | carreraDeAppsPorUnirseDaUn200YSiete409 | 0.032 | ✅ pasó |
| integración | `SalaIT` | unirseSinSalaVivaDa404 | 0.010 | ✅ pasó |
| integración | `SalaIT` | flujoCrearUnirseEstado | 0.045 | ✅ pasó |
| integración | `SalaIT` | elJobNoTocaSalasFinalizadas | 0.017 | ✅ pasó |
| integración | `SalaIT` | unirseAceptaMinusculas | 0.014 | ✅ pasó |
| integración | `SalaIT` | pueblosReflejaLasSalasVivas | 0.038 | ✅ pasó |
| integración | `SalaIT` | estadoExigeElTokenDeEsaSala | 0.042 | ✅ pasó |
| integración | `SalaIT` | salaVencidaExpiraLiberaElNombreYSeConservaComoHistorial | 0.034 | ✅ pasó |
| integración | `SalaIT` | unirseRenuevaLaExpiracion | 0.016 | ✅ pasó |
| integración | `SalaIT` | carreraPorElMismoPuebloDaUn201YSiete409 | 0.017 | ✅ pasó |
| integración | `SalaIT` | crearFueraDelCatalogoDa400 | 0.004 | ✅ pasó |
| integración | `SalaIT` | crearAceptaMinusculasYEspacios | 0.012 | ✅ pasó |
| integración | `SalaIT` | crearInsertaLosCuatroPuzzlesBloqueados | 0.013 | ✅ pasó |
| integración | `SalaIT` | unirseDosVecesDa409 | 0.021 | ✅ pasó |
| integración | `SalaIT` | salaQueAunNoVenceSigueViva | 0.016 | ✅ pasó |
| integración | `repository.ModeloIT` | guardaYLeeSalaPuzzlesYDescubrimientosConJsonb | 0.089 | ✅ pasó |
| integración | `ws.WebSocketIT` | elServidorCierraLaConexionCon1008MientrasNoHayAutenticacion | 0.023 | ✅ pasó |

## 5. Salida de `./mvnw clean verify`

Últimas 40 líneas de Maven sobre el commit de la sección 1 (sin los logs de Spring; rutas recortadas al repo):

```text
[INFO] Running com.vestigia.controller.ApiExceptionHandlerTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.130 s -- in com.vestigia.controller.ApiExceptionHandlerTest
[INFO] Running com.vestigia.service.SalaServiceTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.175 s -- in com.vestigia.service.SalaServiceTest
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
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 34.76 s -- in com.vestigia.MigracionIdsPuzzlesIT
[INFO] Running com.vestigia.AplicacionIT
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.274 s -- in com.vestigia.AplicacionIT
[INFO] Running com.vestigia.repository.ModeloIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.096 s -- in com.vestigia.repository.ModeloIT
[INFO] Running com.vestigia.EsquemaIT
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.049 s -- in com.vestigia.EsquemaIT
[INFO] Running com.vestigia.CatalogoPueblosIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.013 s -- in com.vestigia.CatalogoPueblosIT
[INFO] Running com.vestigia.SalaIT
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.403 s -- in com.vestigia.SalaIT
[INFO] Running com.vestigia.ws.WebSocketIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.030 s -- in com.vestigia.ws.WebSocketIT
[INFO] Results:
[INFO] Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- failsafe:3.5.6:verify (default) @ vestigia ---
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  42.332 s
[INFO] Finished at: 2026-11-05T22:58:57-06:00
[INFO] ------------------------------------------------------------------------
```

## 6. Pruebas adversariales de la API

Pendiente: las completa el orquestador.

## 7. Revisiones independientes

Pendiente: las completa el orquestador.

## 8. Lo que no se probó y por qué

| Qué | Por qué |
|---|---|
| `V3` sobre la base de Railway | No hay nada desplegado todavía (O1), así que no hay filas reales que mover. Cuando se despliegue, Flyway aplicará `V1`, `V2` y `V3` en orden sobre una base vacía |
| Deshacer `V3` | Flyway Community no tiene migraciones de reversa. Volver a los ids en español exigiría una `V4` |
| Que Unity y la app usen los ids nuevos | Es integración de los clientes. Los endpoints de puzzles todavía no existen (entrega 3b) |
| Pruebas automatizadas del script de resumen | Es una herramienta de la evidencia, no código del backend. Se verificó corriéndolo sobre los reportes reales |
