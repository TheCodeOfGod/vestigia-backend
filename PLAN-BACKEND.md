# Vestigia — Plan del backend (documento vivo)

Última actualización: 26 de octubre de 2026.
Responsable: Raúl. Este archivo es el resumen a la mano; el detalle vive en `docs/` y en el contrato de la API.

**Estados:** `Acordado` = confirmado por Raúl. `Propuesto` = pendiente de su visto bueno. `Provisional` = valor de prototipo que puede cambiar sin migrar la base.

---

## 1. Qué resuelve el backend

Enlaza el videojuego (Unity) con la app móvil (Flutter) para que los puzzles jugados en el teléfono se reflejen en el juego. Los clientes **no se hablan entre sí**: todo pasa por el backend, que guarda el estado de cada **sala**.

```
Unity (PC) ──┐                       ┌── PostgreSQL
             ├── REST + WebSocket ───┤
App (móvil) ─┘     Spring Boot       └── (Railway)
```

La PWA solo consultaría el estado de una sala terminada (archivo de evidencias).

## 2. Decisiones

| Tema | Decisión | Estado |
|---|---|---|
| Plataforma de datos | Spring Boot + PostgreSQL. Firebase queda fuera | Acordado |
| Hosting | Railway (backend + Postgres), con Dockerfile | Acordado |
| Sincronización app ↔ juego | Automática por salas. Sin respaldo de código manual por ahora | Acordado |
| Código de sala | Nombre de Pueblo Mágico de una palabra, sin acentos ni ñ, elegido de una lista que muestra el juego | Acordado |
| Datos | Separados por sala | Acordado |
| Escritura y consulta | REST bajo `/api/v1` | Acordado |
| Tiempo real | WebSocket simple en `/ws`, mensajes JSON `type` + `payload` | Acordado |
| Migraciones | Flyway (el esquema se crea solo al arrancar) | Acordado |
| Orientación del teléfono | `azimut` y `altitud` en grados, no cuaternión | Acordado |
| Repositorio | Backend en repo separado | Acordado |
| CI/CD | GitHub Actions: pruebas, build de Docker, deploy a Railway | Acordado (definición propuesta) |
| Valores de puzzles | Ver sección 7 | Acordado (provisional; se pueden ajustar) |

## 3. Salas

- Existe un **catálogo** fijo de nombres (`codigos_pueblo`). Las salas **no** se crean por adelantado: se crean cuando el jugador elige un pueblo.
- El juego pide los pueblos libres (`GET /pueblos?disponibles=true`), el jugador elige uno y el juego lo reclama (`POST /salas`). Si otro lo tomó antes, responde `409` y el juego refresca la lista.
- Una sala es **única mientras está viva**: un índice único parcial sobre `codigo` para los estados `esperando_app` y `activa` evita carreras. Las salas terminadas se conservan como historial (útil para métricas y evidencias) y el nombre vuelve a estar libre.
- Estados: `esperando_app` → `activa` → `finalizada` | `expirada`.
- Solo cabe **una app** por sala. Una vez enlazada, otro intento de unirse recibe `409`.
- Expira tras unas 2 horas sin actividad.
- Al crear y al unirse se entrega un `token` por dispositivo. El juego lo guarda localmente para poder reconectar.

## 4. Modelo de datos

| Tabla | Para qué | Campos clave |
|---|---|---|
| `codigos_pueblo` | Catálogo de nombres | `codigo` |
| `salas` | Instancia de una partida | `id`, `codigo`, `estado`, `escena_actual`, `ultima_actividad`, `expira_en` |
| `puzzles_estado` | Un registro por sala y puzzle | `sala_id`, `puzzle`, `estado`, `datos` JSONB |
| `descubrimientos` | Lo que muestra la libreta | `sala_id`, `clave`, `datos` JSONB, `origen` |
| `eventos` | Bitácora para métricas de pruebas | `sala_id`, `tipo`, `payload`, `origen`, `creado_en` |

Estados de puzzle: `bloqueado` → `disponible` → `en_curso` → `resuelto`. El servidor valida las transiciones y los requisitos.

## 5. API REST (`/api/v1`)

| Ruta | Quién | Para qué |
|---|---|---|
| `GET /pueblos?disponibles=true` | Juego | Lista de pueblos libres |
| `POST /salas` | Juego | Reclama un pueblo; devuelve sala y token |
| `POST /salas/{codigo}/unirse` | App | Enlaza el teléfono |
| `GET /salas/{codigo}/estado` | Todos | Estado completo (sirve para reconectar) |
| `PUT /salas/{codigo}/puzzles/{puzzle}` | App o juego | Reporta intento o resultado |
| `POST /salas/{codigo}/descubrimientos` | App o juego | Registra un fragmento, coordenada, etc. |
| `PUT /salas/{codigo}/escena` | Juego | Cambia la escena actual |
| `DELETE /salas/{codigo}` | Juego | Cierra la sala |

## 6. Tiempo real (`/ws`)

Cada dispositivo se conecta con su rol (`juego` o `app`) y su token.

| Mensaje | Dirección | Notas |
|---|---|---|
| `puzzle.actualizado` | servidor → todos | Tras cada escritura REST |
| `descubrimiento.nuevo` | servidor → todos | Idem |
| `escena.cambiada` | servidor → app | |
| `dispositivo.conectado` / `desconectado` | servidor → todos | Presencia |
| `orientacion` | app → servidor → juego | Solo en memoria, ~20 por segundo; solo se envía si cambió más de 0.5° |
| `observatorio.proximidad` | servidor → todos | Solo cuando cambia de nivel |
| `sala.cerrada` | servidor → todos | |

Regla: si el WebSocket se cae, el cliente reconecta y pide `GET estado` para ponerse al día.

## 7. Puzzles (valores provisionales)

Reglas del diseño: cada puzzle se resuelve en menos de 2 minutos, sin penalizaciones, con retroalimentación inmediata. El **cliente da el feedback inmediato; el servidor decide si está resuelto**. Los valores objetivo viven solo en el servidor y en la guía de integración, nunca como secreto en el APK.

| # | Puzzle | Dónde se juega | Requiere | Datos que reporta | Condición de resolución (servidor) | Resultado |
|---|---|---|---|---|---|---|
| 1 | Radio | App | `pescador_dialogo_completo` (lo reporta el juego) | `{posicion, intentos}` | Dial discreto 0–20; objetivo **13**; acepta 12–14 | Coordenada A |
| 2 | Fotografía | App | `fragmento_1`, `fragmento_2`, `fragmento_3` (juego, uno por nivel del faro) | `{colocacion:[{fragmento, slot, rot}]}` | Cada fragmento en su slot con `rot` 0; `rot` ∈ {0, 90, 180, 270} | Coordenada B |
| 3 | Mirador | App | Fotografía resuelta | Fase 1: `{limpieza_pct}`. Fase 2: `{x, y}` | Fase 1: `limpieza_pct` ≥ 80. Fase 2: X e Y enteros 0–20; objetivo **X=7, Y=14**, tolerancia ±1 sostenida 1 s | Descubrimiento `observatorio_visible` |
| 4 | Observatorio | App + juego | Coordenadas A y B, mirador resuelto | Flujo `orientacion` | Ver abajo | Magnetar localizado |

**Coordenadas** (posición astronómica en coordenadas horizontales):

- **A = azimut: 60°**. Valores posibles: múltiplos de 15 (0–345).
- **B = altitud: 35°**. Valores posibles: múltiplos de 5 (10–80).
- Ninguna sola revela el magnetar. El servidor las entrega como descubrimientos `coordenada_a` y `coordenada_b` cuando se resuelve cada puzzle.
- Presentación opcional en la libreta con claves de catalogación (`PUE-060`, `FAR-035`).

**Observatorio:**

- El teléfono calibra al inicio ("apunta a la pantalla y toca"): fija el azimut 0. La altitud sale de la gravedad y no necesita calibrar.
- Error normalizado = máximo entre `|Δazimut| / 10` y `|Δaltitud| / 8`.
- Niveles: ≤ 1 enfocado (se sostiene 1.5 s para resolver), ≤ 3 caliente, ≤ 6 tibio, > 6 frío.
- Un altitud máxima de trabajo de 85° evita el problema de bloqueo de ejes (gimbal lock).

## 8. CI/CD (GitHub Actions)

- **PR a `develop` o `main`:** `./mvnw verify` con Java 21; pruebas de integración contra un Postgres real (Testcontainers) que además valida las migraciones de Flyway; build de la imagen Docker.
- **Push a `main`:** Railway despliega; un job espera el despliegue y verifica `/actuator/health`.
- **Flujo de ramas:** `feature/*` → PR a `develop` → PR a `main`. Commits con prefijo `New:`, `Refactor:`, `Fix:` o `Merge:`.
- Empezar con un solo entorno desplegado (producción) y agregar `staging` cuando el juego y la app ya integren.

## 9. Plan por pasos

- [ ] **Paso 0:** decidir repo separado, copiar el backend (con historial), configurar acceso y secretos.
- [ ] **Paso 1 — Base:** `CLAUDE.md` nuevo, Flyway, Dockerfile, CI, despliegue vacío con `/actuator/health`.
- [ ] **Paso 2 — Salas:** catálogo de pueblos, crear, unirse, estado.
- [ ] **Paso 3 — Puzzles y descubrimientos:** REST completo con validación de transiciones y requisitos.
- [ ] **Paso 4 — WebSocket:** eventos y presencia.
- [ ] **Paso 5 — Orientación:** flujo del giroscopio y niveles de proximidad.
- [ ] **Paso 6 — Cierre:** Swagger, `docs/tiempo-real.md`, guías para Unity y Flutter, página HTML de prueba.

## 10. Pendientes

- Confirmar los valores de la sección 7 con quien hace la app y con Aldo (ya tienen visto bueno de Raúl).
- Fecha para retirar el CRUD de `salas` del laboratorio de la PWA.
- Convención de nombres de ramas (el equipo no la ha decidido).
- Objetivo visual del mirador y condición exacta de apertura de la compuerta del observatorio (diseño, no backend).
