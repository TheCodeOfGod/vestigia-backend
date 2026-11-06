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
| `403` | El token es válido pero de otra sala, la clave de descubrimiento la genera el servidor o el rol no puede hacer esa operación |
| `404` | No hay sala en curso con ese código |
| `409` | Conflicto de estado: pueblo ocupado o sala con app ya enlazada |
| `500` | Error interno. El cuerpo no trae detalles |

## Flujo

1. El juego pide los pueblos libres y el jugador elige uno.
2. El juego crea la sala y guarda su token. La sala queda en `esperando_app`.
3. El jugador escribe el código en la app y la app se une. La app guarda su token. La sala pasa a `activa`.
4. Los dos consultan el estado con su token. Al reconectar, piden el estado otra vez para ponerse al día.
5. Durante la partida, el juego y la app registran descubrimientos y el juego cambia la escena.
6. Al terminar, el juego cierra la sala. Los dos tokens dejan de servir.

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

Al crearse, la sala tiene sus cuatro puzzles (`radio`, `photo`, `lookout`, `observatory`) en `bloqueado` y la escena `introduccion`. Los ids de puzzle van en inglés, igual que en la app; las claves de descubrimiento, los campos y los estados siguen en español.

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
    {"puzzle": "photo", "estado": "bloqueado", "datos": {}},
    {"puzzle": "lookout", "estado": "bloqueado", "datos": {}},
    {"puzzle": "observatory", "estado": "bloqueado", "datos": {}}
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
| `puzzles` | Siempre los cuatro, en el orden del juego: `radio`, `photo`, `lookout`, `observatory`. `estado` es `bloqueado`, `disponible`, `en_curso` o `resuelto` |
| `descubrimientos` | `[{clave, datos, origen, creado_en}]` en orden de llegada. `origen` es `juego`, `app` o `servidor` |
| `dispositivos` | Quién está conectado por WebSocket. **Hasta el Paso 4 los dos valores son siempre `false`** |
| `expira_en` | Momento en que la sala expira si nadie escribe antes |

| Error | `message` |
|---|---|
| `401` sin token | `Falta el token: envía el encabezado Authorization: Bearer <token>` |
| `401` token desconocido o sala terminada | `Token inválido o de una sala que ya terminó` |
| `403` token de otra sala | `El token no pertenece a esta sala` |

Ante un `401`, el token ya no sirve: vuelve a la pantalla de elegir pueblo (juego) o de escribir el código (app).

## Escrituras

Toda escritura exitosa renueva la sala: `expira_en` vuelve a quedar a 2 horas de ese momento, igual que al unirse. También deja un evento en la bitácora de la sala. Las escrituras de una misma sala se atienden una por una, así que dos peticiones simultáneas no se pisan.

## `POST /salas/{codigo}/descubrimientos`

Juego o app, con el token de esa sala. Registra algo que aparece en la libreta: una pieza, una bandera del juego, etc.

```sh
curl -X POST http://localhost:8080/api/v1/salas/BACALAR/descubrimientos \
	-H "Authorization: Bearer $TOKEN" \
	-H 'Content-Type: application/json' \
	-d '{"clave": "pieza_1", "datos": {"lugar": "muelle"}}'
```

`201 Created` la primera vez:

```json
{"clave": "pieza_1", "datos": {"lugar": "muelle"}, "origen": "juego", "creado_en": "2026-11-05T18:02:11.513204Z"}
```

| Campo | Notas |
|---|---|
| `clave` | Obligatoria. Solo minúsculas sin acentos, dígitos y guion bajo, de 1 a 40 caracteres (`^[a-z0-9_]{1,40}$`) |
| `datos` | Opcional. Un objeto JSON cualquiera. Si no se manda, se guarda `{}` |
| `origen` | Lo pone el servidor según el token: `juego` o `app` |

**Es idempotente.** Repetir la misma clave responde `200 OK` con el descubrimiento que ya existía, **con sus datos originales**: los `datos` nuevos se ignoran. No se duplica ni se guarda otro evento, pero sí se renueva la expiración. Si el juego y la app mandan la misma clave a la vez, uno recibe `201` y el otro `200`.

**Habilita puzzles.** Tras un descubrimiento nuevo, el servidor revisa qué puzzles bloqueados ya cumplen sus requisitos y los pasa a `disponible`. El orden en que lleguen las cosas no importa.

| Puzzle | Pasa a `disponible` cuando |
|---|---|
| `radio` | Existe `pescador_dialogo_completo` |
| `photo` | Existen `pieza_1`, `pieza_2` y `pieza_3` |
| `lookout` | `photo` está `resuelto` y existe `lookout_enabled` |
| `observatory` | Existen `coordenada_a` y `coordenada_b`, `lookout` está `resuelto` y existe `observatory_enabled` |

El juego manda `lookout_enabled` cuando el jugador llega a la cima del faro y `observatory_enabled` cuando llega al observatorio. Para ver el estado nuevo de los puzzles, pide `GET /salas/{codigo}/estado`. Desde el Paso 4 también llegará por WebSocket.

| Error | Cuándo | `message` |
|---|---|---|
| `400` | Falta la clave | `Datos inválidos`, con `details: [{"field": "clave", "message": "es obligatoria"}]` |
| `400` | Clave con mayúsculas, guiones, acentos, espacios o más de 40 caracteres | `Datos inválidos`, con `details: [{"field": "clave", ...}]` |
| `400` | `datos` no es un objeto (`[1,2]`, `"texto"`, `5`) | `JSON malformado o ilegible` |
| `401` | Sin token, token inválido o sala terminada | Igual que en `GET estado` |
| `403` | Token de otra sala | `El token no pertenece a esta sala` |
| `403` | La clave es `coordenada_a`, `coordenada_b`, `observatorio_visible` o `magnetar_localizado` | `La clave 'coordenada_a' la genera el servidor al resolver un puzzle; los clientes no pueden enviarla` |

Esas cuatro claves son los resultados de los puzzles y solo las crea el servidor (Paso 3b).

## `PUT /salas/{codigo}/escena`

**Solo el juego**, con el token de esa sala. Cambia la escena que ve la libreta.

```sh
curl -X PUT http://localhost:8080/api/v1/salas/BACALAR/escena \
	-H "Authorization: Bearer $TOKEN" \
	-H 'Content-Type: application/json' \
	-d '{"escena": "faro"}'
```

`200 OK`:

```json
{"escena_actual": "faro"}
```

Escenas válidas, en orden: `introduccion`, `pueblo`, `faro`, `observatorio` y `desenlace`. Se escriben exactamente así, en minúsculas. La sala empieza en `introduccion`. Mandar la escena en la que ya está responde lo mismo, renueva la expiración y no guarda otro evento.

| Error | Cuándo | `message` |
|---|---|---|
| `400` | Falta `escena` o viene vacía | `Datos inválidos`, con `details: [{"field": "escena", "message": "es obligatoria"}]` |
| `400` | Escena desconocida (`"bosque"`, `"FARO"`) | `'bosque' no es una escena válida. Usa una de: introduccion, pueblo, faro, observatorio, desenlace` |
| `401` | Sin token, token inválido o sala terminada | Igual que en `GET estado` |
| `403` | Token de la app | `Solo el juego puede cambiar la escena` |
| `403` | Token de otra sala | `El token no pertenece a esta sala` |

## `DELETE /salas/{codigo}`

**Solo el juego**, con el token de esa sala. Cierra la partida.

```sh
curl -X DELETE http://localhost:8080/api/v1/salas/BACALAR \
	-H "Authorization: Bearer $TOKEN"
```

`204 No Content`, sin cuerpo.

- La sala pasa a `finalizada` y se conserva como historial.
- El código vuelve a quedar libre: otro juego puede crear una sala nueva con el mismo pueblo.
- Los tokens del juego y de la app dejan de servir. Cualquier petición con ellos, incluido otro `DELETE`, responde `401`.
- Se puede cerrar una sala en `esperando_app` o en `activa`.

| Error | Cuándo | `message` |
|---|---|---|
| `401` | Sin token, token inválido o sala ya terminada | Igual que en `GET estado` |
| `403` | Token de la app | `Solo el juego puede cerrar la sala` |
| `403` | Token de otra sala | `El token no pertenece a esta sala` |

## Pendiente en pasos siguientes

`PUT /salas/{codigo}/puzzles/{puzzle}` todavía no existe (entrega 3b). Responde `404`.
