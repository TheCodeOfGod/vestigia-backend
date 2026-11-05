# API REST

Guía para quien integra el juego (Unity) o la app (Flutter). Todas las rutas cuelgan de `/api/v1`. Los ejemplos usan `http://localhost:8080`. Swagger UI con los mismos endpoints está en `/swagger-ui/index.html`.

Las respuestas de esta página salieron de correr el backend real. Los tokens están recortados.

## Convenciones

- JSON en `snake_case`. Fechas en UTC con formato ISO-8601 (`2026-10-26T23:33:18.706842Z`).
- **Código de sala.** Es el nombre del pueblo. El backend lo recorta y lo pasa a mayúsculas antes de usarlo, tanto en el cuerpo como en la ruta. `" bacalar "`, `"Bacalar"` y `"BACALAR"` son la misma sala. Las respuestas siempre lo devuelven en mayúsculas.
- **Token.** `POST /salas` le entrega un token al juego y `POST /salas/{codigo}/unirse` le entrega otro a la app. Cada token se entrega **una sola vez**: guárdalo en el dispositivo para reconectar. Se manda en el encabezado `Authorization: Bearer <token>`. El token decide la sala y el rol (`juego` o `app`), y solo sirve mientras la sala esté viva (`esperando_app` o `activa`).
- **Errores.** Todos tienen el mismo cuerpo:

	```json
	{
	  "timestamp": "2026-10-26T23:33:18.786573Z",
	  "status": 409,
	  "error": "Conflict",
	  "message": "'BACALAR' ya tiene una sala en curso",
	  "path": "/api/v1/salas",
	  "details": []
	}
	```

	`message` está en español y se puede mostrar al jugador o al log. `details` solo trae elementos en errores de validación, uno por campo.

| Código | Cuándo |
|---|---|
| `400` | JSON malformado, campo obligatorio vacío o código fuera del catálogo |
| `401` | Falta el token, el token no existe o su sala ya terminó |
| `403` | El token es válido pero de otra sala |
| `404` | No hay sala en curso con ese código |
| `409` | Conflicto de estado: pueblo ocupado o sala con app ya enlazada |
| `500` | Error interno. El cuerpo no trae detalles |

## Flujo

1. El juego pide los pueblos libres y el jugador elige uno.
2. El juego crea la sala y guarda su token. La sala queda en `esperando_app`.
3. El jugador escribe el código en la app y la app se une. La app guarda su token. La sala pasa a `activa`.
4. Los dos consultan el estado con su token. Al reconectar, piden el estado otra vez para ponerse al día.

Una sala sin actividad durante 2 horas pasa a `expirada`. Su token deja de servir (`401`) y el pueblo vuelve a estar libre.

## `GET /pueblos`

Público. Lista el catálogo en orden alfabético. Un pueblo está ocupado mientras tiene una sala viva.

| Parámetro | Valores | Efecto |
|---|---|---|
| `disponibles` | `true` o `false` (por defecto) | Con `true`, solo los libres |

```sh
curl 'http://localhost:8080/api/v1/pueblos?disponibles=true'
```

```json
[
  {"codigo": "ACAXOCHITLAN", "libre": true},
  {"codigo": "ACULCO", "libre": true},
  {"codigo": "AHUACATLAN", "libre": true}
]
```

Sin el parámetro vienen los 101 pueblos, y los ocupados traen `"libre": false`.

## `POST /salas`

Sin token. Lo usa el juego para reclamar un pueblo.

```sh
curl -X POST http://localhost:8080/api/v1/salas \
	-H 'Content-Type: application/json' \
	-d '{"codigo": " bacalar "}'
```

`201 Created`:

```json
{"codigo": "BACALAR", "estado": "esperando_app", "token": "dIg7OY_EqA3T…"}
```

Al crearse, la sala tiene sus cuatro puzzles (`radio`, `fotografia`, `mirador`, `observatorio`) en `bloqueado` y la escena `introduccion`.

| Error | `message` |
|---|---|
| `400` código vacío | `Datos inválidos`, con `details: [{"field": "codigo", "message": "es obligatorio"}]` |
| `400` fuera del catálogo | `'ATLANTIDA' no está en el catálogo de pueblos` |
| `409` pueblo ocupado | `'BACALAR' ya tiene una sala en curso` |

Si dos juegos reclaman el mismo pueblo a la vez, uno recibe `201` y el otro `409`. Ante un `409`, vuelve a pedir `GET /pueblos?disponibles=true` y deja que el jugador elija otro.

## `POST /salas/{codigo}/unirse`

Sin token. Lo usa la app. Solo cabe una app por sala.

```sh
curl -X POST http://localhost:8080/api/v1/salas/bacalar/unirse
```

`200 OK`:

```json
{"token": "LiMPYxrtkwFi…"}
```

La sala pasa a `activa` y su expiración se renueva.

| Error | `message` |
|---|---|
| `404` | `No hay una sala en curso con el código 'TULUM'` |
| `409` | `La sala 'BACALAR' ya tiene una app enlazada` |

`404` cubre tanto un código que nunca tuvo sala como una sala que ya expiró o terminó. Si dos apps se unen a la vez, una recibe `200` y la otra `409`.

## `GET /salas/{codigo}/estado`

Pide el token del juego o de la app **de esa sala**. Leer el estado no renueva la expiración.

```sh
curl http://localhost:8080/api/v1/salas/BACALAR/estado \
	-H "Authorization: Bearer $TOKEN"
```

`200 OK`:

```json
{
  "codigo": "BACALAR",
  "estado": "activa",
  "escena_actual": "introduccion",
  "puzzles": [
    {"puzzle": "radio", "estado": "bloqueado", "datos": {}},
    {"puzzle": "fotografia", "estado": "bloqueado", "datos": {}},
    {"puzzle": "mirador", "estado": "bloqueado", "datos": {}},
    {"puzzle": "observatorio", "estado": "bloqueado", "datos": {}}
  ],
  "descubrimientos": [],
  "dispositivos": {"juego": false, "app": false},
  "creada_en": "2026-10-26T23:33:18.706842Z",
  "expira_en": "2026-10-26T23:38:18.836829Z"
}
```

| Campo | Notas |
|---|---|
| `estado` | `esperando_app` o `activa`. Una sala terminada ya no se puede consultar con su token |
| `puzzles` | Siempre los cuatro, en el orden del juego. `estado` es `bloqueado`, `disponible`, `en_curso` o `resuelto` |
| `descubrimientos` | `[{clave, datos, origen, creado_en}]` en orden de llegada. Vacío hasta el Paso 3 |
| `dispositivos` | Quién está conectado por WebSocket. **Hasta el Paso 4 los dos valores son siempre `false`** |
| `expira_en` | Momento en que la sala expira si nadie escribe antes |

| Error | `message` |
|---|---|
| `401` sin token | `Falta el token: envía el encabezado Authorization: Bearer <token>` |
| `401` token desconocido o sala terminada | `Token inválido o de una sala que ya terminó` |
| `403` token de otra sala | `El token no pertenece a esta sala` |

Ante un `401`, el token ya no sirve: vuelve a la pantalla de elegir pueblo (juego) o de escribir el código (app).

## Pendiente en pasos siguientes

`PUT /salas/{codigo}/puzzles/{puzzle}`, `POST /salas/{codigo}/descubrimientos`, `PUT /salas/{codigo}/escena` y `DELETE /salas/{codigo}` todavía no existen. Responden `404`.
