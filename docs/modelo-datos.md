# Modelo de datos

El esquema vive en las migraciones de Flyway de `src/main/resources/db/migration/`. Flyway las aplica al arrancar el backend y Hibernate solo valida que las entidades coincidan con las tablas (`ddl-auto=validate`). Si una entidad no coincide con su tabla, el backend no arranca.

Una migración que ya se aplicó no se edita. Cualquier cambio va en una migración nueva.

| Migración | Qué hace |
|---|---|
| `V1__esquema.sql` | Crea las cinco tablas y sus índices |
| `V2__seed_pueblos.sql` | Carga los 101 códigos del catálogo de pueblos, aprobados por Raúl |
| `V3__ids_puzzles_en_ingles.sql` | Renombra los ids de puzzle al inglés (`fotografia` → `photo`, `mirador` → `lookout`, `observatorio` → `observatory`): quita el `CHECK` de `puzzles_estado.puzzle`, actualiza las filas existentes y vuelve a crear el `CHECK` con los ids nuevos |

## Tablas

| Tabla | Entidad JPA | Para qué |
|---|---|---|
| `codigos_pueblo` | `CodigoPuebloModel` | Catálogo fijo de nombres de sala |
| `salas` | `SalaModel` | Una partida: un juego y, como máximo, una app |
| `puzzles_estado` | `PuzzleEstadoModel` | Una fila por sala y puzzle |
| `descubrimientos` | `DescubrimientoModel` | Lo que aparece en la libreta |
| `eventos` | `EventoModel` | Bitácora para las métricas del plan de pruebas. Una fila por escritura exitosa |

Las entidades no declaran relaciones JPA entre sí. `puzzles_estado` y `descubrimientos` guardan `sala_id` y se consultan por esa columna.

### `codigos_pueblo`

| Columna | Tipo | Notas |
|---|---|---|
| `codigo` | `VARCHAR(30)` PK | Solo letras mayúsculas sin acentos ni ñ (`CHECK codigo ~ '^[A-Z]+$'`) |

Son nombres de Pueblos Mágicos de México de una sola palabra. La lista no cambia en tiempo de ejecución.

### `salas`

| Columna | Tipo | Notas |
|---|---|---|
| `id` | `UUID` PK | Lo genera el backend |
| `codigo` | `VARCHAR(30)` | FK a `codigos_pueblo` |
| `estado` | `VARCHAR(20)` | `esperando_app`, `activa`, `finalizada` o `expirada` |
| `escena_actual` | `VARCHAR(20)` | Empieza en `introduccion`. Valores: `introduccion`, `pueblo`, `faro`, `observatorio`, `desenlace`. La base no los restringe (son provisionales); los valida el enum `SalaModel.Escena` |
| `token_juego_hash` | `CHAR(64)` | SHA-256 del token del juego, en hexadecimal minúscula |
| `token_app_hash` | `CHAR(64)` | SHA-256 del token de la app. `NULL` hasta que la app se une |
| `creada_en` | `TIMESTAMPTZ` | |
| `ultima_actividad` | `TIMESTAMPTZ` | Se renueva con cada escritura exitosa |
| `expira_en` | `TIMESTAMPTZ` | `ultima_actividad` + `vestigia.salas.expiracion` |

**Índice único parcial `salas_codigo_viva`.** `UNIQUE (codigo) WHERE estado IN ('esperando_app', 'activa')`. Una sala está **viva** en esos dos estados. El índice impide que un código tenga dos salas vivas, incluso si dos juegos lo reclaman en el mismo instante: Postgres deja pasar una inserción y rechaza la otra, y el backend responde `409` a la que perdió. Las salas `finalizada` y `expirada` no cuentan, así que se conservan como historial y el código vuelve a quedar libre.

### `puzzles_estado`

| Columna | Tipo | Notas |
|---|---|---|
| `sala_id` | `UUID` | PK compuesta con `puzzle`. FK a `salas` |
| `puzzle` | `VARCHAR(20)` | `radio`, `photo`, `lookout` u `observatory` (`CHECK puzzles_estado_puzzle_check`). En inglés, como en la app. `V1` los creó en español y `V3` los renombró |
| `estado` | `VARCHAR(20)` | `bloqueado`, `disponible`, `en_curso` o `resuelto` |
| `datos` | `JSONB` | El aviso del cliente que dejó el puzzle en su estado actual (`{"iniciado": true}`, `{"completada": true}` o `{"sintonizada": true}`). `{}` hasta el primer aviso |
| `actualizado_en` | `TIMESTAMPTZ` | |

Al crear una sala se insertan sus cuatro filas, todas en `bloqueado`. `radio` también empieza bloqueado porque su requisito, el descubrimiento `pescador_dialogo_completo`, todavía no puede existir.

`HabilitacionPuzzlesService` pasa un puzzle de `bloqueado` a `disponible` cuando se cumplen sus requisitos. Se recalcula tras cada descubrimiento nuevo, incluido el resultado que crea el servidor al resolver un puzzle, así que también corre tras cada puzzle resuelto. Lee el estado completo de la sala, así que el orden en que llegan los requisitos no importa. Nunca baja un puzzle de estado.

| Puzzle | Pasa a `disponible` cuando |
|---|---|
| `radio` | Existe `pescador_dialogo_completo` |
| `photo` | Existen `pieza_1`, `pieza_2` y `pieza_3` |
| `lookout` | `photo` está `resuelto` y existe `lookout_enabled` |
| `observatory` | Existen `coordenada_a` y `coordenada_b`, `lookout` está `resuelto` y existe `observatory_enabled` |

`PuzzleService` mueve el resto del ciclo con los avisos de `PUT /salas/{codigo}/puzzles/{puzzle}`. El enum `PuzzleEstadoModel.Estado` declara los estados en el orden en que avanza un puzzle, y un aviso solo lo mueve hacia adelante:

```
             requisitos                iniciado                 completada o sintonizada
bloqueado ─────────────▶ disponible ─────────────▶ en_curso ──────────────────────────────▶ resuelto
                              │                                                               ▲
                              └──────────────────── completada o sintonizada ─────────────────┘
```

Los cuatro puzzles los avisa la app; el token del juego recibe `403` (`puzzle_solo_app`). Un aviso sobre un puzzle `bloqueado` responde `409` sin tocar nada. Un aviso que ya no mueve el puzzle (`iniciado` en `en_curso`, cualquiera en `resuelto`) no lo cambia. Al pasar a `resuelto`, en la misma transacción, el servidor crea el descubrimiento resultado con origen `servidor`: `coordenada_a` (radio), `coordenada_b` (photo), `observatorio_visible` (lookout) o `magnetar_localizado` (observatory).

### `descubrimientos`

| Columna | Tipo | Notas |
|---|---|---|
| `id` | `BIGSERIAL` PK | |
| `sala_id` | `UUID` | FK a `salas`. `UNIQUE (sala_id, clave)` |
| `clave` | `VARCHAR(40)` | Por ejemplo `coordenada_a` |
| `datos` | `JSONB` | Por ejemplo `{"tipo": "ascension_recta", "valor": "137"}`. `valor` va en texto para conservar el signo y los ceros (`"+042"`). Lo que mandan los clientes se limita a `vestigia.descubrimientos.datos-max-bytes` (4096 bytes de JSON) |
| `origen` | `VARCHAR(10)` | `juego`, `app` o `servidor` |
| `creado_en` | `TIMESTAMPTZ` | |

**Claves que se usan hoy.** `POST /descubrimientos` acepta cualquier clave con `^[a-z0-9_]{1,40}$`; estas son las que usa la partida. Las guías de cada cliente dicen cuándo mandar cada una.

| Clave | La registra | Para qué |
|---|---|---|
| `pescador_dialogo_completo` | Juego | Habilita `radio` |
| `pieza_1`, `pieza_2`, `pieza_3` | Juego | Las tres juntas habilitan `photo` |
| `lookout_enabled` | Juego | Con `photo` resuelto, habilita `lookout` |
| `observatory_enabled` | Juego | Con las dos coordenadas y `lookout` resuelto, habilita `observatory` |
| `coordenada_a` | Servidor, al resolver `radio` | `{"tipo": "ascension_recta", "valor": "137"}` |
| `coordenada_b` | Servidor, al resolver `photo` | `{"tipo": "declinacion", "valor": "+042"}` |
| `observatorio_visible` | Servidor, al resolver `lookout` | `{}` |
| `magnetar_localizado` | Servidor, al resolver `observatory` | `{}` |

Las claves del servidor dan `403` si las manda un cliente. Las del juego dan `403` con `codigo: "clave_solo_juego"` si las manda la app (P8). Las listas viven en `DescubrimientoService.CLAVES_DEL_SERVIDOR` y `CLAVES_DEL_JUEGO`; una prueba unitaria revisa que cada requisito de `HabilitacionPuzzlesService` esté en una de las dos.

### `eventos`

| Columna | Tipo | Notas |
|---|---|---|
| `id` | `BIGSERIAL` PK | |
| `sala_id` | `UUID` | FK a `salas`. Índice `eventos_sala_tiempo (sala_id, creado_en)` |
| `tipo` | `VARCHAR(40)` | |
| `payload` | `JSONB` | Datos del evento (ver la tabla de tipos) |
| `origen` | `VARCHAR(10)` | `juego`, `app` o `servidor`, igual que en `descubrimientos` |
| `creado_en` | `TIMESTAMPTZ` | |

Tipos de evento. El tipo se guarda con su punto. En Java es el enum `EventoModel.Tipo` y un convertidor JPA pasa `sala_creada` a `sala.creada`.

| `tipo` | Cuándo | `origen` | `payload` |
|---|---|---|---|
| `sala.creada` | `POST /salas` | `juego` | `{"codigo": "BACALAR"}` |
| `app.unida` | `POST /salas/{codigo}/unirse` | `app` | `{}` |
| `descubrimiento.registrado` | `POST /salas/{codigo}/descubrimientos` con una clave nueva, o el servidor crea el resultado de un puzzle resuelto | `juego` o `app`; `servidor` para los resultados | `{"clave": "pieza_1", "datos": {...}}`, `{"clave": "coordenada_b", "datos": {"tipo": "declinacion", "valor": "+042"}}` |
| `escena.cambiada` | `PUT /salas/{codigo}/escena` con una escena distinta de la actual | `juego` | `{"anterior": "introduccion", "escena": "faro"}` |
| `sala.finalizada` | `DELETE /salas/{codigo}` | `juego` | `{}` |
| `sala.expirada` | El job de expiración marca la sala `expirada` | `servidor` | `{}` |
| `puzzle.habilitado` | Un puzzle pasa de `bloqueado` a `disponible` | `servidor` | `{"puzzle": "photo", "estado": "disponible", "datos": {}}` |
| `puzzle.iniciado` | `PUT /salas/{codigo}/puzzles/{puzzle}` con `iniciado` pasa el puzzle de `disponible` a `en_curso` | `juego` o `app` | `{"puzzle": "photo", "estado": "en_curso", "datos": {"iniciado": true}}` |
| `puzzle.resuelto` | `PUT /salas/{codigo}/puzzles/{puzzle}` con `completada` o `sintonizada` pasa el puzzle a `resuelto` | `juego` o `app` | `{"puzzle": "photo", "estado": "resuelto", "datos": {"completada": true}}` |

Los tres eventos de puzzle guardan la misma forma, `{puzzle, estado, datos}`, que usará el mensaje `puzzle.actualizado` del Paso 4. Al resolver un puzzle se guardan en orden `puzzle.resuelto`, el `descubrimiento.registrado` del resultado y un `puzzle.habilitado` por cada puzzle que se habilita. Un aviso que no cambia el puzzle no guarda evento.

Cada evento también se publica dentro de la transacción como evento de aplicación de Spring (`ActividadService.SalaEvento`). `EmisorEventos` lo traduce a su mensaje de WebSocket y lo emite después del commit (`@TransactionalEventListener(phase = AFTER_COMMIT)`), así que una escritura que hace rollback no emite nada (`docs/tiempo-real.md`). `eventos` no guarda lecturas (`GET estado`).

`sala.expirada` es el único evento que no viene de una petición de un cliente. Lo guarda el job (Paso 4) para que el WebSocket avise `sala.cerrada` y para que las métricas distingan una sala expirada de una cerrada por el juego. No toca `ultima_actividad`: expirar no es actividad.

## Mapeo en Java

- Los valores cerrados (`estado`, `escena_actual`, `puzzle`, `origen`) son enums anidados en su entidad (`SalaModel.Estado`, `SalaModel.Escena`, `PuzzleEstadoModel.Puzzle`, …). Sus constantes se escriben en minúscula, igual que en la base y en el JSON, así que no hace falta ningún convertidor. La excepción es `eventos.tipo`, que lleva un punto (`sala.creada`) y usa `EventoModel.TipoConverter`.
- Las columnas `JSONB` se mapean como `Map<String, Object>` con `@JdbcTypeCode(SqlTypes.JSON)`. Hibernate 7.4 las serializa con Jackson 3. La prueba `ModeloIT` guarda y relee un JSON real.
- Las columnas `CHAR(64)` llevan `@JdbcTypeCode(SqlTypes.CHAR)`. Sin esa anotación, la validación del esquema falla porque Hibernate espera `VARCHAR`.
- Todas las fechas son `Instant` en UTC. El backend las toma de un `Clock` inyectado (bean en `TiempoConfig`), no de `now()` de Postgres.

## Ciclo de vida de una sala

```
            POST /salas               POST /salas/{codigo}/unirse
(no existe) ───────────▶ esperando_app ──────────────────────────▶ activa
                              │                                     │
                              │                                     │
                 job: expira_en < ahora                job: expira_en < ahora
                 DELETE /salas/{codigo}                DELETE /salas/{codigo}
                              ▼                                     ▼
                   expirada o finalizada                 expirada o finalizada
```

1. **Crear.** El juego reclama un código libre. La sala nace en `esperando_app` con sus cuatro puzzles bloqueados y `expira_en = ahora + 2 h`.
2. **Unirse.** La app se enlaza con un solo `UPDATE … WHERE codigo = ? AND estado = 'esperando_app' AND token_app_hash IS NULL`. Si dos apps llegan a la vez, Postgres bloquea la fila, la segunda vuelve a evaluar el `WHERE`, ya no lo cumple y recibe `409`. La sala pasa a `activa` y se renuevan `ultima_actividad` y `expira_en`.
3. **Expirar.** Un job programado (`SalaService.expirarVencidas`) corre cada `vestigia.salas.expiracion-revision` (por defecto 1 minuto). Bloquea con `SELECT … FOR UPDATE` toda sala viva con `expira_en < ahora`, la pasa a `expirada` y guarda su evento `sala.expirada`. Si una escritura tiene bloqueada una de esas salas, el job espera a que termine y Postgres vuelve a evaluar el filtro, así que una sala que esa escritura renovó o cerró no expira. El código queda libre y la fila se conserva.
4. **Finalizar.** `DELETE /salas/{codigo}`, solo con el token del juego, la pasa a `finalizada`. El código queda libre, la fila se conserva y los dos tokens dejan de servir porque solo autentican salas vivas.

Cada escritura exitosa renueva `ultima_actividad` y `expira_en` con la misma duración (`vestigia.salas.expiracion`) y guarda un evento. `ActividadService` concentra esas dos cosas. Una escritura repetida (un descubrimiento que ya existía, la escena en la que ya está la sala o un aviso que ya no mueve un puzzle) responde con éxito y renueva, pero no guarda otro evento. El cierre guarda su evento y actualiza `ultima_actividad`, pero no renueva `expira_en`: una sala finalizada ya no expira. Las lecturas (`GET estado`) no renuevan nada.

**Escrituras simultáneas.** Descubrimientos, puzzles, escena y cierre empiezan con `SalaRepository.bloquear`, un `SELECT … FOR UPDATE` sobre la fila de la sala. Las escrituras de una misma sala se ejecutan así una tras otra, y cada una ve lo que la anterior confirmó. Por eso dos peticiones con la misma clave dan un `201` y un `200` en lugar de chocar contra `UNIQUE (sala_id, clave)`, tres piezas que llegan a la vez habilitan `photo` y dos `completada` simultáneos resuelven el puzzle una sola vez, con un solo resultado. Si la sala dejó de estar viva entre la autenticación y el bloqueo (otra petición la cerró o el job la expiró), la escritura responde `401`. También responde `401` si su `expira_en` ya pasó aunque el job todavía no la haya marcado: una escritura no puede revivir una sala vencida. Salas distintas no se bloquean entre sí.

**Sala vencida.** Una sala está vencida si `expira_en < ahora`, el mismo corte que usa el job. Entre que `expira_en` pasa y el job corre puede haber hasta un minuto en el que la sala sigue en `esperando_app` o `activa`. En ese minuto ya cuenta como terminada: `autenticar` no acepta sus tokens (`401` en lecturas y escrituras), `unirApp` no la enlaza (`404`) y `bloquear` no la renueva. Lo único que sigue igual es que su código está ocupado (`GET /pueblos` lo marca ocupado y `POST /salas` da `409`) hasta que el job la marca `expirada`.

## Tokens

| Token | Lo recibe | Cuándo | Columna con su hash |
|---|---|---|---|
| Del juego | El juego | Respuesta de `POST /salas` | `token_juego_hash` |
| De la app | La app | Respuesta de `POST /salas/{codigo}/unirse` | `token_app_hash` |

- Cada token son 32 bytes de `SecureRandom` codificados en base64url sin relleno (43 caracteres).
- La base guarda **solo** el SHA-256 del token en hexadecimal minúscula (64 caracteres). El token en claro viaja una sola vez, en la respuesta que lo crea. Si el cliente lo pierde, no hay forma de recuperarlo.
- El token determina la sala y el rol: si su hash coincide con `token_juego_hash`, el rol es `juego`; si coincide con `token_app_hash`, es `app`.
- Un token deja de servir cuando su sala deja de estar viva o cuando su `expira_en` ya pasó, aunque el job todavía no la haya marcado.
- Ni el token ni el encabezado `Authorization` se escriben en los logs.
