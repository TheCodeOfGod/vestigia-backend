# Vestigia: plan del backend (documento vivo)

Última actualización: 8 de noviembre de 2026 (backend desplegado en Railway, CI de GitHub funcionando).
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
| 6 | Cierre: Swagger, guías, página de prueba | ✅ Hecho |
| 7 | Listo para Railway: imagen, prueba de humo, configuración y pasos del primer despliegue | ✅ Hecho y desplegado en Railway (7 de noviembre) |

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
| Quién resuelve la radio | La **app**, como los otros tres puzzles. La radio aparece cuando el juego registra `pescador_dialogo_completo` y la app avisa `{iniciado: true}` y `{sintonizada: true}`. Cambio del 8 de noviembre: antes la resolvía el juego | ✅ |
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
| Segunda conexión de WebSocket del mismo rol | Reemplaza a la anterior, que cierra con `4009`; el otro rol no recibe `desconectado` (Paso 4). Aprobado por Raúl el 2026-11-06 | ✅ |
| Errores y cierres del WebSocket | `error` con `{codigo, message}` ya autenticado; antes de `auth`, cierre con `4000`, `4001` o `4008`; `1000` tras `sala.cerrada`; `4500` a un cliente que no lee (Paso 4). Aprobado por Raúl el 2026-11-06 | ✅ |
| Cliente de WebSocket que no lee | Su envío se corta a los 5 s o al juntar 64 KB sin enviar y se cierra solo su conexión (`4500`); no frena a nadie más (Paso 4, ronda 1). Aprobado por Raúl el 2026-11-06 | ✅ |
| Expiración en la bitácora | El job guarda `sala.expirada` (origen `servidor`) y emite `sala.cerrada` después del commit (Paso 4). Aprobado por Raúl el 2026-11-06 | ✅ |
| Swagger UI y `/v3/api-docs` en producción | Raúl decidió el 2026-11-06 dejarlos públicos en producción por ahora. Se publican en todos los entornos, sin token. Solo describen la API; no exponen datos. El Paso 7 comprobó en la imagen que se apagan sin cambiar código, con `SPRINGDOC_API_DOCS_ENABLED=false` y `SPRINGDOC_SWAGGER_UI_ENABLED=false` (D-Swagger en `docs/despliegue.md`) | ✅ |
| Página de prueba `/prueba-ws.html` en producción | Raúl decidió el 2026-11-06 dejarla pública en producción por ahora. Se sirve en todos los entornos. No guarda ni muestra datos por sí sola: necesita un token. El Paso 7 comprobó que se quita sin cambiar código con `SPRING_WEB_RESOURCES_ADD_MAPPINGS=false`, que no afecta a Swagger UI (D-Prueba) | ✅ |
| Usuarios de administración de la PWA | Cuentas con correo y contraseña (BCrypt, dependencia `spring-security-crypto` aprobada por Raúl el 2026-11-08) que inician sesión en `POST /auth/login`. Token opaco de sesión de 8 horas, sin renovación, en `Authorization: Bearer`; solo se guarda su hash. Rutas nuevas bajo `/auth` y `/admin`; las de salas no cambian y los dos tipos de token no se mezclan. Límite de 5 intentos fallidos por correo en 15 minutos (429). El primer admin sale de `ADMIN_BOOTSTRAP_EMAIL` y `ADMIN_BOOTSTRAP_PASSWORD` | ✅ (aprobado por Raúl; la dependencia queda anotada para el equipo) |
| Quién manda cada clave de descubrimiento (P8) | `pescador_dialogo_completo`, `pieza_1`, `pieza_2`, `pieza_3`, `lookout_enabled` y `observatory_enabled` las manda solo el juego. Con el token de la app, `POST /descubrimientos` responde `403` con `codigo: "clave_solo_juego"`, aunque la clave ya exista, sin tocar la sala. Decidido por Raúl e implementado el 2026-11-07 (`DescubrimientoService.CLAVES_DEL_JUEGO`). No cubre `PUT puzzles` (ver P8b) | ✅ |
| `POST /salas` y `unirse` sin token (P9) | Por diseño: así obtienen su token el juego y la app. Aceptado por Raúl | ✅ |
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
| `POST /salas/{codigo}/descubrimientos` | App o juego | Registra una pieza, una bandera, etc. Las claves del juego solo con el token del juego (P8) | ✅ 3a |
| `PUT /salas/{codigo}/escena` | Juego | Cambia la escena actual | ✅ 3a |
| `DELETE /salas/{codigo}` | Juego | Cierra la sala | ✅ 3a |
| `POST /auth/login`, `POST /auth/logout`, `GET /auth/yo` | PWA (admin) | Inicia, cierra y consulta la sesión de administración | ✅ 9a |
| `GET/POST /admin/usuarios`, `PUT/DELETE /admin/usuarios/{id}` | PWA (admin) | Administra los usuarios | ✅ 9a |
| `GET /admin/salas`, `GET /admin/salas/{id}`, `DELETE /admin/salas/{id}` | PWA (admin) | Ver todas las salas y su detalle, y forzar el cierre de una. Van por `id` porque un código se reutiliza | ✅ 9b |

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

La página `/prueba-ws.html` muestra los mensajes de `/ws` sin Unity ni Flutter (Paso 6).

Los mensajes de las escrituras salen después del commit, así que un `GET estado` pedido al recibirlos ya ve el cambio. Las escrituras repetidas no emiten nada. Las conexiones viven en memoria: el backend corre en una sola instancia.

⏸️ Quedan fuera por ahora `orientacion` (reenvío del giroscopio al juego) y `observatorio.proximidad` (niveles de cercanía).

## 7. Puzzles

Reglas del diseño: cada puzzle se resuelve en menos de 2 minutos, sin penalizaciones, con retroalimentación inmediata. **Quien juega el puzzle dice cuándo se completó.** El servidor valida el orden (que el puzzle no esté bloqueado) y la forma del aviso, lo guarda y crea el resultado.

| # | Puzzle | Quién lo juega | Requiere | Aviso de que se completó | Resultado |
|---|---|---|---|---|---|
| 1 | `radio` | **La app** | `pescador_dialogo_completo` (lo reporta el juego) | `{iniciado: true}` al abrirla y `{sintonizada: true}` al sintonizarla, los manda la app | `coordenada_a` = AR `"137"` |
| 2 | `photo` | Piezas: el juego. Unir las imágenes: la app | `pieza_1`, `pieza_2` y `pieza_3` (las reporta el juego al recogerlas) | `{completada: true}`, lo manda la app | `coordenada_b` = DEC `"+042"` |
| 3 | `lookout` | La app | `photo` resuelto y la bandera `lookout_enabled` del juego | `{completada: true}`, lo manda la app | `observatorio_visible` |
| 4 | `observatory` | **La app** (el juego solo se entera) | `coordenada_a`, `coordenada_b`, `lookout` resuelto y la bandera `observatory_enabled` del juego | `{completada: true}`, lo manda la app | `magnetar_localizado` |

**Reglas comunes:** solo `true` es válido en `sintonizada`, `completada` e `iniciado` (otro valor da `400`). Las escrituras son idempotentes. Un puzzle `bloqueado` responde `409` con `codigo: "puzzle_bloqueado"`. Al abrir `radio`, `photo`, `lookout` u `observatory` la app avisa `{iniciado: true}` y el puzzle pasa a `en_curso`. **Solo la app avisa puzzles**: el token del juego recibe `403` con `codigo: "puzzle_solo_app"` (P8b, decidido el 8 de noviembre). Los clientes no pueden enviar las claves que genera el servidor: `coordenada_a`, `coordenada_b`, `observatorio_visible` y `magnetar_localizado` (`403`). La app no puede enviar las del juego: `pescador_dialogo_completo`, `pieza_1..3`, `lookout_enabled` y `observatory_enabled` (`403` con `codigo: "clave_solo_juego"`, P8).

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

La libreta marca el progreso del juego. Los puzzles ya no están todos abiertos en un menú. La burbuja aplica a los cuatro puzzles, incluida la radio, que aparece cuando el juego registra al pescador.

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
  - El primer despliegue en Railway se hizo el 7 de noviembre y la variable `RAILWAY_PUBLIC_URL` ya existe (ver `docs/despliegue.md`).
  - El workflow `ci.yml` se validó con actionlint. Corrió bien en GitHub el 26 de octubre; del 5 al 8 de noviembre no arrancó (O11, resuelto).
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
  - [x] Cambio del 8 de noviembre: la radio la juega la app. `radio` acepta `{iniciado: true}` y `{sintonizada: true}`, igual que los otros tres se inician y se completan; `PuzzleService` rechaza al juego con `403` y `codigo: "puzzle_solo_app"` después de validar el id y la forma del aviso (P8b). Evidencia en `docs/pruebas/radio-en-la-app.md`.
- [x] **Paso 4: WebSocket.** Autenticación, eventos y presencia.
  - `/ws` reemplaza a `PendienteWebSocketHandler`. El primer mensaje es `auth` con el token, dentro de 5 s (`vestigia.ws.auth-plazo`); si no, cierra con `4000`, `4001` o `4008`. `auth.ok` trae `{rol, sala}` y el otro rol recibe `dispositivo.conectado`; al cerrarse, `dispositivo.desconectado`. `GET estado` reporta los `dispositivos` reales.
  - `EmisorEventos` emite después del commit `puzzle.actualizado` en cada cambio de estado, `descubrimiento.nuevo`, `escena.cambiada` (solo a la app) y `sala.cerrada` (al cerrar o expirar, y luego cierra las conexiones). El job de expiración guarda y publica `sala.expirada`.
  - Ya autenticado, JSON inválido, tipo desconocido u `orientacion` responden `error` y la conexión sigue. Más de 4096 caracteres cierra con `1009`. Ping cada 25 s.
  - Contrato en `docs/tiempo-real.md`, con las decisiones que tomaron los agentes. Evidencia en `docs/pruebas/paso-4-websocket.md`.
- [x] **P8: claves del juego.** `DescubrimientoService.CLAVES_DEL_JUEGO` con las seis claves; la app recibe `403` con `codigo: "clave_solo_juego"` antes de bloquear la sala, así que el `403` no guarda nada, no emite ni renueva la expiración. Una prueba fija que cada requisito de `HabilitacionPuzzlesService` es una clave del juego o del servidor. Evidencia en `docs/pruebas/paso-p8-claves.md`.
- [ ] **Paso 5: Orientación.** ⏸️ Diferido. El observatorio se juega en el teléfono, así que no hace falta reenviar la orientación al juego.
- [x] **Paso 6: Cierre.** Swagger, una guía de integración por cliente y página HTML de prueba.
  - Swagger: cada endpoint con descripción en español, esquemas, ejemplos reales de petición y respuesta, errores con `ErrorBody` (incluido `codigo`) y el esquema `Bearer`. `DocumentacionIT` compara `/v3/api-docs` con las rutas reales.
  - Guías independientes para cada cliente: `docs/integracion-unity.md`, `docs/integracion-flutter.md` y `docs/integracion-pwa.md`, con la misma estructura de 12 secciones. Los fragmentos de C# y Dart no se compilaron; los de TypeScript se comprobaron con `tsc`.
  - `docs/tiempo-real.md` gana la tabla de quién envía cada mensaje y los ejemplos que faltaban.
  - Página de prueba `/prueba-ws.html`: un panel para el juego y otro para la app; el token viaja solo en `auth`.
  - `JsonDocumentadoIT` compara cada bloque JSON de respuesta de `docs/api.md`, `docs/tiempo-real.md` y las guías con una respuesta real. Evidencia en `docs/pruebas/paso-6-cierre.md`.
- [x] **Paso 7: Listo para Railway.** Revisar y corregir lo que haría fallar o exponer el primer despliegue, sin desplegar.
  - La imagen del `Dockerfile` se construyó y corrió contra un Postgres vacío solo con `DB_URL`, `DB_USER`, `DB_PASSWORD`, `PORT` y `CORS_ALLOWED_ORIGINS`: Flyway aplicó `V1` a `V3`, `/actuator/health` respondió `UP` y el proceso corre como `vestigia` (uid 999). No hizo falta cambiar el `Dockerfile` ni `application.properties`.
  - `scripts/smoke-test.sh <url> [pueblo]` juega la partida completa con `curl` y `jq`, sale con código distinto de 0 si algo falla y cierra la sala al salir. `SmokeTestIT` y `SmokeTestFalloIT` lo corren en `./mvnw verify`.
  - Revisado contra la imagen: logs sin tokens ni `Authorization`, actuator solo con `health`, errores sin stacktrace, `ddl-auto=validate`, sin secretos en el repo. `ConfiguracionDespliegueIT` fija lo que se puede probar en Maven.
  - `docs/despliegue.md` gana el mapeo de variables desde el Postgres de Railway, las decisiones pendientes (Swagger, página de prueba, O10, P9) y la lista "Primer despliegue: pasos para Raúl". Los nombres de Railway quedan marcados por verificar.
  - `ci.yml` corre también en `push` a `main`, que es lo que espera Railway con "Wait for CI". El workflow nuevo `salud-produccion.yml` revisa `/actuator/health` de la URL real tras cada despliegue (O2), con `scripts/esperar-health.sh`.
  - Evidencia en `docs/pruebas/paso-7-railway.md`.
- [x] **Paso 9: Administración de la PWA.** Se parte en dos entregas, cada una con su rama, PR y pruebas:
  - [x] 9a. `V4__usuarios_y_sesiones_admin.sql`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/yo` y `GET/POST/PUT/DELETE /admin/usuarios`. `AdminInterceptor` aparte del de salas, BCrypt, sesión de 8 horas, límite de intentos por correo en memoria, no se puede quitar al último admin activo y administrador inicial por variables de entorno.
  - [x] 9b. `GET /admin/salas` (filtro `estado`, paginado con `pagina` y `tamano`), `GET /admin/salas/{id}` (con bitácora) y `DELETE /admin/salas/{id}` (cierre forzado idempotente, emite `sala.cerrada`). Por `id` y no por código, porque el código de una sala terminada se reutiliza.

## 11. Pendientes

### Por decidir

| # | Pendiente | Quién decide | Bloquea | Mi recomendación |
|---|---|---|---|---|
| P7 | El archivo de salas terminadas para la PWA: qué muestra, cómo se identifica una sala (el código se reutiliza) y quién puede verla | Raúl y PWA | Después del Paso 6 | |
| P8b | ✅ Decidido el 8 de noviembre: `PUT puzzles` es solo de la app. El token del juego recibe `403` con `codigo: "puzzle_solo_app"` en los cuatro puzzles, aunque el aviso sea válido. Implementado junto con el cambio de la radio | Raúl | Cerrado | |

### Por confirmar con otros equipos

| # | Con quién | Qué |
|---|---|---|
| C1 | Unity | Que las pistas del juego apunten a `137` y `+042` |
| C2 | Unity | Que registre `lookout_enabled` y `observatory_enabled` al llegar al lugar (nombres aprobados por Raúl), y la bandera del pescador. Qué hacer con `lente_limpio` y `obs_encontrado` de `GameProgress.cs`: `docs/integracion-unity.md` (sección 7.4) deja tres opciones sin decidir |
| C6 | PWA | El origen de producción de la PWA, para agregarlo a `CORS_ALLOWED_ORIGINS`, y si necesita leer `/actuator/health` desde el navegador (hoy no tiene CORS) |
| C3 | App | Que implemente el flujo de la sección 8 y los avisos de completado |
| C4 | App | Dónde guardar el token para reconectar sin volver a escribir el código |
| C5 | Aldo | Los valores adaptados de las secciones 7 y 13 |

### Operativos y de documentación

| # | Pendiente |
|---|---|
| O1 | ✅ Primer despliegue en Railway hecho (7 de noviembre). URL de producción: `https://vestigia-backend-production-745d.up.railway.app`, ya en las guías de integración |
| O2 | ✅ El workflow "Health de producción" (`salud-produccion.yml`) corre tras cada despliegue exitoso o a mano, y la variable `RAILWAY_PUBLIC_URL` ya existe. Comprobado el 8 de noviembre: revisó `/actuator/health` de producción en `UP` |
| O3 | La PWA todavía llama a `/api/salas` (CRUD del laboratorio que el Paso 1 retiró; responde `404`), según su código del 6 de noviembre. `docs/integracion-pwa.md` le indica cambiar a `GET /api/v1/pueblos`. Falta que el equipo de la PWA lo cambie |
| O5 | Entre que `expira_en` pasa y el job corre (hasta un minuto), el código de la sala sigue ocupado (`POST /salas` da `409`). Sus tokens (`401`) y `unirse` (`404`) ya la tratan como terminada desde la entrega 3a |
| O7 | Convención de nombres de ramas (el equipo no la ha decidido) |
| O8 | Diseño, no backend: objetivo visual del mirador y condición de apertura de la compuerta del observatorio |
| O9 | Valores definitivos del canon (AR, DEC, umbrales y rangos del mirador), que dependen de pruebas |
| O10 | Verificar al desplegar en Railway (revisión del Paso 6, B6; lo retoma el Paso 7). Detrás del proxy TLS de Railway, Tomcat ve la petición como `http` aunque el navegador use `https`. Una página servida por el backend (`/prueba-ws.html`, Swagger UI) manda `Origin: https://…`, que no coincide con el origen `http://…` que ve el servidor: el handshake de `/ws` y `POST /salas` responden `403`, y Swagger anuncia `servers` con `http://`. El Paso 7 lo reprodujo con cabeceras simuladas y comparó tres salidas, ninguna activada y ninguna cambia el comportamiento local: la URL pública en `CORS_ALLOWED_ORIGINS` (arregla la página de prueba), `SERVER_FORWARD_HEADERS_STRATEGY=native` (arregla todo si el proxy llega desde una IP interna, por verificar) o `framework` (arregla todo y acepta cabeceras de cualquier cliente). Decide Raúl; detalle en `docs/despliegue.md`. Unity y Flutter nativos no mandan `Origin` y no se ven afectados |
| O11 | ✅ Resuelto el 8 de noviembre. Todas las ejecuciones del CI terminaban en `startup_failure` sin jobs desde el 5 de noviembre, con el mensaje "recent account payments have failed or your spending limit needs to be increased". La causa era el presupuesto de **Actions en $0 con "Stop usage"** de la cuenta de GitHub (Settings, Billing, Budgets and alerts). Raúl lo subió y el CI volvió a arrancar. Al correr completo por primera vez, una prueba de concurrencia (`ConcurrenciaWebSocketIT.jobContraEscrituraQueRenueva`) fallaba siempre en GitHub por un defecto de la propia prueba (la foto de `pg_stat_activity` que Postgres reutiliza dentro de una transacción); se corrigió en el PR #16 |
| O12 | Activar "Wait for CI" en Railway ahora que el CI pasa. Mientras esté apagado, cada merge a `main` despliega sin esperar los checks. Paso manual de Raúl |

## 12. Descartado

Lo que se decidió **no** usar. Se deja aquí para no volver a proponerlo.

| Descartado | Por qué |
|---|---|
| Radio resuelta por el juego con una bandera | Desde el 8 de noviembre la radio la juega la app. Sus valores (el dial y el rango del prototipo) viven en la app, como los del mirador y el observatorio |
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
