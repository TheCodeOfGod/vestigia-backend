# API REST

Guía para quien integra el juego (Unity) o la app (Flutter). Todas las rutas cuelgan de `/api/v1`. Los ejemplos usan `http://localhost:8080`. El servidor desplegado en Railway, para probar sin levantar nada, es `https://vestigia-backend-production-745d.up.railway.app` (REST en `https://vestigia-backend-production-745d.up.railway.app/api/v1`). Swagger UI con los mismos endpoints, sus ejemplos de petición y respuesta y sus errores está en `/swagger-ui/index.html`; el documento OpenAPI en JSON, en `/v3/api-docs`. Para probar en Swagger UI una ruta con candado, pulsa **Authorize** y pega el token (sin la palabra `Bearer`). Swagger documenta solo las rutas de `/api/v1`: `/actuator/health` no aparece porque es de Spring Boot Actuator y no se activó `springdoc.show-actuator`, para no mezclar las rutas de operación con las de los clientes. Es público y responde `200` con `{"groups": ["liveness", "readiness"], "status": "UP"}`.

Las respuestas de esta página salieron de correr el backend real. Los tokens están recortados.

## Convenciones

- JSON en `snake_case`. Fechas en UTC con formato ISO-8601 (`2026-10-26T23:33:18.706842Z`).
- **Claves sin repetir.** Un JSON con la misma clave dos veces en el mismo objeto, en cualquier nivel (`{"codigo": "BACALAR", "codigo": "BACALAR"}` o `{"datos": {"completada": true, "completada": true}}`), responde `400 JSON malformado o ilegible`, aunque los dos valores coincidan. El backend no elige uno de los dos.
- **Campos desconocidos.** Un campo que el endpoint no usa, en la raíz del cuerpo, se ignora en todos los endpoints (`{"clave": "pieza_1", "origen": "servidor"}` guarda `pieza_1` con el origen del token). Dentro de `datos` de `PUT puzzles` no: ahí una clave de más da `400`.
- **Tipos estrictos.** Cada campo se manda con su tipo JSON. Un número o un booleano donde va texto (`{"clave": 123}`, `{"codigo": true}`) responde `400 JSON malformado o ilegible`, y lo mismo un texto donde va un número o un booleano (`"true"` no es `true`). El backend no convierte tipos.
- **Código de sala.** Es el nombre del pueblo. El backend lo recorta y lo pasa a mayúsculas antes de usarlo, tanto en el cuerpo como en la ruta. `" bacalar "`, `"Bacalar"` y `"BACALAR"` son la misma sala. Las respuestas siempre lo devuelven en mayúsculas.
- **Token.** `POST /salas` le entrega un token al juego y `POST /salas/{codigo}/unirse` le entrega otro a la app. Cada token se entrega **una sola vez**: guárdalo en el dispositivo para reconectar. Se manda en el encabezado `Authorization: Bearer <token>`. El token decide la sala y el rol (`juego` o `app`), y solo sirve mientras la sala esté viva (`esperando_app` o `activa`).
- **Errores.** Todos tienen el mismo cuerpo:

	<!-- respuesta: error -->
	```json
	{
	  "timestamp": "2026-10-26T23:33:18.786573Z",
	  "status": 409,
	  "error": "Conflict",
	  "codigo": null,
	  "message": "'BACALAR' ya tiene una sala en curso",
	  "path": "/api/v1/salas",
	  "details": []
	}
	```

	Los errores siempre llegan en JSON (`Content-Type: application/json`), aunque el cliente pida otro formato en `Accept`. Por ejemplo, `GET /pueblos` con `Accept: application/xml` responde `406` con este mismo cuerpo.

	`message` está en español y se puede mostrar al jugador o al log. `details` solo trae elementos en errores de validación, uno por campo. `codigo` es un identificador estable para que el cliente decida qué hacer sin leer `message`. **Siempre viene**: vale `null` salvo en los errores que definen uno. Hoy hay dos: `puzzle_bloqueado` (`409` en `PUT /salas/{codigo}/puzzles/{puzzle}`) y `clave_solo_juego` (`403` en `POST /salas/{codigo}/descubrimientos`).

| Código | Cuándo |
|---|---|
| `400` | JSON malformado, campo obligatorio vacío, código fuera del catálogo, id de puzzle desconocido o aviso de puzzle inválido |
| `401` | Falta el token, el token no existe o su sala ya terminó |
| `403` | El token es válido pero de otra sala, la clave de descubrimiento la genera el servidor, la app manda una clave del juego (`codigo: "clave_solo_juego"`), el juego avisa un puzzle (`codigo: "puzzle_solo_app"`) o el rol no puede hacer esa operación |
| `404` | No hay sala en curso con ese código |
| `409` | Conflicto de estado: pueblo ocupado, sala con app ya enlazada o puzzle bloqueado (`codigo: "puzzle_bloqueado"`) |
| `500` | Error interno. El cuerpo no trae detalles |

## Flujo

1. El juego pide los pueblos libres y el jugador elige uno.
2. El juego crea la sala y guarda su token. La sala queda en `esperando_app`.
3. El jugador escribe el código en la app y la app se une. La app guarda su token. La sala pasa a `activa`.
4. Los dos consultan el estado con su token. Al reconectar, piden el estado otra vez para ponerse al día.
5. Durante la partida, el juego y la app registran descubrimientos y avisan cuando un puzzle empieza o se completa. El juego cambia la escena.
6. Al terminar, el juego cierra la sala. Los dos tokens dejan de servir.

Una sala sin actividad durante 2 horas está vencida. Desde ese momento sus tokens dejan de servir (`401`) y ninguna app se puede unir (`404`). Un job la marca `expirada` en el minuto siguiente y entonces el pueblo vuelve a estar libre.

## `GET /pueblos`

Público. Lista el catálogo en orden alfabético. Un pueblo está ocupado mientras tiene una sala viva.

| Parámetro | Valores | Efecto |
|---|---|---|
| `disponibles` | `true` o `false` (por defecto) | Con `true`, solo los libres |

```sh
curl 'http://localhost:8080/api/v1/pueblos?disponibles=true'
```

<!-- respuesta: pueblos -->
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

<!-- respuesta: sala-creada -->
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

<!-- respuesta: app-unida -->
```json
{"token": "LiMPYxrtkwFi…"}
```

La sala pasa a `activa` y su expiración se renueva.

| Error | `message` |
|---|---|
| `404` | `No hay una sala en curso con el código 'TULUM'` |
| `409` | `La sala 'BACALAR' ya tiene una app enlazada` |

`404` cubre un código que nunca tuvo sala, una sala que ya expiró o terminó y una sala cuyo `expira_en` ya pasó aunque el job todavía no la haya marcado `expirada`. Si dos apps se unen a la vez, una recibe `200` y la otra `409`.

## `GET /salas/{codigo}/estado`

Pide el token del juego o de la app **de esa sala**. Leer el estado no renueva la expiración.

```sh
curl http://localhost:8080/api/v1/salas/BACALAR/estado \
	-H "Authorization: Bearer $TOKEN"
```

`200 OK`:

<!-- respuesta: estado -->
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
| `dispositivos` | Quién tiene en este momento una conexión autenticada en `/ws` (`docs/tiempo-real.md`): `{"juego": bool, "app": bool}` |
| `expira_en` | Momento en que la sala expira si nadie escribe antes |

| Error | `message` |
|---|---|
| `401` sin token | `Falta el token: envía el encabezado Authorization: Bearer <token>` |
| `401` token desconocido, sala terminada o sala con `expira_en` vencido | `Token inválido o de una sala que ya terminó` |
| `403` token de otra sala | `El token no pertenece a esta sala` |

Ante un `401`, el token ya no sirve: vuelve a la pantalla de elegir pueblo (juego) o de escribir el código (app).

## Escrituras

Toda escritura exitosa renueva la sala: `expira_en` vuelve a quedar a 2 horas de ese momento, igual que al unirse. Una sala cuyo `expira_en` ya pasó cuenta como terminada: cualquier petición con su token, lectura o escritura, responde `401`, aunque el job que la marca `expirada` todavía no haya corrido. También deja un evento en la bitácora de la sala. Las escrituras de una misma sala se atienden una por una, así que dos peticiones simultáneas no se pisan.

## `POST /salas/{codigo}/descubrimientos`

Juego o app, con el token de esa sala. Registra algo que aparece en la libreta: una pieza, una bandera del juego, etc.

**Quién manda cada clave.**

| Claves | Quién las manda | Si las manda otro |
|---|---|---|
| `pescador_dialogo_completo`, `pieza_1`, `pieza_2`, `pieza_3`, `lookout_enabled`, `observatory_enabled` | Solo el juego | La app recibe `403` con `codigo: "clave_solo_juego"` |
| `coordenada_a`, `coordenada_b`, `observatorio_visible`, `magnetar_localizado` | Solo el servidor, al resolver un puzzle | Juego y app reciben `403` con `codigo: null` |
| Cualquier otra clave válida | Juego o app | |

La app no registra descubrimientos de la partida: avisa sus puzzles con `PUT /salas/{codigo}/puzzles/{puzzle}`.

```sh
curl -X POST http://localhost:8080/api/v1/salas/BACALAR/descubrimientos \
	-H "Authorization: Bearer $TOKEN" \
	-H 'Content-Type: application/json' \
	-d '{"clave": "pieza_1", "datos": {"lugar": "muelle"}}'
```

`201 Created` la primera vez:

<!-- respuesta: descubrimiento -->
```json
{"clave": "pieza_1", "datos": {"lugar": "muelle"}, "origen": "juego", "creado_en": "2026-11-05T18:02:11.513204Z"}
```

| Campo | Notas |
|---|---|
| `clave` | Obligatoria. Solo minúsculas sin acentos, dígitos y guion bajo, de 1 a 40 caracteres (`^[a-z0-9_]{1,40}$`) |
| `datos` | Opcional. Un objeto JSON cualquiera, de **4096 bytes como máximo** escrito como JSON compacto en UTF-8 (una letra con acento cuenta dos). Si no se manda, se guarda `{}` |
| `origen` | Lo pone el servidor según el token: `juego` o `app` |

**Es idempotente.** Repetir la misma clave responde `200 OK` con el descubrimiento que ya existía, **con sus datos originales**: los `datos` nuevos se ignoran. No se duplica ni se guarda otro evento, pero sí se renueva la expiración. Si el juego y la app mandan la misma clave a la vez, uno recibe `201` y el otro `200`.

**Habilita puzzles.** Tras un descubrimiento nuevo, el servidor revisa qué puzzles bloqueados ya cumplen sus requisitos y los pasa a `disponible`. El orden en que lleguen las cosas no importa.

| Puzzle | Pasa a `disponible` cuando |
|---|---|
| `radio` | Existe `pescador_dialogo_completo` |
| `photo` | Existen `pieza_1`, `pieza_2` y `pieza_3` |
| `lookout` | `photo` está `resuelto` y existe `lookout_enabled` |
| `observatory` | Existen `coordenada_a` y `coordenada_b`, `lookout` está `resuelto` y existe `observatory_enabled` |

El juego manda `lookout_enabled` cuando el jugador llega a la cima del faro y `observatory_enabled` cuando llega al observatorio. Los dos dispositivos se enteran por WebSocket (`descubrimiento.nuevo` y `puzzle.actualizado`, ver `docs/tiempo-real.md`); `GET /salas/{codigo}/estado` trae el estado completo.

| Error | Cuándo | `message` |
|---|---|---|
| `400` | Falta la clave | `Datos inválidos`, con `details: [{"field": "clave", "message": "es obligatoria"}]` |
| `400` | Clave con mayúsculas, guiones, acentos, espacios o más de 40 caracteres | `Datos inválidos`, con `details: [{"field": "clave", ...}]` |
| `400` | `datos` no es un objeto (`[1,2]`, `"texto"`, `5`) | `JSON malformado o ilegible` |
| `400` | `clave` no es texto (`12345`, `true`) | `JSON malformado o ilegible` |
| `400` | `datos` pasa de 4096 bytes | `Datos inválidos`, con `details: [{"field": "datos", "message": "no puede pasar de 4096 bytes escrito como JSON"}]` |
| `401` | Sin token, token inválido o sala terminada | Igual que en `GET estado` |
| `403` | Token de otra sala | `El token no pertenece a esta sala` |
| `403` | La clave es `coordenada_a`, `coordenada_b`, `observatorio_visible` o `magnetar_localizado` | `La clave 'coordenada_a' la genera el servidor al resolver un puzzle; los clientes no pueden enviarla` |
| `403` | Token de la app con `pescador_dialogo_completo`, `pieza_1`, `pieza_2`, `pieza_3`, `lookout_enabled` u `observatory_enabled`, exista o no la clave | `La clave 'pieza_1' la registra el juego; la app no puede enviarla`, con `"codigo": "clave_solo_juego"` |

Esas cuatro claves son los resultados de los puzzles y solo las crea el servidor, al resolver cada uno con `PUT /salas/{codigo}/puzzles/{puzzle}`.

Un `403` no escribe nada: no guarda el descubrimiento ni un evento, no emite mensajes por WebSocket y no renueva la expiración. Una clave del juego que ya existe también da `403` a la app, no `200`. Respuesta real:

<!-- respuesta: error -->
```json
{
  "timestamp": "2026-11-07T15:39:38.400910Z",
  "status": 403,
  "error": "Forbidden",
  "codigo": "clave_solo_juego",
  "message": "La clave 'pieza_1' la registra el juego; la app no puede enviarla",
  "path": "/api/v1/salas/BACALAR/descubrimientos",
  "details": []
}
```

El límite de `datos` lo fijaron los agentes que construyeron el paso, a raíz de una revisión: la especificación no ponía ninguno y un cliente podía guardar un JSON de cualquier tamaño. Se cambia con la propiedad `vestigia.descubrimientos.datos-max-bytes` (por defecto `4096`).

## `PUT /salas/{codigo}/puzzles/{puzzle}`

**Solo la app**, con el token de esa sala. Avisa que un puzzle empezó o se completó. Los cuatro puzzles se juegan en el teléfono y quien los juega decide cuándo se completaron: el servidor solo revisa que el aviso tenga la forma correcta y que el puzzle no esté bloqueado. No mide ni valida números. El juego no avisa puzzles: con su token recibe `403` (`codigo: "puzzle_solo_app"`) y se entera de cada cambio por el WebSocket (`puzzle.actualizado`) y por los descubrimientos resultado.

```sh
curl -X PUT http://localhost:8080/api/v1/salas/BACALAR/puzzles/photo \
	-H "Authorization: Bearer $TOKEN" \
	-H 'Content-Type: application/json' \
	-d '{"datos": {"completada": true}}'
```

`200 OK` con el estado del puzzle:

<!-- respuesta: puzzle -->
```json
{"puzzle": "photo", "estado": "resuelto", "datos": {"completada": true}}
```

| Puzzle | Lo juega | `datos` válidos | Resultado al resolverse (origen `servidor`) |
|---|---|---|---|
| `radio` | La app | `{"iniciado": true}` o `{"sintonizada": true}` | `coordenada_a` con `{"tipo": "ascension_recta", "valor": "137"}` |
| `photo` | La app | `{"iniciado": true}` o `{"completada": true}` | `coordenada_b` con `{"tipo": "declinacion", "valor": "+042"}` |
| `lookout` | La app | `{"iniciado": true}` o `{"completada": true}` | `observatorio_visible` con `{}` |
| `observatory` | La app | `{"iniciado": true}` o `{"completada": true}` | `magnetar_localizado` con `{}` |

- **Solo `true`.** `datos` debe tener exactamente una de esas claves y su valor debe ser el booleano `true`. El texto `"true"`, `1`, `false`, `null`, una clave de más, un objeto vacío o `datos` ausente responden `400`.
- **`iniciado`.** La app lo manda al abrir `radio`, `photo`, `lookout` u `observatory`. El puzzle pasa de `disponible` a `en_curso` y las métricas pueden medir cuánto tardó. No es obligatorio: `completada` también resuelve un puzzle `disponible`.
- **Coordenadas.** `valor` es texto para conservar el signo y los ceros (`"+042"`, no `42`). Los valores salen de las propiedades `vestigia.puzzles.coordenadas.ar` y `vestigia.puzzles.coordenadas.dec`. La app los muestra desde `GET estado`, no los lleva escritos.
- **Solo la app.** El token del juego recibe `403` con `codigo: "puzzle_solo_app"` en los cuatro puzzles, aunque el aviso sea válido. `radio` se muestra a la app cuando el juego registra `pescador_dialogo_completo` (pasa a `disponible`); antes está `bloqueado` y responde `409`.

**Cómo cambia el estado.** Un aviso solo mueve el puzzle hacia adelante:

| Estado actual | `iniciado` | `completada` o `sintonizada` |
|---|---|---|
| `bloqueado` | `409` | `409` |
| `disponible` | pasa a `en_curso` | pasa a `resuelto` |
| `en_curso` | `200` sin cambios | pasa a `resuelto` |
| `resuelto` | `200` sin cambios | `200` sin cambios |

`200` sin cambios responde el estado actual con los `datos` que lo dejaron así y no guarda otro evento ni otro descubrimiento, pero sí renueva la expiración de la sala, igual que un descubrimiento repetido. Si dos avisos llegan a la vez, el puzzle se resuelve una sola vez y los dos reciben `200`.

**Al resolver**, en la misma transacción, el servidor guarda el aviso en `datos`, crea el descubrimiento resultado con origen `servidor`, pasa a `disponible` los puzzles que ya cumplen sus requisitos (ver la tabla de `POST /descubrimientos`), guarda los eventos y renueva la expiración. Por ejemplo, si `lookout_enabled` llegó antes, resolver `photo` deja `lookout` en `disponible` en la misma petición. Los dos dispositivos reciben por WebSocket un mensaje por cada cambio, después de que la transacción se confirma (`docs/tiempo-real.md`); `GET /salas/{codigo}/estado` trae el estado completo.

| Error | Cuándo | `message` |
|---|---|---|
| `400` | `datos` falta o es `null` | `Datos inválidos`, con `details: [{"field": "datos", "message": "es obligatorio"}]` |
| `400` | `datos` no es un aviso válido para ese puzzle, incluido un valor que no es objeto (`true`, `[true]`, `"completada"`, `123`) | `Datos inválidos`, con `details: [{"field": "datos", "message": "en photo debe ser exactamente {\"completada\": true} o {\"iniciado\": true}"}]` |
| `400` | El id del puzzle no es `radio`, `photo`, `lookout` ni `observatory`, en minúsculas. Incluye los ids viejos `fotografia`, `mirador` y `observatorio` | `'fotografia' no es un puzzle válido. Usa uno de: radio, photo, lookout, observatory` |
| `401` | Sin token, token inválido o sala terminada | Igual que en `GET estado` |
| `403` | Token de otra sala | `El token no pertenece a esta sala` |
| `403` | Token del juego, en cualquier puzzle | `Los puzzles los avisa la app; el juego no puede reportar 'radio'`, con `"codigo": "puzzle_solo_app"` |
| `409` | El puzzle está `bloqueado` | `El puzzle 'radio' está bloqueado: todavía no se cumplen sus requisitos`, con `"codigo": "puzzle_bloqueado"` |

Un id de puzzle desconocido responde `400` y no `404`, igual que una escena desconocida: la ruta existe y lo que está mal es un valor que manda el cliente.

**Qué error gana.** Si una petición tiene varios problemas, responde el primero de esta lista:

1. Falta el token, es inválido o es de otra sala (`401` o `403`).
2. El cuerpo no es un objeto JSON válido o repite una clave (`400 JSON malformado o ilegible`).
3. `datos` falta o es `null` (`400`, `details` con `es obligatorio`).
4. El id del puzzle no existe (`400` con la lista de ids).
5. `datos` no es un aviso válido para ese puzzle, incluido un valor que no es objeto (`400`, `details` con la forma esperada).
6. El token es del juego (`403`, `codigo: "puzzle_solo_app"`).
7. La sala terminó entre el token y la escritura (`401`).
8. El puzzle está `bloqueado` (`409`).

Por eso `PUT /puzzles/fotografia` con `{}` responde el `400` de `datos`, y con `{"datos": {"completada": true}}` el del id. Un aviso mal formado da `400` aunque el puzzle esté bloqueado, y también aunque el token sea del juego: primero se revisa el id y la forma, después el rol.

Respuesta real de un `409`:

<!-- respuesta: error -->
```json
{
  "timestamp": "2026-11-06T06:25:51.900917Z",
  "status": 409,
  "error": "Conflict",
  "codigo": "puzzle_bloqueado",
  "message": "El puzzle 'radio' está bloqueado: todavía no se cumplen sus requisitos",
  "path": "/api/v1/salas/BACALAR/puzzles/radio",
  "details": []
}
```

Respuesta real de un `403` cuando el juego avisa un puzzle:

<!-- respuesta: error -->
```json
{
  "timestamp": "2026-11-08T15:39:38.400910Z",
  "status": 403,
  "error": "Forbidden",
  "codigo": "puzzle_solo_app",
  "message": "Los puzzles los avisa la app; el juego no puede reportar 'radio'",
  "path": "/api/v1/salas/BACALAR/puzzles/radio",
  "details": []
}
```

## `PUT /salas/{codigo}/escena`

**Solo el juego**, con el token de esa sala. Cambia la escena que ve la libreta.

```sh
curl -X PUT http://localhost:8080/api/v1/salas/BACALAR/escena \
	-H "Authorization: Bearer $TOKEN" \
	-H 'Content-Type: application/json' \
	-d '{"escena": "faro"}'
```

`200 OK`:

<!-- respuesta: escena -->
```json
{"escena_actual": "faro"}
```

Escenas válidas, en orden: `introduccion`, `pueblo`, `faro`, `observatorio` y `desenlace`. Se escriben exactamente así, en minúsculas. La sala empieza en `introduccion`. Mandar la escena en la que ya está responde lo mismo, renueva la expiración y no guarda otro evento.

| Error | Cuándo | `message` |
|---|---|---|
| `400` | Falta `escena` o viene vacía | `Datos inválidos`, con `details: [{"field": "escena", "message": "es obligatoria"}]` |
| `400` | `escena` no es texto (`123`, `true`) | `JSON malformado o ilegible` |
| `400` | Escena desconocida (`"bosque"`, `"FARO"`) | `'bosque' no es una escena válida. Usa una de: introduccion, pueblo, faro, observatorio, desenlace` |
| `401` | Sin token, token inválido o sala terminada | Igual que en `GET estado` |
| `403` | Token de la app, mande el cuerpo que mande (se revisa antes que el cuerpo) | `Solo el juego puede cambiar la escena` |
| `403` | Token de otra sala | `El token no pertenece a esta sala` |

## `DELETE /salas/{codigo}`

**Solo el juego**, con el token de esa sala. Cierra la partida.

```sh
curl -X DELETE http://localhost:8080/api/v1/salas/BACALAR \
	-H "Authorization: Bearer $TOKEN"
```

`204 No Content`, sin cuerpo.

- La sala pasa a `finalizada` y se conserva como historial. Es la única escritura que no renueva `expira_en`: una sala cerrada ya no expira.
- El código vuelve a quedar libre: otro juego puede crear una sala nueva con el mismo pueblo.
- Los tokens del juego y de la app dejan de servir. Cualquier petición con ellos, incluido otro `DELETE`, responde `401`.
- Se puede cerrar una sala en `esperando_app` o en `activa`.

| Error | Cuándo | `message` |
|---|---|---|
| `401` | Sin token, token inválido o sala ya terminada | Igual que en `GET estado` |
| `403` | Token de la app | `Solo el juego puede cerrar la sala` |
| `403` | Token de otra sala | `El token no pertenece a esta sala` |

## Sesión y administración (PWA)

Estas rutas son para la PWA de administración y **no** las usan el juego ni la app. Piden un token distinto al de las salas: el de una **sesión de administración**, que devuelve `POST /auth/login`. Se manda igual, en `Authorization: Bearer <token>`.

- Los dos tipos de token no se mezclan. Un token de sala en una ruta de administración da `401`, y un token de administración en una ruta de sala (`/salas/{codigo}/...`) también.
- La sesión dura **8 horas** y no se renueva. Pasado ese tiempo, o tras `logout`, el token da `401` y hay que iniciar sesión otra vez.
- Desactivar a un usuario, o cambiarle la contraseña, cierra sus sesiones abiertas.
- Hoy el único rol es `admin`. Las rutas de `/admin` responden `403` a un rol sin permiso, cuando existan otros.
- El primer administrador no se crea por la API: lo crea el backend al arrancar con las variables `ADMIN_BOOTSTRAP_EMAIL` y `ADMIN_BOOTSTRAP_PASSWORD` si no hay ningún usuario (ver `docs/despliegue.md`).

## `POST /auth/login`

Público. Inicia sesión con correo y contraseña.

<!-- peticion -->
```json
{"correo": "raul@vestigia.site", "password": "una-contraseña-larga"}
```

El correo se recorta y se pasa a minúsculas. Respuesta `200`:

<!-- respuesta: sesion -->
```json
{
  "token": "Zk3o1x0b8QmD…",
  "expira_en": "2026-11-09T07:10:00.000000Z",
  "usuario": {
    "id": "5b0c1f3e-7d0b-4f5e-9a39-2f0f5e8f6a11",
    "nombre": "Raúl",
    "correo": "raul@vestigia.site",
    "rol": "admin",
    "activo": true,
    "creado_en": "2026-11-08T23:10:00.000000Z"
  }
}
```

| Estado | Cuándo |
|---|---|
| `400` | Falta `correo` o `password` |
| `401` | Correo desconocido, contraseña incorrecta o usuario desactivado. Es el mismo mensaje en los tres casos: `Correo o contraseña incorrectos` |
| `429` | Cinco intentos fallidos con el mismo correo en 15 minutos. `codigo: "demasiados_intentos"`. Pasados los 15 minutos se puede intentar de nuevo |

## `POST /auth/logout` y `GET /auth/yo`

Con la sesión en `Authorization`. `POST /auth/logout` responde `204` sin cuerpo y borra la sesión. `GET /auth/yo` responde `200` con el usuario dueño del token; sirve para comprobar, al abrir la PWA, que el token guardado sigue vigente.

<!-- respuesta: usuario -->
```json
{
  "id": "5b0c1f3e-7d0b-4f5e-9a39-2f0f5e8f6a11",
  "nombre": "Raúl",
  "correo": "raul@vestigia.site",
  "rol": "admin",
  "activo": true,
  "creado_en": "2026-11-08T23:10:00.000000Z"
}
```

Sin token, con un token desconocido o con uno vencido, responden `401`.

## `GET /admin/usuarios`

Solo admin. Lista los usuarios, del más antiguo al más nuevo. Nunca trae contraseñas.

<!-- respuesta: usuarios -->
```json
[
  {
    "id": "5b0c1f3e-7d0b-4f5e-9a39-2f0f5e8f6a11",
    "nombre": "Raúl",
    "correo": "raul@vestigia.site",
    "rol": "admin",
    "activo": true,
    "creado_en": "2026-11-08T23:10:00.000000Z"
  }
]
```

## `POST /admin/usuarios`

Solo admin. Crea un usuario y responde `201` con el usuario (mismo JSON que `GET /auth/yo`).

<!-- peticion -->
```json
{"nombre": "María", "correo": "maria@vestigia.site", "password": "otra-contraseña", "rol": "admin"}
```

La contraseña lleva de 8 a 72 caracteres y se guarda con BCrypt; no se puede volver a leer. El correo es único sin importar mayúsculas.

| Estado | Cuándo |
|---|---|
| `400` | Falta un campo, el correo no es válido, la contraseña no tiene de 8 a 72 caracteres o el rol no existe. `details` dice cuál |
| `409` | Ya hay un usuario con ese correo. `codigo: "correo_duplicado"` |

## `PUT /admin/usuarios/{id}`

Solo admin. Cambia un usuario. Cada campo es opcional y el que falta no se toca: `nombre`, `rol`, `activo` y `password`. El correo no cambia. Responde `200` con el usuario ya cambiado.

<!-- peticion -->
```json
{"activo": false}
```

| Estado | Cuándo |
|---|---|
| `400` | Un campo es inválido (nombre vacío, contraseña fuera de 8 a 72 caracteres, rol desconocido, `activo` que no es booleano) |
| `404` | No existe ese usuario |
| `409` | Dejaría al sistema sin administradores activos. `codigo: "ultimo_admin"` |

## `DELETE /admin/usuarios/{id}`

Solo admin. Elimina el usuario y sus sesiones. Responde `204`. Da `404` si no existe y `409` con `codigo: "ultimo_admin"` si es el único administrador activo.

## `GET /admin/salas`

Solo admin. Lista todas las salas, vivas y terminadas, de la más nueva a la más antigua. Un mismo código puede aparecer varias veces: la sala vigente y las que ya terminaron (el pueblo se reutiliza). Por eso las salas se piden por `id`, no por código.

Parámetros opcionales: `estado` (`esperando_app`, `activa`, `finalizada` o `expirada`) y `limite` (de 1 a 500; si falta, 100). Un `estado` que no existe da `400`.

<!-- respuesta: salas-admin -->
```json
[
  {
    "id": "0b6c0b0e-8f6e-4c53-b8f1-6f3a2e6f9b21",
    "codigo": "BACALAR",
    "estado": "activa",
    "escena_actual": "faro",
    "puzzles_resueltos": 1,
    "puzzles_total": 4,
    "app_unida": true,
    "dispositivos": {"juego": true, "app": true},
    "creada_en": "2026-11-08T22:40:00.000000Z",
    "ultima_actividad": "2026-11-08T23:05:00.000000Z",
    "expira_en": "2026-11-09T01:05:00.000000Z"
  }
]
```

## `GET /admin/salas/{id}`

Solo admin. El detalle de una sala, viva o terminada: escena, puzzles, descubrimientos, dispositivos conectados y la bitácora completa. No trae tokens ni hashes. Da `400` si el `id` no es un UUID y `404` si no existe.

<!-- respuesta: sala-admin -->
```json
{
  "id": "0b6c0b0e-8f6e-4c53-b8f1-6f3a2e6f9b21",
  "codigo": "BACALAR",
  "estado": "activa",
  "escena_actual": "faro",
  "puzzles": [
    {"puzzle": "radio", "estado": "resuelto", "datos": {"sintonizada": true}},
    {"puzzle": "photo", "estado": "disponible", "datos": {}},
    {"puzzle": "lookout", "estado": "bloqueado", "datos": {}},
    {"puzzle": "observatory", "estado": "bloqueado", "datos": {}}
  ],
  "descubrimientos": [
    {"clave": "pescador_dialogo_completo", "datos": {}, "origen": "juego", "creado_en": "2026-11-08T22:50:00.000000Z"}
  ],
  "dispositivos": {"juego": true, "app": true},
  "app_unida": true,
  "creada_en": "2026-11-08T22:40:00.000000Z",
  "ultima_actividad": "2026-11-08T23:05:00.000000Z",
  "expira_en": "2026-11-09T01:05:00.000000Z",
  "eventos": [
    {"tipo": "sala.creada", "origen": "juego", "payload": {"codigo": "BACALAR"}, "creado_en": "2026-11-08T22:40:00.000000Z"}
  ]
}
```

## `DELETE /admin/salas/{id}`

Solo admin. Cierra una sala a la fuerza, por ejemplo una que quedó atascada. Si está viva pasa a `finalizada`, su código queda libre, los tokens del juego y de la app dejan de servir (`401`) y los dos dispositivos reciben `sala.cerrada` por WebSocket, que se cierra con `1000`. La bitácora guarda `sala.finalizada` con origen `servidor` y `{"cerrada_por": "admin", "usuario": "<id>"}`. Responde `204`. Si la sala ya había terminado no cambia nada y también responde `204`; si no existe, `404`.

## Limitaciones conocidas

- **URLs que rechaza el servidor HTTP.** Tomcat, el servidor que lleva el backend, rechaza algunas URLs antes de que lleguen a la aplicación: una barra codificada (`%2F`), un byte nulo (`%00`), un escape inválido (`%zz`) o una URL demasiado larga. Responde `400` con una página HTML de Tomcat, no con el cuerpo de error en JSON. Ningún cliente de Vestigia necesita esos caracteres: los códigos de sala son solo letras y los ids de puzzle son fijos.
- **Barra final.** Las rutas no aceptan una barra al final. `PUT /salas/BACALAR/puzzles/` o `.../puzzles/photo/` responden `404` con el cuerpo uniforme y un `message` del tipo `No static resource …`. Escribe las rutas sin la barra final.
- **Punto y coma en la ruta.** Spring descarta lo que va después de `;` en un tramo de la ruta, así que `.../puzzles/photo;x` se atiende como `.../puzzles/photo`.
