# Despliegue en Railway

El backend corre en Railway con dos servicios en el mismo proyecto: el backend, construido con el `Dockerfile` de la raíz del repo, y PostgreSQL. Por ahora solo existe un entorno desplegado, producción. `staging` se agrega después.

Todo lo de esta página lo hace Raúl a mano. Ningún agente despliega ni toca credenciales.

Los nombres de Railway marcados **(por verificar en Railway)** no se pudieron comprobar desde aquí: no hay acceso a Railway. Revísalos en su panel antes de usarlos.

## Servicios

| Servicio | Origen | Notas |
|---|---|---|
| Backend | Repo de GitHub, rama `main`, `Dockerfile` en la raíz | Una sola réplica. Las sesiones de WebSocket viven en memoria, así que una segunda instancia no vería las conexiones de la primera. |
| PostgreSQL | Plantilla de Postgres de Railway | Flyway crea y migra el esquema cuando arranca el backend. No hay que correr SQL a mano. |

## Variables del backend

| Variable | Valor | Obligatoria |
|---|---|:-:|
| `DB_URL` | `jdbc:postgresql://<host>:<puerto>/<base>`, armada con las variables que publica el servicio de Postgres | Sí |
| `DB_USER` | El usuario del servicio de Postgres | Sí |
| `DB_PASSWORD` | La contraseña del servicio de Postgres | Sí |
| `PORT` | El puerto en el que escucha el backend. Railway suele definirlo solo **(por verificar en Railway)**; el backend lo usa si existe y si no escucha en 8080 | No |
| `CORS_ALLOWED_ORIGINS` | Los orígenes que pueden llamar a `/api/**` y abrir `/ws` desde un navegador, separados por comas y sin `/` al final: la URL de la PWA (C6) y, si quieres usar `/prueba-ws.html` en producción, la URL pública del backend (ver O10) | Sí, en producción |

Sin `DB_URL`, `DB_USER` y `DB_PASSWORD` el backend usa los valores de desarrollo (`localhost:5432`, `postgres`/`postgres`), no encuentra la base y el proceso termina con código `1` (`Connection to localhost:5432 refused`): el despliegue no queda activo. Con una contraseña equivocada pasa lo mismo (`FATAL: password authentication failed for user …`), sin que la contraseña aparezca en el log.

### Mapeo desde el Postgres de Railway

La plantilla de Postgres de Railway publica sus datos como variables del servicio, y el backend las toma con variables de referencia. La documentación de Railway confirma que la plantilla publica `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `PGDATABASE`, `DATABASE_URL` y `DATABASE_PUBLIC_URL`. Quedan **(por verificar en Railway)** la sintaxis de referencia `${{…}}` y el nombre del servicio (`Postgres` por defecto):

| Variable del backend | Valor sugerido |
|---|---|
| `DB_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `DB_USER` | `${{Postgres.PGUSER}}` |
| `DB_PASSWORD` | `${{Postgres.PGPASSWORD}}` |

- `DATABASE_URL` y `DATABASE_PUBLIC_URL` tienen la forma `postgresql://usuario:contraseña@host:puerto/base` y **no sirve** como `DB_URL`: el driver JDBC necesita `jdbc:postgresql://host:puerto/base`, con el usuario y la contraseña aparte.
- Usa el host de la red privada de Railway, no el proxy público de la base **(por verificar en Railway)**. Si solo funcionara el público y pidiera SSL, agrega `?sslmode=require` al final de `DB_URL`.
- No copies la contraseña a mano en el panel ni la pegues en un chat o en el repo: la variable de referencia la toma del servicio de Postgres.

### Primer administrador

El inicio de sesión de la PWA necesita al menos un usuario, y crear usuarios exige ser admin. Para el primero, define en el servicio del backend:

| Variable | Valor |
|---|---|
| `ADMIN_BOOTSTRAP_EMAIL` | El correo del primer administrador |
| `ADMIN_BOOTSTRAP_PASSWORD` | Su contraseña, de 8 a 72 caracteres |

Al arrancar, si la tabla `usuarios` está vacía y las dos variables existen, el backend crea un administrador llamado `Administrador`. Si ya hay algún usuario no hace nada, así que dejarlas puestas no pisa cambios. Una vez que entres y crees las cuentas del equipo, conviene **borrar la contraseña de la variable**. Escríbela directo en el panel de Railway: no la pegues en el chat ni en el repo.

### Variables opcionales

Spring Boot lee cualquier propiedad como variable de entorno (en mayúsculas, con `_` en lugar de `.` y sin `-`). Estas sirven para las decisiones de la sección siguiente. Hoy no se define ninguna.

| Variable | Efecto | Comprobado en la imagen |
|---|---|:-:|
| `SPRINGDOC_API_DOCS_ENABLED=false` y `SPRINGDOC_SWAGGER_UI_ENABLED=false` | Apagan `/v3/api-docs` y Swagger UI (`404` con el cuerpo de error uniforme). El resto de la API sigue igual | ✅ |
| `SPRING_WEB_RESOURCES_ADD_MAPPINGS=false` | Deja de servir los archivos estáticos: `/prueba-ws.html` da `404`. Swagger UI no se ve afectado (tiene su propio manejador) | ✅ |
| `SERVER_FORWARD_HEADERS_STRATEGY=native` o `framework` | El backend toma el esquema y el host de las cabeceras `X-Forwarded-*` del proxy de Railway (ver O10) | ✅ con cabeceras simuladas |
| `VESTIGIA_AUTH_SESION`, `VESTIGIA_AUTH_INTENTOS_MAX`, `VESTIGIA_AUTH_INTENTOS_VENTANA` | Vigencia de la sesión de administración (`PT8H`) y el límite de intentos fallidos de inicio de sesión por correo (5 en `PT15M`) | No hizo falta |
| `VESTIGIA_SALAS_EXPIRACION`, `VESTIGIA_PUZZLES_COORDENADAS_AR`, … | Las propiedades de `vestigia.*` del README | No hizo falta |

## Decisiones de Raúl antes de publicar la URL

Ninguna bloquea el primer despliegue. El Paso 7 no cambió nada de esto: solo lo verificó y deja cómo cambiarlo.

| # | Tema | Hoy | Cómo cambiarlo |
|---|---|---|---|
| D-Swagger | Swagger UI (`/swagger-ui/index.html`) y `/v3/api-docs` | ✅ Raúl decidió el 2026-11-06 dejarlos públicos en producción por ahora. Públicos en todos los entornos, sin token. Describen la API y no exponen datos. Al arrancar, springdoc avisa en el log: `SpringDoc /v3/api-docs endpoint is enabled by default. To disable it in production…` | `SPRINGDOC_API_DOCS_ENABLED=false` y `SPRINGDOC_SWAGGER_UI_ENABLED=false` en el servicio de Railway. Sin cambio de código |
| D-Prueba | Página `/prueba-ws.html` | ✅ Raúl decidió el 2026-11-06 dejarla pública en producción por ahora. Se sirve en todos los entornos. Sola no muestra ni guarda nada: necesita un token | `SPRING_WEB_RESOURCES_ADD_MAPPINGS=false`. Sin cambio de código |
| O10 | Páginas servidas por el backend detrás del proxy TLS | Ver la sección siguiente | `CORS_ALLOWED_ORIGINS` o `SERVER_FORWARD_HEADERS_STRATEGY` |
| P9 | `POST /salas` y `unirse` no piden token (por diseño) | ✅ Aceptado por Raúl: es como el juego y la app obtienen su token. Cualquiera con la URL puede ocupar pueblos hasta que expiran (2 horas) | Si hiciera falta, un límite por IP o una clave del juego (cambio de código y de contrato) |

## O10: el backend detrás del proxy TLS de Railway

Railway termina el TLS en su proxy y le pasa la petición al contenedor por `http`. Tomcat, sin más configuración, cree que la petición llegó por `http`. Un navegador que abre una página servida por el propio backend (`/prueba-ws.html` o Swagger UI) manda `Origin: https://<backend>`, que no coincide con el `http://<backend>` que ve el servidor. Por eso:

- El handshake de `/ws` y las llamadas a `/api/**` desde esas páginas responden `403`.
- `/v3/api-docs` anuncia `"servers": [{"url": "http://<backend>"}]`, así que "Try it out" de Swagger UI llamaría a `http://` desde una página `https://`, y el navegador lo bloquea como contenido mixto (esto no se probó en un navegador).

**No afecta a Unity ni a Flutter nativo**, que no mandan `Origin`, ni a la PWA, que vive en otro origen y entra por `CORS_ALLOWED_ORIGINS`.

Se simuló el proxy contra la imagen Docker con `curl` (`Host: vestigia.example`, `Origin: https://vestigia.example`, `X-Forwarded-Proto: https`). Resultado de `/ws` y `GET /api/v1/pueblos`:

| Escenario | Hoy | URL del backend en `CORS_ALLOWED_ORIGINS` | `native` | `framework` |
|---|:-:|:-:|:-:|:-:|
| Página del backend detrás del proxy | `403` | `101` / `200` | `101` / `200` solo si el proxy llega desde una IP interna | `101` / `200` |
| Local: `Origin: http://localhost:18080` | `101` / `200` | igual | igual | igual |
| Local: origen de `CORS_ALLOWED_ORIGINS` | `101` / `200` | igual | igual | igual |
| Local: origen ajeno | `403` | `403` | `403` | `403` |
| Sin `Origin` (Unity, Flutter) | `101` / `200` | igual | igual | igual |
| Cliente que falsifica `X-Forwarded-Host` y `Origin` | `403` | `403` | `101` si llega desde una IP interna | `101` |
| `servers` de `/v3/api-docs` | `http://…` | `http://…` | `https://…` si el proxy llega desde una IP interna | `https://…` |

Ninguna opción cambia el comportamiento local. Las tres opciones, de menos a más alcance:

1. **Agregar la URL pública del backend a `CORS_ALLOWED_ORIGINS`** (`https://<tu-servicio>.up.railway.app`, sin `/` final). Arregla `/prueba-ws.html` (WebSocket y API). No confía en ninguna cabecera. No arregla "Try it out" de Swagger.
2. **`SERVER_FORWARD_HEADERS_STRATEGY=native`.** Tomcat toma `X-Forwarded-Proto` y `X-Forwarded-Host` solo si la conexión viene de una IP interna (`10.x`, `172.16–31.x`, `192.168.x`, `127.x` y similares). Arregla todo, incluido Swagger, **si** el proxy de Railway se conecta desde una IP interna **(por verificar en Railway)**. Si no, no cambia nada. En la prueba local, la conexión desde la red interna de Docker sí lo activó y la del host (que Docker Desktop presenta con una IP pública) no.
3. **`SERVER_FORWARD_HEADERS_STRATEGY=framework`.** Spring toma las cabeceras `X-Forwarded-*` de cualquier cliente. Arregla todo, sin depender de la IP del proxy. A cambio, un cliente que no es navegador puede falsificar el esquema y el host; eso no le da nada nuevo, porque un cliente así ya puede omitir `Origin`, y un navegador no puede poner esas cabeceras en el handshake de `/ws`.

Recomendación: si solo vas a usar `/prueba-ws.html`, la opción 1. Si quieres "Try it out" de Swagger en producción, prueba la 2 y, si no surte efecto, la 3. Si apagas Swagger y la página de prueba (D-Swagger y D-Prueba), no hace falta ninguna.

## Probar la imagen en local, igual que en Railway

Esta receta construye la imagen y la corre como en Railway: un Postgres vacío en una red propia, el backend solo con las cinco variables y el nombre del contenedor de Postgres como host. Se copia completa desde la raíz del repo, con Docker, `curl` y `jq`. La contraseña es solo de este Postgres de prueba, no la de Railway.

```sh
# 1. Nombres de la prueba. Cámbialos si chocan con algo que ya tengas.
RED=vestigia-red-local
PG=vestigia-pg-local
APP=vestigia-app-local
IMAGEN=vestigia-backend:local
CLAVE=clave-solo-local
PUERTO=18080

# 2. Imagen, red y Postgres vacío. pg_isready por TCP espera a que termine la inicialización.
docker build -t "$IMAGEN" .
docker network create "$RED"
docker run -d --name "$PG" --network "$RED" \
	-e POSTGRES_DB=vestigia -e POSTGRES_USER=vestigia -e POSTGRES_PASSWORD="$CLAVE" postgres:17-alpine
until docker exec "$PG" pg_isready -h 127.0.0.1 -U vestigia -d vestigia >/dev/null 2>&1; do sleep 1; done

# 3. El backend, solo con las cinco variables, como en Railway.
docker run -d --name "$APP" --network "$RED" -p "$PUERTO:9090" \
	-e DB_URL="jdbc:postgresql://$PG:5432/vestigia" -e DB_USER=vestigia -e DB_PASSWORD="$CLAVE" \
	-e PORT=9090 -e CORS_ALLOWED_ORIGINS=http://localhost:5173 \
	"$IMAGEN"

# 4. Health, usuario del proceso y prueba de humo.
for i in $(seq 1 60); do curl -fsS "http://localhost:$PUERTO/actuator/health" 2>/dev/null && break; sleep 1; done; echo
docker exec "$APP" id
docker logs "$APP" 2>&1 | grep -E "Migrating|Successfully applied|Tomcat started"
scripts/smoke-test.sh "http://localhost:$PUERTO"

# 5. Limpieza.
docker rm -f "$APP" "$PG" && docker network rm "$RED" && docker image rm "$IMAGEN"
```

Lo esperado: `{"groups":["liveness","readiness"],"status":"UP"}`, `uid=999(vestigia) gid=999(vestigia) groups=999(vestigia)`, las tres migraciones, `Tomcat started on port 9090 (http)` y `RESULTADO: ✅`. `PORT=9090` es distinto de 8080 a propósito: así se ve que el backend escucha en el puerto que recibe, como en Railway.

## Qué se verificó antes del primer despliegue (Paso 7)

Todo contra la imagen construida con el `Dockerfile` (`docker build`), corriendo con un Postgres 17 propio en una red de Docker propia y solo con `DB_URL`, `DB_USER`, `DB_PASSWORD`, `PORT=9090` y `CORS_ALLOWED_ORIGINS`. El detalle está en `docs/pruebas/paso-7-railway.md`.

| Qué | Resultado |
|---|---|
| `docker build` | Termina bien. Imagen de 595 MB |
| Flyway contra una base vacía | Crea `flyway_schema_history` y aplica `V1`, `V2` y `V3`; carga los 101 pueblos |
| Arranque | `Started VestigiaApplication in 3.0 seconds`; `/actuator/health` responde `UP` a los 5 s. Escucha en el `PORT` recibido |
| Usuario del proceso | `vestigia` (uid 999), sin privilegios |
| Memoria | Con un límite de 512 MB, tres pruebas de humo seguidas pasan y el contenedor usa ~380 MB |
| Base caída | `/actuator/health` da `503` con `"status":"DOWN"`; la API da `500` con el cuerpo uniforme y sin stacktrace. Al volver la base, `UP` sin reiniciar |
| Logs | Ningún token ni el encabezado `Authorization` (ver la sección de logs) |
| Actuator | Solo `health` (con sus grupos `liveness` y `readiness`). `info`, `env`, `beans`, `metrics`, `heapdump`, … responden `404` |
| Errores | Sin stacktrace ni versión del servidor; sin cabecera `Server` |
| Secretos en el repo | Ninguno (`git grep`). Solo los valores de desarrollo `postgres` y la plantilla `application-local.properties.example` |
| `scripts/smoke-test.sh` | La cadena completa pasa contra la imagen |

## Primer despliegue: pasos para Raúl

> **Hecho el 7 de noviembre de 2026.** El backend corre en `https://vestigia-backend-production-745d.up.railway.app` y la variable `RAILWAY_PUBLIC_URL` ya existe. La lista queda como guía para repetir el despliegue (por ejemplo, en otro entorno).

Cada paso es manual. Marca cada casilla al terminarlo.

**Antes de empezar**

- [ ] Revisa en GitHub por qué el workflow `CI` no arranca (sección "CI"). Mientras no corra, no hay checks que esperar en el paso 6.
- [ ] Haz merge de los PR pendientes hasta `main` por el flujo de siempre (`feature/*` → `develop` → `main`). Railway despliega `main`.
- [ ] Decide O10 (sección "Decisiones"; D-Swagger, D-Prueba y P9 ya están decididos). Puedes desplegar primero y decidir después: son variables de entorno.

**En Railway**

1. [ ] Crea un proyecto nuevo.
2. [ ] Agrega un servicio de PostgreSQL desde la plantilla de Railway. Anota el nombre que le pone al servicio (`Postgres` por defecto, **por verificar en Railway**).
3. [ ] Agrega un servicio desde el repo de GitHub `vestigia-backend`, rama `main`. Railway detecta el `Dockerfile` de la raíz **(por verificar en Railway que use el Dockerfile y no otro builder)**.
4. [ ] En las variables del servicio del backend, define `DB_URL`, `DB_USER` y `DB_PASSWORD` con las referencias de la tabla de mapeo, y `CORS_ALLOWED_ORIGINS` con la URL de la PWA (si todavía no la tienes, deja `http://localhost:5173` y cámbiala cuando llegue C6). No definas `PORT`: Railway lo pone **(por verificar en Railway)**.
5. [ ] En la configuración del despliegue del backend, pon `/actuator/health` como ruta del health check **(por verificar el nombre exacto de la opción)**. El backend tarda unos segundos en arrancar; el tiempo de espera por defecto de Railway debería sobrar.
6. [ ] Activa la opción que espera a que pasen los checks de GitHub antes de desplegar **(por verificar el nombre exacto de la opción, algo como "Wait for CI")**. Railway espera los checks del commit de `main` que va a desplegar; por eso `ci.yml` también corre en `push` a `main`, no solo en los PR. Mientras el CI siga en `startup_failure`, esa opción podría bloquear todos los despliegues: actívala cuando el CI vuelva a correr.
7. [ ] Deja el servicio en **una réplica**.
8. [ ] Genera el dominio público del backend (algo como `https://<servicio>.up.railway.app`) **(por verificar en Railway)**. Anótalo: es la URL para Unity, Flutter, la PWA y la prueba de humo.
9. [ ] Despliega y espera a que Railway marque el despliegue como activo.

**Después del despliegue**

10. [ ] `curl https://<tu-servicio>.up.railway.app/actuator/health` responde `{"groups":["liveness","readiness"],"status":"UP"}`.
11. [ ] Corre la prueba de humo desde tu máquina: `scripts/smoke-test.sh https://<tu-servicio>.up.railway.app`. Debe terminar con `RESULTADO: ✅` y código `0`. Si falla, el script dice en qué paso, muestra la respuesta y cierra la sala.
12. [ ] Revisa los logs del despliegue (sección siguiente).
13. [ ] Si decidiste algo de O10, D-Swagger o D-Prueba, define sus variables, vuelve a desplegar y repite los pasos 10 y 11. Para O10, abre `https://<tu-servicio>.up.railway.app/prueba-ws.html`, crea una sala y conecta el panel del juego: debe llegar `auth.ok`.
14. [ ] Comparte la URL con Unity, Flutter y la PWA, y agrégala a la sección de pendientes de `docs/PLAN.md` (O1).
15. [ ] Con la URL ya fija, crea en GitHub la variable del repositorio `RAILWAY_PUBLIC_URL` (**Settings → Secrets and variables → Actions → Variables → New repository variable**) con la URL pública, sin `/` al final. No es un secreto. Sin ella, el workflow "Health de producción" se salta con un aviso. Pruébalo con **Actions → Health de producción → Run workflow** (sección "CI").

## Qué mirar en los logs

En los logs del servicio del backend en Railway, durante el arranque:

| Debe aparecer | Significa |
|---|---|
| `Database: jdbc:postgresql://… (PostgreSQL 1x.x)` | Flyway llegó a la base. La URL no lleva usuario ni contraseña |
| `Migrating schema "public" to version "1 - esquema"`, `"2 - seed pueblos"` y `"3 - ids puzzles en ingles"`, y `Successfully applied 3 migrations` | La primera vez, Flyway creó el esquema. En los siguientes despliegues dice `Schema "public" is up to date. No migration necessary.` |
| `Tomcat started on port <PORT> (http)` | Escucha en el puerto que dio Railway |
| `Started VestigiaApplication in N seconds` | La app arrancó |
| `WARN … SpringDoc /v3/api-docs endpoint is enabled by default…` | Aviso esperado mientras Swagger siga encendido (D-Swagger) |

Durante el uso, una línea por petición: `[API] POST /api/v1/salas -> 201 | 37 ms`, y para el WebSocket `[WS] conectado sala=BACALAR rol=juego`. **Nunca** debe aparecer un token, `Authorization` ni `Bearer`; si aparece, avisa: es un error.

Señales de problema:

| Mensaje | Causa probable |
|---|---|
| `Connection to localhost:5432 refused` | Faltan `DB_URL`, `DB_USER` o `DB_PASSWORD`, o no se resolvieron las referencias |
| `FATAL: password authentication failed` | `DB_USER` o `DB_PASSWORD` no coinciden con los del servicio de Postgres |
| `UnknownHostException` | El host de `DB_URL` no existe o no está en la red privada |
| `Validate failed: Migrations have failed validation` o `Schema-validation: missing column` | El esquema de la base no coincide con las migraciones o con las entidades. No edites migraciones aplicadas |
| `ERROR … ApiExceptionHandler : Error no controlado en …` | Un `500`. El stacktrace solo va al log, nunca a la respuesta |

## Qué pasa en cada despliegue

1. Railway construye la imagen con el `Dockerfile`. La imagen no corre pruebas. Las pruebas corren en el CI del PR.
2. El contenedor arranca con el usuario `vestigia`, sin privilegios.
3. Flyway aplica las migraciones pendientes antes de que el servidor acepte peticiones. Si una migración falla, la app no arranca y el health check falla.
4. Railway marca el despliegue como sano cuando `/actuator/health` responde `200`. Si la base cae después, el health check pasa a `503` (`DOWN`) y vuelve a `UP` cuando la base regresa, sin reiniciar el backend.

## Prueba de humo

`scripts/smoke-test.sh` juega una partida completa contra una URL base con `curl` y `jq`: health, crear sala, unirse, escenas, las banderas `lookout_enabled` y `observatory_enabled`, los cuatro puzzles hasta `magnetar_localizado`, el estado final y el cierre.

```sh
scripts/smoke-test.sh https://<tu-servicio>.up.railway.app          # elige un pueblo libre
scripts/smoke-test.sh https://<tu-servicio>.up.railway.app BACALAR  # o uno en concreto
```

- Sale con `0` si toda la cadena pasó y con `1` si falló un paso, que nombra junto con la respuesta del backend. `2` es uso incorrecto, `3` que falta `curl` o `jq`, y `129`, `130` o `143` que se interrumpió (`HUP`, `Ctrl+C` o `TERM`). Si la respuesta no es JSON, como la página HTML de un `502` del proxy, la muestra igual.
- Al salir, también tras un fallo o una interrupción, cierra la sala que creó con `DELETE`. Guarda el token del juego en cuanto `POST /salas` responde `201`, antes de revisar el cuerpo, así que también la cierra si esa revisión falla. El pueblo vuelve a quedar libre.
- La sala queda en la base como historial (`finalizada`), con sus eventos. Los descubrimientos que manda el script como juego llevan `{"origen_prueba": "smoke-test"}` en `datos`; los resultados del servidor (`coordenada_a`…) y los eventos no lo llevan. Para sacar esas salas de las métricas, toma los `sala_id` de esos descubrimientos y filtra por ellos `eventos`, `descubrimientos` y `puzzles_estado`.
- No imprime los tokens.
- No prueba el WebSocket: `curl` no lo habla. Para verlo, usa `/prueba-ws.html` (ver O10).
- `SMOKE_TIEMPO_MAX` cambia el tiempo máximo de cada petición, en segundos (por defecto 20).

## CI

`.github/workflows/ci.yml` corre en cada PR a `develop` y `main` y en cada `push` a `main` (lo que espera Railway con "Wait for CI"): Java 21 (Temurin) con caché de Maven, `./mvnw -B verify` (incluye las `*IT` con Postgres de Testcontainers y la prueba de humo, que usa `bash`, `curl` y `jq` del runner) y, si pasa, `docker build`. Usa `actions/checkout@v7` y `actions/setup-java@v6`, que existen (últimas: v7.0.1 y v6.2.0). `actionlint` 1.7.7 no encuentra errores en `ci.yml` ni en `salud-produccion.yml`, y `shellcheck` 0.10.0 tampoco en `scripts/esperar-health.sh` ni en `scripts/smoke-test.sh`.

**O11, resuelto el 8 de noviembre de 2026.** Del 5 al 8 de noviembre ninguna ejecución arrancaba: todas terminaban en `startup_failure` sin jobs, con el mensaje "recent account payments have failed or your spending limit needs to be increased". La causa era el presupuesto de **Actions en $0 con "Stop usage"** de la cuenta de GitHub (**Settings → Billing and licensing → Budgets and alerts**). Se subió y el CI volvió a arrancar. Si vuelve a pasar, abre la ejecución fallida en la pestaña **Actions**: el motivo aparece arriba.

### Health de producción

`.github/workflows/salud-produccion.yml` revisa `/actuator/health` de la URL real después de cada despliegue (O2). Corre `scripts/esperar-health.sh` con la URL de la variable del repositorio `RAILWAY_PUBLIC_URL`: espera 15 s y hace hasta 10 intentos, cada 30 s, hasta ver `"status":"UP"`. Si no llega, el job falla con un `::error::`. Si la variable no existe, avisa con un `::warning::` y termina bien.

- **Cuándo corre:** con el evento `deployment_status` en estado `success`, que manda Railway a GitHub al terminar un despliegue **(por verificar en Railway que publique sus despliegues en GitHub; si aparecen en la sección "Deployments" del repo, lo hace)**. También a mano, con **Run workflow** (`workflow_dispatch`).
- **Por qué no en el `push` a `main`:** con "Wait for CI", Railway espera los checks del push antes de desplegar. Un check que espera el despliegue revisaría la versión anterior o, en el primer despliegue, fallaría y dejaría a Railway esperando un CI en rojo.
- Si Railway no publica sus despliegues en GitHub, el workflow no se dispara solo: lánzalo a mano después de cada despliegue, o queda para otro paso cambiarlo a `workflow_run` del `CI`.
- Solo comprueba que el servicio responde `UP`, no qué commit corre: actuator no expone la versión.

Un job de CI que se vuelve a ejecutar con **Re-run** usa el mismo código de la primera vez: no recoge cambios nuevos de `develop`. Para que un PR vea un arreglo, actualiza su rama con un push.

## Pendiente

- Activar "Wait for CI" en Railway (el CI ya pasa; O12 en `docs/PLAN.md`). Mientras esté apagado, cada merge a `main` despliega sin esperar los checks.
- O10: para usar `/prueba-ws.html` en producción, agregar la URL del backend a `CORS_ALLOWED_ORIGINS`.
- C6: agregar el origen de la PWA a `CORS_ALLOWED_ORIGINS` cuando tenga URL.
