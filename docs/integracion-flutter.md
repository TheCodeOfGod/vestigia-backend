# Guía de integración: la app móvil en Flutter

Esta guía es para quien programa **la app móvil en Flutter (Dart)**, la libreta de Janeth. Explica, paso a paso y en orden, cada cosa que la app le pide o le avisa al backend de Vestigia. No necesitas abrir otras guías ni el código del backend: todo lo que la app usa está aquí.

> **Antes de copiar cualquier comando:** todos usan la variable `BASE` con la dirección del backend. Escribe primero `export BASE=http://localhost:8080` en tu terminal. Si el backend corre en otro puerto (por ejemplo `18081`), usa ese número: `export BASE=http://localhost:18081`. Ver la sección 2.1.

Cada respuesta de ejemplo salió de correr el backend real. Los tokens están recortados (`MdKqZU8pVUJn…`). Los fragmentos de Dart están marcados como **no compilados**: se escribieron para Flutter 3 con Dart 3, pero no se compilaron en un proyecto de Flutter.

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

La app es la libreta de Janeth. La jugadora elige un pueblo en el juego de PC; ese nombre es el **código de sala**, y lo escribe en la app para enlazar el teléfono con la partida.

Desde ahí, la app muestra en la **libreta** lo que pasa en el juego: la escena, las piezas encontradas, las coordenadas, los puzzles resueltos. Cuando un puzzle del teléfono se habilita, aparece una **burbuja que pulsa**; al tocarla se abre el puzzle. La app juega tres puzzles: la fotografía (`photo`), el mirador (`lookout`) y el observatorio (`observatory`, con el giroscopio). Al terminar cada uno le avisa al backend, y el juego de PC reacciona. La radio la juega el juego: la app solo muestra su estado.

```
 Inicio ─▶ Código de sala ─▶ LIBRETA ─────────────────────▶ burbuja que pulsa ─▶ Puzzle ─────────────────▶ LIBRETA ─▶ … ─▶ Fin
           POST unirse        GET estado + WebSocket           puzzle.actualizado    PUT {iniciado: true}       coordenada,        sala.cerrada
           (token)            escena, piezas, coordenadas      con "disponible"      PUT {completada: true}     resultado
```

## 2. Antes de empezar

### 2.1 URL base

| Entorno | URL base |
|---|---|
| Local | `http://localhost:8080` en la computadora que corre el backend (o el puerto que use quien lo levantó) |
| Producción | Por definir. Raúl la entrega cuando el backend esté desplegado |

Todas las rutas REST empiezan con `/api/v1`. El WebSocket está en `/ws`, en el mismo servidor: `ws://…/ws` en local y `wss://…/ws` en producción.

Desde el teléfono, `localhost` **no** es tu computadora:

| Dónde corre la app | URL base en local |
|---|---|
| Emulador de Android | `http://10.0.2.2:8080` |
| Simulador de iOS | `http://localhost:8080` |
| Teléfono físico en la misma red Wi-Fi | `http://<IP de tu computadora>:8080`, por ejemplo `http://192.168.1.50:8080` |

En local el backend usa `http`, sin cifrar. Android lo bloquea salvo que agregues `android:usesCleartextTraffic="true"` en `<application>` de `AndroidManifest.xml` (solo para desarrollo), e iOS salvo que permitas cargas locales en `Info.plist` (`NSAppTransportSecurity` → `NSAllowsLocalNetworking`). En producción será `https` y no hará falta.

En los comandos de esta guía la URL base está en la variable `BASE`. Defínela una vez en tu terminal y, si tu backend corre en otro puerto, cambia solo el número de esta línea:

```sh
export BASE=http://localhost:8080
```

Sin `/` al final. Si abres otra terminal, vuelve a escribir la línea: la variable vive solo en la terminal donde la definiste. `BASE` es para los comandos de tu computadora; la app usa la URL de la tabla de arriba según dónde corra.

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

### 2.3 Probar sin Flutter y sin el juego

La app no puede hacer nada hasta que el juego crea la sala. Sin el juego, haz de juego con `curl`.

**Crear la sala como si fueras el juego.** En esta guía el pueblo es `TAPALPA`:

```sh
curl -X POST $BASE/api/v1/salas -H 'Content-Type: application/json' -d '{"codigo": "TAPALPA"}'
```

Respuesta `201 Created`:

<!-- respuesta: sala-creada -->
```json
{"codigo": "TAPALPA", "estado": "esperando_app", "token": "E6waKVGy5-g5…"}
```

Copia el valor de `token` de la respuesta y guárdalo así (no lo confundas con el de la app):

```sh
TOKEN_JUEGO=pega-aqui-el-token-del-juego
```

Si responde `409`, el pueblo ya tiene una sala de una prueba anterior. Ciérrala con `curl -X DELETE $BASE/api/v1/salas/TAPALPA -H "Authorization: Bearer $TOKEN_JUEGO"` si tienes su token, o usa otro pueblo de `curl "$BASE/api/v1/pueblos?disponibles=true"` y cámbialo en todos los comandos.

**Avanzar la partida como si fueras el juego.** Corre cada bloque en su momento, no todos juntos:

1. Para habilitar la fotografía (`photo`), las tres piezas:

	```sh
	for p in pieza_1 pieza_2 pieza_3; do
		curl -X POST $BASE/api/v1/salas/TAPALPA/descubrimientos \
			-H "Authorization: Bearer $TOKEN_JUEGO" -H 'Content-Type: application/json' -d "{\"clave\": \"$p\"}"
	done
	```

2. Para habilitar el mirador (`lookout`), **después** de que la app resuelva `photo`: la jugadora llegó a la cima del faro.

	```sh
	curl -X POST $BASE/api/v1/salas/TAPALPA/descubrimientos \
		-H "Authorization: Bearer $TOKEN_JUEGO" -H 'Content-Type: application/json' -d '{"clave": "lookout_enabled"}'
	```

3. Para habilitar el observatorio (`observatory`), **después** de que la app resuelva `lookout`: el pescador, la radio (que crea `coordenada_a`) y la llegada al observatorio.

	```sh
	curl -X POST $BASE/api/v1/salas/TAPALPA/descubrimientos \
		-H "Authorization: Bearer $TOKEN_JUEGO" -H 'Content-Type: application/json' -d '{"clave": "pescador_dialogo_completo"}'
	curl -X PUT $BASE/api/v1/salas/TAPALPA/puzzles/radio \
		-H "Authorization: Bearer $TOKEN_JUEGO" -H 'Content-Type: application/json' -d '{"datos": {"sintonizada": true}}'
	curl -X POST $BASE/api/v1/salas/TAPALPA/descubrimientos \
		-H "Authorization: Bearer $TOKEN_JUEGO" -H 'Content-Type: application/json' -d '{"clave": "observatory_enabled"}'
	```

4. Para cambiar la escena en cualquier momento (la app recibe `escena.cambiada`). Valores: `pueblo`, `faro`, `observatorio` o `desenlace`.

	```sh
	curl -X PUT $BASE/api/v1/salas/TAPALPA/escena \
		-H "Authorization: Bearer $TOKEN_JUEGO" -H 'Content-Type: application/json' -d '{"escena": "faro"}'
	```

	Respuesta `200 OK`:

	<!-- respuesta: escena -->
	```json
	{"escena_actual": "faro"}
	```

5. Para terminar la partida, al final de todo (la app recibe `sala.cerrada` y el pueblo queda libre otra vez):

	```sh
	curl -X DELETE $BASE/api/v1/salas/TAPALPA -H "Authorization: Bearer $TOKEN_JUEGO"
	```

	Respuesta `204 No Content`, **sin cuerpo**: `curl` no imprime nada. Para ver el código, agrega `-w '%{http_code}\n'` al comando. Después de esto, los dos tokens responden `401`.

**Hacer de app con `curl`.** Los comandos de la sección 4 son los que manda la app. Guarda su token igual: `TOKEN_APP=pega-aqui-el-token-de-la-app`.

**Con la página de prueba.** Abre `$BASE/prueba-ws.html` en el navegador de tu computadora (en local, `http://localhost:8080/prueba-ws.html`). Tiene un panel para el juego y otro para la app. En cada uno pegas el token, pulsas **Conectar** y ves cada mensaje del WebSocket que llega. Sirve para ver qué recibiría la app mientras mandas peticiones con `curl`.

**Con Swagger.** `$BASE/swagger-ui/index.html` lista cada ruta con sus ejemplos. Para las rutas con candado pulsa **Authorize** y pega el token, sin la palabra `Bearer`.

### 2.4 Glosario

| Palabra | Qué es |
|---|---|
| Sala | Una partida. Junta un juego y, como máximo, una app. Vive en el backend |
| Código de sala | El nombre del pueblo que eligió la jugadora en el juego, en mayúsculas, sin acentos ni ñ y de una sola palabra: `TAPALPA`. La jugadora lo lee en la PC y lo escribe en la app |
| Token | Una contraseña larga y aleatoria que el backend le da a la app **una sola vez** al unirse. Prueba que eres la app de esa sala. Se manda en cada petición |
| Rol | Quién eres para el backend: `juego` o `app`. Lo decide el token |
| Puzzle | Uno de los cuatro retos: `radio`, `photo` (fotografía), `lookout` (mirador) y `observatory` (observatorio). Los ids van en inglés |
| Estado | En qué punto está un puzzle: `bloqueado`, `disponible`, `en_curso` o `resuelto`. La sala también tiene estado: `esperando_app`, `activa`, `finalizada` o `expirada` |
| Descubrimiento | Algo que pasó y queda anotado en la libreta, con una clave (`pieza_1`, `coordenada_a`) y datos opcionales. El pescador, las piezas y las banderas los registra solo el juego; los resultados de los puzzles, el servidor. La app no los registra |
| Bandera | Un descubrimiento que el juego registra para avisar que la jugadora llegó a un lugar: `lookout_enabled` (cima del faro) y `observatory_enabled` (observatorio). La app no las manda |

### 2.5 Lo que todavía no es definitivo

Estas reglas ya funcionan así, pero Raúl todavía puede cambiarlas. Si cambian, esta guía se actualiza.

| # | Tema | Cómo funciona hoy |
|---|---|---|
| C4 | Dónde guardar el token | Esta guía recomienda almacenamiento seguro (sección 5). Falta que el equipo de la app lo confirme |
| C5 | Valores de la app | Los umbrales del mirador y del observatorio (sección 4, paso 8) son del prototipo |
| ✅ | Segunda conexión de la app | Si la app abre otro WebSocket con el mismo token, el nuevo reemplaza al viejo y el viejo se cierra con `4009` |
| ✅ | Códigos de cierre del WebSocket | `4000`, `4001`, `4008`, `4009`, `4500`, `1000` y `1009`, explicados en la sección 7 |
| 🟡 | `codigo` en los errores | Siempre viene en el cuerpo de error; vale `null` salvo en `puzzle_bloqueado` y `clave_solo_juego` |
| 🟡 | Id de puzzle desconocido | Responde `400`, no `404` |

## 3. Qué sí y qué no hace tu aplicación

**Lo que la app sí usa:**

| Qué | Petición o mensaje | Cuándo |
|---|---|---|
| Unirse a la sala | `POST /api/v1/salas/{codigo}/unirse` | La jugadora escribe el código |
| Ver el estado | `GET /api/v1/salas/{codigo}/estado` | Al abrir la libreta, tras cada reconexión y cuando dudes |
| Avisar que abrió un puzzle | `PUT /api/v1/salas/{codigo}/puzzles/{puzzle}` con `{"datos": {"iniciado": true}}` | Al abrir `photo`, `lookout` u `observatory` |
| Avisar que completó un puzzle | `PUT /api/v1/salas/{codigo}/puzzles/{puzzle}` con `{"datos": {"completada": true}}` | Al completar `photo`, `lookout` u `observatory` |
| Tiempo real | WebSocket `/ws`: manda `auth`; recibe `auth.ok`, `dispositivo.conectado`, `dispositivo.desconectado`, `puzzle.actualizado`, `descubrimiento.nuevo`, `escena.cambiada`, `sala.cerrada` y `error` | Desde que se une hasta que la sala se cierra |

**Lo que la app no debe usar:**

| Qué | Qué pasa si lo usas |
|---|---|
| `PUT /api/v1/salas/{codigo}/escena` | `403` `Solo el juego puede cambiar la escena` |
| `DELETE /api/v1/salas/{codigo}` | `403` `Solo el juego puede cerrar la sala` |
| `POST /api/v1/salas` | **No da error**: crearía una sala nueva y ocuparía un pueblo. Es solo del juego |
| `PUT /api/v1/salas/{codigo}/puzzles/radio` | **No da error**: el backend no revisa qué rol avisa cada puzzle. Resolvería la radio, que juega el juego |
| `POST /api/v1/salas/{codigo}/descubrimientos` con `pieza_1`, `pieza_2`, `pieza_3`, `pescador_dialogo_completo`, `lookout_enabled` u `observatory_enabled` | `403` con `"codigo": "clave_solo_juego"`: esas claves las manda solo el juego. La app no registra descubrimientos; avisa sus puzzles con `PUT puzzles` |
| `POST /api/v1/salas/{codigo}/descubrimientos` con `coordenada_a`, `coordenada_b`, `observatorio_visible` o `magnetar_localizado` | `403`: esas claves las crea el servidor |
| Cualquier ruta con el token de otra sala | `403` `El token no pertenece a esta sala` |
| Cualquier ruta con candado sin token | `401` `Falta el token: envía el encabezado Authorization: Bearer <token>` |
| Mandar la orientación del giroscopio (por WebSocket o REST) | El backend no la recibe. Por WebSocket, `orientacion` responde `error` con `tipo_desconocido` |

## 4. Recorrido paso a paso

Los pasos van en el orden en que la jugadora usa la app. En todos, `TAPALPA` es el pueblo que eligió la jugadora y `$TOKEN_APP` es el token que recibiste en el paso 2.

Reglas que aplican a todas las peticiones:

- El cuerpo va en JSON, con el encabezado `Content-Type: application/json`.
- Las rutas con candado llevan `Authorization: Bearer <token>`.
- Los nombres de los campos van en minúsculas con guion bajo (`escena_actual`). Las fechas vienen en UTC (`2026-11-06T15:54:55.102864Z`).
- Cada campo con su tipo: `true` es booleano, `"true"` es texto y da `400`. No repitas claves en un mismo objeto.
- Lee el cuerpo como UTF-8 (`utf8.decode(respuesta.bodyBytes)`): los mensajes traen acentos y el backend no declara el `charset`.

### Paso 1. Pantalla de inicio

**Cuándo:** al abrir la app.

No llama al backend. Muestra un botón "Unirme a una partida" que lleva a la pantalla del código.

Si la app ya tiene un token guardado de una partida anterior (sección 5), intenta recuperarla: pide el estado (paso 4). Si responde `200`, ve directo a la libreta; si responde `401`, borra el token y quédate en el inicio.

### Paso 2. Escribir el código de sala y unirse

**Cuándo:** la jugadora escribe el pueblo que ve en la pantalla de la PC y pulsa "Unirme".

Antes de mandarlo, **normaliza** el texto: quita todos los espacios y pásalo a mayúsculas. `" tapalpa "`, `"Tapalpa"` y `"TAPALPA"` son la misma sala. Si después de normalizar queda vacío, no mandes nada y pide que escriba el código.

```sh
curl -X POST $BASE/api/v1/salas/TAPALPA/unirse
```

Sin token y sin cuerpo. Respuesta `200 OK`:

<!-- respuesta: app-unida -->
```json
{"token": "MdKqZU8pVUJn…"}
```

**Qué hacer:**

1. Guarda `token` y el código en almacenamiento seguro (sección 5). **Es la única vez que el backend lo entrega.**
2. La sala pasa a `activa`.
3. Conecta el WebSocket (paso 3) y abre la libreta (paso 4).

**Errores:**

| Respuesta | Cuándo | Qué mostrar |
|---|---|---|
| `404` `No hay una sala en curso con el código 'TULUM'` | El código está mal escrito, la partida ya terminó o todavía no se crea | "No encontramos una partida con ese código. Revisa el nombre que aparece en la pantalla de la PC." |
| `409` `La sala 'TAPALPA' ya tiene una app enlazada` | Otro teléfono ya se unió a esa partida | "Esa partida ya tiene un teléfono conectado." |
| Sin respuesta | Sin red o servidor apagado | "No hay conexión. Revisa tu internet e inténtalo de nuevo." |

Cuerpo real del `404`:

<!-- respuesta: error -->
```json
{
  "timestamp": "2026-11-06T15:54:55.203194Z",
  "status": 404,
  "error": "Not Found",
  "codigo": null,
  "message": "No hay una sala en curso con el código 'TULUM'",
  "path": "/api/v1/salas/TULUM/unirse",
  "details": []
}
```

```dart
// No compilado. `cliente` es el VestigiaCliente de la sección 10.
Future<void> alPulsarUnirme(BuildContext context, String texto) async {
  final codigo = VestigiaCliente.normalizar(texto); // sin espacios y en mayúsculas
  if (codigo.isEmpty) {
    mostrarAviso(context, 'Escribe el nombre del pueblo que ves en la PC.');
    return;
  }
  try {
    await cliente.unirse(codigo);
    Navigator.of(context).pushReplacementNamed('/libreta');
  } on ApiError catch (e) {
    if (e.status == 404) {
      mostrarAviso(context, 'No encontramos una partida con ese código. Revisa el nombre que aparece en la pantalla de la PC.');
    } else if (e.status == 409) {
      mostrarAviso(context, 'Esa partida ya tiene un teléfono conectado.');
    } else {
      mostrarAviso(context, e.message);
    }
  } on SinConexion {
    mostrarAviso(context, 'No hay conexión. Revisa tu internet e inténtalo de nuevo.');
  }
}
```

### Paso 3. Conectar el WebSocket y autenticarte

**Cuándo:** justo después de unirte, al recuperar una partida guardada y cada vez que la conexión se caiga (sección 7).

1. Abre `ws://<host>/ws` (en producción `wss://…/ws`). **El token no va en la URL.**
2. En menos de 5 segundos manda este texto, cambiando `<token>` por el token de la app que guardaste en el paso 2:

	<!-- peticion -->
	```json
	{"type": "auth", "payload": {"token": "<token>"}}
	```

3. El servidor responde:

	<!-- respuesta: ws -->
	```json
	{"type": "auth.ok", "payload": {"rol": "app", "sala": "TAPALPA"}}
	```

	y al juego, si está conectado, le avisa `dispositivo.conectado` con `{"rol": "app"}`.

**Qué hacer:** al recibir `auth.ok`, pide el estado (paso 4). El código de Dart está en la sección 10 (`conectar`).

Para verlo sin la app, abre la página de prueba, pega el token en el panel de la app y pulsa **Conectar**.

### Paso 4. Abrir la libreta con el estado de la sala

**Cuándo:** después de cada `auth.ok` (al conectar y al reconectar), al volver la app al primer plano y siempre que necesites ponerte al día.

```sh
curl $BASE/api/v1/salas/TAPALPA/estado \
	-H "Authorization: Bearer $TOKEN_APP"
```

Respuesta `200 OK` recién unida la app, con los dos dispositivos conectados:

<!-- respuesta: estado -->
```json
{
  "codigo": "TAPALPA",
  "estado": "activa",
  "escena_actual": "introduccion",
  "puzzles": [
    {"puzzle": "radio", "estado": "bloqueado", "datos": {}},
    {"puzzle": "photo", "estado": "bloqueado", "datos": {}},
    {"puzzle": "lookout", "estado": "bloqueado", "datos": {}},
    {"puzzle": "observatory", "estado": "bloqueado", "datos": {}}
  ],
  "descubrimientos": [],
  "dispositivos": {"juego": true, "app": true},
  "creada_en": "2026-11-06T15:54:55.102864Z",
  "expira_en": "2026-11-06T17:54:55.170467Z"
}
```

Y al final de una partida completa. Los `descubrimientos` están recortados a los que lee la libreta: se omitieron `lookout_enabled` y `observatory_enabled`, las banderas del juego, que en la respuesta real aparecen después de `coordenada_b` y de `observatorio_visible`, respectivamente. La app no las muestra:

<!-- respuesta: estado -->
```json
{
  "codigo": "TAPALPA",
  "estado": "activa",
  "escena_actual": "desenlace",
  "puzzles": [
    {"puzzle": "radio", "estado": "resuelto", "datos": {"sintonizada": true}},
    {"puzzle": "photo", "estado": "resuelto", "datos": {"completada": true}},
    {"puzzle": "lookout", "estado": "resuelto", "datos": {"completada": true}},
    {"puzzle": "observatory", "estado": "resuelto", "datos": {"completada": true}}
  ],
  "descubrimientos": [
    {"clave": "pescador_dialogo_completo", "datos": {}, "origen": "juego", "creado_en": "2026-11-06T15:54:55.564724Z"},
    {"clave": "coordenada_a", "datos": {"tipo": "ascension_recta", "valor": "137"}, "origen": "servidor", "creado_en": "2026-11-06T15:54:55.594751Z"},
    {"clave": "pieza_1", "datos": {}, "origen": "juego", "creado_en": "2026-11-06T15:54:55.614272Z"},
    {"clave": "pieza_2", "datos": {}, "origen": "juego", "creado_en": "2026-11-06T15:54:55.625431Z"},
    {"clave": "pieza_3", "datos": {}, "origen": "juego", "creado_en": "2026-11-06T15:54:55.636728Z"},
    {"clave": "coordenada_b", "datos": {"tipo": "declinacion", "valor": "+042"}, "origen": "servidor", "creado_en": "2026-11-06T15:54:55.658904Z"},
    {"clave": "observatorio_visible", "datos": {}, "origen": "servidor", "creado_en": "2026-11-06T15:54:55.688427Z"},
    {"clave": "magnetar_localizado", "datos": {}, "origen": "servidor", "creado_en": "2026-11-06T15:54:55.729063Z"}
  ],
  "dispositivos": {"juego": true, "app": true},
  "creada_en": "2026-11-06T15:54:55.102864Z",
  "expira_en": "2026-11-06T17:54:55.744952Z"
}
```

| Campo | Qué significa |
|---|---|
| `estado` | `activa` mientras dura la partida |
| `escena_actual` | Dónde está la jugadora en el juego: `introduccion`, `pueblo`, `faro`, `observatorio` o `desenlace` |
| `puzzles` | Siempre los cuatro, en este orden: `radio`, `photo`, `lookout`, `observatory` |
| `descubrimientos` | Todo lo registrado, en orden de llegada: `{clave, datos, origen, creado_en}`. `origen` es `juego`, `app` o `servidor` |
| `dispositivos` | Quién tiene el WebSocket conectado ahora. `juego: true` es "PC conectada" |
| `expira_en` | Si nadie escribe nada antes de esa hora, la sala expira |

**Qué mostrar en la libreta y de dónde sale cada dato:**

| Qué se muestra | De dónde sale | Ejemplo de texto |
|---|---|---|
| Escena actual | `escena_actual` | "Estás en el Faro" |
| Pescador | Descubrimiento `pescador_dialogo_completo` | "Hablaste con el pescador" |
| Radio | Estado de `radio` y descubrimiento `coordenada_a` | "Radio sintonizada. AR 137°" |
| Piezas de la foto | Cuántos descubrimientos `pieza_1`, `pieza_2` y `pieza_3` existen | "Piezas de foto encontradas 1/3" |
| Fotografía | Estado de `photo` y descubrimiento `coordenada_b` | "Foto reconstruida. DEC +042°" |
| Coordenadas | `coordenada_a` y `coordenada_b` | "Coordenadas obtenidas 2/2" |
| Mirador | Estado de `lookout` y descubrimiento `observatorio_visible` | "Mirador resuelto" |
| Observatorio | Estado de `observatory` y descubrimiento `magnetar_localizado` | "Magnetar localizado" |
| Dispositivos conectados | `dispositivos.juego` y los mensajes `dispositivo.conectado` y `dispositivo.desconectado` | "PC conectada" |

**Las coordenadas se leen, no se escriben en la app.** `coordenada_a` trae la ascensión recta (AR) y `coordenada_b` la declinación (DEC). Muéstralas desde `datos.valor`, que es **texto**: `"137"` y `"+042"`, con el signo y los ceros. No lo conviertas a número, porque `+042` perdería el `+` y el `0`.

```dart
// No compilado.
String? coordenada(EstadoSala estado, String clave) =>
    estado.descubrimientos[clave]?.datos['valor'] as String?; // "137" o "+042", texto

final ar = coordenada(estado, 'coordenada_a');
final dec = coordenada(estado, 'coordenada_b');
final piezas = ['pieza_1', 'pieza_2', 'pieza_3'].where(estado.descubrimientos.containsKey).length;
Text(ar == null ? 'Radio sin sintonizar' : 'Radio sintonizada. AR $ar°');
Text('Piezas de foto encontradas $piezas/3');
```

**Errores:**

| Respuesta | Cuándo | Qué hacer |
|---|---|---|
| `401` | Token ausente o inválido, o la sala ya terminó o venció | Borra el token y vuelve al inicio con "La partida terminó" |
| `403` `El token no pertenece a esta sala` | El código de la ruta no es el de tu token | Error de programación: usa el código que guardaste junto al token |

### Paso 5. Mantener la libreta al día

**Cuándo:** mientras la libreta está abierta. No mandas nada: escuchas el WebSocket.

| Mensaje | Qué cambia en la libreta |
|---|---|
| `escena.cambiada` | La escena actual |
| `descubrimiento.nuevo` | Agrega el descubrimiento (pescador, piezas, coordenadas, resultados) |
| `puzzle.actualizado` | El estado de ese puzzle, y la burbuja (paso 6) |
| `dispositivo.conectado` / `dispositivo.desconectado` con `rol: juego` | "PC conectada" o "PC desconectada" |

Ejemplos reales, en el orden en que llegaron durante una partida:

<!-- respuesta: ws -->
```json
{"type": "escena.cambiada", "payload": {"escena": "faro", "anterior": "pueblo"}}
{"type": "descubrimiento.nuevo", "payload": {"clave": "pieza_1", "datos": {}, "origen": "juego"}}
{"type": "descubrimiento.nuevo", "payload": {"clave": "coordenada_a", "datos": {"tipo": "ascension_recta", "valor": "137"}, "origen": "servidor"}}
{"type": "dispositivo.desconectado", "payload": {"rol": "juego"}}
{"type": "dispositivo.conectado", "payload": {"rol": "juego"}}
```

El detalle de cada mensaje está en la sección 7.

### Paso 6. Mostrar la burbuja que pulsa

**Cuándo:** un puzzle del teléfono (`photo`, `lookout` u `observatory`) pasa a `disponible`. Llega este mensaje:

<!-- respuesta: ws -->
```json
{"type": "puzzle.actualizado", "payload": {"puzzle": "photo", "estado": "disponible", "datos": {}}}
```

**Qué hacer:** muestra la burbuja flotante y hazla pulsar. Al tocarla, abre ese puzzle (paso 7). Revisa también el estado del paso 4: si al abrir la libreta un puzzle ya está `disponible`, la burbuja tiene que aparecer aunque no haya llegado el mensaje.

| Puzzle | Burbuja |
|---|---|
| `radio` | **No tiene burbuja.** La juega el juego; la libreta solo muestra su estado |
| `photo` | Pulsa cuando está `disponible` |
| `lookout` | Pulsa cuando está `disponible` |
| `observatory` | Pulsa cuando está `disponible` |

Si la jugadora abrió un puzzle y salió sin terminarlo, queda `en_curso`. Sugerencia: deja la burbuja visible, sin pulsar, para que pueda volver a abrirlo.

```dart
// No compilado.
const puzzlesDelTelefono = ['photo', 'lookout', 'observatory'];

String? puzzleDeLaBurbuja(EstadoSala estado) {
  for (final id in puzzlesDelTelefono) {
    final e = estado.puzzles[id]?.estado;
    if (e == 'disponible' || e == 'en_curso') return id;
  }
  return null;
}

bool burbujaPulsa(EstadoSala estado, String id) => estado.puzzles[id]?.estado == 'disponible';
```

### Paso 7. Abrir un puzzle: avisar `iniciado`

**Cuándo:** la jugadora toca la burbuja y se abre `photo`, `lookout` u `observatory`. Manda el aviso al abrir la pantalla del puzzle, no al terminarlo. La radio no tiene `iniciado`.

```sh
curl -X PUT $BASE/api/v1/salas/TAPALPA/puzzles/photo \
	-H "Authorization: Bearer $TOKEN_APP" \
	-H 'Content-Type: application/json' \
	-d '{"datos": {"iniciado": true}}'
```

Respuesta `200 OK`:

<!-- respuesta: puzzle -->
```json
{"puzzle": "photo", "estado": "en_curso", "datos": {"iniciado": true}}
```

**Qué hacer:** el puzzle queda `en_curso` y sirve para medir cuánto tardó la jugadora. Si lo vuelves a mandar (por ejemplo, al reabrir el puzzle) responde `200` con el mismo estado y no pasa nada. No esperes a la respuesta para mostrar el puzzle: si falla por red, la jugadora puede jugar igual.

**Errores:**

| Respuesta | Cuándo | Qué hacer |
|---|---|---|
| `409` con `"codigo": "puzzle_bloqueado"` | El puzzle sigue bloqueado | No abras el puzzle: la burbuja no debió aparecer. Pide el estado (paso 4) |
| `400` `Datos inválidos` | `datos` no es exactamente `{"iniciado": true}`, o mandaste `iniciado` a `radio` | Corrige el cuerpo |
| `401` | La sala terminó | Igual que en el paso 4 |

### Paso 8. Completar un puzzle: avisar `completada` una sola vez

**Cuándo:** la jugadora completa el puzzle **según las reglas de la app**. El backend no mide ni valida números: confía en el aviso. Mándalo **una sola vez** por puzzle.

```sh
curl -X PUT $BASE/api/v1/salas/TAPALPA/puzzles/photo \
	-H "Authorization: Bearer $TOKEN_APP" \
	-H 'Content-Type: application/json' \
	-d '{"datos": {"completada": true}}'
```

Respuesta `200 OK`:

<!-- respuesta: puzzle -->
```json
{"puzzle": "photo", "estado": "resuelto", "datos": {"completada": true}}
```

Y por WebSocket, a la app y al juego, en este orden:

<!-- respuesta: ws -->
```json
{"type": "puzzle.actualizado", "payload": {"puzzle": "photo", "estado": "resuelto", "datos": {"completada": true}}}
{"type": "descubrimiento.nuevo", "payload": {"clave": "coordenada_b", "datos": {"tipo": "declinacion", "valor": "+042"}, "origen": "servidor"}}
```

**Qué hacer:** cierra el puzzle, quita la burbuja y vuelve a la libreta. Muestra el resultado que crea el servidor:

| Puzzle | Qué juega la jugadora en la app | Resultado que crea el servidor | Qué muestra la libreta |
|---|---|---|---|
| `photo` | Une las tres piezas de la fotografía que encontró el juego | `coordenada_b` con `{"tipo": "declinacion", "valor": "+042"}` | "Foto reconstruida. DEC +042°" |
| `lookout` | Limpia el lente y alinea la vista del mirador | `observatorio_visible` | "Mirador resuelto" |
| `observatory` | Apunta el teléfono al cielo con el giroscopio | `magnetar_localizado` | "Magnetar localizado" |

**Cuándo se completa cada puzzle (valores de la app).** Viven en la app, no en el backend. Son del prototipo y pueden cambiar con las pruebas y el arte final (C5):

| Puzzle | Valor |
|---|---|
| Mirador, fase 1 | Lente limpio al 85 % |
| Mirador, fase 2 | Ejes de −100 a 100, tolerancia ±14, objetivo `(0.82, 0.40)` sobre la imagen (equivale a X≈88.89, Y=37.5) |
| Mirador, final | Animación de 4 s tras alinear |
| Observatorio | Objetivo azimut 0°, altitud 68° |
| Observatorio | Distancia angular de 6° o menos durante 2 s, con lecturas de no más de 500 ms de antigüedad |

**El giroscopio no se envía.** La orientación del teléfono se usa solo dentro de la app para decidir que el observatorio se completó. No se manda al backend ni por REST ni por WebSocket.

**Errores:**

| Respuesta | Cuándo | Qué hacer |
|---|---|---|
| `409` con `"codigo": "puzzle_bloqueado"` | El puzzle sigue bloqueado | No debería pasar si la burbuja salió de un `disponible`. Pide el estado |
| `400` `Datos inválidos` | `datos` no es exactamente `{"completada": true}` (`"true"`, `1`, una clave de más) | Corrige el cuerpo |
| `401` | La sala terminó | Igual que en el paso 4 |
| Sin respuesta | Sin red | Reintenta el mismo aviso con espera. Repetirlo es seguro: un puzzle `resuelto` responde `200` sin cambios |

Cuerpo real de un `400`:

<!-- respuesta: error -->
```json
{
  "timestamp": "2026-11-06T15:54:55.760654Z",
  "status": 400,
  "error": "Bad Request",
  "codigo": null,
  "message": "Datos inválidos",
  "path": "/api/v1/salas/TAPALPA/puzzles/photo",
  "details": [{"field": "datos", "message": "en photo debe ser exactamente {\"completada\": true} o {\"iniciado\": true}"}]
}
```

```dart
// No compilado.
class PantallaPuzzle extends StatefulWidget {
  const PantallaPuzzle({super.key, required this.puzzle}); // 'photo', 'lookout' u 'observatory'
  final String puzzle;
  @override
  State<PantallaPuzzle> createState() => _PantallaPuzzleState();
}

class _PantallaPuzzleState extends State<PantallaPuzzle> {
  bool _avisado = false;

  @override
  void initState() {
    super.initState();
    cliente.reportar(widget.puzzle, 'iniciado').catchError((_) {}); // al abrir; un fallo no impide jugar
  }

  Future<void> alCompletar() async {
    if (_avisado) return; // una sola vez
    _avisado = true;
    try {
      await cliente.reportar(widget.puzzle, 'completada');
    } on SinConexion {
      _avisado = false; // deja reintentar
      mostrarAviso(context, 'No hay conexión. Reintentando…');
      return;
    }
    if (mounted) Navigator.of(context).pop(); // vuelve a la libreta
  }

  @override
  Widget build(BuildContext context) => const Placeholder(); // el puzzle de la app
}
```

### Paso 9. El final de la partida

**Cuándo:** el juego cierra la sala al terminar la cinemática final, o la sala vence tras 2 horas sin actividad.

Llega por WebSocket:

<!-- respuesta: ws -->
```json
{"type": "sala.cerrada", "payload": {"estado": "finalizada"}}
```

y el servidor cierra la conexión con el código `1000`. `estado` es `finalizada` si el juego la cerró y `expirada` si venció.

**Qué hacer:** borra el token guardado, **no reconectes** y muestra una pantalla de cierre ("La partida terminó") con un botón para volver al inicio. Si el WebSocket estaba caído y no llegó el mensaje, te enterarás por un `401` en la siguiente petición: trátalo igual.

## 5. Autenticación

**Dónde guardar el token.** Guárdalo junto con el código en **almacenamiento seguro**, por ejemplo el paquete `flutter_secure_storage` (Keychain en iOS, Keystore en Android). Así la app puede volver a la partida si se cierra, sin pedir el código otra vez. No uses `shared_preferences`, que guarda en texto plano.

```dart
// No compilado. Paquete flutter_secure_storage.
const almacen = FlutterSecureStorage();

Future<void> guardarSesion(String codigo, String token) async {
  await almacen.write(key: 'vestigia_codigo', value: codigo);
  await almacen.write(key: 'vestigia_token', value: token);
}

Future<void> borrarSesion() async {
  await almacen.delete(key: 'vestigia_codigo');
  await almacen.delete(key: 'vestigia_token');
}
```

**Cómo enviarlo.**

- **REST:** en el encabezado `Authorization: Bearer <token>`, con un espacio después de `Bearer`.
- **WebSocket:** dentro del primer mensaje, `{"type": "auth", "payload": {"token": "<token>"}}`. **Nunca** en la URL.
- No lo escribas en logs ni en pantalla.

El token sirve solo para su sala y solo mientras la sala esté viva. Si nadie escribe nada en 2 horas, la sala vence.

**Qué hacer con un `401`.** El token ya no sirve: la sala se cerró, venció o el token está mal. Borra la sesión y vuelve al inicio. Un `401` puede llegar aunque no haya llegado `sala.cerrada`; trátalo igual.

**Qué hacer con un `403`.** El token es válido pero no te deja hacer eso: es de otra sala, o es una ruta del juego (escena, cierre), o mandaste una clave que solo crea el servidor, o una clave que solo manda el juego (`codigo: "clave_solo_juego"`). Es un error de programación: regístralo y no reintentes.

## 6. Estados de un puzzle

```
             requisitos                 iniciado                  completada o sintonizada
bloqueado ─────────────▶ disponible ─────────────▶ en_curso ──────────────────────────────▶ resuelto
                              │                                                                ▲
                              └──────────────────── completada o sintonizada ──────────────────┘
```

Un puzzle solo avanza, nunca regresa. El servidor lo pasa de `bloqueado` a `disponible` cuando el juego cumple sus requisitos:

| Puzzle | Quién lo juega | Pasa a `disponible` cuando | Al resolverse el servidor crea |
|---|---|---|---|
| `radio` | El juego | Existe `pescador_dialogo_completo` | `coordenada_a` = `{"tipo": "ascension_recta", "valor": "137"}` |
| `photo` | La app | Existen `pieza_1`, `pieza_2` y `pieza_3` | `coordenada_b` = `{"tipo": "declinacion", "valor": "+042"}` |
| `lookout` | La app | `photo` está `resuelto` y existe `lookout_enabled` | `observatorio_visible` |
| `observatory` | La app | Existen `coordenada_a` y `coordenada_b`, `lookout` está `resuelto` y existe `observatory_enabled` | `magnetar_localizado` |

| Si el estado es… | La app hace… |
|---|---|
| `radio` en cualquier estado | Solo lo muestra en la libreta. Sin burbuja ni avisos |
| `photo`, `lookout` u `observatory` `bloqueado` | Nada. Sin burbuja |
| `photo`, `lookout` u `observatory` `disponible` | Muestra la burbuja pulsando. Al tocarla abre el puzzle y manda `iniciado` |
| `photo`, `lookout` u `observatory` `en_curso` | La jugadora lo abrió. Deja que lo reabra; al completarlo manda `completada` |
| `photo`, `lookout` u `observatory` `resuelto` | Quita la burbuja y muestra el resultado en la libreta. No mandes nada más |

## 7. Tiempo real

El WebSocket sirve para que la libreta cambie en cuanto pasa algo en el juego, sin preguntar cada segundo. Por el WebSocket la app **solo manda `auth`**; todo lo demás va por REST.

### 7.1 Conectar

1. Abre `ws://<host>/ws` con el paquete `web_socket_channel` (`WebSocketChannel.connect`).
2. En menos de 5 segundos manda `{"type": "auth", "payload": {"token": "<token>"}}`.
3. Espera `auth.ok`. Luego pide `GET estado` (paso 4).

Al conectarte **no** recibes si la PC está conectada: léelo en `dispositivos.juego` de `GET estado`.

El servidor manda un ping cada 25 segundos. `web_socket_channel` lo responde solo; no hay que hacer nada.

### 7.2 Mensajes que recibe la app

Cada mensaje es un texto JSON `{"type": "...", "payload": {...}}`.

| `type` | Cuándo llega | Qué hacer |
|---|---|---|
| `auth.ok` | Respuesta a tu `auth` | Pide `GET estado`. Reinicia el contador de reintentos |
| `dispositivo.conectado` | La PC abrió su WebSocket | Si `rol` es `juego`, muestra "PC conectada" |
| `dispositivo.desconectado` | La PC perdió su conexión | Muestra "PC desconectada". La partida no se detiene |
| `puzzle.actualizado` | Un puzzle cambió de estado | Actualiza ese puzzle y la burbuja (sección 6) |
| `descubrimiento.nuevo` | Se registró un descubrimiento nuevo | Agrégalo a la libreta |
| `escena.cambiada` | El juego cambió de escena | Actualiza "Estás en…". Solo la app recibe este mensaje |
| `sala.cerrada` | La sala se cerró (`finalizada`) o expiró (`expirada`) | Borra la sesión y muestra el cierre. No reconectes |
| `error` | Mandaste algo que no es `auth` o un `auth` repetido | Regístralo en el log. La conexión sigue abierta |

Ejemplos reales de cada uno:

<!-- respuesta: ws -->
```json
{"type": "auth.ok", "payload": {"rol": "app", "sala": "TAPALPA"}}
{"type": "dispositivo.conectado", "payload": {"rol": "juego"}}
{"type": "dispositivo.desconectado", "payload": {"rol": "juego"}}
{"type": "puzzle.actualizado", "payload": {"puzzle": "lookout", "estado": "disponible", "datos": {}}}
{"type": "descubrimiento.nuevo", "payload": {"clave": "magnetar_localizado", "datos": {}, "origen": "servidor"}}
{"type": "escena.cambiada", "payload": {"escena": "observatorio", "anterior": "faro"}}
{"type": "sala.cerrada", "payload": {"estado": "finalizada"}}
{"type": "error", "payload": {"codigo": "tipo_desconocido", "message": "El mensaje orientacion está diferido: el servidor no lo recibe por ahora"}}
```

Notas:

- También recibes los mensajes de tus propias peticiones (por ejemplo `puzzle.actualizado` con `resuelto` después de tu `completada`). Úsalos o ignóralos: ya tienes la respuesta REST.
- Los mensajes salen **después** de que el cambio se guardó. Si pides `GET estado` al recibir uno, ya verás el cambio.
- Una petición repetida que no cambia nada no manda mensajes.
- Al resolverse un puzzle, los mensajes llegan en este orden: `puzzle.actualizado` (`resuelto`), `descubrimiento.nuevo` (el resultado) y un `puzzle.actualizado` (`disponible`) por cada puzzle que se habilita.

### 7.3 Reconexión

Si la conexión se cae (el teléfono cambió de red, la app pasó a segundo plano), reconecta con el mismo token, esperando cada vez más: 1, 2, 4, 8 y 16 segundos, y desde ahí cada 30 segundos. En cuanto llegue `auth.ok`, pide `GET estado`: los mensajes que se emitieron mientras estabas desconectada **no se repiten**.

Decide según el código de cierre (`canal.closeCode`):

| Código | Razón | ¿Reconectar? | Qué hacer |
|---|---|---|---|
| `1000` | `La sala se cerró` | No | Llegó `sala.cerrada`: la partida terminó |
| `4001` | `Token inválido o de una sala que ya terminó` | No | Igual que un `401`: borra la sesión y vuelve al inicio |
| `4009` | `Otra conexión del mismo rol tomó su lugar` | No | Otra copia de la app usa la sala. Avisa y no reconectes en automático |
| `4008` | `No llegó auth a tiempo` | Sí | Manda `auth` en cuanto se abra la conexión |
| `4000` | `El primer mensaje debe ser auth` | No | Error de programación: el primer mensaje no fue `auth` |
| `4500` | `El cliente no lee sus mensajes o falló un envío` | Sí | La app no estaba leyendo. Reconecta y pide `GET estado` |
| `1009` | Mensaje demasiado grande | No | Ningún mensaje válido pasa de 4096 caracteres |
| Otro, o la red se cayó | | Sí | Reconecta con espera creciente |

## 8. Tabla de errores

Todos los errores REST traen el mismo cuerpo JSON. Lee `status` y `codigo` para decidir; `message` está en español y se puede mostrar.

<!-- respuesta: error -->
```json
{
  "timestamp": "2026-11-06T15:54:55.699232Z",
  "status": 409,
  "error": "Conflict",
  "codigo": "puzzle_bloqueado",
  "message": "El puzzle 'observatory' está bloqueado: todavía no se cumplen sus requisitos",
  "path": "/api/v1/salas/TAPALPA/puzzles/observatory",
  "details": []
}
```

`codigo` **siempre viene**: vale `null` salvo en `puzzle_bloqueado` y `clave_solo_juego`. `details` solo trae elementos en errores de validación (`[{"field": "datos", "message": "..."}]`).

| HTTP | `codigo` | Cuándo | Qué hacer |
|---|---|---|---|
| `400` | `null` | JSON mal escrito, clave repetida, un campo con otro tipo (`"true"` en vez de `true`), aviso de puzzle inválido, id de puzzle desconocido | Error de programación: corrige la petición. No reintentes igual |
| `401` | `null` | Sin token, token inválido, sala cerrada o vencida | Borra la sesión y vuelve al inicio con "La partida terminó" |
| `403` | `null` | Token de otra sala, ruta solo del juego (escena, cierre) o clave que crea el servidor | Error de programación. Regístralo y no reintentes |
| `403` | `clave_solo_juego` | Mandaste `POST /descubrimientos` con una clave que manda solo el juego (`pieza_*`, `pescador_dialogo_completo`, `lookout_enabled`, `observatory_enabled`) | Error de programación: la app no manda esas claves. Quita la petición; no reintentes |
| `404` | `null` | Al unirse: no hay partida con ese código | "No encontramos una partida con ese código. Revisa el nombre que aparece en la pantalla de la PC." |
| `404` | `null` | Ruta mal escrita (por ejemplo con `/` al final) | Revisa la URL |
| `409` | `null` | Al unirse: la partida ya tiene un teléfono | "Esa partida ya tiene un teléfono conectado." |
| `409` | `puzzle_bloqueado` | Avisaste un puzzle que todavía está `bloqueado` | Pide el estado y no muestres la burbuja de ese puzzle |
| `500` | `null` | Error interno del servidor | "Algo salió mal. Inténtalo de nuevo." Reintenta una vez más tarde |
| Sin respuesta | | Sin red o servidor apagado | "No hay conexión" y reintenta con espera |

## 9. Si quiero X, hago Y

| Si quiero… | Hago… |
|---|---|
| Saber si la PC está conectada | Leo `dispositivos.juego` de `GET estado` y escucho `dispositivo.conectado` y `dispositivo.desconectado` con `rol: juego` |
| Mostrar la AR y la DEC | Leo `datos.valor` de los descubrimientos `coordenada_a` y `coordenada_b`, como texto |
| Contar las piezas de la foto | Cuento las claves `pieza_1`, `pieza_2` y `pieza_3` en `descubrimientos` |
| Volver a la partida después de cerrar la app | Leo el token del almacenamiento seguro, pido `GET estado` y, si da `200`, conecto el WebSocket. Si da `401`, borro la sesión |
| Reintentar un aviso que falló por red | Lo repito igual: todas las escrituras se pueden repetir sin duplicar nada |
| Saber qué burbuja mostrar al abrir la libreta | Busco en `puzzles` de `GET estado` el de `photo`, `lookout` u `observatory` que esté `disponible` o `en_curso` |
| Saber si la partida terminó | Llega `sala.cerrada`, o cualquier petición responde `401` |
| Ver los mensajes del WebSocket sin la app | Abro `$BASE/prueba-ws.html`, pego el token de la app y pulso Conectar |
| Probar una ruta a mano | Uso `curl` como en la sección 4, o Swagger en `$BASE/swagger-ui/index.html` |

## 10. Cliente mínimo completo

Un solo archivo, `vestigia_cliente.dart`, con todo lo de esta guía: REST, almacenamiento seguro, WebSocket con reconexión y el estado de la libreta como `ChangeNotifier` para que las pantallas se redibujen solas.

> **No compilado.** Escrito para Flutter 3 con Dart 3. Usa los paquetes `http`, `web_socket_channel` y `flutter_secure_storage` (`flutter pub add http web_socket_channel flutter_secure_storage`). No se compiló en un proyecto de Flutter.

```dart
import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;
import 'package:web_socket_channel/web_socket_channel.dart';

/// URL base. Emulador de Android: http://10.0.2.2:8080. Se cambia con --dart-define=VESTIGIA_URL=...
const baseUrl = String.fromEnvironment('VESTIGIA_URL', defaultValue: 'http://10.0.2.2:8080');

class ApiError implements Exception {
  ApiError(this.status, this.codigo, this.message);
  final int status;
  final String? codigo; // null salvo "puzzle_bloqueado" o "clave_solo_juego"
  final String message;
}

class SinConexion implements Exception {}

class Puzzle {
  Puzzle(this.puzzle, this.estado, this.datos);
  factory Puzzle.fromJson(Map<String, dynamic> j) =>
      Puzzle(j['puzzle'] as String, j['estado'] as String, Map<String, dynamic>.from(j['datos'] as Map));
  final String puzzle; // radio, photo, lookout u observatory
  final String estado; // bloqueado, disponible, en_curso o resuelto
  final Map<String, dynamic> datos;
}

class Descubrimiento {
  Descubrimiento(this.clave, this.datos, this.origen);
  factory Descubrimiento.fromJson(Map<String, dynamic> j) =>
      Descubrimiento(j['clave'] as String, Map<String, dynamic>.from(j['datos'] as Map), j['origen'] as String);
  final String clave;
  final Map<String, dynamic> datos;
  final String origen; // juego, app o servidor
}

class EstadoSala {
  EstadoSala.fromJson(Map<String, dynamic> j)
      : codigo = j['codigo'] as String,
        escenaActual = j['escena_actual'] as String,
        puzzles = {for (final p in j['puzzles'] as List) (p as Map)['puzzle'] as String: Puzzle.fromJson(Map<String, dynamic>.from(p))},
        descubrimientos = {for (final d in j['descubrimientos'] as List) (d as Map)['clave'] as String: Descubrimiento.fromJson(Map<String, dynamic>.from(d))},
        juegoConectado = (j['dispositivos'] as Map)['juego'] as bool;
  final String codigo;
  String escenaActual;
  final Map<String, Puzzle> puzzles;
  final Map<String, Descubrimiento> descubrimientos;
  bool juegoConectado;
}

class VestigiaCliente extends ChangeNotifier {
  static const _almacen = FlutterSecureStorage();

  String? _codigo;
  String? _token;
  EstadoSala? estado; // null hasta el primer GET estado
  bool terminada = false;

  WebSocketChannel? _canal;
  bool _reconectar = false;
  int _intentos = 0;
  Timer? _espera;

  /// " tapalpa " → "TAPALPA".
  static String normalizar(String texto) => texto.replaceAll(RegExp(r'\s+'), '').toUpperCase();

  /// Al abrir la app: si hay una partida guardada, intenta volver a ella. Devuelve true si sigue viva.
  Future<bool> restaurar() async {
    _codigo = await _almacen.read(key: 'vestigia_codigo');
    _token = await _almacen.read(key: 'vestigia_token');
    if (_token == null) return false;
    try {
      await refrescar();
      conectar();
      return true;
    } on ApiError {
      return false; // 401: _terminar ya borró la sesión
    }
  }

  Future<void> unirse(String codigoEscrito) async {
    final codigo = normalizar(codigoEscrito);
    final r = await _enviar('POST', '/api/v1/salas/$codigo/unirse', conToken: false);
    _codigo = codigo;
    _token = r!['token'] as String;
    terminada = false;
    await _almacen.write(key: 'vestigia_codigo', value: _codigo);
    await _almacen.write(key: 'vestigia_token', value: _token);
    conectar();
  }

  Future<void> refrescar() async {
    final r = await _enviar('GET', '/api/v1/salas/$_codigo/estado');
    estado = EstadoSala.fromJson(r!);
    notifyListeners();
  }

  /// aviso: "iniciado" al abrir el puzzle o "completada" al terminarlo. Solo photo, lookout y observatory.
  Future<void> reportar(String puzzle, String aviso) async {
    final r = await _enviar('PUT', '/api/v1/salas/$_codigo/puzzles/$puzzle', cuerpo: {
      'datos': {aviso: true},
    });
    estado?.puzzles[puzzle] = Puzzle.fromJson(r!);
    notifyListeners();
  }

  Future<Map<String, dynamic>?> _enviar(String metodo, String ruta, {Object? cuerpo, bool conToken = true}) async {
    final req = http.Request(metodo, Uri.parse('$baseUrl$ruta'));
    if (conToken) req.headers['Authorization'] = 'Bearer $_token';
    if (cuerpo != null) {
      req.headers['Content-Type'] = 'application/json';
      req.body = jsonEncode(cuerpo);
    }
    final http.Response r;
    try {
      r = await http.Response.fromStream(await req.send().timeout(const Duration(seconds: 10)));
    } on Exception {
      throw SinConexion();
    }
    final texto = utf8.decode(r.bodyBytes); // el backend no declara charset: lee UTF-8 a mano
    final json = texto.isEmpty ? null : jsonDecode(texto) as Map<String, dynamic>;
    if (r.statusCode >= 200 && r.statusCode < 300) return json;
    if (r.statusCode == 401) await _terminar();
    throw ApiError(r.statusCode, json?['codigo'] as String?, json?['message'] as String? ?? 'Error ${r.statusCode}');
  }

  // ---------- WebSocket ----------

  void conectar() {
    _detener();
    _reconectar = true;
    _intentos = 0;
    _abrir();
  }

  Future<void> _abrir() async {
    final canal = WebSocketChannel.connect(Uri.parse('${baseUrl.replaceFirst('http', 'ws')}/ws'));
    _canal = canal;
    try {
      await canal.ready;
    } catch (_) {
      _programarReconexion();
      return;
    }
    canal.sink.add(jsonEncode({
      'type': 'auth',
      'payload': {'token': _token},
    }));
    canal.stream.listen(
      (dato) => _alRecibir(jsonDecode(dato as String) as Map<String, dynamic>),
      onDone: () {
        if (identical(canal, _canal)) _alCerrar(canal.closeCode);
      },
      onError: (_) {}, // después llega onDone
    );
  }

  void _alCerrar(int? codigo) {
    if (!_reconectar) return;
    if (const {1000, 4000, 4001, 4009, 1009}.contains(codigo)) {
      _reconectar = false;
      // 1000: la sala se cerró (llega después de sala.cerrada, por si ese mensaje se perdió). 4001: token muerto.
      if (codigo == 1000 || codigo == 4001) _terminar();
      return;
    }
    _programarReconexion();
  }

  void _programarReconexion() {
    if (!_reconectar) return;
    final segundos = min(30, 1 << min(_intentos, 5)); // 1, 2, 4, 8, 16, 30...
    _intentos++;
    _espera = Timer(Duration(seconds: segundos), _abrir);
  }

  void _alRecibir(Map<String, dynamic> m) {
    final p = Map<String, dynamic>.from(m['payload'] as Map);
    switch (m['type']) {
      case 'auth.ok':
        _intentos = 0;
        refrescar().catchError((_) {}); // siempre después de conectar o reconectar
      case 'dispositivo.conectado' when p['rol'] == 'juego':
        estado?.juegoConectado = true;
      case 'dispositivo.desconectado' when p['rol'] == 'juego':
        estado?.juegoConectado = false;
      case 'puzzle.actualizado':
        estado?.puzzles[p['puzzle'] as String] = Puzzle.fromJson(p);
      case 'descubrimiento.nuevo':
        estado?.descubrimientos[p['clave'] as String] = Descubrimiento.fromJson(p);
      case 'escena.cambiada':
        estado?.escenaActual = p['escena'] as String;
      case 'sala.cerrada':
        _terminar();
      case 'error':
        debugPrint('[Vestigia] WebSocket error ${p['codigo']}: ${p['message']}');
    }
    notifyListeners();
  }

  void _detener() {
    _reconectar = false;
    _espera?.cancel();
    _canal?.sink.close();
    _canal = null;
  }

  Future<void> _terminar() async {
    _detener();
    terminada = true;
    _token = null;
    await _almacen.delete(key: 'vestigia_codigo');
    await _almacen.delete(key: 'vestigia_token');
    notifyListeners();
  }

  @override
  void dispose() {
    _detener();
    super.dispose();
  }
}
```

Cómo se usa desde una pantalla (no compilado):

```dart
final cliente = VestigiaCliente();

ListenableBuilder(
  listenable: cliente,
  builder: (context, _) {
    if (cliente.terminada) return const Text('La partida terminó');
    final estado = cliente.estado;
    if (estado == null) return const CircularProgressIndicator();
    final burbuja = puzzleDeLaBurbuja(estado); // paso 6
    return Column(children: [
      Text('Estás en: ${estado.escenaActual}'),
      Text(estado.juegoConectado ? 'PC conectada' : 'PC desconectada'),
      if (burbuja != null) BurbujaPuzzle(puzzle: burbuja, pulsa: burbujaPulsa(estado, burbuja)),
    ]);
  },
);
```

## 11. Checklist de "ya terminé"

- [ ] La pantalla del código normaliza el texto (sin espacios y en mayúsculas) antes de mandarlo.
- [ ] Muestro mensajes claros para `404` y `409` al unirme, y para la falta de red.
- [ ] Guardo el token y el código en almacenamiento seguro y nunca en logs, pantalla ni URL.
- [ ] Abro el WebSocket, mando `auth` en menos de 5 s y pido `GET estado` después de cada `auth.ok`.
- [ ] La libreta muestra la escena, el pescador, la radio, las piezas, las coordenadas, el mirador, el observatorio y si la PC está conectada.
- [ ] Leo la AR y la DEC de `coordenada_a` y `coordenada_b` como texto; no están escritas en la app.
- [ ] La burbuja pulsa con `photo`, `lookout` u `observatory` en `disponible`; la radio no tiene burbuja.
- [ ] Al abrir un puzzle mando `{"datos": {"iniciado": true}}`.
- [ ] Al completarlo mando `{"datos": {"completada": true}}` una sola vez, y reintento si falla la red.
- [ ] La orientación del giroscopio no sale del teléfono.
- [ ] Reconecto con espera creciente y no reconecto con `1000`, `4001` ni `4009`.
- [ ] `sala.cerrada` o un `401` borran la sesión y muestran el cierre.
- [ ] Al abrir la app recupero una partida guardada si sigue viva.
- [ ] La app no manda escena, cierre, radio, piezas, pescador ni banderas (las claves del juego darían `403` `clave_solo_juego`).
- [ ] Probé el recorrido completo con `curl` haciendo de juego, o con el juego real.

## 12. Errores comunes

| Síntoma | Causa | Solución |
|---|---|---|
| `404` al unirse con un código que sí existe | Espacios dentro del código (`TA PALPA`), que el servidor no quita, o el juego todavía no creó la sala. Las minúsculas no son el problema: el servidor las pasa a mayúsculas | Quita todos los espacios y confirma en la PC el nombre exacto |
| `409` al unirse | Otro teléfono (o una prueba anterior con `curl`) ya se unió | Pide al juego que cree otra sala |
| Las peticiones fallan en el emulador de Android | `localhost` en el emulador es el propio emulador | Usa `http://10.0.2.2:8080` |
| Android o iOS bloquean `http://` | El tráfico sin cifrar está prohibido por defecto | Permítelo solo en desarrollo (sección 2.1) |
| Acentos raros en `message` (`estÃ¡`) | Se leyó el cuerpo como Latin-1 | Usa `utf8.decode(respuesta.bodyBytes)` |
| `400` `Datos inválidos` al avisar un puzzle | `{"completada": "true"}`, `{"completada": 1}` o dos claves | Manda exactamente `{"datos": {"completada": true}}` |
| `409` `puzzle_bloqueado` al abrir un puzzle | La burbuja salió sin que el puzzle estuviera `disponible` | Muestra la burbuja solo con `disponible` o `en_curso` |
| `403` `Solo el juego puede cambiar la escena` | La app intentó cambiar la escena | Solo el juego la cambia; la app la recibe en `escena.cambiada` |
| `403` con `"codigo": "clave_solo_juego"` | La app mandó `pieza_*`, `pescador_dialogo_completo`, `lookout_enabled` u `observatory_enabled` | Quita esa petición: esas claves las manda el juego y la app se entera por `descubrimiento.nuevo` |
| La AR sale como `137.0` o la DEC como `42` | Se convirtió `valor` a número | Muestra el texto tal cual |
| El WebSocket se cierra a los 5 s con `4008` | No mandaste `auth` a tiempo | Manda `auth` justo después de conectar |
| El WebSocket se cierra con `4009` | Dos copias de la app con el mismo token (por ejemplo, hot restart sin cerrar el canal anterior) | Cierra el canal viejo antes de abrir otro |
| La libreta se queda vieja tras volver del segundo plano | Se perdieron mensajes mientras el WebSocket estaba caído | Pide `GET estado` después de cada `auth.ok` y al volver al primer plano |
| No sé si la PC está conectada al abrir la libreta | `dispositivo.conectado` solo avisa a quien ya estaba conectado | Lee `dispositivos.juego` en `GET estado` |
