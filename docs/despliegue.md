# Despliegue en Railway

El backend corre en Railway con dos servicios en el mismo proyecto: el backend, construido con el `Dockerfile` de la raíz del repo, y PostgreSQL. Por ahora solo existe un entorno desplegado, producción. `staging` se agrega después.

Todo lo de esta página lo hace Raúl a mano. Ningún agente despliega ni toca credenciales.

## Servicios

| Servicio | Origen | Notas |
|---|---|---|
| Backend | Repo de GitHub, rama `main`, `Dockerfile` en la raíz | Una sola réplica. Las sesiones de WebSocket viven en memoria, así que una segunda instancia no vería las conexiones de la primera. |
| PostgreSQL | Plantilla de Postgres de Railway | Flyway crea y migra el esquema cuando arranca el backend. No hay que correr SQL a mano. |

## Variables del backend

| Variable | Valor |
|---|---|
| `DB_URL` | `jdbc:postgresql://<host>:<puerto>/<base>`, armada con las variables que publica el servicio de Postgres |
| `DB_USER` | El usuario del servicio de Postgres |
| `DB_PASSWORD` | La contraseña del servicio de Postgres |
| `PORT` | El puerto en el que escucha el backend. Si Railway define `PORT` para el servicio, el backend lo usa. Si no, escucha en 8080 |
| `CORS_ALLOWED_ORIGINS` | Las URLs de la PWA, separadas por comas, sin `/` al final |

Railway publica el host, el puerto, la base, el usuario y la contraseña de Postgres como variables del servicio. Mapéalas a `DB_URL`, `DB_USER` y `DB_PASSWORD` con variables de referencia. Verifica los nombres reales en Railway.

Si Railway entrega una URL con la forma `postgresql://usuario:contraseña@host:puerto/base`, no sirve tal cual como `DB_URL`. El driver JDBC necesita `jdbc:postgresql://host:puerto/base`, con el usuario y la contraseña en `DB_USER` y `DB_PASSWORD`.

## Configurar el servicio del backend

1. Conecta el servicio al repo de GitHub y elige la rama `main`.
2. Activa la opción que espera a que pasen los checks de GitHub antes de desplegar. Así un PR con el CI en rojo no llega a producción. Verifica el nombre exacto de la opción en Railway.
3. Pon `/actuator/health` como ruta del health check. El endpoint responde `200` con `"status":"UP"` cuando la app arrancó y la base responde.
4. Define las variables de la tabla anterior.
5. Deja el servicio en una réplica.

## Qué pasa en cada despliegue

1. Railway construye la imagen con el `Dockerfile`. La imagen no corre pruebas. Las pruebas corren en el CI del PR.
2. El contenedor arranca con el usuario `vestigia`, sin privilegios.
3. Flyway aplica las migraciones pendientes antes de que el servidor acepte peticiones. Si una migración falla, la app no arranca y el health check falla.
4. Railway marca el despliegue como sano cuando `/actuator/health` responde.

## Prueba de humo

`scripts/smoke-test.sh` juega una partida completa contra una URL base con `curl` y `jq`: health, crear sala, unirse, escenas, las banderas `lookout_enabled` y `observatory_enabled`, los cuatro puzzles hasta `magnetar_localizado`, el estado final y el cierre.

```sh
scripts/smoke-test.sh https://<tu-servicio>.up.railway.app          # elige un pueblo libre
scripts/smoke-test.sh https://<tu-servicio>.up.railway.app BACALAR  # o uno en concreto
```

- Sale con `0` si toda la cadena pasó y con `1` si falló un paso, que nombra junto con la respuesta del backend. `2` es uso incorrecto y `3` que falta `curl` o `jq`.
- Al salir, también tras un fallo o `Ctrl+C`, cierra la sala que creó con `DELETE`. El pueblo vuelve a quedar libre.
- La sala queda en la base como historial (`finalizada`), con sus eventos. Sus descubrimientos llevan `{"origen_prueba": "smoke-test"}` en `datos`, para distinguirla en las métricas.
- No imprime los tokens.
- No prueba el WebSocket: `curl` no lo habla. Para verlo, abre `/prueba-ws.html` (ver la sección de O10).

## Pendiente

- El primer despliegue.
- Un job de GitHub Actions posterior al despliegue en `main` que revise `/actuator/health` de la URL real con reintentos. Necesita la URL pública que asigne Railway.
