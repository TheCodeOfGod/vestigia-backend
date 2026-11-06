# API REST

Guía para quien integra el juego (Unity) o la app (Flutter). Todas las rutas cuelgan de `/api/v1`. Los ejemplos usan `http://localhost:8080`. Swagger UI con los mismos endpoints, sus ejemplos de petición y respuesta y sus errores está en `/swagger-ui/index.html`; el documento OpenAPI en JSON, en `/v3/api-docs`. Para probar en Swagger UI una ruta con candado, pulsa **Authorize** y pega el token (sin la palabra `Bearer`).

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

	`message` está en español y se puede mostrar al jugador o al log. `details` solo trae elementos en errores de validación, uno por campo. `codigo` es un identificador estable para que el cliente decida qué hacer sin leer `message`. **Siempre viene**: vale `null` salvo en los errores que definen uno. Hoy el único es `puzzle_bloqueado` (`409` en `PUT /salas/{codigo}/puzzles/{puzzle}`).

| Código | Cuándo |
|---|---|
| `400` | JSON malformado, campo obligatorio vacío, código fuera del catálogo, id de puzzle desconocido o aviso de puzzle inválido |
| `401` | Falta el token, el token no existe o su sala ya terminó |
| `403` | El token es válido pero de otra sala, la clave de descubrimiento la genera el servidor o el rol no puede hacer esa operación |
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

Esas cuatro claves son los resultados de los puzzles y solo las crea el servidor, al resolver cada uno con `PUT /salas/{codigo}/puzzles/{puzzle}`.

El límite de `datos` lo fijaron los agentes que construyeron el paso, a raíz de una revisión: la especificación no ponía ninguno y un cliente podía guardar un JSON de cualquier tamaño. Se cambia con la propiedad `vestigia.descubrimientos.datos-max-bytes` (por defecto `4096`).

## `PUT /salas/{codigo}/puzzles/{puzzle}`

Juego o app, con el token de esa sala. Avisa que un puzzle empezó o se completó. Quien juega el puzzle decide cuándo se completó: el servidor solo revisa que el aviso tenga la forma correcta y que el puzzle no esté bloqueado. No mide ni valida números.

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
| `radio` | El juego | `{"sintonizada": true}` | `coordenada_a` con `{"tipo": "ascension_recta", "valor": "137"}` |
| `photo` | La app | `{"iniciado": true}` o `{"completada": true}` | `coordenada_b` con `{"tipo": "declinacion", "valor": "+042"}` |
| `lookout` | La app | `{"iniciado": true}` o `{"completada": true}` | `observatorio_visible` con `{}` |
| `observatory` | La app | `{"iniciado": true}` o `{"completada": true}` | `magnetar_localizado` con `{}` |

- **Solo `true`.** `datos` debe tener exactamente una de esas claves y su valor debe ser el booleano `true`. El texto `"true"`, `1`, `false`, `null`, una clave de más, un objeto vacío o `datos` ausente responden `400`. `iniciado` no existe en `radio`.
- **`iniciado`.** La app lo manda al abrir `photo`, `lookout` u `observatory`. El puzzle pasa de `disponible` a `en_curso` y las métricas pueden medir cuánto tardó. No es obligatorio: `completada` también resuelve un puzzle `disponible`.
- **Coordenadas.** `valor` es texto para conservar el signo y los ceros (`"+042"`, no `42`). Los valores salen de las propiedades `vestigia.puzzles.coordenadas.ar` y `vestigia.puzzles.coordenadas.dec`. La app los muestra desde `GET estado`, no los lleva escritos.
- Cualquier rol puede reportar cualquier puzzle. La tabla dice quién lo juega en el diseño, pero el servidor no lo exige.

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
| `409` | El puzzle está `bloqueado` | `El puzzle 'radio' está bloqueado: todavía no se cumplen sus requisitos`, con `"codigo": "puzzle_bloqueado"` |

Un id de puzzle desconocido responde `400` y no `404`, igual que una escena desconocida: la ruta existe y lo que está mal es un valor que manda el cliente.

**Qué error gana.** Si una petición tiene varios problemas, responde el primero de esta lista:

1. Falta el token, es inválido o es de otra sala (`401` o `403`).
2. El cuerpo no es un objeto JSON válido o repite una clave (`400 JSON malformado o ilegible`).
3. `datos` falta o es `null` (`400`, `details` con `es obligatorio`).
4. El id del puzzle no existe (`400` con la lista de ids).
5. `datos` no es un aviso válido para ese puzzle, incluido un valor que no es objeto (`400`, `details` con la forma esperada).
6. La sala terminó entre el token y la escritura (`401`).
7. El puzzle está `bloqueado` (`409`).

Por eso `PUT /puzzles/fotografia` con `{}` responde el `400` de `datos`, y con `{"datos": {"completada": true}}` el del id. Un aviso mal formado da `400` aunque el puzzle esté bloqueado.

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

## Limitaciones conocidas

- **URLs que rechaza el servidor HTTP.** Tomcat, el servidor que lleva el backend, rechaza algunas URLs antes de que lleguen a la aplicación: una barra codificada (`%2F`), un byte nulo (`%00`), un escape inválido (`%zz`) o una URL demasiado larga. Responde `400` con una página HTML de Tomcat, no con el cuerpo de error en JSON. Ningún cliente de Vestigia necesita esos caracteres: los códigos de sala son solo letras y los ids de puzzle son fijos.
- **Barra final.** Las rutas no aceptan una barra al final. `PUT /salas/BACALAR/puzzles/` o `.../puzzles/photo/` responden `404` con el cuerpo uniforme y un `message` del tipo `No static resource …`. Escribe las rutas sin la barra final.
- **Punto y coma en la ruta.** Spring descarta lo que va después de `;` en un tramo de la ruta, así que `.../puzzles/photo;x` se atiende como `.../puzzles/photo`.
