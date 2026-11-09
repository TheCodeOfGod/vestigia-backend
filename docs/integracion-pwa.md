# Guía de integración: la PWA en React y Vite

Esta guía es para quien programa **la PWA de Vestigia en React con Vite (TypeScript)**. Explica, paso a paso, cómo conectarla al backend de Vestigia, qué puede pedir hoy y qué todavía no existe. No necesitas abrir otras guías ni el código del backend: todo lo que la PWA usa está aquí.

> **Antes de copiar cualquier comando:** todos usan la variable `BASE` con la dirección del backend. Escribe primero `export BASE=http://localhost:8080` en tu terminal. Si el backend corre en otro puerto (por ejemplo `18081`), usa ese número: `export BASE=http://localhost:18081`. Ver la sección 2.1.

> **Servidor de pruebas en Railway (ya desplegado).** Puedes probar sin levantar nada en tu computadora:
>
> - REST: `https://vestigia-backend-production-745d.up.railway.app/api/v1`
> - WebSocket: `wss://vestigia-backend-production-745d.up.railway.app/ws`
> - Swagger UI (contratos y ejemplos): `https://vestigia-backend-production-745d.up.railway.app/swagger-ui/index.html`
> - Para los comandos de esta guía: `export BASE=https://vestigia-backend-production-745d.up.railway.app`
>
> Es **un solo servidor compartido por todo el equipo**. Un pueblo solo puede tener una sala viva: si `POST /salas` responde `409`, otra persona lo está usando; elige otro de `GET /api/v1/pueblos?disponibles=true`. Cuando termines, cierra tu sala con `DELETE /api/v1/salas/{codigo}` para liberar el pueblo (si no, expira sola a las 2 horas sin actividad).
>
> La PWA sí manda `Origin`. Corriendo en `http://localhost:5173` o `http://localhost:4173` ya puede llamar a este servidor. Si la sirves desde otra URL, pásasela a Raúl para agregarla a `CORS_ALLOWED_ORIGINS`. Con la URL real, `VITE_API_URL={URL}` (sin `/` al final).

Cada respuesta de ejemplo salió de correr el backend real. El cliente de la sección 10, el componente del paso 4 y la declaración de `VITE_API_URL` del paso 1 se comprobaron con `tsc --noEmit` (TypeScript 6.0.3, modo estricto) usando los tipos de React 19 y Vite de la PWA. La regla de caché de la sección 9 es un fragmento de configuración y no se comprobó aparte.

**Índice**

1. [Qué hace tu aplicación en la historia](#1-qué-hace-tu-aplicación-en-la-historia)
2. [Antes de empezar](#2-antes-de-empezar)
3. [Qué sí y qué no hace tu aplicación](#3-qué-sí-y-qué-no-hace-tu-aplicación)
4. [Recorrido paso a paso](#4-recorrido-paso-a-paso)
5. [Autenticación](#5-autenticación)
6. [Estados de un puzzle](#6-estados-de-un-puzzle)
7. [Tiempo real](#7-tiempo-real)
8. [Tabla de errores](#8-tabla-de-errores)
9. [Si quiero X, hago Y](#9-si-quiero-x-hago-y)
10. [Cliente mínimo completo](#10-cliente-mínimo-completo)
11. [Checklist de "ya terminé"](#11-checklist-de-ya-terminé)
12. [Errores comunes](#12-errores-comunes)

---

## 1. Qué hace tu aplicación en la historia

Vestigia es un juego de misterio para PC con una app móvil que hace de libreta. La PWA **no participa en la partida**: no crea salas, no se une a ellas y no juega puzzles. Es la cara web del proyecto y solo consulta información complementaria.

Hoy lo único que puede pedirle al backend es el **catálogo de pueblos** (los nombres de sala) y saber cuáles están libres. El plan contempla que, más adelante, muestre el archivo de partidas terminadas, pero ese endpoint **todavía no existe** (sección 4, "Todavía no existe, no lo implementes").

```
 Navegador (PWA) ── fetch ──▶ GET /api/v1/pueblos ──▶ Backend ──▶ PostgreSQL
                    sin token, sin WebSocket, solo lectura

 Juego (PC) y app (teléfono) ── REST + WebSocket ──▶ Backend      (la PWA no habla con ellos)
```

## 2. Antes de empezar

### 2.1 URL base

| Entorno | URL base |
|---|---|
| Local | `http://localhost:8080` (o el puerto que use quien levantó el backend) |
| Producción (Railway) | `https://vestigia-backend-production-745d.up.railway.app` |

Todas las rutas REST empiezan con `/api/v1`. La PWA guarda la URL base en la variable de Vite `VITE_API_URL` (paso 1 de la sección 4); nunca la escribas en el código.

En los comandos de esta guía la URL base está en la variable `BASE`. Defínela una vez en tu terminal y, si tu backend corre en otro puerto, cambia solo el número de esta línea:

```sh
export BASE=http://localhost:8080
```

Sin `/` al final. Si abres otra terminal, vuelve a escribir la línea: la variable vive solo en la terminal donde la definiste. `BASE` es solo para tus comandos; la PWA usa `VITE_API_URL`.

### 2.2 Levantar el backend en tu computadora

Necesitas Java 21, Docker y el repositorio del backend.

1. Arranca una base de datos PostgreSQL:

	```sh
	docker run -d --name vestigia-pg -e POSTGRES_DB=vestigia -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:17-alpine
	```

2. Desde la carpeta del repositorio del backend, arranca el servidor:

	```sh
	./mvnw spring-boot:run
	```

3. Comprueba que responde:

	```sh
	curl $BASE/actuator/health
	```

	<!-- respuesta: health -->
	```json
	{"groups": ["liveness", "readiness"], "status": "UP"}
	```

Si el puerto 5432 ya está ocupado, usa `-p 55432:5432` en el paso 1 y arranca con `DB_URL=jdbc:postgresql://localhost:55432/vestigia ./mvnw spring-boot:run`.

Por defecto el backend deja pasar las peticiones del navegador que vienen de `http://localhost:5173` (`vite dev`) y `http://localhost:4173` (`vite preview`). Si tu PWA corre en otro puerto, arranca el backend con ese origen agregado, separado por comas:

```sh
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:4173,http://localhost:3000 ./mvnw spring-boot:run
```

### 2.3 Probar sin tu aplicación

**Con `curl`.** Cada paso de la sección 4 trae su comando. Para simular al navegador, agrega el encabezado `Origin`:

```sh
curl -i "$BASE/api/v1/pueblos?disponibles=true" -H 'Origin: http://localhost:5173'
```

Si el origen está permitido, la respuesta trae `Access-Control-Allow-Origin: http://localhost:5173`.

**Con Swagger.** `$BASE/swagger-ui/index.html` lista todas las rutas del backend con sus ejemplos. La PWA solo usa `GET /api/v1/pueblos`.

**La página de prueba** `$BASE/prueba-ws.html` sirve para ver los mensajes del WebSocket del juego y la app. La PWA no usa WebSocket, así que no la necesitas.

### 2.4 Glosario

| Palabra | Qué es |
|---|---|
| Sala | Una partida en curso. Junta el juego de PC y la app del teléfono. La PWA no entra a salas |
| Código de sala | El nombre de un pueblo del catálogo, en mayúsculas, sin acentos ni ñ y de una sola palabra: `BACALAR`. Mientras una sala lo usa, el pueblo está ocupado (`"libre": false`) |
| Token | Una contraseña larga que el backend le da al juego y a la app para su sala. **La PWA no tiene token** ni debe pedirlo |
| Rol | Quién es cada cliente para el backend: `juego` o `app`, según su token. La PWA no tiene rol: solo usa rutas públicas |
| Puzzle | Uno de los cuatro retos de la partida: `radio`, `photo`, `lookout` y `observatory`. La PWA no los ve hoy |
| Estado | En qué punto está un puzzle (`bloqueado`, `disponible`, `en_curso`, `resuelto`) o una sala (`esperando_app`, `activa`, `finalizada`, `expirada`) |
| Descubrimiento | Algo que pasó en la partida y queda en la libreta (`pieza_1`, `coordenada_a`). La PWA no los ve hoy |
| Bandera | Un descubrimiento que el juego registra al llegar a un lugar (`lookout_enabled`). La PWA no las usa |
| Origen | Esquema, dominio y puerto desde donde se sirve la PWA, sin `/` al final: `https://vestigia.example` |
| CORS | La regla del navegador que bloquea las peticiones a otro origen salvo que el backend las permita |

### 2.5 Lo que todavía no es definitivo

| # | Tema | Cómo está hoy |
|---|---|---|
| P7 | Archivo de partidas terminadas | **No existe.** Falta decidir qué muestra, cómo se identifica una sala y quién puede verla (sección 4) |
| O1 | URL del backend en producción | ✅ `https://vestigia-backend-production-745d.up.railway.app` |
| O3 | ¿La PWA sigue llamando a `/api/salas`? | El código de la PWA todavía lo llama, pero el backend ya no lo tiene: responde `404` (sección 12) |
| — | `/actuator/health` desde el navegador | No tiene CORS. Si la PWA necesita leerlo desde el navegador en otro origen, hay que pedírselo a Raúl |
| 🟡 | `codigo` en los errores | Siempre viene en el cuerpo de error; vale `null` en todo lo que usa la PWA |

## 3. Qué sí y qué no hace tu aplicación

**Lo que la PWA sí usa:**

| Qué | Petición | Desde dónde |
|---|---|---|
| Catálogo de pueblos | `GET /api/v1/pueblos` | Navegador (`fetch`) |
| Solo los pueblos libres | `GET /api/v1/pueblos?disponibles=true` | Navegador (`fetch`) |
| Saber si el backend está vivo | `GET /actuator/health` | Terminal (`curl`) o el proxy de Vite. Desde el navegador en otro origen el navegador bloquea la respuesta (no tiene CORS) |

**Lo que la PWA no debe usar:**

| Qué | Qué pasa si lo usas |
|---|---|
| `GET /api/v1/salas/{codigo}/estado` | `401` `Falta el token: envía el encabezado Authorization: Bearer <token>` |
| `POST /api/v1/salas/{codigo}/descubrimientos`, `PUT /api/v1/salas/{codigo}/puzzles/{puzzle}`, `PUT /api/v1/salas/{codigo}/escena`, `DELETE /api/v1/salas/{codigo}` | `401`: la PWA no tiene token |
| `POST /api/v1/salas` | **No da error**: no pide token, a propósito, porque es como el juego obtiene el suyo. Crearía una sala de verdad y ocuparía un pueblo. Es solo del juego: la PWA no debe llamarlo |
| `POST /api/v1/salas/{codigo}/unirse` | **No da error** si la sala existe y espera a su app: no pide token, a propósito, porque es como la app obtiene el suyo, y ocuparía el lugar del teléfono de una partida real. Si no hay sala en curso con ese código responde `404` `No hay una sala en curso con el código '…'`, lo normal al probarlo sin juego. Es solo de la app: la PWA no debe llamarlo |
| WebSocket `/ws` | Sin un token válido el servidor cierra la conexión. Lo indica con un **código de cierre del WebSocket**, no con un código HTTP: `4001` si el token es inválido y `4008` si no llega `auth` en 5 s. Un `GET /ws` normal, sin el saludo de WebSocket (por ejemplo con `curl $BASE/ws`), responde `400` con el texto plano `Can "Upgrade" only to "WebSocket".`. La PWA no necesita tiempo real |
| `/api/salas` (sin `/v1`) | `404`: era del laboratorio y ya no existe |

## 4. Recorrido paso a paso

### Paso 1. Configurar la URL del backend

**Cuándo:** una vez, al preparar el proyecto, y en cada entorno donde se publique la PWA.

Vite lee las variables que empiezan con `VITE_` de los archivos `.env`. Crea `.env.local` en la raíz de la PWA (Git no lo sube):

```sh
# .env.local
VITE_API_URL=http://localhost:8080
```

En el código, léela una sola vez y quita la `/` final si la trae:

```ts
export const API_URL = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '');
```

Declara su tipo en `src/vite-env.d.ts` para que TypeScript la conozca:

```ts
interface ImportMetaEnv {
  /** URL base del backend, sin "/" al final. Vacía = mismo origen (proxy de Vite). */
  readonly VITE_API_URL?: string;
}
```

Hay dos formas de trabajar en local:

| Opción | `VITE_API_URL` | Cómo llega la petición | CORS |
|---|---|---|---|
| Directo al backend | `http://localhost:8080` | El navegador llama a `http://localhost:8080/api/v1/...` | Hace falta. `5173` y `4173` ya están permitidos |
| Proxy de Vite | vacía | El navegador llama a `/api/v1/...` en el mismo origen y `vite dev` lo reenvía (`server.proxy` con `'/api': 'http://localhost:8080'`) | No hace falta. Solo funciona con `vite dev`, no con `vite preview` ni en producción |

En producción, `VITE_API_URL` es la URL pública del backend: `https://vestigia-backend-production-745d.up.railway.app`. Se fija al compilar (`vite build`): si cambia, hay que volver a compilar.

**Errores:** si `VITE_API_URL` está mal, `fetch` falla sin respuesta (sección 8). Revisa en la consola del navegador a qué URL fue la petición.

### Paso 2. Comprobar que el backend está vivo

**Cuándo:** antes de empezar a programar, y cuando algo no responde.

```sh
curl $BASE/actuator/health
```

Respuesta `200 OK`, con el encabezado `Content-Type: application/vnd.spring-boot.actuator.v3+json` (el formato de Actuator, que también es JSON). `curl` lo imprime en una sola línea, sin espacios (`{"groups":["liveness","readiness"],"status":"UP"}`); aquí está reformateado para leerlo mejor:

<!-- respuesta: health -->
```json
{"groups": ["liveness", "readiness"], "status": "UP"}
```

**Qué hacer:** si ves `"status": "UP"`, el backend y su base de datos funcionan. Si `curl` no conecta, el backend no está corriendo o está en otro puerto.

**Desde el navegador:** `/actuator/health` está fuera de `/api/**` y no tiene CORS. Un `fetch` desde `http://localhost:5173` a `http://localhost:8080/actuator/health` llega al servidor, pero el navegador no deja leer la respuesta y lanza `TypeError: Failed to fetch`. Si la PWA necesita mostrar si el backend está vivo, usa `GET /api/v1/pueblos`: si responde, el backend está vivo. Si de verdad hace falta `/actuator/health` desde el navegador, pídeselo a Raúl.

### Paso 3. Pedir la lista de pueblos

**Cuándo:** al abrir la pantalla que muestra el catálogo o los pueblos libres.

```sh
curl "$BASE/api/v1/pueblos?disponibles=true"
```

Sin token, sin cuerpo y sin encabezados especiales. Respuesta `200 OK` (recortada a tres pueblos):

<!-- respuesta: pueblos -->
```json
[
  {"codigo": "ACAXOCHITLAN", "libre": true},
  {"codigo": "ACULCO", "libre": true},
  {"codigo": "AHUACATLAN", "libre": true}
]
```

| Parámetro | Valores | Efecto |
|---|---|---|
| `disponibles` | `true` | Solo los pueblos libres |
| `disponibles` | `false` o sin el parámetro | Los 101 pueblos; los ocupados traen `"libre": false` |

Manda siempre `true` o `false`, en minúsculas. El backend (Spring) también acepta otras formas, sin importar mayúsculas: `1`, `yes` y `on` filtran como `true`; `0`, `no`, `off` y el parámetro vacío (`?disponibles=`) devuelven todos, como `false`. Cualquier otro valor (`si`, `x`) responde `400`.

| Campo | Tipo | Qué es |
|---|---|---|
| `codigo` | `string` | Nombre del pueblo en mayúsculas, sin acentos ni ñ: `"BACALAR"` |
| `libre` | `boolean` | `false` mientras una partida lo está usando |

La lista viene en orden alfabético. Un pueblo vuelve a estar libre cuando su partida termina.

**Qué hacer:** muéstrala tal cual o conviértela a tus nombres (`codigo` → `nombre`). El campo `libre` cambia en cuanto un juego crea o cierra una sala, así que no lo guardes mucho tiempo en caché (sección 9).

**Errores:**

| Respuesta | Cuándo | Qué hacer |
|---|---|---|
| `400` `Valor inválido para 'disponibles'` | `disponibles` no es un booleano que Spring reconozca (`?disponibles=si`, `?disponibles=x`) | Error de programación: manda `true` o nada |
| `403` con el texto `Invalid CORS request` (no es JSON) | El navegador mandó un `Origin` que el backend no permite | Agrega tu origen a `CORS_ALLOWED_ORIGINS` en local, o pide que lo agreguen en producción (paso 6) |
| Sin respuesta | Sin red, backend apagado o `VITE_API_URL` mal | Muestra "No hay conexión con el servidor" y un botón de reintentar |

Cuerpo real del `400`:

<!-- respuesta: error -->
```json
{
  "timestamp": "2026-11-06T15:46:06.520093Z",
  "status": 400,
  "error": "Bad Request",
  "codigo": null,
  "message": "Valor inválido para 'disponibles'",
  "path": "/api/v1/pueblos",
  "details": []
}
```

```ts
// Los tipos y la función get están en la sección 10.
const libres = await get<Pueblo[]>('/api/v1/pueblos?disponibles=true');
```

### Paso 4. Mostrar la lista con un hook de React

**Cuándo:** en el componente que pinta los pueblos.

El hook pide la lista al montar el componente, cancela la petición si el componente se desmonta y expone `cargando`, `error` y `recargar`.

```tsx
import { usePueblos } from './vestigiaApi';

export function ListaPueblos() {
  const { pueblos, cargando, error, recargar } = usePueblos(true);

  if (cargando) return <p>Cargando pueblos…</p>;
  if (error) {
    return (
      <p>
        {error.message} <button onClick={recargar}>Reintentar</button>
      </p>
    );
  }
  return (
    <ul>
      {pueblos.map((p) => (
        <li key={p.codigo}>{p.codigo}</li>
      ))}
    </ul>
  );
}
```

**Qué hacer:** con `error.status === 0` no hubo respuesta (sin red); con otro valor, el backend respondió un error y `error.message` trae su `message` en español, que se puede mostrar.

### Paso 5. Manejar los errores y la falta de red

**Cuándo:** en cada `fetch`.

Hay dos tipos de fallo y hay que tratarlos distinto:

| Fallo | Cómo se ve en `fetch` | Qué mostrar |
|---|---|---|
| El backend respondió un error | `response.ok` es `false`. El cuerpo es el JSON de error (salvo el `403` de CORS, que es texto) | `message` del cuerpo, o un texto genérico si es `500` |
| No hubo respuesta | `fetch` lanza `TypeError` (sin red, backend apagado, URL mal o CORS bloqueado) | "No hay conexión con el servidor" |

Todos los errores JSON del backend tienen el mismo cuerpo:

<!-- respuesta: error -->
```json
{
  "timestamp": "2026-11-06T15:58:57.661425Z",
  "status": 404,
  "error": "Not Found",
  "codigo": null,
  "message": "No static resource api/salas.",
  "path": "/api/salas",
  "details": []
}
```

Pon un límite de tiempo a cada petición (`AbortSignal.timeout(10000)`) para no dejar la pantalla esperando sin fin. La función `get` de la sección 10 ya hace todo esto.

### Paso 6. Publicar la PWA: pedir que agreguen tu origen a CORS

**Cuándo:** antes de publicar la PWA en cualquier dominio que no sea `localhost:5173` ni `localhost:4173`.

El backend solo acepta peticiones del navegador desde los orígenes de su variable `CORS_ALLOWED_ORIGINS`. Los de desarrollo ya están; el de producción no.

1. Averigua el origen exacto donde quedará la PWA: esquema, dominio y puerto. Por ejemplo `https://vestigia-pwa.example`. Así se escribe en `CORS_ALLOWED_ORIGINS`: **siempre sin `/` al final**. El navegador manda el encabezado `Origin` solo y nunca le pone `/`. Hoy Spring quita la `/` final de los orígenes configurados (se comprobó: `https://vestigia-pwa.example/` en la variable deja pasar tanto `/api/**` como el WebSocket), pero esa tolerancia no forma parte del contrato y no conviene depender de ella: un origen con `/` puede dejar de coincidir con otra versión de Spring o con otro proxy. Esta regla es para quien configura la variable; tú no controlas lo que manda el navegador. `http://…` (sin la `s`) es otro origen.
2. Mándaselo a Raúl, que lo agrega a `CORS_ALLOWED_ORIGINS` en el servidor de producción.
3. Cuando lo confirme, compruébalo:

	```sh
	curl -i "$BASE/api/v1/pueblos" -H 'Origin: https://vestigia-pwa.example'
	```

	Si está permitido, la respuesta es `200` y trae `Access-Control-Allow-Origin: https://vestigia-pwa.example`. Si no, es `403` con el texto `Invalid CORS request`.

Reglas de CORS del backend:

| Regla | Valor |
|---|---|
| Rutas con CORS | Solo `/api/**` |
| Orígenes permitidos por defecto | `http://localhost:5173` y `http://localhost:4173` |
| Métodos | `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS` |
| Encabezados | Cualquiera |
| Credenciales (cookies) | No. La PWA no las necesita |
| Origen no permitido | `403` con el texto `Invalid CORS request`, sin `Access-Control-Allow-Origin` |

### Todavía no existe, no lo implementes

**El archivo de partidas terminadas.** El plan menciona que la PWA podría mostrar el resumen de una partida que ya terminó. **Hoy no hay ningún endpoint para eso** y no se debe programar contra uno inventado. Las razones:

- `GET /api/v1/salas/{codigo}/estado` exige un token, y la PWA no tiene.
- Al cerrar la sala, los tokens del juego y de la app dejan de servir.
- El código de una sala es el nombre del pueblo. Cuando la partida termina, el pueblo queda libre y otra partida puede usar el mismo código, así que el código no identifica una partida terminada.

El backend sí conserva las salas terminadas y su bitácora. Falta decidir (pendiente P7, de Raúl con el equipo):

| Pregunta | Quién responde |
|---|---|
| ¿Qué muestra la PWA de una partida terminada? | Raúl con el equipo de diseño |
| ¿Cómo se identifica una partida, si el código se reutiliza? | Raúl |
| ¿Quién puede verla y cómo se autoriza? | Raúl |

Cuando se decida, esta guía se actualiza con la ruta, su JSON y sus errores.

## 5. Autenticación

Hay **dos** tipos de pantalla.

- **Las públicas** (la lista de pueblos) no usan token: no mandes `Authorization`.
- **Las de administración** (Usuarios y Salas) piden iniciar sesión con una cuenta. El detalle de cada ruta está en `docs/api.md`, sección "Sesión y administración".

Nunca guardes ni mandes el token de una sala (juego o app) desde la PWA: son de cada partida y no sirven en `/admin`.

### Iniciar sesión

```js
const base = import.meta.env.VITE_API_URL; // https://…/api/v1

async function iniciarSesion(correo, password) {
  const r = await fetch(`${base}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ correo, password }),
  });
  const cuerpo = await r.json();
  if (r.status === 401) throw new Error('Correo o contraseña incorrectos');
  if (r.status === 429) throw new Error('Demasiados intentos, espera unos minutos');
  if (!r.ok) throw new Error(cuerpo.message);
  sessionStorage.setItem('vestigia.sesion', JSON.stringify(cuerpo)); // { token, expira_en, usuario }
  return cuerpo;
}
```

Guardar el token en `sessionStorage` lo conserva al recargar y lo pierde al cerrar la pestaña. La sesión dura 8 horas y no se renueva.

### Llamar a las rutas de administración

```js
async function api(ruta, opciones = {}) {
  const sesion = JSON.parse(sessionStorage.getItem('vestigia.sesion') ?? 'null');
  const r = await fetch(`${base}${ruta}`, {
    ...opciones,
    headers: {
      'Content-Type': 'application/json',
      ...(sesion && { Authorization: `Bearer ${sesion.token}` }),
      ...opciones.headers,
    },
  });
  if (r.status === 401) {
    sessionStorage.removeItem('vestigia.sesion');
    window.location.assign('/login');
  }
  return r;
}
```

Al abrir la PWA, si hay un token guardado, llama a `GET /auth/yo`: `200` es que sigue vigente y `401` que hay que volver a `/login`.

| Respuesta | Qué significa | Qué hacer |
|---|---|---|
| `401` en `/admin/**` o `/auth/yo` | Sin sesión, sesión vencida, o mandaste un token de sala | Borra la sesión y manda a `/login` |
| `401` en `/auth/login` | Correo o contraseña incorrectos | Muestra el mensaje; no distingas cuál falló |
| `429` en `/auth/login` | Cinco intentos fallidos con ese correo | Pide esperar unos minutos |
| `403` | El rol no tiene permiso | Muestra "No tienes permiso"; no cierres la sesión |
| `403` con texto `Invalid CORS request` | Tu origen no está permitido | Paso 6 |
| `409` con `codigo: "correo_duplicado"` o `"ultimo_admin"` | Regla del backend | Muestra el `message` |

### Pantalla Salas

- El listado sale de `GET /admin/salas`. Para "En partida" cuenta las salas con `estado` `esperando_app` o `activa`; para "Libres", usa `GET /pueblos?disponibles=true` (público). Los filtros Todos, Libres y En partida se pueden resolver en el cliente.
- Un código puede repetirse en el listado (la sala vigente y las ya terminadas). Identifica cada fila por `id`.
- El detalle y el cierre usan ese `id`: `GET /admin/salas/{id}` y `DELETE /admin/salas/{id}`. Cerrar una sala ya terminada también da `204`, así que no hace falta ocultar el botón por eso.
- Trata una respuesta vacía o con error como lista vacía; no leas `.length` de algo que puede ser `null`.

Proteger una ruta en React (`RutaProtegida`) solo evita pantallas vacías. Lo que protege de verdad es el backend, que revisa el token en cada petición a `/admin`.

## 6. Estados de un puzzle

La PWA no lee puzzles hoy. Esta sección es solo para que entiendas el vocabulario si el equipo lo menciona, o si algún día se define el archivo de partidas (P7).

```
             requisitos                 iniciado                  completada o sintonizada
bloqueado ─────────────▶ disponible ─────────────▶ en_curso ──────────────────────────────▶ resuelto
                              │                                                                ▲
                              └──────────────────── completada o sintonizada ──────────────────┘
```

| Si el estado es… | La PWA hace… |
|---|---|
| `bloqueado`, `disponible`, `en_curso` o `resuelto` | Nada hoy: la PWA no consulta salas ni puzzles |

## 7. Tiempo real

**No aplica.** La PWA no usa el WebSocket de `/ws`: es solo para el juego y la app, y pide un token en el primer mensaje. La lista de pueblos se pide por REST cuando hace falta.

## 8. Tabla de errores

Los errores del backend traen este cuerpo JSON. `codigo` siempre viene; en las rutas de la PWA vale `null`.

<!-- respuesta: error -->
```json
{
  "timestamp": "2026-11-06T15:46:06.520093Z",
  "status": 400,
  "error": "Bad Request",
  "codigo": null,
  "message": "Valor inválido para 'disponibles'",
  "path": "/api/v1/pueblos",
  "details": []
}
```

| HTTP | `codigo` | Cuándo | Qué hacer |
|---|---|---|---|
| `400` | `null` | `disponibles` no es `true` ni `false` | Error de programación: corrige la URL |
| `401` | `null` | Llamaste una ruta de salas | No la uses (sección 3) |
| `403` | — (texto `Invalid CORS request`, no JSON) | Tu origen no está en `CORS_ALLOWED_ORIGINS` | Paso 6 |
| `404` | `null` | Ruta inexistente, por ejemplo `/api/salas` o una `/` al final | Usa `/api/v1/pueblos` exactamente |
| `406` | `null` | Mandaste `Accept` con un formato que no es JSON. El `message` real viene en inglés porque lo genera Spring: `Acceptable representations: [application/json, application/*+json, application/yaml].` | Usa `Accept: application/json` o no lo mandes |
| `500` | `null` | Error interno | "Algo salió mal. Inténtalo más tarde." Reintenta una vez |
| Sin respuesta (`TypeError`) | | Sin red, backend apagado, `VITE_API_URL` mal, o CORS bloqueó la respuesta | "No hay conexión con el servidor". Revisa la pestaña Red del navegador |

## 9. Si quiero X, hago Y

| Si quiero… | Hago… |
|---|---|
| Mostrar todos los pueblos | `GET /api/v1/pueblos` (sin parámetro). Los ocupados traen `"libre": false` |
| Mostrar solo los libres | `GET /api/v1/pueblos?disponibles=true` |
| Contar cuántas partidas hay en curso | Cuento los pueblos con `"libre": false` en `GET /api/v1/pueblos` |
| Cambiar de backend local a producción | Cambio `VITE_API_URL` y vuelvo a compilar |
| Usar un puerto distinto de 5173 o 4173 | Arranco el backend con ese origen en `CORS_ALLOWED_ORIGINS`, o uso el proxy de Vite |
| Saber si el backend está vivo desde el navegador | Pido `GET /api/v1/pueblos`; si responde, está vivo |
| Mostrar la lista sin conexión | Guardo la última respuesta y la muestro con un aviso de que puede estar desactualizada |
| Cachear con el service worker | Uso `NetworkFirst` para `/api/v1/pueblos`: `libre` cambia en cuanto alguien crea o cierra una sala |
| Mostrar una partida terminada | Todavía no se puede (sección 4, "Todavía no existe") |

Recomendación para `vite.config.ts` (la regla de caché que hoy apunta a `/api/salas` ya no sirve). Fragmento no comprobado con `tsc`:

```ts
// Dentro de VitePWA({ workbox: { runtimeCaching: [...] } })
{
  urlPattern: ({ url, request }) =>
    request.method === 'GET' && url.pathname.startsWith('/api/v1/pueblos'),
  handler: 'NetworkFirst',
  options: {
    cacheName: 'api-pueblos',
    networkTimeoutSeconds: 5,
    cacheableResponse: { statuses: [200] },
  },
}
```

## 10. Cliente mínimo completo

Un solo archivo, `src/vestigiaApi.ts`: los tipos de las respuestas, una función `get` que separa los errores del servidor de la falta de red, la llamada a pueblos y el hook de React.

**Solo corre dentro de la PWA**, con `vite dev` o `vite build`: usa `import.meta.env`, que Vite rellena al compilar, y el paquete `react`. No se puede ejecutar suelto con `node` ni con `ts-node` en la terminal. Para probar el backend desde la terminal usa `curl` (sección 4). Si quieres una versión sin React, copia solo `API_URL`, los tipos, `BackendError`, `get` y `listarPueblos`, y cambia `import.meta.env.VITE_API_URL` por la URL escrita o por `process.env`.

> Comprobado por quien escribió esta guía, el 6 de noviembre de 2026, con el `tsc` 6.0.3 de `node_modules` del repositorio de la PWA (`vestigia-pwa`) y sus tipos de React 19 y Vite: `tsc --noEmit` en modo estricto, con las opciones de `tsconfig.app.json` de la PWA, sobre este archivo como `src/vestigiaApi.ts`, el componente del paso 4 como `src/ListaPueblos.tsx` y la declaración del paso 1 en `src/vite-env.d.ts`. Sin errores.

```ts
import { useCallback, useEffect, useState } from 'react';

/** URL base del backend, sin "/" al final. Vacía = mismo origen (proxy de Vite). */
export const API_URL = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '');

/** Un pueblo del catálogo. GET /api/v1/pueblos */
export interface Pueblo {
  codigo: string;
  libre: boolean;
}

/** Cuerpo de todos los errores JSON del backend. */
export interface ErrorBody {
  timestamp: string;
  status: number;
  error: string;
  /** Siempre viene; null salvo en los errores que definen uno (ninguno en las rutas de la PWA). */
  codigo: string | null;
  message: string;
  path: string;
  details: { field: string; message: string }[];
}

/** GET /actuator/health (solo desde curl o el proxy de Vite). */
export interface Health {
  status: 'UP' | 'DOWN' | string;
  groups?: string[];
}

/** status 0 = no hubo respuesta (sin red, backend apagado, URL mal o CORS). */
export class BackendError extends Error {
  readonly status: number;
  readonly cuerpo: ErrorBody | null;

  constructor(status: number, message: string, cuerpo: ErrorBody | null = null) {
    super(message);
    this.name = 'BackendError';
    this.status = status;
    this.cuerpo = cuerpo;
  }
}

export async function get<T>(ruta: string, signal?: AbortSignal): Promise<T> {
  const limite = AbortSignal.timeout(10_000);
  let respuesta: Response;
  try {
    respuesta = await fetch(`${API_URL}${ruta}`, {
      headers: { Accept: 'application/json' },
      signal: signal ? AbortSignal.any([signal, limite]) : limite,
    });
  } catch (causa) {
    if (signal?.aborted) throw causa; // el componente se desmontó: no es un error que mostrar
    throw new BackendError(0, 'No hay conexión con el servidor');
  }
  if (!respuesta.ok) {
    const texto = await respuesta.text();
    let cuerpo: ErrorBody | null = null;
    try {
      cuerpo = JSON.parse(texto) as ErrorBody;
    } catch {
      // el 403 de CORS es texto plano ("Invalid CORS request")
    }
    const mensaje =
      respuesta.status >= 500 ? 'Algo salió mal. Inténtalo más tarde.' : (cuerpo?.message ?? `Error ${respuesta.status}`);
    throw new BackendError(respuesta.status, mensaje, cuerpo);
  }
  return (await respuesta.json()) as T;
}

export function listarPueblos(soloLibres: boolean, signal?: AbortSignal): Promise<Pueblo[]> {
  return get<Pueblo[]>(`/api/v1/pueblos${soloLibres ? '?disponibles=true' : ''}`, signal);
}

/** Hook de React: pide los pueblos al montar y cada vez que llamas a recargar(). */
export function usePueblos(soloLibres: boolean) {
  const [pueblos, setPueblos] = useState<Pueblo[]>([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<BackendError | null>(null);
  const [vuelta, setVuelta] = useState(0);

  useEffect(() => {
    const control = new AbortController();
    setCargando(true);
    setError(null);
    listarPueblos(soloLibres, control.signal)
      .then(setPueblos)
      .catch((e: unknown) => {
        if (control.signal.aborted) return;
        setError(e instanceof BackendError ? e : new BackendError(0, 'No hay conexión con el servidor'));
      })
      .finally(() => {
        if (!control.signal.aborted) setCargando(false);
      });
    return () => control.abort();
  }, [soloLibres, vuelta]);

  const recargar = useCallback(() => setVuelta((v) => v + 1), []);
  return { pueblos, cargando, error, recargar };
}
```

## 11. Checklist de "ya terminé"

- [ ] La URL del backend sale de `VITE_API_URL`, sin `/` al final, y no está escrita en el código.
- [ ] `.env.local` tiene `VITE_API_URL=http://localhost:8080` (o la PWA usa el proxy de Vite con la variable vacía).
- [ ] La PWA llama a `/api/v1/pueblos` y ya no a `/api/salas`.
- [ ] Distingo "el backend respondió un error" de "no hubo respuesta", y muestro un mensaje para cada caso.
- [ ] Cada petición tiene un límite de tiempo y se cancela si el componente se desmonta.
- [ ] La PWA no manda `Authorization`, no guarda tokens y no abre WebSocket.
- [ ] La PWA no llama a `POST /api/v1/salas` ni a `/unirse`.
- [ ] La caché del service worker no sirve `/api/v1/pueblos` viejo cuando hay red.
- [ ] Le mandé a Raúl el origen exacto de producción para `CORS_ALLOWED_ORIGINS`.
- [ ] No programé nada del archivo de partidas terminadas (P7).

## 12. Errores comunes

| Síntoma | Causa | Solución |
|---|---|---|
| `404` con `No static resource api/salas.` | La PWA todavía llama al CRUD `/api/salas` del laboratorio, que ya no existe | Cambia a `GET /api/v1/pueblos`. Las salas ya no se crean desde la PWA |
| `TypeError: Failed to fetch` en la consola y `403` en la pestaña Red | Tu origen no está en CORS | Paso 6, o arranca el backend local con tu origen |
| `TypeError: Failed to fetch` sin nada en la pestaña Red | Backend apagado o `VITE_API_URL` mal | Comprueba `curl $BASE/actuator/health` y la variable |
| Funciona en `vite dev` pero no en `vite preview` | Usabas el proxy de Vite, que solo existe en `vite dev` | Define `VITE_API_URL` con la URL del backend |
| `fetch` a `/actuator/health` falla desde el navegador | `/actuator/health` no tiene CORS | Usa `curl`, o `GET /api/v1/pueblos` como prueba de vida |
| `401` `Falta el token…` | Llamaste una ruta de salas | La PWA solo usa `/api/v1/pueblos` |
| La lista de libres no cambia aunque alguien creó una sala | El service worker sirve una copia vieja | Usa `NetworkFirst` (sección 9) |
| `400` `Valor inválido para 'disponibles'` | `?disponibles=si` o `?disponibles=x` | Usa `?disponibles=true` o no mandes el parámetro |
| `404` con una `/` al final (`/api/v1/pueblos/`) | Las rutas no aceptan `/` final | Quita la `/` |
| El cambio de `VITE_API_URL` no se nota en producción | Vite fija la variable al compilar | Vuelve a correr `vite build` |
