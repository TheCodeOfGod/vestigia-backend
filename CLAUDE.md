# CLAUDE.md — Backend de Vestigia

Instrucciones para trabajar en el backend de **Vestigia** (proyecto integrador, Universidad Tecnológica de Chihuahua, grupo IDGS101N). Este repositorio contiene **solo el backend**. Responsable: Raúl.

> Regla general: lo marcado como **[PENDIENTE]** o **[PROVISIONAL]** no está decidido por el equipo. No lo inventes ni lo des por hecho; si una tarea depende de ello, pregunta. Nunca inventes datos de contenido (listas, valores, nombres): si no están aquí, pídelos.

## 1. Qué es

Vestigia es un videojuego de misterio de ~15 minutos para PC (Unity + C#) con una app móvil (Flutter) que funciona como la libreta de la protagonista. Algunos puzzles se juegan **en el teléfono** y el resultado se refleja **en el juego**. La PWA (React/Vite) vive en otro repo y solo consulta información complementaria.

Este backend enlaza al juego con la app. **Los clientes no se hablan entre sí**: todo pasa por el backend, que guarda el estado de cada **sala**.

```
Unity (PC) ──┐                        ┌── PostgreSQL
             ├── REST + WebSocket ────┤
App (móvil) ─┘      Spring Boot       └── (Railway)
```

Equipo de 6 personas con Scrum (Product Owner: Aldo Barrera; Scrum Master: María José Durán). Primera versión en español, mercado México.

## 2. Stack

- **Java 21**, **Spring Boot 4.1.x** (versión actual del proyecto: 4.1.1), **Maven** con wrapper (`./mvnw`), JAR. Paquete base `com.vestigia`.
- Dependencias permitidas: Spring Web MVC, Spring Data JPA, Validation, WebSocket, Actuator, PostgreSQL Driver, **Flyway**, **springdoc-openapi** (Swagger UI), **Testcontainers** (solo pruebas).
- **No agregar** Lombok, MapStruct ni otras dependencias sin acuerdo del equipo.
- Spring Boot 4: Jackson 3, `@WebMvcTest` en `org.springframework.boot.webmvc.test.autoconfigure`, `@MockitoBean` en lugar de `@MockBean`. **Verifica siempre** el nombre y la versión de cada starter/artefacto contra Spring Boot 4.1.x (Flyway, WebSocket, springdoc, Testcontainers); no asumas coordenadas de versiones anteriores. Si algo no es compatible, repórtalo y propone alternativa; no lo fuerces.
- **PostgreSQL** como única base de datos. Firebase está descartado.
- Hibernate solo valida el esquema (`spring.jpa.hibernate.ddl-auto=validate`); **el esquema lo crea Flyway**.
- JSON en `snake_case` (`spring.jackson.property-naming-strategy=SNAKE_CASE`). Fechas con `Instant` y `TIMESTAMPTZ` (UTC, ISO-8601).
- Columnas JSONB: verifica el mapeo con Hibernate 7 / Jackson 3. Si da problemas, usa `String` + conversión en el service y repórtalo.

## 3. Arquitectura del código

Paquetes por capa (la estructura del ejemplo `customer` de clase, que el equipo conoce):

```
com.vestigia
├── controller/   Controladores REST + ApiExceptionHandler (@RestControllerAdvice)
├── dto/          Records que viajan en JSON
├── model/        Entidades JPA (sufijo Model)
├── repository/   Spring Data JPA
├── service/      Lógica de negocio y validación de puzzles
├── ws/           WebSocket: handler, sesiones en memoria, mensajes
├── config/       CorsConfig, RequestLoggingFilter, WebSocketConfig, seguridad por token
└── VestigiaApplication.java
```

Reglas:
- Clases en singular y PascalCase, identificadores de dominio en español (`Sala`, `descubrimiento`, `azimut`).
- DTO de una entidad con el mismo nombre sin sufijo (`Sala`); DTOs de petición con sufijo `Request` (`CrearSalaRequest`). Entidades JPA con sufijo `Model`.
- El controller **no** toca el repository; todo pasa por el service. El handler de WebSocket también usa services.
- Sin Lombok: getters y setters a mano (los records no los necesitan).
- Tipos auxiliares pequeños como records anidados, no como archivos nuevos, salvo que se reutilicen.
- Errores con cuerpo JSON uniforme: `timestamp`, `status`, `error`, `message`, `path`, `details`. 400 validación o JSON malformado, 401 token ausente o inválido, 403 rol sin permiso, 404 no encontrado, 409 conflicto de estado, 500 genérico sin stacktrace.
- `RequestLoggingFilter`: una línea `[API]` por petición. **Nunca** registrar tokens ni el encabezado `Authorization`.

## 4. Dominio: salas

- Existe un **catálogo fijo** de nombres (`codigos_pueblo`): nombres de Pueblos Mágicos de México de **una sola palabra**, en mayúsculas, **sin acentos ni ñ**.
  - **[PENDIENTE]** La lista concreta la define Raúl. Obtén la lista oficial de Pueblos Mágicos de la Secretaría de Turismo, filtra los de una palabra, normaliza y **preséntala para aprobación antes de crear la migración de seed**. No inventes nombres.
- Las salas **no se crean por adelantado**: se crean cuando el jugador elige un pueblo en el juego.
- Estados: `esperando_app` → `activa` → `finalizada` | `expirada`.
- Un código solo puede estar en una sala **viva** (`esperando_app` o `activa`). Se garantiza con un **índice único parcial**. Las salas terminadas se conservan como historial.
- **Una sola app** por sala. Un segundo intento de unirse recibe `409`.
- Expiración: ~2 horas sin actividad (configurable). Un job programado marca `expirada` y libera el nombre.
- Al crear la sala (juego) y al unirse (app) se entrega un **token opaco** aleatorio. Se guarda **solo su hash**. Se envía como `Authorization: Bearer <token>`. El token determina el **rol** (`juego` o `app`).
- Escenas (`escena_actual`) **[PROVISIONAL]**: `introduccion`, `pueblo`, `faro`, `observatorio`, `desenlace`.

## 5. Modelo de datos

El esquema se crea con migraciones Flyway en `src/main/resources/db/migration/` (`V1__esquema.sql`, `V2__seed_pueblos.sql`, …). **Nunca edites una migración ya aplicada**: crea una nueva.

```sql
CREATE TABLE codigos_pueblo (
  codigo VARCHAR(30) PRIMARY KEY CHECK (codigo ~ '^[A-Z]+$')
);

CREATE TABLE salas (
  id               UUID PRIMARY KEY,
  codigo           VARCHAR(30) NOT NULL REFERENCES codigos_pueblo(codigo),
  estado           VARCHAR(20) NOT NULL CHECK (estado IN ('esperando_app','activa','finalizada','expirada')),
  escena_actual    VARCHAR(20) NOT NULL DEFAULT 'introduccion',
  token_juego_hash CHAR(64) NOT NULL,
  token_app_hash   CHAR(64),
  creada_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
  ultima_actividad TIMESTAMPTZ NOT NULL DEFAULT now(),
  expira_en        TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX salas_codigo_viva ON salas(codigo) WHERE estado IN ('esperando_app','activa');

CREATE TABLE puzzles_estado (
  sala_id        UUID NOT NULL REFERENCES salas(id) ON DELETE CASCADE,
  puzzle         VARCHAR(20) NOT NULL CHECK (puzzle IN ('radio','fotografia','mirador','observatorio')),
  estado         VARCHAR(20) NOT NULL CHECK (estado IN ('bloqueado','disponible','en_curso','resuelto')),
  datos          JSONB NOT NULL DEFAULT '{}',
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (sala_id, puzzle)
);

CREATE TABLE descubrimientos (
  id        BIGSERIAL PRIMARY KEY,
  sala_id   UUID NOT NULL REFERENCES salas(id) ON DELETE CASCADE,
  clave     VARCHAR(40) NOT NULL,
  datos     JSONB NOT NULL DEFAULT '{}',
  origen    VARCHAR(10) NOT NULL CHECK (origen IN ('juego','app','servidor')),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (sala_id, clave)
);

CREATE TABLE eventos (
  id        BIGSERIAL PRIMARY KEY,
  sala_id   UUID NOT NULL REFERENCES salas(id) ON DELETE CASCADE,
  tipo      VARCHAR(40) NOT NULL,
  payload   JSONB NOT NULL DEFAULT '{}',
  origen    VARCHAR(10) NOT NULL,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX eventos_sala_tiempo ON eventos(sala_id, creado_en);
```

Al crear una sala se insertan sus 4 filas de `puzzles_estado` (`bloqueado`, salvo `radio` según requisitos). `eventos` sirve para las métricas del plan de pruebas (tiempos de resolución, intentos, uso de pistas); **no** se registran las lecturas de `orientacion`.

## 6. API REST (`/api/v1`)

| Método y ruta | Rol | Respuesta |
|---|---|---|
| `GET /pueblos?disponibles=true` | público | Lista de pueblos libres (o todos con `libre: bool`) |
| `POST /salas` `{codigo}` | juego | `201 {codigo, estado, token}`. `400` si no está en el catálogo, `409` si está ocupado |
| `POST /salas/{codigo}/unirse` | app | `200 {token}`. `404` si no existe o no está en `esperando_app`, `409` si ya hay una app |
| `GET /salas/{codigo}/estado` | juego o app | Estado completo: escena, puzzles, descubrimientos, dispositivos conectados |
| `PUT /salas/{codigo}/puzzles/{puzzle}` `{datos}` | juego o app | Reporta intento o resultado. Responde el estado nuevo del puzzle |
| `POST /salas/{codigo}/descubrimientos` `{clave, datos}` | juego o app | Registra un descubrimiento |
| `PUT /salas/{codigo}/escena` `{escena}` | juego | Cambia la escena |
| `DELETE /salas/{codigo}` | juego | Cierra la sala (`finalizada`) |
| `GET /actuator/health` | público | Chequeo de salud |

Reglas:
- Los tokens solo funcionan para **su** sala. Un `{codigo}` distinto del del token da `403`.
- Los endpoints de escritura son **idempotentes**: reportar un puzzle ya resuelto devuelve `200` con el estado actual; registrar un descubrimiento repetido no duplica.
- Un puzzle `bloqueado` responde `409` con código de error `puzzle_bloqueado`.
- Las claves **generadas por el servidor** (`coordenada_a`, `coordenada_b`, `observatorio_visible`, `magnetar_localizado`) no pueden ser enviadas por clientes (`403`).
- Cada escritura exitosa actualiza `ultima_actividad`, guarda un `evento` y **emite un mensaje por WebSocket**.
- La documentación REST se expone con Swagger UI (springdoc).

## 7. Tiempo real (`/ws`)

WebSocket **simple** (sin STOMP ni SockJS), mensajes JSON con la forma `{"type": "...", "payload": {...}}`.

Conexión:
1. El cliente abre `/ws`.
2. En menos de 5 s envía `{"type":"auth","payload":{"token":"..."}}`. Si no, el servidor cierra la conexión. (El token **no** va en la URL.)
3. El servidor responde `auth.ok` con el rol y la sala, y avisa al otro dispositivo (`dispositivo.conectado`).
4. El servidor envía pings periódicos (~25 s). Los clientes reconectan con backoff; tras reconectar piden `GET estado`.

| Mensaje | Dirección | Notas |
|---|---|---|
| `puzzle.actualizado` | servidor → todos | Tras cada escritura REST de puzzle |
| `descubrimiento.nuevo` | servidor → todos | |
| `escena.cambiada` | servidor → app | |
| `dispositivo.conectado` / `dispositivo.desconectado` | servidor → todos | Presencia |
| `orientacion` | app → servidor → juego | `{azimut, altitud, t}`. **Solo en memoria**, no se guarda |
| `observatorio.proximidad` | servidor → todos | `{nivel}`; solo cuando cambia de nivel |
| `sala.cerrada` | servidor → todos | |

`orientacion`:
- `azimut` en grados 0–360 (ya relativo a la calibración hecha por la app), `altitud` en grados −90 a 90, `t` en milisegundos.
- El servidor valida rangos, ignora mensajes de quien no tenga rol `app`, **descarta el excedente por encima de ~40 por segundo** y reenvía solo la última lectura al juego.
- Cliente: ~20 envíos por segundo y solo si cambió más de 0.5°.
- Las sesiones viven en memoria: **una sola instancia** del backend (anótalo como limitación conocida).
- Límite de tamaño de mensaje bajo (unos pocos KB).

## 8. Puzzles **[PROVISIONAL, aprobado por Raúl]**

Reglas: cada puzzle se resuelve en < 2 minutos, sin penalizaciones, con feedback inmediato. **El cliente da el feedback inmediato; el servidor decide si el puzzle está resuelto.** Los valores objetivo viven **solo en el servidor** (configurables en `application.properties`, p. ej. `vestigia.puzzles.radio.objetivo=13`) y en la guía de integración; nunca devuelvas un objetivo antes de que el puzzle esté resuelto.

| Puzzle | Requiere | `datos` que reporta el cliente | Se resuelve cuando | Resultado |
|---|---|---|---|---|
| `radio` | descubrimiento `pescador_dialogo_completo` (lo reporta el juego) | `{posicion, intentos}` | `posicion` ∈ [12, 14] (objetivo 13, dial 0–20) | Descubrimiento `coordenada_a` |
| `fotografia` | descubrimientos `fragmento_1`, `fragmento_2`, `fragmento_3` (el juego, uno por nivel del faro) | `{colocacion:[{fragmento, slot, rot}]}` | Cada fragmento en su slot con `rot` 0 (`rot` ∈ {0, 90, 180, 270}) | Descubrimiento `coordenada_b` |
| `mirador` | `fotografia` resuelto | Fase 1: `{fase:1, limpieza_pct}`. Fase 2: `{fase:2, x, y, sostenido_ms}` | Fase 1: `limpieza_pct` ≥ 80. Fase 2: X=7, Y=14 (enteros 0–20), tolerancia ±1, `sostenido_ms` ≥ 1000 | Descubrimiento `observatorio_visible` |
| `observatorio` | descubrimientos `coordenada_a` y `coordenada_b`, `mirador` resuelto | Flujo `orientacion` por WebSocket | Ver abajo | Descubrimiento `magnetar_localizado` |

Notas:
- Cuando un puzzle se resuelve, el servidor crea el descubrimiento resultado (origen `servidor`) y desbloquea los puzzles que dependen de él.
- Los datos de cada puzzle se validan por forma y rango; datos inválidos dan `400`.
- **Coordenadas** (horizontales): **A = azimut 60°** (múltiplos de 15, 0–345), **B = altitud 35°** (múltiplos de 5, 10–80). Cada una se entrega como `{tipo, valor}` en `datos` del descubrimiento. Ninguna sola revela el magnetar.
- **Observatorio:** durante el puzzle `en_curso`, el servidor calcula el error normalizado con cada `orientacion`: `e = max(|Δazimut| / 10, |Δaltitud| / 8)`, con la diferencia de azimut calculada en círculo (mínimo entre `d` y `360 − d`). Niveles: `e ≤ 1` **enfocado**, `e ≤ 3` **caliente**, `e ≤ 6` **tibio**, si no **frio**. Se emite `observatorio.proximidad` solo al cambiar de nivel. Si se mantiene `enfocado` durante **1.5 s continuos**, el puzzle queda `resuelto`.
- Los nombres de claves de descubrimiento **[PROVISIONAL]**: `pescador_dialogo_completo`, `fragmento_1..3`, `coordenada_a`, `coordenada_b`, `observatorio_visible`, `magnetar_localizado`.
- Pendientes de diseño (no del backend): objetivo visual del mirador y apertura de la compuerta del observatorio.

## 9. Configuración y secretos

- Variables de entorno: `DB_URL` (formato `jdbc:postgresql://host:puerto/base`), `DB_USER`, `DB_PASSWORD`, `PORT` (puerto del servidor, por defecto 8080), `CORS_ALLOWED_ORIGINS`.
- Orden de prioridad: variables de entorno, luego `application-local.properties` (opcional, ignorado por git, con plantilla `application-local.properties.example`), luego valores por defecto de desarrollo.
- **Nunca** subas contraseñas, tokens ni URLs con credenciales al repo.
- Railway entrega las credenciales del Postgres como variables del servicio; mapéalas a `DB_URL`, `DB_USER` y `DB_PASSWORD` (verifica los nombres reales en Railway).

## 10. Despliegue (Railway)

- Un servicio para el backend (Dockerfile) y uno para PostgreSQL, en el mismo proyecto.
- **Dockerfile de dos etapas** (compilar con Maven y correr con un JRE 21), usuario sin privilegios, `.dockerignore`.
- Health check: `/actuator/health`.
- Flyway migra al arrancar. Un solo entorno desplegado (producción) al inicio; `staging` se agrega después.
- Railway despliega desde `main`; configura que espere los checks de GitHub. **No despliegues ni toques credenciales por tu cuenta**: indica los pasos manuales que le tocan a Raúl.

## 11. Git, ramas y CI

- Commits con prefijo obligatorio: `New:`, `Refactor:`, `Fix:`, `Merge:`. Formato `Prefijo: descripción breve`.
- Flujo: `feature/<descripcion-corta>` → PR a `develop` → PR de `develop` a `main`. Cada PR indica su propósito, cómo se comprobó y qué paso del plan cubre.
- **GitHub Actions** (`.github/workflows/`):
  - `ci.yml` en PR a `develop` y `main`: Java 21, caché de Maven, `./mvnw verify` (incluye pruebas de integración con Postgres real vía Testcontainers; si no es viable con Spring Boot 4.1.x, usa un service container) y **build de la imagen Docker**.
  - Job posterior al despliegue en `main`: verifica `/actuator/health` de la URL real con reintentos.
  - Un fallo bloquea la integración.

## 12. Pruebas

- Unitarias de services con Mockito; controllers con `@WebMvcTest` + `MockMvc`.
- Integración con PostgreSQL real: migraciones Flyway, índice único parcial (carrera al reclamar un pueblo), transiciones y requisitos de puzzles, idempotencia.
- WebSocket: pruebas del flujo `auth`, reenvío de `orientacion` y niveles de `observatorio.proximidad`.
- Cada cambio incluye sus pruebas. Verifica siempre que reclamar el mismo pueblo dos veces no genere dos salas vivas.

## 13. Documentación (se actualiza en el mismo cambio que el código)

- `README.md`: qué es, cómo levantarlo local, variables de entorno, enlace al plan.
- `docs/PLAN.md`: resumen vivo de decisiones, pasos y pendientes (marca los pasos al terminarlos).
- `docs/modelo-datos.md`, `docs/despliegue.md`.
- `docs/tiempo-real.md`: cada mensaje con JSON de ejemplo y quién lo envía.
- `docs/integracion-unity.md` y `docs/integracion-flutter.md`: guías con fragmentos para copiar.
- Una página HTML de prueba (en `src/main/resources/static/`) para ver mensajes sin Unity ni Flutter.
- Documentación en Markdown, en español. Los entregables académicos en Word se hacen aparte.

## 14. Plan por pasos

Trabaja **un paso a la vez**, en su propia rama y PR, y detente al terminar para reportar.

- [x] **Paso 1 — Base:** limpiar el código del laboratorio, Flyway con `V1__esquema.sql`, Dockerfile, configuración por entorno, CI, README y `docs/PLAN.md`.
- [ ] **Paso 2 — Salas:** catálogo de pueblos (seed aprobado), crear, unirse, estado, expiración, autenticación por token.
- [ ] **Paso 3 — Puzzles y descubrimientos:** REST completo con transiciones y requisitos.
- [ ] **Paso 4 — WebSocket:** `auth`, eventos y presencia.
- [ ] **Paso 5 — Orientación:** flujo del giroscopio y niveles de proximidad.
- [ ] **Paso 6 — Cierre:** Swagger, guías de integración y página de prueba.

## 15. Reglas para el agente

- Lee este archivo y `docs/PLAN.md` antes de empezar. Si algo aquí contradice lo que encuentras en el código, repórtalo; no lo resuelvas en silencio.
- No inventes contenido ni decisiones: lo no definido es **[PENDIENTE]**; pregunta.
- Cambios pequeños y verificables; corre `./mvnw verify` antes de cada commit.
- Si cambias un contrato (ruta, JSON, mensaje, esquema), actualiza en el mismo cambio este archivo, la documentación y las pruebas.
- Los compañeros que hacen Unity y Flutter son tus usuarios: prioriza contratos claros, errores comprensibles y ejemplos.

### Gates del operador

Estas decisiones **requieren a Raúl**, aunque trabajes en un modo autónomo (por ejemplo poteto-mode) y aunque el trabajo parezca reversible. Detente, repórtalo y espera su respuesta:

- Cualquier despliegue o cambio en Railway.
- Aprobar la lista de pueblos del catálogo.
- Cambiar un contrato (rutas, JSON, mensajes, esquema) o los valores de los puzzles.
- Avanzar al siguiente paso del plan.
- Hacer merge a `main`.

Prioridad de convenciones: las del proyecto (prefijos de commit, documentación en español, estructura de este archivo) ganan sobre el estilo de cualquier skill.

**Revisión independiente:** en este entorno solo hay modelos de la familia Claude. Si un skill exige un revisor de otra familia, el gate queda `BLOCKED: independent review`. No lo omitas ni cuentes tu propia revisión como independiente: deja los commits listos, repórtalo y Raúl lo revisa.
