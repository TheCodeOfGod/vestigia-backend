# Guía de integración: el juego en Unity

Esta guía es para quien programa **el juego de PC en Unity (C#)**. Explica, paso a paso y en orden, cada cosa que el juego le pide o le avisa al backend de Vestigia. No necesitas abrir otras guías ni el código del backend: todo lo que el juego usa está aquí.

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
> Unity no manda `Origin`, así que no tiene restricción de CORS. Usa `wss://` (con TLS) y `https://` en producción.

Cada respuesta de ejemplo salió de correr el backend real. Los tokens están recortados (`K8srOIYIL0jr…`). Los fragmentos de C# están marcados como **no compilados**: se escribieron para Unity 2021 o posterior, pero no se compilaron en un proyecto de Unity.

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

El juego es la partida principal. La jugadora elige un pueblo, y ese nombre es el **código de sala** que luego escribe en su teléfono para enlazar la app (la libreta de Janeth).

Durante la partida, el juego **avisa** al backend lo que pasa en el mundo: la escena en la que está, que terminó el diálogo con el pescador, cada pieza de la fotografía que recoge y cuando llega a la cima del faro o al observatorio. Con eso, el backend habilita los puzzles que se juegan en el teléfono.

El juego también **escucha**: el teléfono resuelve la radio, la fotografía, el mirador y el observatorio, y el backend le avisa al juego por WebSocket para que reaccione (abrir el camino, mostrar la coordenada, pasar al final). Al terminar, el juego cierra la sala.

```
 Elegir pueblo ─▶ Crear sala ─▶ Esperar la app ─▶ Pueblo ─────────────────▶ Faro ──────────────────────────▶ Observatorio ─▶ Desenlace ─▶ Cerrar sala
 GET /pueblos     POST /salas   WebSocket          pescador_dialogo_completo  pieza_1, pieza_2, pieza_3         observatory_enabled   PUT escena    DELETE /salas
                  (token)       dispositivo.       espera radio (app)          lookout_enabled (cima)           espera magnetar
                                conectado          PUT escena en cada cambio   espera photo y lookout resueltos  _localizado
```

## 2. Antes de empezar

### 2.1 URL base

| Entorno | URL base |
|---|---|
| Local | `http://localhost:8080` (o el puerto que use quien levantó el backend) |
| Producción (Railway) | `https://vestigia-backend-production-745d.up.railway.app` |

Todas las rutas REST empiezan con `/api/v1`. El WebSocket está en `/ws`, en el mismo servidor: `ws://localhost:8080/ws` en local y `wss://vestigia-backend-production-745d.up.railway.app/ws` en producción.

En los comandos de esta guía la URL base está en la variable `BASE`. Defínela una vez en tu terminal y, si tu backend corre en otro puerto, cambia solo el número de esta línea:

```sh
export BASE=http://localhost:8080
```

Sin `/` al final. Si abres otra terminal, vuelve a escribir la línea: la variable vive solo en la terminal donde la definiste.

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

### 2.3 Probar sin Unity

**Con `curl`.** Cada paso de la sección 4 trae su comando. Los tokens se guardan en variables de la terminal. Copia el valor de `token` de la respuesta y pégalo así:

```sh
TOKEN_JUEGO=pega-aqui-el-token-del-juego
```

Si tienes `jq`, puedes guardarlo directo: `TOKEN_JUEGO=$(curl -s -X POST $BASE/api/v1/salas -H 'Content-Type: application/json' -d '{"codigo": "BACALAR"}' | jq -r .token)`.

**Hacer de app con `curl`.** Para avanzar la partida sin teléfono, puedes mandar lo que mandaría la app:

```sh
# La app se une a la sala y recibe su propio token
curl -X POST $BASE/api/v1/salas/BACALAR/unirse
TOKEN_APP=pega-aqui-el-token-de-la-app

# La app abre la fotografía (photo), el mirador (lookout) o el observatorio (observatory): el puzzle pasa a en_curso
curl -X PUT $BASE/api/v1/salas/BACALAR/puzzles/photo \
	-H "Authorization: Bearer $TOKEN_APP" -H 'Content-Type: application/json' \
	-d '{"datos": {"iniciado": true}}'

# La app lo completa: el puzzle pasa a resuelto
curl -X PUT $BASE/api/v1/salas/BACALAR/puzzles/photo \
	-H "Authorization: Bearer $TOKEN_APP" -H 'Content-Type: application/json' \
	-d '{"datos": {"completada": true}}'
```

Cambia `photo` por `lookout` u `observatory` para los otros dos. El aviso `iniciado` es opcional: `completada` también resuelve un puzzle `disponible`.

**Unirse con `curl` no avisa al juego.** `dispositivo.conectado` llega cuando la app **abre y autentica su WebSocket**, no cuando llama a `/unirse`. Para simularlo sin teléfono, abre la página de prueba (abajo), pega `TOKEN_APP` en el panel de la app y pulsa **Conectar**.

**Con la página de prueba.** Abre `$BASE/prueba-ws.html` en el navegador (en local, `http://localhost:8080/prueba-ws.html`). Tiene un panel para el juego y otro para la app. En cada uno pegas el token, pulsas **Conectar** y ves cada mensaje del WebSocket que llega, con su hora. Sirve para ver qué recibiría Unity mientras mandas peticiones con `curl`.

**Con Swagger.** `$BASE/swagger-ui/index.html` lista cada ruta con sus ejemplos. Para las rutas con candado pulsa **Authorize** y pega el token, sin la palabra `Bearer`.

### 2.4 Glosario

| Palabra | Qué es |
|---|---|
| Sala | Una partida. Junta un juego y, como máximo, una app. Vive en el backend |
| Código de sala | El nombre del pueblo que eligió la jugadora, en mayúsculas, sin acentos ni ñ y de una sola palabra: `BACALAR`. La jugadora lo escribe en la app |
| Token | Una contraseña larga y aleatoria que el backend te da **una sola vez** al crear la sala. Prueba que eres el juego de esa sala. Se manda en cada petición |
| Rol | Quién eres para el backend: `juego` o `app`. Lo decide el token |
| Puzzle | Uno de los cuatro retos: `radio`, `photo` (fotografía), `lookout` (mirador) y `observatory` (observatorio). Los ids van en inglés |
| Estado | En qué punto está un puzzle: `bloqueado`, `disponible`, `en_curso` o `resuelto`. También la sala tiene estado: `esperando_app`, `activa`, `finalizada` o `expirada` |
| Descubrimiento | Algo que pasó y queda anotado en la libreta, con una clave (`pieza_1`) y datos opcionales. El pescador, las piezas y las banderas los registra solo el juego; los resultados de los puzzles, el servidor |
| Bandera | Un descubrimiento que el juego registra solo para avisar que llegó a un lugar: `lookout_enabled` (cima del faro) y `observatory_enabled` (observatorio) |

### 2.5 Lo que todavía no es definitivo

Estas reglas ya funcionan así, pero Raúl todavía puede cambiarlas. Si cambian, esta guía se actualiza.

| # | Tema | Cómo funciona hoy |
|---|---|---|
| C2 | Banderas `lookout_enabled` y `observatory_enabled` | Nombres aprobados por Raúl. Falta que Unity confirme el momento exacto en que las manda |
| ✅ | Segunda conexión del juego | Si el juego abre otro WebSocket con el mismo token, el nuevo reemplaza al viejo y el viejo se cierra con `4009` |
| ✅ | Códigos de cierre del WebSocket | `4000`, `4001`, `4008`, `4009`, `4500`, `1000` y `1009`, explicados en la sección 7 |
| 🟡 | `codigo` en los errores | Siempre viene en el cuerpo de error; vale `null` salvo en `puzzle_bloqueado`, `clave_solo_juego` y `puzzle_solo_app` |
| 🟡 | Id de puzzle desconocido | Responde `400`, no `404` |

## 3. Qué sí y qué no hace tu aplicación

**Lo que el juego sí usa:**

| Qué | Petición o mensaje | Cuándo |
|---|---|---|
| Ver pueblos libres | `GET /api/v1/pueblos?disponibles=true` | Menú de elegir pueblo |
| Crear la sala | `POST /api/v1/salas` | La jugadora elige un pueblo |
| Ver el estado | `GET /api/v1/salas/{codigo}/estado` | Al arrancar, tras cada reconexión y cuando dudes |
| Cambiar de escena | `PUT /api/v1/salas/{codigo}/escena` | Cada vez que cambia la escena |
| Registrar descubrimientos y banderas | `POST /api/v1/salas/{codigo}/descubrimientos` | Pescador, piezas, cima del faro, observatorio. Solo el juego puede mandar `pescador_dialogo_completo`, `pieza_1`, `pieza_2`, `pieza_3`, `lookout_enabled` y `observatory_enabled`: si la app las manda recibe `403` (`clave_solo_juego`) |
| Escuchar la radio | WebSocket `puzzle.actualizado` y `descubrimiento.nuevo` (`coordenada_a`) | La jugadora la sintoniza **en el teléfono**; el juego solo se entera |
| Cerrar la sala | `DELETE /api/v1/salas/{codigo}` | Al terminar la partida |
| Tiempo real | WebSocket `/ws`: manda `auth`; recibe `auth.ok`, `dispositivo.conectado`, `dispositivo.desconectado`, `puzzle.actualizado`, `descubrimiento.nuevo`, `sala.cerrada` y `error` | Desde que creas la sala hasta que la cierras |

**Lo que el juego no debe usar:**

| Qué | Qué pasa si lo usas |
|---|---|
| `POST /api/v1/salas/{codigo}/unirse` | **No da error**: el juego ocuparía el lugar de la app y el teléfono recibiría `409` al unirse. Es solo de la app |
| `PUT /api/v1/salas/{codigo}/puzzles/{puzzle}` (cualquiera de los cuatro: `radio`, `photo`, `lookout` u `observatory`) | `403` con `"codigo": "puzzle_solo_app"`: los cuatro puzzles se juegan en el teléfono y solo la app los avisa. El juego se entera por WebSocket |
| Cualquier ruta con el token de otra sala | `403` `El token no pertenece a esta sala` |
| Cualquier ruta con candado sin token | `401` `Falta el token: envía el encabezado Authorization: Bearer <token>` |
| Mandar `coordenada_a`, `coordenada_b`, `observatorio_visible` o `magnetar_localizado` | `403`: esas claves las crea el servidor |
| Mandar por WebSocket algo que no sea `auth` (por ejemplo `orientacion`) | Mensaje `error` con `tipo_desconocido`; la conexión sigue abierta |

## 4. Recorrido paso a paso

Los pasos van en el orden en que ocurren en la partida. En todos, `BACALAR` es el pueblo que eligió la jugadora y `$TOKEN_JUEGO` es el token que recibiste en el paso 2.

Reglas que aplican a todas las peticiones:

- El cuerpo va en JSON, con el encabezado `Content-Type: application/json`.
- Las rutas con candado llevan `Authorization: Bearer <token>`.
- Los nombres de los campos van en minúsculas con guion bajo (`escena_actual`). Las fechas vienen en UTC (`2026-11-06T15:45:31.735796Z`).
- Cada campo con su tipo: `true` es booleano, `"true"` es texto y da `400`. No repitas claves en un mismo objeto.

### Paso 1. Mostrar los pueblos libres

**Cuándo:** al abrir el menú donde la jugadora elige el pueblo.

```sh
curl "$BASE/api/v1/pueblos?disponibles=true"
```

Sin token ni cuerpo. Respuesta `200 OK` (recortada a tres pueblos):

<!-- respuesta: pueblos -->
```json
[
  {"codigo": "ACAXOCHITLAN", "libre": true},
  {"codigo": "ACULCO", "libre": true},
  {"codigo": "AHUACATLAN", "libre": true}
]
```

**Qué hacer:** muestra la lista. Con `disponibles=true` solo vienen los libres. Sin el parámetro vienen los 101 pueblos y los ocupados traen `"libre": false`.

**Errores:** no hay errores propios. Sin red, muestra "No hay conexión con el servidor" y un botón de reintentar.

```csharp
// No compilado. JsonUtility no lee un arreglo suelto: se envuelve en un objeto.
[System.Serializable] public class Pueblo { public string codigo; public bool libre; }
[System.Serializable] class ListaPueblos { public Pueblo[] items; }

IEnumerator PedirPueblos(System.Action<Pueblo[]> listo)
{
    using var req = UnityWebRequest.Get(baseUrl + "/api/v1/pueblos?disponibles=true");
    yield return req.SendWebRequest();
    if (req.result != UnityWebRequest.Result.Success) { MostrarSinConexion(); yield break; }
    listo(JsonUtility.FromJson<ListaPueblos>("{\"items\":" + req.downloadHandler.text + "}").items);
}
```

### Paso 2. Crear la sala y guardar el token

**Cuándo:** la jugadora elige un pueblo y confirma.

```sh
curl -X POST $BASE/api/v1/salas \
	-H 'Content-Type: application/json' \
	-d '{"codigo": "BACALAR"}'
```

Respuesta `201 Created`:

<!-- respuesta: sala-creada -->
```json
{"codigo": "BACALAR", "estado": "esperando_app", "token": "K8srOIYIL0jr…"}
```

**Qué hacer:**

1. Guarda `token`. **Es la única vez que el backend lo entrega**; si lo pierdes, no hay forma de recuperarlo (ver sección 5).
2. Guarda `codigo` y muéstralo grande en pantalla: la jugadora lo escribe en la app.
3. La sala empieza en `esperando_app`, en la escena `introduccion` y con los cuatro puzzles en `bloqueado`.
4. Sigue con el paso 3 (WebSocket).

El backend acepta el código con espacios o en minúsculas (`" bacalar "`) y lo responde en mayúsculas.

**Errores:**

| Respuesta | Cuándo | Qué hacer |
|---|---|---|
| `409` `'BACALAR' ya tiene una sala en curso` | Otro juego tomó ese pueblo antes | Avisa "Ese pueblo ya está en uso", vuelve a pedir el paso 1 y deja elegir otro |
| `400` `'ATLANTIDA' no está en el catálogo de pueblos` | El código no existe | Es un error del juego: solo ofrece códigos del paso 1 |
| `400` `Datos inválidos` con `details` | Falta `codigo` o viene vacío | Revisa el cuerpo |

Cuerpo real del `409`:

<!-- respuesta: error -->
```json
{
  "timestamp": "2026-11-06T15:45:31.819834Z",
  "status": 409,
  "error": "Conflict",
  "codigo": null,
  "message": "'BACALAR' ya tiene una sala en curso",
  "path": "/api/v1/salas",
  "details": []
}
```

```csharp
// No compilado.
[System.Serializable] public class SalaCreada { public string codigo; public string estado; public string token; }

IEnumerator CrearSala(string codigo)
{
    yield return Enviar("POST", "/api/v1/salas", "{\"codigo\":\"" + codigo + "\"}", false, (status, cuerpo) =>
    {
        if (status == 201)
        {
            var sala = JsonUtility.FromJson<SalaCreada>(cuerpo);
            GuardarSesion(sala.codigo, sala.token); // sección 5
            MostrarCodigoEnPantalla(sala.codigo);
            ConectarWebSocket();                   // paso 3
        }
        else if (status == 409) { AvisarPuebloOcupado(); StartCoroutine(PedirPueblos(MostrarPueblos)); }
        else ManejarError(status, cuerpo);       // sección 8
    });
}
```

### Paso 3. Conectar el WebSocket y autenticarte

**Cuándo:** justo después de crear la sala, y cada vez que la conexión se caiga (sección 7).

1. Abre `ws://localhost:8080/ws` (en producción `wss://…/ws`). **El token no va en la URL.**
2. En menos de 5 segundos manda este texto, cambiando `<token>` por el token del juego que guardaste en el paso 2:

	<!-- peticion -->
	```json
	{"type": "auth", "payload": {"token": "<token>"}}
	```

3. El servidor responde:

	<!-- respuesta: ws -->
	```json
	{"type": "auth.ok", "payload": {"rol": "juego", "sala": "BACALAR"}}
	```

**Qué hacer:** al recibir `auth.ok`, pide el estado (paso 4). Si el servidor cierra la conexión en vez de responder, mira el código de cierre en la sección 7.

Para verlo sin Unity, abre la página de prueba, pega el token en el panel del juego y pulsa **Conectar**.

El código de C# está en la sección 10 (`ConectarWebSocket`).

### Paso 4. Pedir el estado de la sala

**Cuándo:** después de cada `auth.ok` (al conectar y al reconectar) y siempre que necesites ponerte al día.

```sh
curl $BASE/api/v1/salas/BACALAR/estado \
	-H "Authorization: Bearer $TOKEN_JUEGO"
```

Respuesta `200 OK` recién creada la sala, con el WebSocket del juego conectado:

<!-- respuesta: estado -->
```json
{
  "codigo": "BACALAR",
  "estado": "esperando_app",
  "escena_actual": "introduccion",
  "puzzles": [
    {"puzzle": "radio", "estado": "bloqueado", "datos": {}},
    {"puzzle": "photo", "estado": "bloqueado", "datos": {}},
    {"puzzle": "lookout", "estado": "bloqueado", "datos": {}},
    {"puzzle": "observatory", "estado": "bloqueado", "datos": {}}
  ],
  "descubrimientos": [],
  "dispositivos": {"juego": true, "app": false},
  "creada_en": "2026-11-06T15:45:31.735796Z",
  "expira_en": "2026-11-06T17:45:31.776885Z"
}
```

| Campo | Qué significa |
|---|---|
| `estado` | `esperando_app` hasta que el teléfono se une; luego `activa` |
| `escena_actual` | La última escena que mandó el juego |
| `puzzles` | Siempre los cuatro, en este orden: `radio`, `photo`, `lookout`, `observatory` |
| `descubrimientos` | Todo lo registrado, en orden de llegada: `{clave, datos, origen, creado_en}` |
| `dispositivos` | Quién tiene el WebSocket conectado ahora: `{"juego": bool, "app": bool}` |
| `expira_en` | Si nadie escribe nada antes de esa hora, la sala expira |

**Qué hacer:** usa este estado como la verdad. Por ejemplo, si `dispositivos.app` es `true`, la app ya está conectada; si `photo` está `resuelto`, abre lo que la foto desbloquea; si `magnetar_localizado` está en `descubrimientos`, pasa al final.

**Errores:**

| Respuesta | Cuándo | Qué hacer |
|---|---|---|
| `401` | Token ausente o inválido, o la sala ya terminó o venció | Borra el token y vuelve al menú de elegir pueblo |
| `403` `El token no pertenece a esta sala` | El código de la ruta no es el de tu token | Es un error del juego: usa el código que guardaste junto al token |

```csharp
// No compilado. Las clases EstadoSala, PuzzleEstado, etc. están en la sección 10.
IEnumerator PedirEstado()
{
    yield return Enviar("GET", "/api/v1/salas/" + Codigo + "/estado", null, true, (status, cuerpo) =>
    {
        if (status == 200) AplicarEstado(JsonUtility.FromJson<EstadoSala>(cuerpo));
        else ManejarError(status, cuerpo);
    });
}
```

### Paso 5. Esperar a que la app se una

**Cuándo:** mientras el código está en pantalla.

No mandas nada. La jugadora escribe `BACALAR` en la app, la app se une y abre su WebSocket. En cuanto la app se **autentica en el WebSocket** (no antes: unirse por REST no basta), el juego recibe:

<!-- respuesta: ws -->
```json
{"type": "dispositivo.conectado", "payload": {"rol": "app"}}
```

**Qué hacer:** muestra "Teléfono conectado" y deja avanzar a la escena del pueblo. Si la app ya estaba conectada antes de que el juego abriera su WebSocket, no llega este mensaje: por eso el paso 4 lee `dispositivos.app`.

**Para probarlo sin teléfono:** une la app con `curl` (sección 2.3) y después conecta su WebSocket con el panel de la app de `$BASE/prueba-ws.html`. Solo con el `curl` de `/unirse` el juego no recibe nada.

Si el teléfono se desconecta durante la partida llega `{"type": "dispositivo.desconectado", "payload": {"rol": "app"}}`. Muestra un aviso discreto ("El teléfono se desconectó"); la partida no se detiene y la sala no cambia.

### Paso 6. Avisar cada cambio de escena

**Cuándo:** cada vez que el juego cambia de escena. La sala empieza en `introduccion`.

| Escena | Valor exacto | Cuándo mandarla |
|---|---|---|
| Cinemática inicial en la casa | `introduccion` | No hace falta: la sala ya empieza así |
| Pueblo | `pueblo` | Al empezar la parte jugable en el pueblo |
| Faro | `faro` | Al entrar al faro |
| Observatorio | `observatorio` | Al llegar al observatorio |
| Cinemática final | `desenlace` | Al empezar la cinemática final |

Los valores se escriben así, en minúsculas y sin acentos. La lista de escenas es provisional: si Unity necesita otra, se pide a Raúl.

```sh
curl -X PUT $BASE/api/v1/salas/BACALAR/escena \
	-H "Authorization: Bearer $TOKEN_JUEGO" \
	-H 'Content-Type: application/json' \
	-d '{"escena": "pueblo"}'
```

Respuesta `200 OK`:

<!-- respuesta: escena -->
```json
{"escena_actual": "pueblo"}
```

**Qué hacer:** nada más. La app recibe `escena.cambiada` y actualiza su libreta. El juego no recibe ese mensaje. Mandar la escena en la que ya estás responde lo mismo y no hace daño.

**Errores:**

| Respuesta | Cuándo | Qué hacer |
|---|---|---|
| `400` `'bosque' no es una escena válida. Usa una de: introduccion, pueblo, faro, observatorio, desenlace` | Valor mal escrito (`"Faro"`, `"FARO"`) | Corrige el texto |
| `401` | La sala terminó | Igual que en el paso 4 |
| `403` `Solo el juego puede cambiar la escena` | Usaste el token de la app | Usa el del juego |

```csharp
// No compilado.
IEnumerator CambiarEscena(string escena) // "pueblo", "faro", "observatorio" o "desenlace"
{
    yield return Enviar("PUT", "/api/v1/salas/" + Codigo + "/escena", "{\"escena\":\"" + escena + "\"}", true,
        (status, cuerpo) => { if (status != 200) ManejarError(status, cuerpo); });
}
```

### Paso 7. Terminar el diálogo con el pescador

**Cuándo:** en el pueblo, cuando termina el diálogo con el pescador. Esto habilita la radio.

```sh
curl -X POST $BASE/api/v1/salas/BACALAR/descubrimientos \
	-H "Authorization: Bearer $TOKEN_JUEGO" \
	-H 'Content-Type: application/json' \
	-d '{"clave": "pescador_dialogo_completo"}'
```

Respuesta `201 Created` la primera vez:

<!-- respuesta: descubrimiento -->
```json
{"clave": "pescador_dialogo_completo", "datos": {}, "origen": "juego", "creado_en": "2026-11-06T15:45:32.305120Z"}
```

Y por WebSocket, al juego y a la app:

<!-- respuesta: ws -->
```json
{"type": "descubrimiento.nuevo", "payload": {"clave": "pescador_dialogo_completo", "datos": {}, "origen": "juego"}}
{"type": "puzzle.actualizado", "payload": {"puzzle": "radio", "estado": "disponible", "datos": {}}}
```

**Qué hacer:** la radio ya aparece en la libreta de la jugadora, que la sintoniza en el teléfono. Si repites la petición, responde `200 OK` con el mismo cuerpo y no pasa nada más: es seguro reintentar.

`datos` es opcional: un objeto JSON de hasta 4096 bytes. Para estas claves no hace falta mandarlo.

**Errores:**

| Respuesta | Cuándo | Qué hacer |
|---|---|---|
| `400` `Datos inválidos` con `details` de `clave` | La clave tiene mayúsculas, guiones, acentos o espacios | Escríbela exactamente como en esta guía |
| `401` | La sala terminó | Igual que en el paso 4 |

```csharp
// No compilado.
IEnumerator RegistrarDescubrimiento(string clave) // "pescador_dialogo_completo", "pieza_1", "lookout_enabled"...
{
    yield return Enviar("POST", "/api/v1/salas/" + Codigo + "/descubrimientos", "{\"clave\":\"" + clave + "\"}", true,
        (status, cuerpo) => { if (status != 201 && status != 200) ManejarError(status, cuerpo); });
}
```

### Paso 8. La radio se juega en el teléfono (el juego solo escucha)

**Cuándo:** después del paso 7, cuando la radio queda `disponible`. La radio la juega **la app**: la jugadora la sintoniza en el teléfono. **El juego no manda nada en este paso**; solo se entera.

La app avisa que abrió la radio y que la sintonizó. Si el juego manda ese aviso con su token, recibe `403` (los cuatro puzzles los avisa solo la app):

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

Lo que el juego recibe por WebSocket cuando la app abre la radio y cuando la sintoniza, en este orden:

<!-- respuesta: ws -->
```json
{"type": "puzzle.actualizado", "payload": {"puzzle": "radio", "estado": "en_curso", "datos": {"iniciado": true}}}
{"type": "puzzle.actualizado", "payload": {"puzzle": "radio", "estado": "resuelto", "datos": {"sintonizada": true}}}
{"type": "descubrimiento.nuevo", "payload": {"clave": "coordenada_a", "datos": {"tipo": "ascension_recta", "valor": "137"}, "origen": "servidor"}}
```

**Qué hacer:** el juego reacciona a `puzzle.actualizado` de `radio` (`en_curso`: la jugadora está en la radio; `resuelto`: ya la sintonizó). Al resolverse, el servidor crea `coordenada_a`, la ascensión recta. Su `valor` es **texto** (`"137"`), no número. Si el juego la muestra, léela de aquí y no la escribas en el código. Si el juego se conecta después, lee el estado de la radio en `GET estado`.

```csharp
// No compilado.
void AlRecibirPuzzleActualizado(string puzzle, string estado)
{
    if (puzzle == "radio" && estado == "resuelto")
        Progreso.RadioSintonizada = true;   // la jugadora la sintonizó en el teléfono
}
```

### Paso 9. Recoger cada pieza de la fotografía

**Cuándo:** en el faro, **una petición por cada pieza**, en el momento en que la jugadora la recoge. Las claves son `pieza_1`, `pieza_2` y `pieza_3`. Antes, manda la escena `faro` (paso 6).

```sh
curl -X POST $BASE/api/v1/salas/BACALAR/descubrimientos \
	-H "Authorization: Bearer $TOKEN_JUEGO" \
	-H 'Content-Type: application/json' \
	-d '{"clave": "pieza_1"}'
```

Respuesta `201 Created`:

<!-- respuesta: descubrimiento -->
```json
{"clave": "pieza_1", "datos": {}, "origen": "juego", "creado_en": "2026-11-06T15:45:32.354251Z"}
```

Cuando recoge la segunda y la tercera pieza, lo mismo con `pieza_2` y `pieza_3`:

```sh
curl -X POST $BASE/api/v1/salas/BACALAR/descubrimientos \
	-H "Authorization: Bearer $TOKEN_JUEGO" \
	-H 'Content-Type: application/json' \
	-d '{"clave": "pieza_2"}'

curl -X POST $BASE/api/v1/salas/BACALAR/descubrimientos \
	-H "Authorization: Bearer $TOKEN_JUEGO" \
	-H 'Content-Type: application/json' \
	-d '{"clave": "pieza_3"}'
```

Con la tercera, la fotografía pasa a `disponible` y los dos dispositivos reciben:

<!-- respuesta: ws -->
```json
{"type": "descubrimiento.nuevo", "payload": {"clave": "pieza_3", "datos": {}, "origen": "juego"}}
{"type": "puzzle.actualizado", "payload": {"puzzle": "photo", "estado": "disponible", "datos": {}}}
```

**Qué hacer:** la app muestra la burbuja de la fotografía y la jugadora une las piezas en el teléfono. El juego espera. No importa el orden de las piezas ni si alguna se manda dos veces.

**Errores:** los mismos del paso 7.

### Paso 10. Esperar a que el teléfono resuelva la fotografía

**Cuándo:** después de las tres piezas. No mandas nada.

Cuando la jugadora abre la fotografía en el teléfono llega `puzzle.actualizado` con `en_curso`, y cuando la completa:

<!-- respuesta: ws -->
```json
{"type": "puzzle.actualizado", "payload": {"puzzle": "photo", "estado": "en_curso", "datos": {"iniciado": true}}}
{"type": "puzzle.actualizado", "payload": {"puzzle": "photo", "estado": "resuelto", "datos": {"completada": true}}}
{"type": "descubrimiento.nuevo", "payload": {"clave": "coordenada_b", "datos": {"tipo": "declinacion", "valor": "+042"}, "origen": "servidor"}}
```

**Qué hacer:** con `photo` en `resuelto`, el juego ya tiene la foto reconstruida. `coordenada_b` es la declinación; su `valor` es **texto** con signo y ceros (`"+042"`). Deja que la jugadora suba a la cima del faro.

### Paso 11. Llegar a la cima del faro

**Cuándo:** la jugadora llega a la cima del faro, donde está el mirador.

```sh
curl -X POST $BASE/api/v1/salas/BACALAR/descubrimientos \
	-H "Authorization: Bearer $TOKEN_JUEGO" \
	-H 'Content-Type: application/json' \
	-d '{"clave": "lookout_enabled"}'
```

Respuesta `201 Created`:

<!-- respuesta: descubrimiento -->
```json
{"clave": "lookout_enabled", "datos": {}, "origen": "juego", "creado_en": "2026-11-06T15:45:32.397949Z"}
```

Si `photo` ya está `resuelto`, en la misma petición el mirador pasa a `disponible`:

<!-- respuesta: ws -->
```json
{"type": "descubrimiento.nuevo", "payload": {"clave": "lookout_enabled", "datos": {}, "origen": "juego"}}
{"type": "puzzle.actualizado", "payload": {"puzzle": "lookout", "estado": "disponible", "datos": {}}}
```

**Qué hacer:** el mirador se juega en el teléfono. El orden no importa: si mandas `lookout_enabled` antes de que se resuelva la fotografía, el mirador se habilita solo en cuanto se resuelva.

### Paso 12. Esperar a que el teléfono resuelva el mirador

**Cuándo:** no mandas nada. Al completarse llega:

<!-- respuesta: ws -->
```json
{"type": "puzzle.actualizado", "payload": {"puzzle": "lookout", "estado": "resuelto", "datos": {"completada": true}}}
{"type": "descubrimiento.nuevo", "payload": {"clave": "observatorio_visible", "datos": {}, "origen": "servidor"}}
```

**Qué hacer:** `observatorio_visible` es la señal de que ya se ve el observatorio. Abre el camino hacia él.

**Importante:** el backend **no** manda avisos intermedios del mirador (por ejemplo "lente limpio" o "observatorio encontrado"). Solo llega `lookout` resuelto. Lee la sección 7.4 sobre las banderas `lente_limpio` y `obs_encontrado`.

### Paso 13. Llegar al observatorio

**Cuándo:** la jugadora llega al observatorio. Manda primero la escena y después la bandera. El plan dice "llega al observatorio y activa el telescopio"; el momento exacto lo confirma Unity (C2).

```sh
curl -X PUT $BASE/api/v1/salas/BACALAR/escena \
	-H "Authorization: Bearer $TOKEN_JUEGO" \
	-H 'Content-Type: application/json' \
	-d '{"escena": "observatorio"}'

curl -X POST $BASE/api/v1/salas/BACALAR/descubrimientos \
	-H "Authorization: Bearer $TOKEN_JUEGO" \
	-H 'Content-Type: application/json' \
	-d '{"clave": "observatory_enabled"}'
```

Respuesta del segundo, `201 Created`:

<!-- respuesta: descubrimiento -->
```json
{"clave": "observatory_enabled", "datos": {}, "origen": "juego", "creado_en": "2026-11-06T15:45:32.429702Z"}
```

Por WebSocket, a los dos dispositivos, llega primero el descubrimiento y, si ya existen `coordenada_a` y `coordenada_b` y `lookout` está `resuelto`, en la misma petición el observatorio pasa a `disponible`:

<!-- respuesta: ws -->
```json
{"type": "descubrimiento.nuevo", "payload": {"clave": "observatory_enabled", "datos": {}, "origen": "juego"}}
{"type": "puzzle.actualizado", "payload": {"puzzle": "observatory", "estado": "disponible", "datos": {}}}
```

**Qué hacer:** el observatorio se juega **entero en el teléfono**, con su giroscopio. La orientación del teléfono no pasa por el backend: el juego no recibe hacia dónde apunta el teléfono.

### Paso 14. Esperar el magnetar

**Cuándo:** no mandas nada. Cuando la jugadora encuentra el magnetar en el teléfono llega:

<!-- respuesta: ws -->
```json
{"type": "puzzle.actualizado", "payload": {"puzzle": "observatory", "estado": "resuelto", "datos": {"completada": true}}}
{"type": "descubrimiento.nuevo", "payload": {"clave": "magnetar_localizado", "datos": {}, "origen": "servidor"}}
```

**Qué hacer:** `magnetar_localizado` es la señal para el final. Manda la escena `desenlace` (paso 6) y empieza la cinemática final.

### Paso 15. Cerrar la sala

**Cuándo:** al terminar la cinemática final, o si la jugadora abandona la partida desde el menú.

```sh
curl -X DELETE $BASE/api/v1/salas/BACALAR \
	-H "Authorization: Bearer $TOKEN_JUEGO"
```

Respuesta `204 No Content`, sin cuerpo. Por WebSocket, los dos dispositivos reciben:

<!-- respuesta: ws -->
```json
{"type": "sala.cerrada", "payload": {"estado": "finalizada"}}
```

y el servidor cierra las conexiones con el código `1000`.

**Qué hacer:** borra el token y el código guardados. **No reconectes** el WebSocket. El pueblo vuelve a quedar libre para otra partida.

**Errores:**

| Respuesta | Cuándo | Qué hacer |
|---|---|---|
| `401` | La sala ya estaba cerrada o venció | Bórrala igual de tu lado: ya terminó |
| `403` `Solo el juego puede cerrar la sala` | Usaste el token de la app | Usa el del juego |

```csharp
// No compilado.
IEnumerator CerrarSala()
{
    DetenerWebSocket(); // para que no intente reconectar al recibir el cierre
    yield return Enviar("DELETE", "/api/v1/salas/" + Codigo, null, true, (status, cuerpo) =>
    {
        if (status == 204 || status == 401) BorrarSesion();
        else ManejarError(status, cuerpo);
    });
}
```

## 5. Autenticación

**Dónde guardar el token.** Guarda el token y el código juntos al crear la sala. En PC basta con `PlayerPrefs`. Así, si el juego se cierra por accidente, puede reabrir la misma partida.

```csharp
// No compilado.
void GuardarSesion(string codigo, string token)
{
    PlayerPrefs.SetString("vestigia_codigo", codigo);
    PlayerPrefs.SetString("vestigia_token", token);
    PlayerPrefs.Save();
}

void BorrarSesion()
{
    PlayerPrefs.DeleteKey("vestigia_codigo");
    PlayerPrefs.DeleteKey("vestigia_token");
    PlayerPrefs.Save();
}
```

**Cómo enviarlo.**

- **REST:** en el encabezado `Authorization: Bearer <token>`, con un espacio después de `Bearer`. Ejemplo: `req.SetRequestHeader("Authorization", "Bearer " + token);`.
- **WebSocket:** dentro del primer mensaje, `{"type": "auth", "payload": {"token": "<token>"}}`. **Nunca** en la URL.
- No lo escribas en logs ni en pantalla.

El token sirve solo para su sala y solo mientras la sala esté viva (`esperando_app` o `activa`). Si nadie escribe nada en 2 horas, la sala vence.

**Qué hacer con un `401`.** El token ya no sirve: la sala se cerró, venció o el token está mal. Borra la sesión guardada y vuelve al menú de elegir pueblo. Un `401` puede llegar aunque no haya llegado `sala.cerrada` (por ejemplo, si la sala venció mientras tu WebSocket estaba caído); trátalo igual.

**Qué hacer con un `403`.** El token es válido pero no te deja hacer eso: es de otra sala, es el de la app en una ruta del juego, mandaste una clave que solo crea el servidor, o avisaste un puzzle (`puzzle_solo_app`), que solo avisa la app. Es un error de programación, no de la jugadora: regístralo en el log y no reintentes.

## 6. Estados de un puzzle

```
             requisitos                 iniciado                  completada o sintonizada
bloqueado ─────────────▶ disponible ─────────────▶ en_curso ──────────────────────────────▶ resuelto
                              │                                                                ▲
                              └──────────────────── completada o sintonizada ──────────────────┘
```

Un puzzle solo avanza, nunca regresa. El servidor lo pasa de `bloqueado` a `disponible` cuando se cumplen sus requisitos, sin importar el orden en que lleguen:

| Puzzle | Quién lo juega | Pasa a `disponible` cuando | Al resolverse el servidor crea |
|---|---|---|---|
| `radio` | La app | Existe `pescador_dialogo_completo` | `coordenada_a` = `{"tipo": "ascension_recta", "valor": "137"}` |
| `photo` | La app | Existen `pieza_1`, `pieza_2` y `pieza_3` | `coordenada_b` = `{"tipo": "declinacion", "valor": "+042"}` |
| `lookout` | La app | `photo` está `resuelto` y existe `lookout_enabled` | `observatorio_visible` |
| `observatory` | La app | Existen `coordenada_a` y `coordenada_b`, `lookout` está `resuelto` y existe `observatory_enabled` | `magnetar_localizado` |

| Si el estado es… | El juego hace… |
|---|---|
| `radio` `bloqueado` | Nada. Falta el diálogo del pescador (paso 7) |
| `radio` `disponible` o `en_curso` | Espera: la jugadora la sintoniza en el teléfono. Puedes mostrar "Revisa tu libreta" |
| `radio` `resuelto` | Muestra la coordenada AR si el juego la usa. Pon `radio_sintonizada` en `true` |
| `photo` `bloqueado` | Faltan piezas. Sigue el paso 9 |
| `photo` `disponible` o `en_curso` | Espera: la jugadora la arma en el teléfono. Puedes mostrar "Revisa tu libreta" |
| `photo` `resuelto` | Foto reconstruida. Deja subir a la cima del faro |
| `lookout` `bloqueado` | Si la jugadora ya está en la cima, revisa que mandaste `lookout_enabled` |
| `lookout` `disponible` o `en_curso` | Espera: se juega en el teléfono |
| `lookout` `resuelto` | Abre el camino al observatorio |
| `observatory` `bloqueado` | Si la jugadora ya está en el observatorio, revisa que mandaste `observatory_enabled` |
| `observatory` `disponible` o `en_curso` | Espera: se juega en el teléfono |
| `observatory` `resuelto` | Final: escena `desenlace` y cinemática |

## 7. Tiempo real

El WebSocket sirve para que el juego se entere de lo que hace el teléfono sin preguntar cada segundo. Por el WebSocket el juego **solo manda `auth`**; todo lo demás lo manda por REST.

### 7.1 Conectar

1. Abre `ws://<host>/ws` con `System.Net.WebSockets.ClientWebSocket` (funciona en las compilaciones standalone de PC; en WebGL no existe y haría falta otra biblioteca).
2. En menos de 5 segundos manda `{"type": "auth", "payload": {"token": "<token>"}}`.
3. Espera `auth.ok`. Luego pide `GET estado` (paso 4).

Al conectarte **no** recibes quién más está conectado: léelo en `dispositivos` de `GET estado`.

El servidor manda un ping cada 25 segundos. `ClientWebSocket` lo responde solo; no hay que hacer nada.

### 7.2 Mensajes que recibe el juego

Cada mensaje es un texto JSON `{"type": "...", "payload": {...}}`.

| `type` | Cuándo llega | Qué hacer |
|---|---|---|
| `auth.ok` | Respuesta a tu `auth` | Pide `GET estado`. Reinicia el contador de reintentos |
| `dispositivo.conectado` | El teléfono abrió su WebSocket | Si `rol` es `app`, muestra "Teléfono conectado" |
| `dispositivo.desconectado` | El teléfono perdió su conexión | Muestra un aviso discreto. No detengas la partida |
| `puzzle.actualizado` | Un puzzle cambió de estado | Actualiza ese puzzle y aplica la tabla de la sección 6 |
| `descubrimiento.nuevo` | Se registró un descubrimiento nuevo (también los tuyos) | Reacciona a `coordenada_a`, `coordenada_b`, `observatorio_visible` y `magnetar_localizado` |
| `sala.cerrada` | La sala se cerró (`finalizada`) o expiró (`expirada`) | Borra la sesión y vuelve al menú. No reconectes |
| `error` | Mandaste algo que no es `auth` o un `auth` repetido | Regístralo en el log. La conexión sigue abierta |

El juego **no** recibe `escena.cambiada`: ese mensaje es solo para la app.

Ejemplos reales de cada uno:

<!-- respuesta: ws -->
```json
{"type": "auth.ok", "payload": {"rol": "juego", "sala": "BACALAR"}}
{"type": "dispositivo.conectado", "payload": {"rol": "app"}}
{"type": "dispositivo.desconectado", "payload": {"rol": "app"}}
{"type": "puzzle.actualizado", "payload": {"puzzle": "photo", "estado": "disponible", "datos": {}}}
{"type": "descubrimiento.nuevo", "payload": {"clave": "coordenada_b", "datos": {"tipo": "declinacion", "valor": "+042"}, "origen": "servidor"}}
{"type": "sala.cerrada", "payload": {"estado": "expirada"}}
{"type": "error", "payload": {"codigo": "tipo_desconocido", "message": "El mensaje orientacion está diferido: el servidor no lo recibe por ahora"}}
```

Notas:

- También recibes los mensajes de tus propias peticiones (por ejemplo `descubrimiento.nuevo` de `pieza_1`). Puedes ignorarlos.
- Los mensajes salen **después** de que el cambio se guardó. Si pides `GET estado` al recibir uno, ya verás el cambio.
- Una petición repetida que no cambia nada no manda mensajes.
- Al resolver un puzzle, los mensajes llegan en este orden: `puzzle.actualizado` (`resuelto`), `descubrimiento.nuevo` (el resultado) y un `puzzle.actualizado` (`disponible`) por cada puzzle que se habilita.

### 7.3 Reconexión

Si la conexión se cae, reconecta con el mismo token, esperando cada vez más: 1, 2, 4, 8 y 16 segundos, y desde ahí cada 30 segundos. En cuanto llegue `auth.ok`, pide `GET estado`: los mensajes que se emitieron mientras estabas desconectado **no se repiten**.

Decide según el código de cierre:

| Código | Razón | ¿Reconectar? | Qué hacer |
|---|---|---|---|
| `1000` | `La sala se cerró` | No | Llegó `sala.cerrada`: la partida terminó |
| `4001` | `Token inválido o de una sala que ya terminó` | No | Igual que un `401`: borra la sesión y vuelve al menú |
| `4009` | `Otra conexión del mismo rol tomó su lugar` | No | Otra copia del juego usa la sala. Avisa y no reconectes en automático |
| `4008` | `No llegó auth a tiempo` | Sí | Manda `auth` en cuanto se abra la conexión |
| `4000` | `El primer mensaje debe ser auth` | No | Error de programación: el primer mensaje no fue `auth` |
| `4500` | `El cliente no lee sus mensajes o falló un envío` | Sí | El juego no estaba leyendo. Reconecta y pide `GET estado` |
| `1009` | Mensaje demasiado grande | No | Ningún mensaje válido pasa de 4096 caracteres |
| Otro, o la red se cayó | | Sí | Reconecta con espera creciente |

### 7.4 Banderas de `GameProgress.cs`

> **Nombres tomados del plan; no verificados contra el código de Unity.** `GameProgress.cs` no estaba disponible al escribir esta guía.

| Bandera de Unity | Cómo se relaciona con el backend |
|---|---|
| `radio_sintonizada` | El juego la pone en `true` cuando recibe `puzzle.actualizado` de `radio` en `resuelto` (la jugadora la sintonizó en el teléfono) o al leer `GET estado`. El juego **no** manda nada al backend por esta bandera |
| `photo_quantity` | Cuántas piezas recogió la jugadora. El juego la aumenta y manda `pieza_1`, `pieza_2` y `pieza_3` (paso 9). Al reconectar, cuenta las claves `pieza_*` en `descubrimientos` |
| `lente_limpio` | **Decisión pendiente de Unity (C2).** El backend no la manda: no hay avisos intermedios del mirador, solo `lookout` resuelto |
| `obs_encontrado` | **Decisión pendiente de Unity (C2).** Igual que `lente_limpio` |

Opciones para `lente_limpio` y `obs_encontrado`, para que Unity y Raúl elijan una. Ninguna está decidida:

| Opción | Cómo funcionaría | Qué cambia |
|---|---|---|
| A. No usarlas | El juego solo reacciona a `lookout` resuelto y a `observatorio_visible` | Nada en el backend |
| B. Ponerlas juntas al final | Al recibir `lookout` en `resuelto`, el juego pone las dos en `true` | Nada en el backend. El juego no ve los pasos intermedios |
| C. Que la app las registre | La app manda `POST /descubrimientos` con `lente_limpio` y `obs_encontrado` en cada paso del mirador, y el juego reacciona a `descubrimiento.nuevo` | Hay que acordarlo con la app y con Raúl: el backend acepta esas claves de la app (no son del juego ni del servidor), pero nadie lo ha decidido |

## 8. Tabla de errores

Todos los errores REST traen el mismo cuerpo JSON. Lee `status` y `codigo` para decidir; `message` está en español y sirve para el log.

<!-- respuesta: error -->
```json
{
  "timestamp": "2026-11-06T15:45:31.934995Z",
  "status": 401,
  "error": "Unauthorized",
  "codigo": null,
  "message": "Token inválido o de una sala que ya terminó",
  "path": "/api/v1/salas/BACALAR/estado",
  "details": []
}
```

`codigo` **siempre viene**: vale `null` salvo en `puzzle_bloqueado`, `clave_solo_juego` y `puzzle_solo_app`. `details` solo trae elementos en errores de validación (`[{"field": "clave", "message": "..."}]`).

| HTTP | `codigo` | Cuándo | Qué hacer |
|---|---|---|---|
| `400` | `null` | JSON mal escrito, clave repetida, un campo con otro tipo (`"true"` en vez de `true`), campo vacío, pueblo fuera del catálogo, escena o id de puzzle desconocido, aviso de puzzle inválido, `datos` de más de 4096 bytes | Error de programación: corrige la petición. No reintentes igual |
| `401` | `null` | Sin token, token inválido, sala cerrada o vencida | Borra la sesión y vuelve al menú de elegir pueblo |
| `403` | `null` | Token de otra sala, token de la app en una ruta del juego, o clave que crea el servidor | Error de programación. Regístralo y no reintentes |
| `403` | `clave_solo_juego` | Una clave del juego (`pieza_*`, `pescador_dialogo_completo`, `lookout_enabled`, `observatory_enabled`) llegó con el token de la app | El juego no lo ve con su propio token. Si aparece, se cruzaron los tokens: revisa que mandas `$TOKEN_JUEGO` |
| `404` | `null` | Ruta mal escrita (por ejemplo con `/` al final) | Revisa la URL |
| `409` | `null` | Al crear la sala: el pueblo ya está ocupado | Vuelve a pedir los pueblos libres y deja elegir otro |
| `409` | `puzzle_bloqueado` | Reportaste un puzzle que todavía está `bloqueado` | Revisa que mandaste antes sus requisitos (sección 6) |
| `500` | `null` | Error interno del servidor | Reintenta una vez más tarde. Si sigue, avisa a Raúl |
| Sin respuesta | | Sin red o servidor apagado | Muestra "Sin conexión" y reintenta con espera |

## 9. Si quiero X, hago Y

| Si quiero… | Hago… |
|---|---|
| Saber si el teléfono está conectado | Leo `dispositivos.app` de `GET estado` y escucho `dispositivo.conectado` y `dispositivo.desconectado` |
| Saber qué coordenadas hay | Busco `coordenada_a` y `coordenada_b` en `descubrimientos` de `GET estado`, y leo `datos.valor` (texto) |
| Contar las piezas recogidas | Cuento las claves `pieza_1`, `pieza_2` y `pieza_3` en `descubrimientos` |
| Reanudar una partida después de cerrar el juego | Leo el token guardado, pido `GET estado` y, si responde `200`, conecto el WebSocket y aplico el estado. Si da `401`, borro la sesión |
| Reintentar una petición que falló por red | La repito igual: todas las escrituras se pueden repetir sin duplicar nada |
| Empezar otra partida con el mismo pueblo | Cierro la sala (paso 15) y la vuelvo a crear (paso 2) |
| Saber cuánto le queda a la sala | Leo `expira_en` de `GET estado`. Cada escritura la renueva 2 horas |
| Ver los mensajes del WebSocket sin Unity | Abro `$BASE/prueba-ws.html`, pego el token del juego y pulso Conectar |
| Probar una ruta a mano | Uso `curl` como en la sección 4, o Swagger en `$BASE/swagger-ui/index.html` |

## 10. Cliente mínimo completo

Un solo `MonoBehaviour` con todo lo de esta guía: REST con `UnityWebRequest`, WebSocket con `ClientWebSocket`, reconexión con espera creciente y eventos para que el resto del juego reaccione. Ponlo en un objeto que no se destruya entre escenas.

> **No compilado.** Escrito para Unity 2021 o posterior (C# 9) con compilación standalone. No se compiló en un proyecto de Unity.

```csharp
using System;
using System.Collections;
using System.Collections.Concurrent;
using System.IO;
using System.Net.WebSockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using UnityEngine;
using UnityEngine.Networking;

[Serializable] public class Pueblo { public string codigo; public bool libre; }
[Serializable] public class ListaPueblos { public Pueblo[] items; }
[Serializable] public class SalaCreada { public string codigo; public string estado; public string token; }
[Serializable] public class Datos { public bool sintonizada; public bool iniciado; public bool completada; public string tipo; public string valor; }
[Serializable] public class PuzzleEstado { public string puzzle; public string estado; public Datos datos; }
[Serializable] public class Descubrimiento { public string clave; public Datos datos; public string origen; public string creado_en; }
[Serializable] public class Dispositivos { public bool juego; public bool app; }
[Serializable] public class EstadoSala
{
    public string codigo; public string estado; public string escena_actual;
    public PuzzleEstado[] puzzles; public Descubrimiento[] descubrimientos; public Dispositivos dispositivos;
    public string creada_en; public string expira_en;
}
[Serializable] public class ErrorApi { public int status; public string error; public string codigo; public string message; public string path; }
[Serializable] public class PayloadWs
{
    public string rol; public string sala; public string puzzle; public string estado; public Datos datos;
    public string clave; public string origen; public string codigo; public string message;
}
[Serializable] public class MensajeWs { public string type; public PayloadWs payload; }

public class VestigiaCliente : MonoBehaviour
{
    [SerializeField] string baseUrl = "http://localhost:8080";

    // Acepta "localhost:8080", "http://localhost:8080/" o "https://…": siempre con esquema y sin "/" final.
    string Base
    {
        get
        {
            string b = baseUrl.Trim().TrimEnd('/');
            return b.StartsWith("http://") || b.StartsWith("https://") ? b : "http://" + b;
        }
    }

    // http → ws y https → wss.
    string WsUrl => (Base.StartsWith("https://") ? "wss://" + Base.Substring("https://".Length)
                                                  : "ws://" + Base.Substring("http://".Length)) + "/ws";

    public string Codigo => PlayerPrefs.GetString("vestigia_codigo", "");
    string Token => PlayerPrefs.GetString("vestigia_token", "");

    // El resto del juego se suscribe a estos eventos. Siempre se llaman en el hilo principal.
    public event Action<EstadoSala> EstadoRecibido;
    public event Action<bool> AppConectada;
    public event Action<PuzzleEstado> PuzzleActualizado;
    public event Action<Descubrimiento> DescubrimientoNuevo;
    public event Action SalaTerminada;

    readonly ConcurrentQueue<Action> enHiloPrincipal = new ConcurrentQueue<Action>();
    ClientWebSocket ws;
    CancellationTokenSource cancelar;
    bool reconectar;
    int intentos;

    void Update()
    {
        while (enHiloPrincipal.TryDequeue(out var accion)) accion();
    }

    void OnDestroy() => DetenerWebSocket();

    // ---------- REST ----------

    public IEnumerator Enviar(string metodo, string ruta, string cuerpoJson, bool conToken, Action<long, string> listo)
    {
        using var req = new UnityWebRequest(Base + ruta, metodo);
        req.downloadHandler = new DownloadHandlerBuffer();
        if (cuerpoJson != null)
        {
            req.uploadHandler = new UploadHandlerRaw(Encoding.UTF8.GetBytes(cuerpoJson));
            req.SetRequestHeader("Content-Type", "application/json");
        }
        if (conToken) req.SetRequestHeader("Authorization", "Bearer " + Token);
        req.timeout = 10;
        yield return req.SendWebRequest();
        if (req.result == UnityWebRequest.Result.ConnectionError || req.result == UnityWebRequest.Result.DataProcessingError)
        {
            listo(0, null); // sin red: 0
            yield break;
        }
        listo(req.responseCode, req.downloadHandler.text);
    }

    public IEnumerator PedirPueblos(Action<Pueblo[]> listo) =>
        Enviar("GET", "/api/v1/pueblos?disponibles=true", null, false, (status, cuerpo) =>
        {
            if (status == 200) listo(JsonUtility.FromJson<ListaPueblos>("{\"items\":" + cuerpo + "}").items);
            else ManejarError(status, cuerpo);
        });

    public IEnumerator CrearSala(string codigo, Action<SalaCreada> creada, Action puebloOcupado) =>
        Enviar("POST", "/api/v1/salas", "{\"codigo\":\"" + codigo + "\"}", false, (status, cuerpo) =>
        {
            if (status == 201)
            {
                var sala = JsonUtility.FromJson<SalaCreada>(cuerpo);
                PlayerPrefs.SetString("vestigia_codigo", sala.codigo);
                PlayerPrefs.SetString("vestigia_token", sala.token);
                PlayerPrefs.Save();
                creada(sala);
                ConectarWebSocket();
            }
            else if (status == 409) puebloOcupado();
            else ManejarError(status, cuerpo);
        });

    public IEnumerator PedirEstado() =>
        Enviar("GET", "/api/v1/salas/" + Codigo + "/estado", null, true, (status, cuerpo) =>
        {
            if (status == 200) EstadoRecibido?.Invoke(JsonUtility.FromJson<EstadoSala>(cuerpo));
            else ManejarError(status, cuerpo);
        });

    public IEnumerator CambiarEscena(string escena) =>
        Enviar("PUT", "/api/v1/salas/" + Codigo + "/escena", "{\"escena\":\"" + escena + "\"}", true, SoloErrores);

    public IEnumerator RegistrarDescubrimiento(string clave) =>
        Enviar("POST", "/api/v1/salas/" + Codigo + "/descubrimientos", "{\"clave\":\"" + clave + "\"}", true, SoloErrores);

    public IEnumerator CerrarSala()
    {
        DetenerWebSocket();
        yield return Enviar("DELETE", "/api/v1/salas/" + Codigo, null, true, (status, cuerpo) =>
        {
            if (status == 204 || status == 401) Terminar();
            else ManejarError(status, cuerpo);
        });
    }

    void SoloErrores(long status, string cuerpo)
    {
        if (status < 200 || status >= 300) ManejarError(status, cuerpo);
    }

    void ManejarError(long status, string cuerpo)
    {
        if (status == 0) { Debug.LogWarning("[Vestigia] Sin conexión con el servidor"); return; }
        var error = string.IsNullOrEmpty(cuerpo) ? null : JsonUtility.FromJson<ErrorApi>(cuerpo);
        if (status == 401) { Terminar(); return; } // la sala terminó o venció
        if (status == 409 && error?.codigo == "puzzle_bloqueado")
        {
            Debug.LogWarning("[Vestigia] Puzzle bloqueado: falta un requisito. " + error.message);
            return;
        }
        Debug.LogError("[Vestigia] " + status + " " + error?.message);
    }

    void Terminar()
    {
        DetenerWebSocket();
        PlayerPrefs.DeleteKey("vestigia_codigo");
        PlayerPrefs.DeleteKey("vestigia_token");
        PlayerPrefs.Save();
        SalaTerminada?.Invoke();
    }

    // ---------- WebSocket ----------

    public async void ConectarWebSocket()
    {
        DetenerWebSocket();
        reconectar = true;
        intentos = 0;
        var miCancelacion = cancelar = new CancellationTokenSource();
        while (reconectar && !miCancelacion.IsCancellationRequested)
        {
            int cierre = -1;
            var socket = ws = new ClientWebSocket();
            try
            {
                await socket.ConnectAsync(new Uri(WsUrl), miCancelacion.Token);
                string auth = "{\"type\":\"auth\",\"payload\":{\"token\":\"" + Token + "\"}}";
                await socket.SendAsync(new ArraySegment<byte>(Encoding.UTF8.GetBytes(auth)), WebSocketMessageType.Text, true, miCancelacion.Token);
                cierre = await LeerHastaCerrar(socket, miCancelacion.Token);
            }
            catch (Exception e) when (!(e is OperationCanceledException))
            {
                Debug.LogWarning("[Vestigia] WebSocket: " + e.Message);
            }
            catch (OperationCanceledException) { break; }
            finally { socket.Dispose(); }

            if (cierre == 1000 || cierre == 4001 || cierre == 4009 || cierre == 4000 || cierre == 1009)
            {
                reconectar = false;
                if (cierre == 4001) enHiloPrincipal.Enqueue(Terminar);
                break;
            }
            int segundos = Math.Min(30, 1 << Math.Min(intentos, 5)); // 1, 2, 4, 8, 16, 30...
            intentos++;
            try { await Task.Delay(segundos * 1000, miCancelacion.Token); } catch (OperationCanceledException) { break; }
        }
    }

    public void DetenerWebSocket()
    {
        reconectar = false;
        cancelar?.Cancel();
        cancelar = null;
    }

    async Task<int> LeerHastaCerrar(ClientWebSocket socket, CancellationToken token)
    {
        var buffer = new byte[8192];
        using var mensaje = new MemoryStream();
        while (socket.State == WebSocketState.Open)
        {
            WebSocketReceiveResult r = await socket.ReceiveAsync(new ArraySegment<byte>(buffer), token);
            if (r.MessageType == WebSocketMessageType.Close)
            {
                int codigo = (int)(socket.CloseStatus ?? WebSocketCloseStatus.Empty);
                try { await socket.CloseOutputAsync(WebSocketCloseStatus.NormalClosure, "", CancellationToken.None); } catch { }
                return codigo;
            }
            mensaje.Write(buffer, 0, r.Count);
            if (r.EndOfMessage)
            {
                string json = Encoding.UTF8.GetString(mensaje.ToArray());
                mensaje.SetLength(0);
                enHiloPrincipal.Enqueue(() => AlRecibir(json));
            }
        }
        return -1;
    }

    void AlRecibir(string json)
    {
        var m = JsonUtility.FromJson<MensajeWs>(json);
        switch (m.type)
        {
            case "auth.ok":
                intentos = 0;
                StartCoroutine(PedirEstado()); // siempre después de conectar o reconectar
                break;
            case "dispositivo.conectado":
                if (m.payload.rol == "app") AppConectada?.Invoke(true);
                break;
            case "dispositivo.desconectado":
                if (m.payload.rol == "app") AppConectada?.Invoke(false);
                break;
            case "puzzle.actualizado":
                PuzzleActualizado?.Invoke(new PuzzleEstado { puzzle = m.payload.puzzle, estado = m.payload.estado, datos = m.payload.datos });
                break;
            case "descubrimiento.nuevo":
                DescubrimientoNuevo?.Invoke(new Descubrimiento { clave = m.payload.clave, datos = m.payload.datos, origen = m.payload.origen });
                break;
            case "sala.cerrada":
                Terminar();
                break;
            case "error":
                Debug.LogWarning("[Vestigia] WebSocket error " + m.payload.codigo + ": " + m.payload.message);
                break;
        }
    }
}
```

Cómo se usa desde el resto del juego (no compilado):

```csharp
cliente.DescubrimientoNuevo += d =>
{
    if (d.clave == "observatorio_visible") AbrirCaminoAlObservatorio();
    if (d.clave == "magnetar_localizado") StartCoroutine(IrAlDesenlace());
};
StartCoroutine(EntrarAlFaro());

// Dos StartCoroutine seguidos corren en paralelo y sus peticiones pueden llegar en cualquier orden. Para mandarlas en
// orden (primero la escena, luego la pieza, como en los pasos 6 y 9), encadénalas con yield return:
IEnumerator EntrarAlFaro()
{
    yield return cliente.CambiarEscena("faro");
    yield return cliente.RegistrarDescubrimiento("pieza_1");
}
```

El orden entre la escena y las piezas no es obligatorio para el backend: las piezas habilitan `photo` en cualquier escena. Encadenarlas solo hace que la libreta muestre "Estás en el Faro" antes de "Piezas de foto encontradas 1/3".

## 11. Checklist de "ya terminé"

- [ ] El menú muestra los pueblos de `GET /pueblos?disponibles=true` y ante un `409` al crear la sala vuelve a pedirlos.
- [ ] Al crear la sala guardo el token y el código, y muestro el código en pantalla.
- [ ] Abro el WebSocket, mando `auth` en menos de 5 s y nunca pongo el token en la URL.
- [ ] Después de cada `auth.ok` pido `GET estado` y aplico lo que trae.
- [ ] Muestro cuándo el teléfono se conecta y se desconecta.
- [ ] Mando `PUT escena` con `pueblo`, `faro`, `observatorio` y `desenlace` en el momento de cada cambio.
- [ ] Mando `pescador_dialogo_completo` al terminar el diálogo con el pescador.
- [ ] No mando `PUT` de ningún puzzle (los cuatro los avisa la app): escucho `puzzle.actualizado` de `radio` y pongo `radio_sintonizada` cuando llega `resuelto`.
- [ ] Mando `pieza_1`, `pieza_2` y `pieza_3`, una por cada pieza recogida.
- [ ] Mando `lookout_enabled` al llegar a la cima del faro y `observatory_enabled` al llegar al observatorio.
- [ ] Reacciono a `radio`, `photo`, `lookout` y `observatory` en `resuelto` y a `magnetar_localizado`.
- [ ] Leo las coordenadas como texto desde los descubrimientos; no las escribo en el código.
- [ ] Reconecto con espera creciente y no reconecto con `1000`, `4001` ni `4009`.
- [ ] Un `401` (o `sala.cerrada`) borra la sesión y vuelve al menú.
- [ ] Al terminar mando `DELETE /salas/{codigo}`.
- [ ] El token no aparece en logs ni en pantalla.
- [ ] Probé el recorrido completo con `curl` haciendo de app, o con la app real.
- [ ] Acordé con Raúl qué hacer con `lente_limpio` y `obs_encontrado` (sección 7.4).

## 12. Errores comunes

| Síntoma | Causa | Solución |
|---|---|---|
| `403` `puzzle_solo_app` | El juego mandó un `PUT` de puzzle | No lo mandes: los cuatro puzzles los avisa la app. Escucha `puzzle.actualizado` por el WebSocket |
| `400 JSON malformado o ilegible` | Comillas sin escapar en el string de C#, `"true"` en vez de `true`, o una clave repetida | Revisa el JSON exacto que envías con `Debug.Log` (sin el token) |
| `400` `no es una escena válida` | Escena con mayúscula o acento (`"Faro"`, `"introducción"`) | Usa los valores de la tabla del paso 6 |
| `400` `Datos inválidos` en una clave | `pieza-1`, `Pieza_1` o `pieza 1` | Solo minúsculas, dígitos y guion bajo: `pieza_1` |
| `401` en todo de repente | La sala venció (2 horas sin escrituras) o se cerró | Vuelve al menú y crea otra sala |
| `403` `El token no pertenece a esta sala` | La ruta usa otro código que el del token | Usa el código que guardaste junto al token |
| `404` con `No static resource` | La ruta termina en `/` o tiene un error de dedo | Quita la `/` final y copia la ruta de esta guía |
| El WebSocket se cierra a los 5 s con `4008` | No mandaste `auth` a tiempo | Manda `auth` justo después de conectar |
| El WebSocket se cierra con `4009` | Hay dos copias del juego (por ejemplo el editor y una compilación) con el mismo token | Cierra una. No reconectes en automático |
| No llega `dispositivo.conectado` aunque la app ya está | La app se conectó antes que el juego | Lee `dispositivos.app` en `GET estado` |
| Se pierde un mensaje tras una caída | Los mensajes no se repiten | Pide `GET estado` después de cada `auth.ok` |
| El mirador no se habilita | Falta `lookout_enabled` o `photo` todavía no está `resuelto` | Revisa `GET estado` |
| El observatorio no se habilita | Falta `observatory_enabled`, una coordenada o `lookout` resuelto | Revisa `GET estado` |
| `ClientWebSocket` no funciona en WebGL | WebGL no tiene sockets de .NET | Usa una biblioteca de WebSocket para WebGL o compila para PC |
