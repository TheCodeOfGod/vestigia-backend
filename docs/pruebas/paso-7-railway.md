# Paso 7 · Listo para Railway

Rama `feature/listo-para-railway`, comparada contra `feature/cierre`. Verifica todo lo que haría fallar o exponer el primer despliegue en Railway, **sin desplegar ni tocar Railway**: construye la imagen y la corre contra un Postgres vacío solo con variables de entorno, agrega `scripts/smoke-test.sh` (la partida completa con `curl` y `jq`), revisa configuración, logs, errores, secretos y CI, y escribe en `docs/despliegue.md` los pasos del primer despliegue para Raúl. No cambió el código de `src/main`, el `Dockerfile` ni `ci.yml`: no se encontró nada que lo pidiera. La API sigue en `/api/v1`. No hay migraciones nuevas.

## 1. Veredicto

✅ 242 pruebas, 0 fallos, 0 errores, 0 omitidas.

| Total | Unitarias | Integración | Fallos | Errores | Omitidas | Fecha | Commit | Rama |
|---:|---:|---:|---:|---:|---:|---|---|---|
| 242 | 124 | 118 | 0 | 0 | 0 | 2026-11-06 14:05 | `2f1fda7` | `feature/listo-para-railway` |

Las cifras son de `./mvnw -B clean verify` sobre `2f1fda7`. El commit siguiente solo agrega esta evidencia y el índice de `docs/pruebas/README.md`, que ninguna prueba lee. La rama partió de `bb2bdb7` con 235 pruebas y suma 7, todas de integración: `SmokeTestIT` (2), `SmokeTestFalloIT` (1) y `ConfiguracionDespliegueIT` (4).

**Arranque en frío.** `git clone --branch feature/listo-para-railway <worktree>` en un directorio temporal (HEAD `2f1fda7`, sin `target/` ni `maven-wrapper.jar`): `./mvnw -B clean verify` terminó en `BUILD SUCCESS` con `Tests run: 124` (unitarias) y `Tests run: 118` (integración), sin fallos, en 29.6 s, y `python3 scripts/resumen-pruebas.py --solo-conteo` dio `242 pruebas, 0 fallos, 0 errores, 0 omitidas`. Después, `docker build` en el mismo clon terminó con código 0.

## 2. Cómo reproducirlo

Con Docker corriendo (las pruebas `*IT` levantan `postgres:17-alpine` con Testcontainers; `SmokeTestIT` y `SmokeTestFalloIT` necesitan además `bash`, `curl` y `jq`):

```sh
git checkout feature/listo-para-railway
./mvnw clean verify
python3 scripts/resumen-pruebas.py               # secciones 1 y 4 de este archivo
python3 scripts/resumen-pruebas.py --solo-conteo # una línea
```

La imagen contra un Postgres vacío, en una red propia y solo con las variables de entorno:

```sh
docker build -t vestigia-backend:7 .
docker network create vestigia-net-7
docker run -d --name vestigia-pg-7 --network vestigia-net-7 -p 55432:5432 \
	-e POSTGRES_DB=vestigia7 -e POSTGRES_USER=vestigia7 -e POSTGRES_PASSWORD=<contraseña-local> postgres:17-alpine
docker run -d --name vestigia-app-7 --network vestigia-net-7 -p 18080:9090 \
	-e DB_URL=jdbc:postgresql://vestigia-pg-7:5432/vestigia7 -e DB_USER=vestigia7 -e DB_PASSWORD=<contraseña-local> \
	-e PORT=9090 -e CORS_ALLOWED_ORIGINS=https://pwa.example vestigia-backend:7
curl http://localhost:18080/actuator/health          # {"groups":["liveness","readiness"],"status":"UP"}
docker exec vestigia-app-7 id                        # uid=999(vestigia) gid=999(vestigia)
scripts/smoke-test.sh http://localhost:18080         # RESULTADO: ✅ …, código 0
docker rm -f vestigia-app-7 vestigia-pg-7 && docker network rm vestigia-net-7
```

`PORT=9090` es a propósito distinto de 8080, para comprobar que el backend escucha en el puerto que recibe (`Tomcat started on port 9090`).

### 2.1 La imagen, paso a paso

| Comprobación | Comando | Resultado |
|---|---|:-:|
| `docker build` | `docker build -t vestigia-backend:7 .` | ✅ 61 s con caché fría de Maven; imagen de 595 MB |
| La base está vacía antes de arrancar | `psql -c '\dt'` | ✅ `Did not find any relations.` |
| Flyway migra desde la base vacía | Log del contenedor | ✅ `Schema history table … does not exist yet`, `Current version of schema "public": << Empty Schema >>`, `Migrating … "1 - esquema"`, `"2 - seed pueblos"`, `"3 - ids puzzles en ingles"`, `Successfully applied 3 migrations … now at version v3` |
| Historial de Flyway y catálogo | `SELECT … FROM flyway_schema_history`; `SELECT count(*) FROM codigos_pueblo` | ✅ versiones 1, 2 y 3 con `success = t`; 101 pueblos |
| Rearranque sobre la misma base | Log | ✅ `Schema "public" is up to date. No migration necessary.` |
| Escucha en `PORT` | Log | ✅ `Tomcat started on port 9090 (http)`; `Started VestigiaApplication in 3.013 seconds` |
| Health | `curl /actuator/health` | ✅ `200`, `{"groups":["liveness","readiness"],"status":"UP"}` a los 5 s del `docker run` |
| Usuario sin privilegios | `docker inspect --format '{{.Config.User}}'`, `docker exec … id`, `ps` | ✅ `User=vestigia`; `uid=999(vestigia) gid=999(vestigia)`; PID 1 `java -jar app.jar` es de `vestigia` |
| Límite de 512 MB | `docker run --memory=512m …` y tres `smoke-test.sh` seguidos | ✅ los tres con código 0; 378 MB en uso; `OOMKilled=false` |
| Base caída | `docker stop vestigia-pg-7` | ✅ health `503` `{"status":"DOWN"}`; `GET /pueblos` `500` con `"message":"Error interno del servidor"`, sin stacktrace. Al volver la base, `UP` sin reiniciar |
| Sin variables de base | `docker run vestigia-backend:7` | ✅ el proceso termina con código 1 (`Connection to localhost:5432 refused`) |
| Contraseña equivocada | `-e DB_PASSWORD=equivocada` | ✅ código 1, `FATAL: password authentication failed for user …`; la contraseña no aparece en el log |

### 2.2 `scripts/smoke-test.sh` contra la imagen

| Escenario | Resultado | Código |
|---|---|:-:|
| Cadena completa, sin pueblo (eligió `JALA`), con `/` al final de la URL | 26 pasos con ✓, `AR 137, DEC +042`, `401` tras cerrar, `JALA` libre otra vez | ✅ 0 |
| Sin argumentos | Imprime el uso | ✅ 2 |
| Backend que no responde (`http://localhost:18999`) | `no hubo respuesta`, falla en el paso 1 | ✅ 1 |
| Pueblo fuera del catálogo (`atlantida`) | `se esperaba 201 y llegó 400`, con la respuesta | ✅ 1 |
| Pueblo ocupado (`BACALAR`, creado antes a mano) | `se esperaba 201 y llegó 409`; no toca la sala ajena | ✅ 1 |
| Sin `jq` en el `PATH` | `Falta 'jq'. Instálalo…` | ✅ 3 |
| `SIGTERM` a mitad (pasos 4 y 10) | `Limpieza: la sala ALAMOS se cerró (DELETE 204)`; `ALAMOS` libre | ✅ 130 |

Con `kill -INT` a un proceso en segundo plano el script no se interrumpió: bash ignora `SIGINT` en los procesos en segundo plano de un shell no interactivo, así que el `trap` no se puede instalar ahí. Con `Ctrl+C` en una terminal el proceso está en primer plano y sí lo recibe; la limpieza se comprobó con `SIGTERM`, que sigue el mismo camino (`trap 'exit 130' INT TERM`).

### 2.3 Logs sin tokens, contra la imagen

Con el contenedor corriendo se creó la sala `XICO` y se unió la app. Con los dos tokens se hicieron 8 peticiones REST (incluidas: el token en la query `?token=`, copiado dentro de `datos` de un descubrimiento, como clave de un aviso de puzzle y como `clave` de un descubrimiento; un token con una letra de más; el token de otra sala; la app intentando cambiar la escena) y 4 conexiones a `/ws` con `node` (`auth` del juego, de la app, un token inválido y el token también en la URL `?token=`), y se cerró la sala.

| Búsqueda en `docker logs` completo | Apariciones |
|---|---:|
| Token del juego (43 caracteres) | 0 |
| Token de la app (43 caracteres) | 0 |
| Los primeros 10 caracteres de cada token | 0 y 0 |
| `authorization` o `bearer` (sin distinguir mayúsculas) | 0 |

Lo que sí registra: `[API] GET /api/v1/salas/XICO/estado -> 200 | 5 ms`, `[WS] conectado sala=XICO rol=juego`, `[WS] auth rechazada <id de sesión>`.

### 2.4 Configuración y seguridad, contra la imagen

| Comprobación | Resultado |
|---|:-:|
| `/actuator` lista solo `self`, `health` y `health-path` | ✅ |
| `/actuator/info`, `env`, `beans`, `configprops`, `metrics`, `heapdump`, `threaddump`, `loggers`, `mappings`, `prometheus`, `shutdown` (también `POST`) | ✅ `404` |
| JSON malformado, `415`, `405` | ✅ cuerpo uniforme, `message` sin detalles internos |
| `GET /error` | ✅ `{"timestamp":…,"status":999,"error":"None"}` |
| `%2F` y `%zz` en la ruta (los rechaza Tomcat) | ✅ página `HTTP Status 400 – Bad Request` sin stacktrace ni versión |
| `GET /ws` sin saludo | ✅ `400` `Can "Upgrade" only to "WebSocket".` |
| Cabecera `Server` | ✅ no se envía |
| `spring.jpa.hibernate.ddl-auto` | ✅ `validate` (`application.properties` y `ConfiguracionDespliegueIT`) |

### 2.5 Swagger y la página de prueba (decisión de Raúl)

Estado actual: `/swagger-ui/index.html` `200`, `/swagger-ui.html` `302`, `/v3/api-docs` `200`, `/prueba-ws.html` `200`. springdoc avisa en el arranque `SpringDoc /v3/api-docs endpoint is enabled by default. To disable it in production…`. No se cambió nada; se comprobó cómo apagarlos con variables de entorno:

| Variables | `/v3/api-docs` | `/swagger-ui/index.html` | `/prueba-ws.html` | API, `404` y health |
|---|:-:|:-:|:-:|:-:|
| Ninguna (hoy) | `200` | `200` | `200` | sin cambios |
| `SPRINGDOC_API_DOCS_ENABLED=false`, `SPRINGDOC_SWAGGER_UI_ENABLED=false` | `404` | `404` | `200` | sin cambios |
| Las dos anteriores y `SPRING_WEB_RESOURCES_ADD_MAPPINGS=false` | `404` | `404` | `404` | sin cambios |
| Solo `SPRING_WEB_RESOURCES_ADD_MAPPINGS=false` | `200` | `200` | `404` | sin cambios |

Los `404` llegan con el cuerpo de error uniforme. Con springdoc apagado desaparece el aviso del log.

### 2.6 O10: el proxy TLS de Railway, simulado

Con `curl` contra la imagen: `Host: vestigia.example`, `Origin: https://vestigia.example` y `X-Forwarded-Proto: https`, como mandaría un navegador que abre `/prueba-ws.html` detrás del proxy. Cada celda es `/ws` (handshake de WebSocket) / `GET /api/v1/pueblos`.

| Escenario | Hoy | `native` (desde el host) | `framework` | URL pública en `CORS_ALLOWED_ORIGINS` |
|---|:-:|:-:|:-:|:-:|
| Proxy: Host público, Origin https, `X-Forwarded-Proto: https` | 403 / 403 | 403 / 403 | 101 / 200 | 101 / 200 |
| Proxy sin `X-Forwarded-Proto` | 403 / 403 | 403 / 403 | 403 / 403 | 101 / 200 |
| Proxy con `X-Forwarded-Host` en lugar de `Host` | 403 / 403 | 403 / 403 | 101 / 200 | 101 / 200 |
| Local: `Origin: http://localhost:18080` | 101 / 200 | 101 / 200 | 101 / 200 | 101 / 200 |
| Local: origen de `CORS_ALLOWED_ORIGINS` | 101 / 200 | 101 / 200 | 101 / 200 | 101 / 200 |
| Local: origen ajeno | 403 / 403 | 403 / 403 | 403 / 403 | 403 / 403 |
| Falsificado: origen y `X-Forwarded-Host` ajenos, proto https | 403 / 403 | 403 / 403 | 101 / 200 | 403 / 403 |
| Sin `Origin` (Unity, Flutter nativo) | 101 / 200 | 101 / 200 | 101 / 200 | 101 / 200 |
| `servers` de `/v3/api-docs` | `http://` | `http://` | `https://` | `http://` |

Con `native`, Tomcat solo acepta las cabeceras de una IP interna. Docker Desktop presenta las conexiones del host con una IP pública, así que desde el host no tuvo efecto. Desde otro contenedor de la misma red (IP `172.20.x`, interna) `native` sí dio `101` / `200` y `servers` con `https://`, y también aceptó el escenario falsificado. Ninguna opción cambia el comportamiento local. Ninguna se activó: es decisión de Raúl (`docs/despliegue.md`, O10).

### 2.7 Secretos, CI y arranque en frío

| Comprobación | Resultado |
|---|:-:|
| `git grep` de `password`, `secret`, `api_key`, `private_key`, `access_token` con valor | ✅ solo los valores de desarrollo `postgres`, la plantilla `tu-contraseña` y las variables vacías de `mvnw` |
| `git grep` de URLs con usuario y contraseña | ✅ solo el ejemplo `postgresql://usuario:contraseña@host:puerto/base` de `docs/despliegue.md` |
| Llaves privadas, tokens de GitHub, AWS, Slack u OpenAI | ✅ ninguno |
| Tokens de 43 caracteres con algún dígito | ✅ ninguno (solo nombres de métodos de prueba) |
| Archivos sensibles versionados (`.env`, `application-local.properties`, `.pem`, `.key`) | ✅ ninguno |
| Historial: URLs JDBC que no sean de desarrollo o ejemplo (`git log --all -p`) | ✅ ninguna |
| `actionlint` | No está instalado. Se corrió la imagen oficial `rhysd/actionlint:1.7.7` (contenedor `vestigia-actionlint-7`, borrado): ✅ 0 errores en `ci.yml`. Con dos errores sembrados en una copia (`ubuntu-latst`, `needs: verifi`) reportó los dos |
| `actions/checkout@v7` y `actions/setup-java@v6` existen | ✅ etiquetas `v7` y `v6` en GitHub (`git ls-remote`); últimas `v7.0.1` y `v6.2.0` |
| `ci.yml`: Java 21, `./mvnw -B verify`, `docker build` | ✅ sin cambios. `mvnw` y `scripts/smoke-test.sh` están en Git con modo `100755` |
| GitHub Actions | No se puede correr en local. Las 10 últimas ejecuciones (5 y 6 de noviembre) terminaron en `startup_failure`, sin nombre de workflow (`path: BuildFailed`). La última buena: 26 de octubre, `feature/base`. Repo privado; Actions habilitado y con todas las acciones permitidas. Queda como paso manual de Raúl (O11) |

### 2.8 Las pruebas detectan lo que dicen cubrir

Se rompió el archivo a propósito, se corrió la prueba indicada y se restauró:

| Mutación | Prueba | Resultado con la mutación |
|---|---|---|
| `scripts/smoke-test.sh` sin `trap cerrar_al_salir EXIT` | `SmokeTestFalloIT` | ❌ `Expecting actual … to contain` (no aparece `se cerró (DELETE 204)`) |
| `management.endpoints.web.exposure.include=health,env` | `ConfiguracionDespliegueIT` | ❌ 2 fallos: `actuatorSoloPublicaHealth` (`/actuator/env`) y `variablesDeEntorno` |

## 3. Requisito → pruebas

| Requisito del paso | Pruebas | Resultado |
|---|---|:-:|
| `docker build` termina bien | Sin prueba en Maven (la imagen no forma parte de `verify`; el job `docker` del CI la construye). Comprobado a mano en el worktree y en el arranque en frío (secciones 1 y 2.1) | ✅ manual |
| La imagen corre contra un Postgres propio, en una red propia, con `DB_URL`, `DB_USER`, `DB_PASSWORD` y `PORT` | Sin prueba en Maven. Comprobado a mano (sección 2.1); `ConfiguracionDespliegueIT` · application.properties toma DB_URL, DB_USER, DB_PASSWORD, PORT y CORS_ALLOWED_ORIGINS del entorno | ✅ |
| Flyway migra desde una base vacía | `EsquemaIT` · flywayAplicaV1YCreaLasCincoTablas (Testcontainers parte de una base vacía) · `ConfiguracionDespliegueIT` · Hibernate solo valida el esquema (ddl-auto=validate) y Flyway lo crea al arrancar. En la imagen: sección 2.1 | ✅ |
| `/actuator/health` responde `UP` | `AplicacionIT` · healthResponde200ConEstadoUp · `SmokeTestIT` (paso 1 del script). En la imagen: sección 2.1 | ✅ |
| El contenedor corre con usuario sin privilegios | Sin prueba automática: no hay forma de verlo desde Maven. `docker inspect`, `docker exec id` y `ps` (sección 2.1) | ✅ manual |
| `docs/pruebas/README.md` con el índice de todos los pasos | Sin prueba automática. Revisado: una fila por paso (3.0, 3a, 3b, 4, 6 y 7); el 5 está diferido | ✅ revisado |
| `scripts/smoke-test.sh` recorre la cadena completa contra una URL recibida | `SmokeTestIT` · smoke-test.sh recorre la partida completa contra el backend real, sale con 0, cierra la sala y no imprime tokens. Contra la imagen: sección 2.2 | ✅ |
| Toma un pueblo libre si no se le da uno | `SmokeTestFalloIT` · smoke-test.sh sin pueblo elige uno libre; si un paso falla a mitad, sale con 1, dice cuál y cierra la sala que creó | ✅ |
| Sale con código distinto de cero si algo falla | `SmokeTestIT` · smoke-test.sh sale con 1 si el backend no responde y con 2 si falta la URL · `SmokeTestFalloIT` (la misma). Contra la imagen: 400, 409, sin `jq` (sección 2.2) | ✅ |
| Cierra la sala al final aunque algo falle (trap) | `SmokeTestFalloIT` (la misma), con la mutación de la sección 2.8. `SIGTERM` contra la imagen (sección 2.2) | ✅ |
| Documentado, también contra la URL de Railway | Sin prueba automática. `README.md` (Prueba de humo) y `docs/despliegue.md` (Prueba de humo y paso 11 del primer despliegue) | ✅ revisado |
| Sin secretos en el repo | Sin prueba automática. `git grep` y `git log --all -p` (sección 2.7) | ✅ manual |
| `DB_URL`, `DB_USER`, `DB_PASSWORD`, `PORT` y `CORS_ALLOWED_ORIGINS` documentadas | `ConfiguracionDespliegueIT` · application.properties toma DB_URL, DB_USER, DB_PASSWORD, PORT y CORS_ALLOWED_ORIGINS del entorno. Documentadas en `README.md` y `docs/despliegue.md` | ✅ |
| Actuator expone solo `health` | `ConfiguracionDespliegueIT` · Actuator publica solo health: info, env, beans, metrics, heapdump y los demás responden 404. Contra la imagen: sección 2.4 | ✅ |
| Los logs no contienen tokens ni `Authorization`, contra la imagen Docker | Contra la imagen: sección 2.3 (0 apariciones). En Maven: `WebSocketIT` · Ni los tokens ni Authorization aparecen en los logs durante auth, auth rechazada, errores y desconexión · `config.ApiHttpConfigTest` · registraUnaLineaPorPeticionSinTokens · `SmokeTestIT` (la salida del script no tiene tokens) | ✅ |
| `ddl-auto=validate` | `ConfiguracionDespliegueIT` · Hibernate solo valida el esquema (ddl-auto=validate) y Flyway lo crea al arrancar | ✅ |
| Mensajes de error sin stacktrace | `controller.ApiExceptionHandlerTest` · errorInesperadoDevuelve500SinDetallesInternos · `ConfiguracionDespliegueIT` · Los errores fuera de /api no muestran stacktrace, excepción ni versión del servidor. Contra la imagen, incluida la base caída: secciones 2.1 y 2.4 | ✅ |
| Swagger UI expuesto o no, reportado como decisión de Raúl | `AplicacionIT` · swaggerUiYOpenApiEstanDisponibles (fija el estado actual). Cómo apagarlo, comprobado contra la imagen (sección 2.5). Decisión D-Swagger y D-Prueba en `docs/despliegue.md` | ✅ reportado |
| CI: Java 21, `./mvnw verify`, build de Docker; `actionlint` | `actionlint` 1.7.7 desde su imagen oficial: 0 errores (sección 2.7). GitHub Actions no se puede correr en local y en GitHub no arranca (O11) | ⚠️ sin ejecución en GitHub |
| Arranque en frío: clonar la rama, `./mvnw verify` y `docker build` | Sección 1: 242 pruebas sin fallos y `docker build` con código 0 | ✅ manual |
| `docs/despliegue.md` con "Primer despliegue: pasos para Raúl" y los nombres de Railway sin verificar marcados | Sin prueba automática. Revisado: 15 pasos con casillas, cada nombre de Railway marcado "(por verificar en Railway)" | ✅ revisado |
| O10: `server.forward-headers-strategy`, probado con `X-Forwarded-Proto: https` y `Origin: https://…` | Contra la imagen (sección 2.6). Propuesto como decisión de Raúl, sin activar | ✅ reportado |

## 4. Lista completa de pruebas


| Tipo | Clase | Prueba | Tiempo (s) | Resultado |
|---|---|---|---:|---|
| unitaria | `config.ApiHttpConfigTest` | corsRechazaOrigenNoConfigurado | 0.021 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | corsPermiteOrigenConfigurado | 0.003 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | registraUnaLineaPorPeticionSinTokens(CapturedOutput) | 0.013 | ✅ pasó |
| unitaria | `config.ApiHttpConfigTest` | noRegistraRutasFueraDeApi(CapturedOutput) | 0.016 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Los tipos correctos se siguen leyendo, y en un objeto libre (Map) cada valor conserva su tipo JSON | 0.015 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Una clave repetida en el mismo objeto falla en la raíz, anidada, en un Map o en un objeto libre, aunque los dos valores coincidan | 0.003 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un texto o un número en un campo booleano no se convierte: falla, también "true" entre comillas | 0.001 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un texto o un booleano en un campo numérico no se convierte: falla | 0.001 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | Un número o un booleano en un campo de texto no se convierte a texto: falla | 0.001 | ✅ pasó |
| unitaria | `config.JacksonConfigTest` | La misma clave en objetos distintos, hermanos o anidados, se sigue leyendo | 0.001 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | Los errores sin código traen "codigo": null en 400, 401, 403, 404, 405, 409 y 500 | 0.015 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | Los errores salen en JSON aunque el cliente pida otro formato: 406 con Accept XML y 409 con Accept HTML | 0.003 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | rutaInexistenteDevuelve404ConCuerpoUniforme | 0.002 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | jsonMalformadoDevuelve400ConCuerpoUniforme | 0.002 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | validacionDevuelve400ConDetallePorCampo | 0.005 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | errorInesperadoDevuelve500SinDetallesInternos | 0.002 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | Un error con código lo lleva en codigo y sus detalles en details, con el mismo cuerpo uniforme | 0.002 | ✅ pasó |
| unitaria | `controller.ApiExceptionHandlerTest` | conflictoConservaSuCodigoYMensaje | 0.003 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Sin clave, con una clave que no es texto o con datos que no son un objeto JSON da 400 sin llamar al servicio | 0.014 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Unos datos de más de 4096 bytes en JSON dan 400 con detalle en datos, sin llamar al servicio | 0.003 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Sin token da 401 y con el token de otra sala da 403, sin llamar al servicio | 0.002 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Una clave del servidor responde 403 con el cuerpo de error uniforme | 0.003 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Repetir una clave responde 200 con el descubrimiento que ya existía | 0.004 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Una clave vacía, con mayúsculas, guiones, acentos, espacios o de más de 40 caracteres da 400 con detalle | 0.013 | ✅ pasó |
| unitaria | `controller.DescubrimientoControllerTest` | Un descubrimiento nuevo responde 201 con clave, datos, origen y creado_en | 0.003 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Sin datos, con datos null o con el aviso fuera de datos da 400 con details en datos y codigo null, sin llamar al servicio | 0.014 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Sin token da 401 y con el token de otra sala da 403, con codigo null y sin llamar al servicio | 0.003 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Un aviso inválido para el puzzle responde 400 con el details del servicio y codigo null | 0.003 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | PUT puzzle responde 200 con {puzzle, estado, datos} y pasa al servicio el id de la ruta y los datos | 0.005 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Un puzzle bloqueado responde 409 con codigo puzzle_bloqueado en el cuerpo uniforme | 0.004 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | datos que no es un objeto JSON llega al servicio tal cual, para que responda 400 con details | 0.005 | ✅ pasó |
| unitaria | `controller.PuzzleControllerTest` | Los valores de datos llegan al servicio con su tipo JSON: "true" es texto, 1 es número y true es booleano | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | La app recibe 403 en PUT escena y en DELETE antes de validar el cuerpo, sin llamar al servicio | 0.023 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | pueblosPasaElFiltroDeDisponibles | 0.014 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeOtraSalaDa403 | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena sin token da 401 | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | unirseNoPideTokenYDevuelveElDeLaApp | 0.009 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearResponde201ConCodigoEstadoYToken | 0.027 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | DELETE de la sala responde 204 sin cuerpo y sin token da 401 | 0.004 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena sin escena o con escena vacía da 400 con detalle, sin llamar al servicio | 0.008 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDesconocidoDa401 | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearSinCodigoDa400ConDetalle | 0.002 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoConTokenDeLaSalaDevuelveElEstadoEnSnakeCase | 0.008 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | PUT escena responde 200 con escena_actual | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | El token de otra sala recibe 403 en PUT escena y DELETE, sin llamar al servicio | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | crearConPuebloOcupadoDa409ConCuerpoUniforme | 0.003 | ✅ pasó |
| unitaria | `controller.SalaControllerTest` | estadoSinTokenDa401 | 0.003 | ✅ pasó |
| unitaria | `dto.JsonAcotadoTest` | Los datos que caben en el límite, en bytes de su JSON compacto, son válidos; nulos también | 0.062 | ✅ pasó |
| unitaria | `dto.JsonAcotadoTest` | Un byte más del límite es inválido, y los acentos cuentan como dos bytes | 0.031 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear devuelve la sala si sigue viva | 0.045 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Renovar pone ultima_actividad en ahora y expira_en en ahora más la expiración configurada | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar el cierre de una sala finalizada guarda el evento y ultima_actividad, pero no renueva expira_en | 0.000 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Los tipos de evento se guardan con su punto: sala.creada, app.unida, descubrimiento.registrado... | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar la expiración guarda y publica sala.expirada con origen servidor sin tocar ultima_actividad ni expira_en | 0.002 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Registrar renueva la sala, guarda el evento y lo publica para el WebSocket del Paso 4 | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear una sala finalizada, expirada o inexistente responde 401 | 0.001 | ✅ pasó |
| unitaria | `service.ActividadServiceTest` | Bloquear una sala viva cuyo expira_en ya pasó responde 401 aunque el job no la haya marcado | 0.000 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Si la sala terminó entre el token y la escritura, el 401 de bloquear se propaga sin guardar nada | 0.082 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | El descubrimiento se guarda en la sala del token con la clave recibida | 0.002 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Repetir una clave devuelve el descubrimiento original sin guardar otro, sin evento y renovando la sala | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Un descubrimiento nuevo se guarda con el origen del rol, guarda su evento y luego recalcula los puzzles | 0.002 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Sin datos, el descubrimiento se guarda con un objeto vacío y origen juego si lo manda el juego | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Las cuatro claves del servidor dan 403 con cualquier rol y no tocan la sala | 0.001 | ✅ pasó |
| unitaria | `service.DescubrimientoServiceTest` | Un resultado del servidor se guarda con origen servidor y sus datos, guarda su evento y luego recalcula los puzzles | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | radio se habilita con pescador_dialogo_completo y no antes | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | photo se habilita solo con las tres piezas, lleguen en el orden que lleguen | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Solo un puzzle bloqueado pasa a disponible: los que están disponibles, en curso o resueltos no cambian | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Recalcular pasa a disponible los puzzles habilitados y guarda un evento puzzle.habilitado de origen servidor con {puzzle, estado, datos} por cada uno | 0.002 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | observatory pide coordenada_a, coordenada_b, observatory_enabled y lookout resuelto; falta uno y no se habilita | 0.000 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | Recalcular sin requisitos nuevos no cambia nada ni guarda eventos | 0.001 | ✅ pasó |
| unitaria | `service.HabilitacionPuzzlesServiceTest` | lookout pide photo resuelto y lookout_enabled; la bandera puede llegar antes o después | 0.000 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | en_curso más iniciado y resuelto más cualquier aviso responden el estado actual, solo renuevan la sala y no guardan eventos ni descubrimientos | 0.030 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | El evento lleva el origen del rol que avisó: juego o app | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Si la sala terminó entre el token y la escritura, el 401 de bloquear se propaga sin leer ni tocar el puzzle | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Las coordenadas salen de la configuración, sin convertirse a número | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | disponible más iniciado pasa a en_curso, guarda los datos y puzzle.iniciado con {puzzle, estado, datos} y el origen del rol, sin crear descubrimiento | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Un puzzle bloqueado da 409 con codigo puzzle_bloqueado ante cualquier aviso válido y no cambia nada | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Cada puzzle crea su resultado con origen servidor: coordenada_a con AR "137", coordenada_b con DEC "+042" como texto, observatorio_visible y magnetar_localizado | 0.003 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | disponible o en_curso más completada pasa a resuelto: guarda puzzle.resuelto y después crea el resultado | 0.002 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Si falta la fila del puzzle, un estado imposible, falla con un mensaje que nombra la sala y el puzzle, sin escribir nada | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | radio solo acepta {sintonizada: true}: iniciado o completada dan 400; los puzzles de la app no aceptan sintonizada | 0.003 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Solo vale un objeto con una clave aceptada y el booleano true: un valor que no es objeto, texto, número, false, null, claves extra, vacío o una clave ajena dan 400 con details en datos | 0.001 | ✅ pasó |
| unitaria | `service.PuzzleServiceTest` | Un id de puzzle desconocido da 400 con la lista de ids, también los viejos fotografia, mirador y observatorio y las variantes de mayúsculas o espacios | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | crearFueraDelCatalogoDa400SinTocarSalas | 0.071 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | Cambiar la escena la guarda y registra escena.cambiada con la anterior y la nueva | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | otraViolacionDeIntegridadNoSeDisfrazaDe409 | 0.003 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseSinSalaVivaDa404 | 0.001 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | listarPueblosMarcaOcupadosLosQueTienenSalaViva | 0.002 | ✅ pasó |
| unitaria | `service.SalaServiceTest` | unirseEnlazaConUnSoloUpdateYRenuevaLaExpiracion | 0.002 | ✅ pasó |
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
| unitaria | `service.SalaServiceTest` | Autenticar con el token de una sala viva con expira_en vencido no da credencial; justo en expira_en sí | 0.001 | ✅ pasó |
| unitaria | `ws.EmisorEventosTest` | Cada tipo de evento tiene su emisión: puzzle.actualizado y descubrimiento.nuevo a todos, escena.cambiada solo a la app, sala.cerrada cierra la sala, crear y unirse no emiten | 0.028 | ✅ pasó |
| unitaria | `ws.SalaWebSocketHandlerTest` | Al autenticarse, el cierre programado por el plazo de auth se cancela | 0.065 | ✅ pasó |
| unitaria | `ws.SalaWebSocketHandlerTest` | Un auth rechazado no cancela el plazo: la sesión se cierra con 4001 y el plazo ya no hace nada | 0.000 | ✅ pasó |
| unitaria | `ws.SalaWebSocketHandlerTest` | Al abrir la conexión fija BLOCKING_SEND_TIMEOUT de Tomcat en la sesión con vestigia.ws.envio-limite, en milisegundos | 0.021 | ✅ pasó |
| unitaria | `ws.SalaWebSocketHandlerTest` | Al cerrarse una conexión sin autenticar, el cierre programado por el plazo de auth se cancela | 0.001 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | desconectar olvida una sesión rechazada, así que no queda guardada para siempre | 0.023 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Una sala que se cerró después de validar el token no registra la conexión: conectar devuelve false y no envía nada | 0.002 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | auth.ok es el primer mensaje de una conexión y los demás salen en el orden en que se encolaron | 0.003 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | La conexión reemplazada sigue autenticada hasta que Tomcat la cierra: un auth o un mensaje suyo no crean otra conexión ni otro close, y al desconectarse no avisa | 0.001 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Si la cola de una conexión pasa del límite de bytes, se descarta y la conexión se cierra con 4500 | 0.004 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Al desconectarse, lo que quedaba en la cola se descarta: no hay otro envío ni otro close sobre la sesión cerrada | 0.212 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Tras cerrarSala, la sesión sigue autenticada hasta que Tomcat la cierra: responder, conectar o cerrarSiNoSeAutentico no producen otro close ni otro envío | 0.003 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Si un envío falla, la conexión se cierra con 4500 y lo que seguía en su cola se descarta | 0.001 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Una sesión que el servidor ya decidió cerrar sin auth no se registra aunque su auth llegue después | 0.000 | ✅ pasó |
| unitaria | `ws.SesionesWebSocketTest` | Un envío que se bloquea no frena a nadie: enviar, dispositivos y la otra sala responden, y al soltarse sale todo en orden | 0.023 | ✅ pasó |
| integración | `AplicacionIT` | El programador que comparten el plazo de auth, los pings y el job de expiración tiene dos hilos | 0.001 | ✅ pasó |
| integración | `AplicacionIT` | rutaInexistenteBajoSalasDa404SinPedirToken | 0.003 | ✅ pasó |
| integración | `AplicacionIT` | elJobDeExpiracionEstaProgramadoCadaMinuto | 0.003 | ✅ pasó |
| integración | `AplicacionIT` | openApiDocumentaLosEndpointsDeSalasYElToken | 0.194 | ✅ pasó |
| integración | `AplicacionIT` | swaggerUiYOpenApiEstanDisponibles | 0.011 | ✅ pasó |
| integración | `AplicacionIT` | GET /pueblos con Accept: application/xml responde 406 con el error uniforme en JSON, no con un cuerpo vacío | 0.010 | ✅ pasó |
| integración | `AplicacionIT` | healthResponde200ConEstadoUp | 0.004 | ✅ pasó |
| integración | `CatalogoPueblosIT` | flywayAplicaV2YLosCodigosEstanEnLaTabla | 0.004 | ✅ pasó |
| integración | `CatalogoPueblosIT` | seedDeclara101CodigosValidosYSinDuplicados | 0.002 | ✅ pasó |
| integración | `ConfiguracionDespliegueIT` | Actuator publica solo health: info, env, beans, metrics, heapdump y los demás responden 404 | 0.018 | ✅ pasó |
| integración | `ConfiguracionDespliegueIT` | Hibernate solo valida el esquema (ddl-auto=validate) y Flyway lo crea al arrancar | 0.001 | ✅ pasó |
| integración | `ConfiguracionDespliegueIT` | Los errores fuera de /api no muestran stacktrace, excepción ni versión del servidor | 0.006 | ✅ pasó |
| integración | `ConfiguracionDespliegueIT` | application.properties toma DB_URL, DB_USER, DB_PASSWORD, PORT y CORS_ALLOWED_ORIGINS del entorno | 0.001 | ✅ pasó |
| integración | `DocumentacionIT` | La página de prueba /prueba-ws.html se sirve como HTML, manda el token en auth y no lo pone en la URL ni en el navegador | 0.003 | ✅ pasó |
| integración | `DocumentacionIT` | Cada operación tiene resumen, descripción y ejemplos; cada error usa ErrorBody con codigo en su ejemplo | 0.008 | ✅ pasó |
| integración | `DocumentacionIT` | Swagger UI se sirve en /swagger-ui/index.html | 0.002 | ✅ pasó |
| integración | `DocumentacionIT` | Las rutas con token declaran el esquema Bearer y las públicas no; el esquema es HTTP bearer | 0.005 | ✅ pasó |
| integración | `DocumentacionIT` | /v3/api-docs documenta exactamente las rutas y métodos reales de los controladores de la API | 0.005 | ✅ pasó |
| integración | `EscriturasSalaIT` | Con expira_en vencido y sin que corra el job, unirse da 404 y GET estado da 401 a los dos tokens; la sala no revive | 0.028 | ✅ pasó |
| integración | `EscriturasSalaIT` | pescador_dialogo_completo habilita radio y GET estado lo muestra disponible | 0.017 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una sala en esperando_app también se puede cerrar | 0.012 | ✅ pasó |
| integración | `EscriturasSalaIT` | El token del juego de otra sala recibe 403 en PUT escena y DELETE, y la sala no cambia | 0.017 | ✅ pasó |
| integración | `EscriturasSalaIT` | lookout_enabled que llega después de photo resuelto habilita lookout en la misma petición | 0.015 | ✅ pasó |
| integración | `EscriturasSalaIT` | Diez peticiones simultáneas con la misma clave dan un 201 y nueve 200, una sola fila y un solo evento | 0.025 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una escena inválida, vacía, de otro tipo o ausente da 400 y no cambia nada | 0.026 | ✅ pasó |
| integración | `EscriturasSalaIT` | El juego cierra la sala: 204, queda finalizada sin renovar expira_en, guarda sala.finalizada y los dos tokens dan 401 | 0.028 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un descubrimiento, nuevo o repetido, renueva ultima_actividad y expira_en con la duración configurada | 0.017 | ✅ pasó |
| integración | `EscriturasSalaIT` | Una sala con expira_en vencido que el job aún no marcó responde 401 a toda escritura y no revive | 0.023 | ✅ pasó |
| integración | `EscriturasSalaIT` | Cerrar la sala libera el código: se puede crear otra sala con el mismo pueblo y la cerrada queda como historial | 0.018 | ✅ pasó |
| integración | `EscriturasSalaIT` | Crear y unirse guardan los eventos sala.creada (origen juego) y app.unida (origen app) | 0.010 | ✅ pasó |
| integración | `EscriturasSalaIT` | Cada escritura deja su evento en orden: sala.creada, app.unida, descubrimiento.registrado, puzzle.habilitado, escena.cambiada y sala.finalizada | 0.023 | ✅ pasó |
| integración | `EscriturasSalaIT` | El juego cambia la escena: 200, GET estado la muestra, guarda escena.cambiada y renueva expira_en | 0.015 | ✅ pasó |
| integración | `EscriturasSalaIT` | Repetir la escena actual responde 200, renueva expira_en y no guarda otro escena.cambiada | 0.014 | ✅ pasó |
| integración | `EscriturasSalaIT` | Repetir una clave con otros datos responde 200 con los datos originales y no guarda otro evento | 0.015 | ✅ pasó |
| integración | `EscriturasSalaIT` | La app recibe 403 al cerrar la sala y la sala sigue activa | 0.014 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un JSON con una clave repetida da 400 JSON malformado en crear sala, descubrimientos, escena y puzzles, sin escribir nada; los mismos cuerpos sin repetir siguen funcionando | 0.040 | ✅ pasó |
| integración | `EscriturasSalaIT` | lookout y observatory se habilitan por servicio cuando se resuelve el puzzle previo, aunque la bandera haya llegado antes | 0.025 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un descubrimiento nuevo responde 201, aparece en GET estado y guarda descubrimiento.registrado con el origen del rol | 0.019 | ✅ pasó |
| integración | `EscriturasSalaIT` | Las tres piezas enviadas a la vez, repetidas, habilitan photo una sola vez | 0.030 | ✅ pasó |
| integración | `EscriturasSalaIT` | Las cuatro claves del servidor dan 403 al juego y a la app y no se guardan | 0.026 | ✅ pasó |
| integración | `EscriturasSalaIT` | El job guarda sala.expirada con origen servidor y deja ultima_actividad en la última escritura | 0.017 | ✅ pasó |
| integración | `EscriturasSalaIT` | Unos datos que pasan de 4096 bytes en JSON dan 400 y no se guardan; justo en el límite responden 201 | 0.016 | ✅ pasó |
| integración | `EscriturasSalaIT` | La app recibe 403 al cambiar la escena, con cuerpo válido, inválido o vacío, y la escena no cambia | 0.016 | ✅ pasó |
| integración | `EscriturasSalaIT` | DELETE simultáneo con nueve descubrimientos: los que pierden reciben 401 y no dejan filas ni eventos después del cierre | 0.053 | ✅ pasó |
| integración | `EscriturasSalaIT` | Un número o booleano JSON donde va texto da 400 sin convertirse: clave, escena y código de pueblo | 0.029 | ✅ pasó |
| integración | `EsquemaIT` | salasTerminadasNoBloqueanUnaNuevaSalaViva | 0.008 | ✅ pasó |
| integración | `EsquemaIT` | indiceEventosPorSalaYTiempoExiste | 0.004 | ✅ pasó |
| integración | `EsquemaIT` | noPuedeHaberDosSalasVivasConElMismoCodigo | 0.003 | ✅ pasó |
| integración | `EsquemaIT` | catalogoRechazaCodigosConMinusculasAcentosOEnie | 0.003 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaCodigoFueraDelCatalogo | 0.001 | ✅ pasó |
| integración | `EsquemaIT` | flywayAplicaV1YCreaLasCincoTablas | 0.002 | ✅ pasó |
| integración | `EsquemaIT` | alFinalizarUnaSalaElCodigoSePuedeReclamarDeNuevo | 0.002 | ✅ pasó |
| integración | `EsquemaIT` | carreraPorElMismoPuebloDejaUnaSolaSalaViva | 0.003 | ✅ pasó |
| integración | `EsquemaIT` | salasRechazaEstadoInvalido | 0.002 | ✅ pasó |
| integración | `JsonDocumentadoIT` | Cada ejemplo de respuesta de Swagger (/v3/api-docs) tiene los campos y tipos de una respuesta real | 0.080 | ✅ pasó |
| integración | `JsonDocumentadoIT` | Cada bloque JSON de respuesta de la documentación tiene los campos y tipos de una respuesta real | 0.010 | ✅ pasó |
| integración | `JsonDocumentadoIT` | La comparación detecta campos de más o de menos, tipos distintos, null fuera de codigo, coordenadas numéricas, avisos que no son booleanos y valores fuera de los enums | 0.004 | ✅ pasó |
| integración | `JsonDocumentadoIT` | Cada bloque JSON de api.md, tiempo-real.md y las tres guías está marcado como respuesta o como petición | 0.003 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | Tras V3, el CHECK de puzzles_estado rechaza fotografia, mirador y observatorio y acepta los ids nuevos | 0.293 | ✅ pasó |
| integración | `MigracionIdsPuzzlesIT` | V3 renombra a inglés los puzzles de una sala creada con V2, conserva su estado y sus datos, y ese esquema rechaza fotografia | 0.159 | ✅ pasó |
| integración | `PuzzlesIT` | Con expira_en vencido y sin que corra el job, PUT puzzles responde 401 y no resuelve nada | 0.082 | ✅ pasó |
| integración | `PuzzlesIT` | Cada cambio de estado de un puzzle guarda {puzzle, estado, datos} y cada resultado guarda {clave, datos} con origen servidor | 0.085 | ✅ pasó |
| integración | `PuzzlesIT` | Los errores reales de PUT puzzles traen codigo null en 400, 401, 403, 404 y 405, y puzzle_bloqueado en el 409 | 0.056 | ✅ pasó |
| integración | `PuzzlesIT` | Resolver es una sola transacción: si crear el resultado falla, el puzzle no queda resuelto, no se guarda ningún evento y la sala no se renueva | 0.087 | ✅ pasó |
| integración | `PuzzlesIT` | Un cliente no puede fabricar un resultado del servidor: POST de las cuatro claves da 403 y PUT de un puzzle bloqueado da 409, sin crear nada; el resultado real tiene origen servidor y los datos de la configuración | 0.088 | ✅ pasó |
| integración | `PuzzlesIT` | lookout_enabled y observatory_enabled que llegan antes que sus requisitos habilitan su puzzle en el PUT que completa el último requisito | 0.119 | ✅ pasó |
| integración | `PuzzlesIT` | lookout_enabled y observatory_enabled que llegan después de sus requisitos habilitan su puzzle en ese mismo POST | 0.115 | ✅ pasó |
| integración | `PuzzlesIT` | "+042" llega intacto como texto en GET estado y en la base (jsonb string), igual que "137" | 0.069 | ✅ pasó |
| integración | `PuzzlesIT` | Matriz de estados: bloqueado da 409; disponible+iniciado pasa a en_curso; en_curso+iniciado no cambia; en_curso+completada resuelve; resuelto+cualquier aviso no cambia ni duplica | 0.100 | ✅ pasó |
| integración | `PuzzlesIT` | observatory espera coordenada_a: con lookout resuelto y su bandera sigue bloqueado hasta que se resuelve radio, aunque llegue al final | 0.071 | ✅ pasó |
| integración | `PuzzlesIT` | Solo el booleano true: texto "true", 1, false, null, claves extra, objeto vacío, datos que no es objeto o datos ausente dan 400 con details en datos y no cambian nada; iniciado en radio también | 0.094 | ✅ pasó |
| integración | `PuzzlesIT` | Resolver un puzzle actualiza ultima_actividad y renueva expira_en; repetir el aviso también renueva, sin guardar eventos | 0.035 | ✅ pasó |
| integración | `PuzzlesIT` | Un id de puzzle desconocido da 400 con la lista de ids, igual para los viejos fotografia, mirador y observatorio, y no toca la sala | 0.024 | ✅ pasó |
| integración | `PuzzlesIT` | Diez completada simultáneos sobre photo responden 200 y dejan una sola coordenada_b, un solo puzzle.resuelto y lookout habilitado una vez | 0.065 | ✅ pasó |
| integración | `PuzzlesIT` | La cadena completa por HTTP: pescador → radio → coordenada_a; tres piezas → photo → coordenada_b; lookout_enabled → lookout; observatory_enabled → observatory → magnetar_localizado | 0.101 | ✅ pasó |
| integración | `SalaIT` | crearDevuelveElTokenUnaVezYGuardaSoloSuHash | 0.025 | ✅ pasó |
| integración | `SalaIT` | carreraDeAppsPorUnirseDaUn200YSiete409 | 0.031 | ✅ pasó |
| integración | `SalaIT` | unirseSinSalaVivaDa404 | 0.009 | ✅ pasó |
| integración | `SalaIT` | flujoCrearUnirseEstado | 0.024 | ✅ pasó |
| integración | `SalaIT` | elJobNoTocaSalasFinalizadas | 0.029 | ✅ pasó |
| integración | `SalaIT` | unirseAceptaMinusculas | 0.013 | ✅ pasó |
| integración | `SalaIT` | pueblosReflejaLasSalasVivas | 0.014 | ✅ pasó |
| integración | `SalaIT` | estadoExigeElTokenDeEsaSala | 0.021 | ✅ pasó |
| integración | `SalaIT` | salaVencidaExpiraLiberaElNombreYSeConservaComoHistorial | 0.024 | ✅ pasó |
| integración | `SalaIT` | unirseRenuevaLaExpiracion | 0.013 | ✅ pasó |
| integración | `SalaIT` | carreraPorElMismoPuebloDaUn201YSiete409 | 0.010 | ✅ pasó |
| integración | `SalaIT` | crearFueraDelCatalogoDa400 | 0.003 | ✅ pasó |
| integración | `SalaIT` | crearAceptaMinusculasYEspacios | 0.007 | ✅ pasó |
| integración | `SalaIT` | crearInsertaLosCuatroPuzzlesBloqueados | 0.007 | ✅ pasó |
| integración | `SalaIT` | unirseDosVecesDa409 | 0.013 | ✅ pasó |
| integración | `SalaIT` | salaQueAunNoVenceSigueViva | 0.008 | ✅ pasó |
| integración | `SmokeTestFalloIT` | smoke-test.sh sin pueblo elige uno libre; si un paso falla a mitad, sale con 1, dice cuál y cierra la sala que creó | 0.219 | ✅ pasó |
| integración | `SmokeTestIT` | smoke-test.sh sale con 1 si el backend no responde y con 2 si falta la URL | 0.034 | ✅ pasó |
| integración | `SmokeTestIT` | smoke-test.sh recorre la partida completa contra el backend real, sale con 0, cierra la sala y no imprime tokens | 0.983 | ✅ pasó |
| integración | `repository.ModeloIT` | guardaYLeeSalaPuzzlesYDescubrimientosConJsonb | 0.032 | ✅ pasó |
| integración | `ws.ConcurrenciaWebSocketIT` | Una conexión cuyo envío se queda bloqueado no frena GET estado, ni las escrituras de su sala, ni al otro dispositivo, ni a otra sala | 0.237 | ✅ pasó |
| integración | `ws.ConcurrenciaWebSocketIT` | auth contra la expiración: si el job expira la sala entre validar el token y registrar la conexión, no queda conexión viva | 0.055 | ✅ pasó |
| integración | `ws.ConcurrenciaWebSocketIT` | auth contra DELETE: si la sala se cierra entre validar el token y registrar la conexión, no queda conexión viva | 0.038 | ✅ pasó |
| integración | `ws.ConcurrenciaWebSocketIT` | El job no expira una sala que una escritura simultánea renueva: espera su candado y vuelve a evaluar expira_en | 0.030 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Al cerrar la sala, los dos reciben sala.cerrada con estado finalizada y luego el servidor cierra con 1000 | 0.033 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | escena.cambiada llega solo a la app, con la escena nueva y la anterior; el juego no la recibe | 0.243 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Al expirar la sala, el job hace que los dos reciban sala.cerrada con estado expirada y cierra con 1000 | 0.063 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Los mensajes salen después del commit: nada mientras la transacción sigue abierta, y un GET estado pedido al recibir el mensaje ya ve el cambio aunque el commit tarde; un rollback no emite nada | 2.087 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Cada escritura REST de la cadena completa emite a los dos dispositivos su descubrimiento.nuevo y un puzzle.actualizado por cada cambio de estado, incluido bloqueado → disponible; otra sala no recibe nada | 0.324 | ✅ pasó |
| integración | `ws.EmisionWebSocketIT` | Las escrituras repetidas (descubrimiento, aviso de puzzle, misma escena) responden con éxito y no emiten nada | 0.048 | ✅ pasó |
| integración | `ws.WebSocketIT` | Antes de auth, otro tipo, JSON inválido o un mensaje binario cierran con 4000 | 0.009 | ✅ pasó |
| integración | `ws.WebSocketIT` | Ni los tokens ni Authorization aparecen en los logs durante auth, auth rechazada, errores y desconexión | 0.020 | ✅ pasó |
| integración | `ws.WebSocketIT` | Un token desconocido, vacío, que no es texto o ausente cierra con 4001 sin auth.ok | 0.007 | ✅ pasó |
| integración | `ws.WebSocketIT` | Origin: un navegador de un origen de CORS_ALLOWED_ORIGINS se conecta; otro origen recibe 403 en el handshake; sin Origin se conecta | 0.015 | ✅ pasó |
| integración | `ws.WebSocketIT` | Una segunda conexión del mismo rol reemplaza a la primera: la vieja cierra con 4009 y el otro rol no recibe desconectado | 0.347 | ✅ pasó |
| integración | `ws.WebSocketIT` | Presencia: cada conexión avisa dispositivo.conectado al otro rol, cada cierre dispositivo.desconectado, y GET estado lo refleja | 0.290 | ✅ pasó |
| integración | `ws.WebSocketIT` | auth con el token del juego o de la app responde auth.ok con su rol y la sala | 0.045 | ✅ pasó |
| integración | `ws.WebSocketIT` | El token de una sala cerrada o con expira_en vencido cierra con 4001, aunque el job no la haya marcado | 0.028 | ✅ pasó |
| integración | `ws.WebSocketIT` | El servidor manda pings a las conexiones autenticadas, programados cada 25 s | 0.040 | ✅ pasó |
| integración | `ws.WebSocketIT` | Ya autenticado, JSON inválido, binario, tipo desconocido, orientacion o un segundo auth responden error y la conexión sigue | 0.023 | ✅ pasó |
| integración | `ws.WebSocketIT` | Sin auth en 5 s el servidor cierra con 4008; una conexión que se autenticó a tiempo sigue abierta | 5.540 | ✅ pasó |
| integración | `ws.WebSocketIT` | Reconexión: la app vuelve con el mismo token, recibe auth.ok y no el mensaje que se perdió, y GET estado trae lo que pasó mientras no estaba | 0.272 | ✅ pasó |
| integración | `ws.WebSocketIT` | Un mensaje de 4096 caracteres recibe su error; uno de 4097 cierra con 1009 y avisa desconectado al otro rol | 0.031 | ✅ pasó |

## 5. Salida de `./mvnw clean verify`

Pruebas unitarias (`surefire`), línea 519 del log:

```
[INFO] Tests run: 124, Failures: 0, Errors: 0, Skipped: 0
```

Pruebas de integración (`failsafe`) y cierre:

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.vestigia.MigracionIdsPuzzlesIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.905 s -- in com.vestigia.MigracionIdsPuzzlesIT
[INFO] Running com.vestigia.SmokeTestIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.024 s -- in com.vestigia.SmokeTestIT
[INFO] Running com.vestigia.PuzzlesIT
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.206 s -- in com.vestigia.PuzzlesIT
[INFO] Running com.vestigia.AplicacionIT
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.234 s -- in com.vestigia.AplicacionIT
[INFO] Running com.vestigia.repository.ModeloIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.040 s -- in com.vestigia.repository.ModeloIT
[INFO] Running com.vestigia.SmokeTestFalloIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.557 s -- in com.vestigia.SmokeTestFalloIT
[INFO] Running com.vestigia.ConfiguracionDespliegueIT
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.031 s -- in com.vestigia.ConfiguracionDespliegueIT
[INFO] Running com.vestigia.EsquemaIT
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.033 s -- in com.vestigia.EsquemaIT
[INFO] Running com.vestigia.CatalogoPueblosIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.010 s -- in com.vestigia.CatalogoPueblosIT
[INFO] Running com.vestigia.DocumentacionIT
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.029 s -- in com.vestigia.DocumentacionIT
[INFO] Running com.vestigia.SalaIT
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.268 s -- in com.vestigia.SalaIT
[INFO] Running com.vestigia.JsonDocumentadoIT
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.103 s -- in com.vestigia.JsonDocumentadoIT
[INFO] Running com.vestigia.EscriturasSalaIT
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.607 s -- in com.vestigia.EscriturasSalaIT
[INFO] Running com.vestigia.ws.WebSocketIT
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.687 s -- in com.vestigia.ws.WebSocketIT
[INFO] Running com.vestigia.ws.EmisionWebSocketIT
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.810 s -- in com.vestigia.ws.EmisionWebSocketIT
[INFO] Running com.vestigia.ws.ConcurrenciaWebSocketIT
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.370 s -- in com.vestigia.ws.ConcurrenciaWebSocketIT
[INFO] Results:
[INFO] Tests run: 118, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- failsafe:3.5.6:verify (default) @ vestigia ---
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  31.612 s
[INFO] Finished at: 2026-11-06T14:05:43-06:00
[INFO] ------------------------------------------------------------------------
```

## 6. Pruebas adversariales de la API

pendiente: las completa el orquestador.

## 7. Revisiones independientes

pendiente: las completa el orquestador.

## 8. Lo que no se probó y por qué

| Qué | Por qué |
|---|---|
| El despliegue en Railway y cualquier nombre de Railway (`PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`, `DATABASE_URL`, `${{Postgres.…}}`, la opción de esperar los checks, el dominio, si Railway define `PORT`) | Prohibido tocar Railway. Quedan marcados "(por verificar en Railway)" en `docs/despliegue.md` |
| Desde qué IP llega el proxy de Railway (decide si `native` sirve para O10) y qué cabeceras `X-Forwarded-*` manda | Solo se puede ver en Railway. Se simuló con `curl` |
| Swagger UI y `/prueba-ws.html` en un navegador real detrás de https | Sin navegador ni proxy TLS. El bloqueo por contenido mixto de "Try it out" es lo que haría un navegador con `servers: http://`, no se observó |
| El workflow de GitHub Actions | No se puede correr en local y en GitHub no arranca (`startup_failure`, O11). Se validó con `actionlint` y se corrió su equivalente (`./mvnw verify` y `docker build`) en el arranque en frío |
| El WebSocket en `smoke-test.sh` | `curl` no habla WebSocket. El handshake y `auth` contra la imagen se probaron con `node` (secciones 2.3 y 2.6) y en Maven con `WebSocketIT` |
| Interrumpir el script con `Ctrl+C` en una terminal | Se probó la limpieza con `SIGTERM`, que usa el mismo `trap` (sección 2.2) |
| `SmokeTestIT` en Windows | Necesita `bash`, `curl` y `jq`. Está pensado para macOS, Linux y el runner `ubuntu-latest` del CI, que los traen |
