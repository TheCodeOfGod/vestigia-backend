# Vestigia: plan del backend (documento vivo)

Última actualización: 6 de noviembre de 2026 (Paso 4: WebSocket).
Responsable: Raúl. Este archivo es el resumen a la mano; el detalle vive en `docs/` y en el contrato de la API. El plan de implementación del Paso 3 en adelante está en [`docs/plan-cambios-backend.md`](plan-cambios-backend.md).

**Estados:** ✅ `Acordado` = confirmado por Raúl · 🟡 `Propuesto` = pendiente de su visto bueno · 🧪 `Provisional` = valor de prototipo que puede cambiar sin migrar la base · ⏸️ `Diferido` = se decidió no hacerlo por ahora.

---

## 0. Dónde estamos

| Paso | Qué es | Estado |
|---|---|:-:|
| 1 | Base: Flyway, Dockerfile, configuración, CI, README | ✅ Hecho |
| 2 | Salas: catálogo de pueblos, crear, unirse, estado, expiración, token | ✅ Hecho |
| 3 | Puzzles y descubrimientos (REST) | ✅ Hecho (3.0, 3a y 3b) |
| 4 | WebSocket: autenticación, eventos y presencia | ✅ Hecho |
| 5 | Orientación: reenvío del giroscopio al juego | ⏸️ Diferido |
| 6 | Cierre: Swagger, guías, página de prueba | ⬜ |

---

## 1. Qué resuelve el backend

Enlaza el videojuego (Unity) con la app móvil (Flutter) para que lo que ocurre en un dispositivo se refleje en el otro. Los clientes **no se hablan entre sí**: todo pasa por el backend, que guarda el estado de cada **sala**.

```
Unity (PC) ──┐                       ┌── PostgreSQL
             ├── REST + WebSocket ───┤
App (móvil) ─┘     Spring Boot       └── (Railway)
```

La PWA solo consultaría el estado de una sala terminada (archivo de evidencias).

## 2. Decisiones

| Tema | Decisión | Estado |
|---|---|:-:|
| Plataforma de datos | Spring Boot + PostgreSQL. Firebase queda fuera | ✅ |
| Hosting | Railway (backend + Postgres), con Dockerfile | ✅ |
| Código de sala | Nombre de Pueblo Mágico de una palabra, sin acentos ni ñ. El jugador lo elige en el juego y lo escribe en la app | ✅ |
| Datos | Separados por sala | ✅ |
| Escritura y consulta | REST bajo `/api/v1` | ✅ |
| Tiempo real | WebSocket simple en `/ws`, mensajes JSON `type` + `payload` | ✅ |
| Migraciones | Flyway | ✅ |
| Orientación del teléfono | `azimut` y `altitud` en grados, no cuaternión. Solo aplica si se reactiva el reenvío (ver sección 14) | ⏸️ |
| Quién resuelve la radio | El **juego**. Avisa al backend cuando se completó | ✅ |
| Coordenadas del misterio | **Ascensión recta (AR) y declinación (DEC)**, como el canon narrativo y la app | ✅ |
| Mirador y observatorio | **La app indica cuándo se completó.** El servidor no mide ni valida números por ahora | ✅ |
| Observatorio | Se juega **por completo en el teléfono**. El juego solo se entera de que terminó, por la bandera | ✅ |
| Telescopio de la PC moviéndose en tiempo real con el teléfono | No se hace por ahora | ⏸️ |
| Valores del mirador y del observatorio | Viven en la app (ver sección 13) | ✅ |
| Ids de puzzle | En inglés, como en la app: `radio`, `photo`, `lookout`, `observatory` (solo estos cuatro) | ✅ |
| Códigos del misterio en la app | La app los muestra desde lo que entrega el servidor, no los lleva escritos | ✅ |
| Feedback de cercanía del observatorio (frío, tibio, caliente) | No se hace por ahora | ⏸️ |
| Habilitar un puzzle | El servidor lo hace por requisitos; el mirador y el observatorio además esperan una bandera del juego: `lookout_enabled` y `observatory_enabled` | ✅ (falta confirmarlo con Unity) |
| `en_curso` | La app avisa `{iniciado: true}` al abrir `photo`, `lookout` u `observatory` | ✅ |
| Código del error `puzzle_bloqueado` | Campo `codigo` en el cuerpo de error (nulo si no aplica) | ✅ |
| `codigo` en los errores que no lo definen | Siempre viaja como `"codigo": null`, no se omite, para que los clientes lean siempre la misma forma (entrega 3b) | 🟡 |
| Id de puzzle desconocido en la ruta | `400` con la lista de ids, igual que una escena desconocida. También para los ids viejos `fotografia`, `mirador` y `observatorio` (entrega 3b) | 🟡 |
| Versión de la API | Sigue en `/api/v1`. Estos cambios no crean una versión nueva | ✅ |
| Segunda conexión de WebSocket del mismo rol | Reemplaza a la anterior, que cierra con `4009`; el otro rol no recibe `desconectado` (Paso 4) | 🟡 |
| Errores y cierres del WebSocket | `error` con `{codigo, message}` ya autenticado; antes de `auth`, cierre con `4000`, `4001` o `4008`; `1000` tras `sala.cerrada` (Paso 4) | 🟡 |
| Expiración en la bitácora | El job guarda `sala.expirada` (origen `servidor`) y emite `sala.cerrada` después del commit (Paso 4) | 🟡 |
| CI/CD | GitHub Actions: pruebas, build de Docker, deploy a Railway | ✅ (definición propuesta) |

## 3. Salas

- Existe un **catálogo** fijo de nombres (`codigos_pueblo`). Las salas **no** se crean por adelantado: se crean cuando el jugador elige un pueblo en el juego.
- El juego pide los pueblos libres (`GET /pueblos?disponibles=true`), el jugador elige uno y el juego lo reclama (`POST /salas`). Si otro lo tomó antes, responde `409` y el juego refresca la lista.
- Una sala es **única mientras está viva**: un índice único parcial sobre `codigo` evita carreras. Las salas terminadas se conservan como historial y el nombre vuelve a quedar libre.
- Estados: `esperando_app` → `activa` → `finalizada` | `expirada`.
- Solo cabe **una app** por sala. Otro intento de unirse recibe `409`. Si no hay sala viva con ese código, recibe `404`.
- Expira tras unas 2 horas sin actividad.
- Al crear y al unirse se entrega un `token` por dispositivo.

## 4. Modelo de datos

| Tabla | Para qué | Campos clave |
|---|---|---|
| `codigos_pueblo` | Catálogo de nombres | `codigo` |
| `salas` | Instancia de una partida | `id`, `codigo`, `estado`, `escena_actual`, `ultima_actividad`, `expira_en` |
| `puzzles_estado` | Un registro por sala y puzzle | `sala_id`, `puzzle`, `estado`, `datos` JSONB |
| `descubrimientos` | Lo que muestra la libreta | `sala_id`, `clave`, `datos` JSONB, `origen` |
| `eventos` | Bitácora para métricas de pruebas | `sala_id`, `tipo`, `payload`, `origen`, `creado_en` |

Estados de puzzle: `bloqueado` → `disponible` → `en_curso` → `resuelto`. El servidor valida las transiciones y los requisitos.

La migración `V3` renombró los ids de puzzle a `photo`, `lookout` y `observatory` (entrega 3.0). No hay tablas ni columnas nuevas.

## 5. API REST (`/api/v1`)

| Ruta | Quién | Para qué | Paso |
|---|---|---|:-:|
| `GET /pueblos?disponibles=true` | Juego | Lista de pueblos libres | ✅ 2 |
| `POST /salas` | Juego | Reclama un pueblo; devuelve sala y token | ✅ 2 |
| `POST /salas/{codigo}/unirse` | App | Enlaza el teléfono | ✅ 2 |
| `GET /salas/{codigo}/estado` | Todos | Estado completo (sirve para reconectar) | ✅ 2 |
| `PUT /salas/{codigo}/puzzles/{puzzle}` | App o juego | Reporta que un puzzle empezó o se completó | ✅ 3b |
| `POST /salas/{codigo}/descubrimientos` | App o juego | Registra una pieza, una bandera, etc. | ✅ 3a |
| `PUT /salas/{codigo}/escena` | Juego | Cambia la escena actual | ✅ 3a |
| `DELETE /salas/{codigo}` | Juego | Cierra la sala | ✅ 3a |

## 6. Tiempo real (`/ws`)

Cada dispositivo se conecta con su rol (`juego` o `app`) y su token, que manda en el primer mensaje (`auth`), nunca en la URL. El contrato completo, con ejemplos, errores y cierres, está en [`docs/tiempo-real.md`](tiempo-real.md).

| Mensaje | Dirección | Notas |
|---|---|---|
| `puzzle.actualizado` | servidor → todos | Tras cada cambio de estado de un puzzle |
| `descubrimiento.nuevo` | servidor → todos | Idem |
| `escena.cambiada` | servidor → app | |
| `dispositivo.conectado` / `desconectado` | servidor → todos | Presencia |
| `sala.cerrada` | servidor → todos | |

Regla: si el WebSocket se cae, el cliente reconecta y pide `GET estado` para ponerse al día.

Los mensajes de las escrituras salen después del commit, así que un `GET estado` pedido al recibirlos ya ve el cambio. Las escrituras repetidas no emiten nada. Las conexiones viven en memoria: el backend corre en una sola instancia.

⏸️ Quedan fuera por ahora `orientacion` (reenvío del giroscopio al juego) y `observatorio.proximidad` (niveles de cercanía).

## 7. Puzzles

Reglas del diseño: cada puzzle se resuelve en menos de 2 minutos, sin penalizaciones, con retroalimentación inmediata. **Quien juega el puzzle dice cuándo se completó.** El servidor valida el orden (que el puzzle no esté bloqueado) y la forma del aviso, lo guarda y crea el resultado.

| # | Puzzle | Quién lo juega | Requiere | Aviso de que se completó | Resultado |
|---|---|---|---|---|---|
| 1 | `radio` | **El juego** | `pescador_dialogo_completo` (lo reporta el juego) | `{sintonizada: true}`, lo manda el juego | `coordenada_a` = AR `"137"` |
| 2 | `photo` | Piezas: el juego. Unir las imágenes: la app | `pieza_1`, `pieza_2` y `pieza_3` (las reporta el juego al recogerlas) | `{completada: true}`, lo manda la app | `coordenada_b` = DEC `"+042"` |
| 3 | `lookout` | La app | `photo` resuelto y la bandera `lookout_enabled` del juego | `{completada: true}`, lo manda la app | `observatorio_visible` |
| 4 | `observatory` | **La app** (el juego solo se entera) | `coordenada_a`, `coordenada_b`, `lookout` resuelto y la bandera `observatory_enabled` del juego | `{completada: true}`, lo manda la app | `magnetar_localizado` |

**Reglas comunes:** solo `true` es válido en `sintonizada`, `completada` e `iniciado` (otro valor da `400`). Las escrituras son idempotentes. Un puzzle `bloqueado` responde `409` con `codigo: "puzzle_bloqueado"`. Al abrir `photo`, `lookout` u `observatory` la app avisa `{iniciado: true}` y el puzzle pasa a `en_curso`. Los clientes no pueden enviar las claves que genera el servidor: `coordenada_a`, `coordenada_b`, `observatorio_visible` y `magnetar_localizado` (`403`).

**Coordenadas:** se entregan como descubrimientos con `valor` en **texto**, para conservar el signo y los ceros. Son los únicos valores del misterio que viven en el servidor (configurables).

| Clave | `datos` | Valor del prototipo |
|---|---|---|
| `coordenada_a` | `{tipo: "ascension_recta", valor: "137"}` | 🧪 Definitivo pendiente |
| `coordenada_b` | `{tipo: "declinacion", valor: "+042"}` | 🧪 Definitivo pendiente |

**Observatorio:** se juega entero en el teléfono, con su giroscopio. Cuando el jugador apunta bien, la app avisa `{completada: true}`. El juego se entera por el cambio de estado (`puzzle.actualizado`) y por el descubrimiento `magnetar_localizado`, que es la bandera que usa. La orientación no pasa por el servidor.

### Cómo se habilita un puzzle

El servidor recalcula cada vez que llega un descubrimiento o se resuelve un puzzle. Una bandera del juego puede llegar antes o después de los requisitos; el puzzle se habilita cuando están todos.

| Puzzle | Pasa a `disponible` cuando |
|---|---|
| `radio` | Existe `pescador_dialogo_completo` |
| `photo` | Existen `pieza_1`, `pieza_2` y `pieza_3` |
| `lookout` | `photo` está `resuelto` **y** existe `lookout_enabled` |
| `observatory` | Existen `coordenada_a` y `coordenada_b`, `lookout` está `resuelto` **y** existe `observatory_enabled` |

`lookout_enabled` y `observatory_enabled` son banderas que el juego registra con `POST /descubrimientos` cuando el jugador llega al lugar (la cima del faro, el observatorio). Evitan que la burbuja de la app pulse antes de tiempo. Los nombres los aprobó Raúl; falta confirmarlos con Unity.

## 8. App móvil: flujo y libreta

### Flujo acordado

```
Inicio ─▶ [Unirme a una sala] ─▶ Escribir código de sala ─▶ LIBRETA ─▶ burbuja que pulsa ─▶ Puzzle
                                  (el jugador lo ve y lo elige en Unity)
```

| Pantalla | Qué hace | Servicio del backend |
|---|---|---|
| Inicio | Un botón que lleva a escribir el código | Ninguno |
| Código de sala | El jugador escribe el pueblo que eligió en Unity | `POST /salas/{codigo}/unirse` (`404` si no existe, `409` si ya hay un teléfono) |
| Libreta | Primera pantalla tras unirse. Muestra el progreso del juego | `GET /salas/{codigo}/estado` y, en el Paso 4, `puzzle.actualizado` y `descubrimiento.nuevo` |
| Burbuja flotante | Aparece y **pulsa** cuando un puzzle del teléfono está `disponible`. Al tocarla se abre el puzzle | `puzzle.actualizado` con estado `disponible` |
| Puzzle | Se juega y avisa cuando se completó | `PUT /salas/{codigo}/puzzles/{puzzle}` |

La libreta marca el progreso del juego. Los puzzles ya no están todos abiertos en un menú. La burbuja aplica a `photo`, `lookout` y `observatory`; la radio la juega el juego, así que en la libreta solo se ve su estado.

### Qué progreso puede mostrar la libreta

| Qué se muestra | De dónde sale | Ejemplo de texto | Paso |
|---|---|---|:-:|
| Escena actual | `escena_actual` | "Estás en el Faro" | 3 |
| Pescador | Descubrimiento `pescador_dialogo_completo` | "Hablaste con el pescador" | 3 |
| Radio | Estado de `radio` y `coordenada_a` | "Radio sintonizada. AR 137°" | 3 |
| Piezas de la foto | Cuántos descubrimientos `pieza_1..3` existen | "Piezas de foto encontradas 1/3" | 3 |
| Fotografía | Estado de `photo` y `coordenada_b` | "Foto reconstruida. DEC +042°" | 3 |
| Coordenadas | `coordenada_a` y `coordenada_b` | "Coordenadas obtenidas 2/2" | 3 |
| Mirador | Estado de `lookout` y `observatorio_visible` | "Mirador resuelto" | 3 |
| Observatorio | Estado de `observatory` y `magnetar_localizado` | "Magnetar localizado" | 3 |
| Dispositivos conectados | `dispositivos` de `GET estado` y los mensajes `dispositivo.conectado` y `dispositivo.desconectado` | "PC conectada" | ✅ 4 |

**Lo que no hay hoy:** progreso *dentro* de un puzzle (por ejemplo "señal 2 de 3" en la radio o "lente limpio" en el mirador), el nivel del faro por separado (la cuenta de piezas lo aproxima), pistas o tiempos. Se puede agregar cualquier elemento nuevo: basta que alguien registre un descubrimiento con otra clave y la libreta lo lea.

## 9. CI/CD (GitHub Actions)

- **PR a `develop` o `main`:** `./mvnw verify` con Java 21; pruebas de integración contra un Postgres real (Testcontainers) que además valida las migraciones de Flyway; build de la imagen Docker.
- **Push a `main`:** Railway despliega; un job espera el despliegue y verifica `/actuator/health`.
- **Flujo de ramas:** `feature/*` → PR a `develop` → PR a `main`. Commits con prefijo `New:`, `Refactor:`, `Fix:` o `Merge:`.
- Empezar con un solo entorno desplegado (producción) y agregar `staging` cuando el juego y la app ya integren.

## 10. Plan por pasos

- [ ] **Paso 0:** decidir repo separado, copiar el backend (con historial), configurar acceso y secretos.
- [x] **Paso 1: Base.** Limpiar el código del laboratorio, Flyway con `V1__esquema.sql`, Dockerfile, configuración por entorno, CI, README y `docs/PLAN.md`.
  - Pendiente de Raúl: el primer despliegue en Railway y el job de CI que revisa `/actuator/health` después del despliegue (ver `docs/despliegue.md`).
  - El workflow `ci.yml` se validó con actionlint, pero todavía no ha corrido en GitHub.
- [x] **Paso 2: Salas.** Catálogo de pueblos (seed aprobado), crear, unirse, estado, expiración, autenticación por token.
  - `V2__seed_pueblos.sql` carga los 101 códigos aprobados por Raúl.
  - Endpoints `GET /pueblos`, `POST /salas`, `POST /salas/{codigo}/unirse` y `GET /salas/{codigo}/estado`. Contrato con ejemplos en `docs/api.md`.
  - Tokens de 32 bytes en base64url. La base guarda solo su SHA-256. Un `HandlerInterceptor` protege `/api/v1/salas/**` salvo crear y unirse.
  - Job de expiración cada minuto (`vestigia.salas.expiracion=PT2H`, `vestigia.salas.expiracion-revision=PT1M`).
  - `dispositivos` en el estado devuelve `{"juego": false, "app": false}` hasta el Paso 4.
- [x] **Paso 3: Puzzles y descubrimientos.** Se parte en entregas pequeñas, cada una con su rama, PR y pruebas:
  - [x] 3.0. Migración `V3`: ids de puzzle `photo`, `lookout` y `observatory`. Evidencia en `docs/pruebas/paso-3.0-ids.md`.
  - [x] 3a. `POST /descubrimientos` (idempotente, también bajo concurrencia), `PUT /escena` (solo juego) y `DELETE /salas/{codigo}` (solo juego). Cada escritura renueva `ultima_actividad` y `expira_en` y guarda un evento; crear y unirse también. `HabilitacionPuzzlesService` recalcula `radio` y `photo` tras cada descubrimiento y ya tiene las reglas de `lookout` y `observatory` para el 3b. Evidencia en `docs/pruebas/paso-3a-descubrimientos.md`.
  - [x] 3b. `PUT /salas/{codigo}/puzzles/{puzzle}` para los cuatro puzzles (`{sintonizada: true}` en `radio`; `{iniciado: true}` o `{completada: true}` en `photo`, `lookout` y `observatory`; solo el booleano `true`). `PuzzleService` valida la forma, responde `409` con `codigo: "puzzle_bloqueado"` y mueve el puzzle solo hacia adelante; un aviso que ya no lo mueve responde `200` sin cambios. Al resolver crea el resultado con origen `servidor` (las coordenadas salen de `vestigia.puzzles.coordenadas.ar` y `.dec` como texto), recalcula los dependientes y guarda `puzzle.resuelto`, todo en una transacción. El cuerpo de error gana `codigo`. Evidencia en `docs/pruebas/paso-3b-puzzles.md`.
- [x] **Paso 4: WebSocket.** Autenticación, eventos y presencia.
  - `/ws` reemplaza a `PendienteWebSocketHandler`. El primer mensaje es `auth` con el token, dentro de 5 s (`vestigia.ws.auth-plazo`); si no, cierra con `4000`, `4001` o `4008`. `auth.ok` trae `{rol, sala}` y el otro rol recibe `dispositivo.conectado`; al cerrarse, `dispositivo.desconectado`. `GET estado` reporta los `dispositivos` reales.
  - `EmisorEventos` emite después del commit `puzzle.actualizado` en cada cambio de estado, `descubrimiento.nuevo`, `escena.cambiada` (solo a la app) y `sala.cerrada` (al cerrar o expirar, y luego cierra las conexiones). El job de expiración guarda y publica `sala.expirada`.
  - Ya autenticado, JSON inválido, tipo desconocido u `orientacion` responden `error` y la conexión sigue. Más de 4096 caracteres cierra con `1009`. Ping cada 25 s.
  - Contrato en `docs/tiempo-real.md`, con las decisiones que tomaron los agentes. Evidencia en `docs/pruebas/paso-4-websocket.md`.
- [ ] **Paso 5: Orientación.** ⏸️ Diferido. El observatorio se juega en el teléfono, así que no hace falta reenviar la orientación al juego.
- [ ] **Paso 6: Cierre.** Swagger, una guía de integración por cliente (`docs/integracion-unity.md`, `docs/integracion-flutter.md` y `docs/integracion-pwa.md`) y página HTML de prueba.

## 11. Pendientes

### Por decidir

| # | Pendiente | Quién decide | Bloquea | Mi recomendación |
|---|---|---|---|---|
| P7 | El archivo de salas terminadas para la PWA: qué muestra, cómo se identifica una sala (el código se reutiliza) y quién puede verla | Raúl y PWA | Después del Paso 6 | |
| P8 | Qué rol puede mandar cada clave de descubrimiento. Hoy la app también puede registrar `pieza_*`, `pescador_dialogo_completo`, `lookout_enabled` y `observatory_enabled`, que en el diseño manda el juego, y así habilitar puzzles sin pasar por él. El contrato vigente dice "juego o app" (hallazgo B3 de la revisión del 3a). Lo mismo pasa con `PUT puzzles`: cualquier rol puede reportar cualquier puzzle, aunque en el diseño la radio la manda el juego y los demás la app | Raúl | No bloquea. Sigue abierto tras el 3b | Restringir esas claves al rol `juego` (`403` para la app) si Unity confirma que las manda todas (C2) |

### Por confirmar con otros equipos

| # | Con quién | Qué |
|---|---|---|
| C1 | Unity | Que las pistas del juego apunten a `137` y `+042` |
| C2 | Unity | Que registre `lookout_enabled` y `observatory_enabled` al llegar al lugar (nombres aprobados por Raúl), y la bandera del pescador. Si necesita reaccionar a `lente_limpio` u `obs_encontrado` dentro del mirador, la app puede registrarlas con `POST /descubrimientos` |
| C3 | App | Que implemente el flujo de la sección 8 y los avisos de completado |
| C4 | App | Dónde guardar el token para reconectar sin volver a escribir el código |
| C5 | Aldo | Los valores adaptados de las secciones 7 y 13 |

### Operativos y de documentación

| # | Pendiente |
|---|---|
| O1 | Primer despliegue en Railway y URL de producción |
| O2 | Job de CI posterior al despliegue en `main` (necesita la URL de Railway) |
| O3 | Confirmar que la PWA ya no consume `/api/salas`, que el Paso 1 retiró |
| O4 | ✅ Resuelto en el Paso 4: las escrituras emiten sus mensajes por WebSocket después del commit. Crear y unirse no emiten mensaje (nadie está conectado antes; el juego se entera de la app por `dispositivo.conectado`) |
| O5 | Entre que `expira_en` pasa y el job corre (hasta un minuto), el código de la sala sigue ocupado (`POST /salas` da `409`). Sus tokens (`401`) y `unirse` (`404`) ya la tratan como terminada desde la entrega 3a |
| O6 | Escribir una guía por cliente (Unity, Flutter y PWA), en español sencillo y con cada paso explicado. Entra con el Paso 6 |
| O7 | Convención de nombres de ramas (el equipo no la ha decidido) |
| O8 | Diseño, no backend: objetivo visual del mirador y condición de apertura de la compuerta del observatorio |
| O9 | Valores definitivos del canon (AR, DEC, umbrales y rangos del mirador), que dependen de pruebas |

## 12. Descartado

Lo que se decidió **no** usar. Se deja aquí para no volver a proponerlo.

| Descartado | Por qué |
|---|---|
| Radio con dial 0 a 20, posición, intentos y rango 12 a 14 | La radio la resuelve el juego con una bandera |
| Minijuego de radio dentro de la app | Idem |
| `fragmento_1..3` | Pasan a `pieza_1..3` |
| Fotografía con `slot`, `rot` y `colocacion` | Basta con que la app avise que se completó |
| Coordenadas de azimut 60° y altitud 35° | El canon usa AR y DEC |
| Claves de catalogación `PUE-060` y `FAR-035` | Dependían de las coordenadas anteriores |
| Fórmula de error `max(\|Δaz\|/10, \|Δalt\|/8)` y 1.5 s | El observatorio lo decide la app |
| Reenvío de `orientacion` al juego | El observatorio se juega solo en el teléfono (diferido, ver sección 14) |
| `sostenido_ms` y valores `X=7`, `Y=14` del mirador | Los valores del mirador son los de la app |
| Niveles frío, tibio, caliente y `observatorio.proximidad` | Diferido |
| Menú con los cuatro puzzles siempre abiertos | Lo reemplaza la libreta con la burbuja |
| Firebase | Se mantiene PostgreSQL |

## 13. Valores de la app (referencia)

Los usa la app para decidir que un puzzle se completó. Son del prototipo, así que pueden cambiar con las pruebas y el arte final sin tocar el backend.

| Puzzle | Valor | |
|---|---|---|
| Mirador, fase 1 | Lente limpio al 85 % | 🧪 |
| Mirador, fase 2 | Ejes de −100 a 100, tolerancia ±14, objetivo `(0.82, 0.40)` sobre la imagen (equivale a X≈88.89, Y=37.5) | 🧪 imagen provisional |
| Mirador, final | Animación de 4 s tras alinear | 🧪 |
| Observatorio | Objetivo azimut 0°, altitud 68° | 🧪 |
| Observatorio | Distancia angular de 6° o menos durante 2 s, con lecturas de no más de 500 ms de antigüedad | 🧪 |

## 14. Mejoras futuras (si hay tiempo)

Ideas diferidas. Los valores de la sección 13 ya sirven como base para cada una.

| Mejora | Qué cambiaría |
|---|---|
| El servidor valida el mirador | La app avisa tres momentos (lente limpio, vista alineada, animación terminada) y el servidor comprueba los números. Permite que el juego reaccione a cada paso |
| El servidor decide el observatorio | El servidor calcula la distancia angular con cada `orientacion` y resuelve a los 2 s. La app deja de llevar el objetivo |
| Niveles de cercanía | El servidor emite `observatorio.proximidad` (frío, tibio, caliente, enfocado) |
| Telescopio de la PC en tiempo real | La app manda `orientacion` por WebSocket (~20 por segundo, solo si cambió más de 0.5°) y el servidor la reenvía al juego, con un límite de ~40 por segundo y sin guardarla. Es el Paso 5 original |
