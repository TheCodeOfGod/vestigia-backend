# Tiempo real (`/ws`)

Guía para quien integra el juego (Unity) o la app (Flutter). El backend avisa por WebSocket lo que cambia en la sala para que el otro dispositivo se entere sin preguntar. Las escrituras siguen yendo por REST (`docs/api.md`): por el WebSocket el cliente solo manda `auth`.

Es un WebSocket simple, sin STOMP ni SockJS. Cada mensaje es un texto JSON con la forma:

```json
{"type": "puzzle.actualizado", "payload": {"puzzle": "radio", "estado": "disponible", "datos": {}}}
```

Los ejemplos de esta página salieron de las pruebas `WebSocketIT` y `EmisionWebSocketIT`, que usan un cliente WebSocket real contra el backend.

## Conectarse

1. Abre `ws://<host>/ws` (`wss://` en producción). **El token no va en la URL.**
2. En menos de 5 segundos manda:

	```json
	{"type": "auth", "payload": {"token": "<token>"}}
	```

	`<token>` es el mismo que usas en `Authorization: Bearer <token>`: el de `POST /salas` (juego) o el de `POST /salas/{codigo}/unirse` (app).

3. El servidor responde:

	```json
	{"type": "auth.ok", "payload": {"rol": "juego", "sala": "BACALAR"}}
	```

	y le avisa al otro dispositivo, si está conectado:

	```json
	{"type": "dispositivo.conectado", "payload": {"rol": "juego"}}
	```

4. Pide `GET /api/v1/salas/{codigo}/estado` para ponerte al día. Desde ahí, los mensajes del servidor te dicen qué cambió.

`auth.ok` siempre es el primer mensaje que recibe una conexión. El servidor la registra antes de mandarlo, así que un `GET estado` pedido después de `auth.ok` ya la cuenta en `dispositivos`.

**Quién más está conectado.** Al conectarte no recibes la presencia del otro dispositivo por WebSocket: `dispositivo.conectado` solo avisa a quien ya estaba conectado. Léela en `dispositivos` de `GET estado` y, desde ahí, sigue los mensajes `dispositivo.conectado` y `dispositivo.desconectado`.

**Un `401` por REST es una sala terminada.** Si una petición REST responde `401`, la sala terminó o venció aunque todavía no te haya llegado `sala.cerrada`: el job que la marca `expirada` corre cada minuto, y si tu WebSocket estaba caído el aviso no se repite. Trátalo igual que `sala.cerrada`: vuelve a elegir pueblo (juego) o a escribir el código (app).

**Reconectar.** Si la conexión se cae, vuelve a conectarte con el mismo token, con espera creciente entre intentos (por ejemplo 1, 2, 4 y 8 s, sin pasar de 30 s), y pide `GET estado` en cuanto llegue `auth.ok`. Los mensajes que se emitieron mientras no estabas no se repiten: `GET estado` los trae todos.

## Mensajes del servidor

| `type` | A quién | Cuándo | `payload` |
|---|---|---|---|
| `auth.ok` | A quien se autenticó | `auth` con un token válido | `{rol, sala}` |
| `dispositivo.conectado` | Al otro rol | Un dispositivo se autenticó | `{rol}` del que se conectó |
| `dispositivo.desconectado` | Al otro rol | Un dispositivo cerró o perdió su conexión | `{rol}` del que se fue |
| `error` | A quien mandó el mensaje | Un mensaje que el servidor no acepta, ya autenticado | `{codigo, message}` |

Los mensajes que emiten las escrituras están en la sección siguiente.

El servidor también manda un **ping** del protocolo WebSocket cada 25 segundos (`vestigia.ws.ping`). Las bibliotecas de WebSocket responden el pong solas; no es un mensaje JSON y no hay que hacer nada.

## Mensajes que emiten las escrituras

Cada escritura REST que cambia algo emite sus mensajes **después de confirmarse en la base**. Un cliente que reacciona pidiendo `GET estado` ya ve el cambio, y una escritura que falla no emite nada. Los mensajes de una misma escritura llegan en el orden en que ocurrieron.

| `type` | A quién | Lo emite | `payload` |
|---|---|---|---|
| `puzzle.actualizado` | Juego y app | Cada cambio de estado de un puzzle: `bloqueado` → `disponible` (por un descubrimiento o un puzzle resuelto), `disponible` → `en_curso` (`iniciado`) y → `resuelto` (`completada` o `sintonizada`) | `{puzzle, estado, datos}`, igual que `PUT /puzzles` y `GET estado` |
| `descubrimiento.nuevo` | Juego y app | `POST /descubrimientos` con una clave nueva, y el resultado que crea el servidor al resolver un puzzle | `{clave, datos, origen}`; `origen` es `juego`, `app` o `servidor` |
| `escena.cambiada` | **Solo la app** | `PUT /escena` con una escena distinta de la actual | `{escena, anterior}` |
| `sala.cerrada` | Juego y app | `DELETE /salas/{codigo}` o el job que expira la sala | `{estado}`: `finalizada` o `expirada`. Después el servidor cierra la conexión con `1000` |

Ejemplos reales:

```json
{"type": "puzzle.actualizado", "payload": {"puzzle": "photo", "estado": "resuelto", "datos": {"completada": true}}}
{"type": "descubrimiento.nuevo", "payload": {"clave": "coordenada_b", "datos": {"tipo": "declinacion", "valor": "+042"}, "origen": "servidor"}}
{"type": "escena.cambiada", "payload": {"escena": "faro", "anterior": "introduccion"}}
{"type": "sala.cerrada", "payload": {"estado": "finalizada"}}
```

**Resolver un puzzle** emite, en este orden, `puzzle.actualizado` con `resuelto`, `descubrimiento.nuevo` del resultado y un `puzzle.actualizado` con `disponible` por cada puzzle que se habilita. Por ejemplo, `PUT /puzzles/photo` con `{"completada": true}`, con `lookout_enabled` ya registrado:

```json
{"type": "puzzle.actualizado", "payload": {"puzzle": "photo", "estado": "resuelto", "datos": {"completada": true}}}
{"type": "descubrimiento.nuevo", "payload": {"clave": "coordenada_b", "datos": {"tipo": "declinacion", "valor": "+042"}, "origen": "servidor"}}
{"type": "puzzle.actualizado", "payload": {"puzzle": "lookout", "estado": "disponible", "datos": {}}}
```

**Lo que no emite nada.**

- Las escrituras repetidas: un descubrimiento que ya existía, la escena en la que ya está la sala o un aviso que ya no mueve el puzzle. Responden con éxito y renuevan la sala, pero no cambian nada que avisar.
- Crear la sala y unirse. Nadie está conectado antes de crear la sala, y el juego se entera de la app por `dispositivo.conectado` cuando la app abre su WebSocket.
- Las lecturas (`GET estado`, `GET /pueblos`).

Quien hizo la escritura también recibe sus mensajes. Un cliente puede usarlos para actualizar su pantalla o ignorarlos, porque ya tiene la respuesta REST.

## Errores y cierres

**Antes de `auth`** el servidor no responde errores: cierra la conexión con uno de estos códigos.

| Código | Razón | Cuándo | Qué hacer |
|---|---|---|---|
| `4000` | `El primer mensaje debe ser auth` | El primer mensaje no es `auth`: otro `type`, JSON inválido o un mensaje binario | Corrige el cliente |
| `4001` | `Token inválido o de una sala que ya terminó` | El token no existe, falta, no es texto o su sala terminó o venció | Igual que un `401`: vuelve a elegir pueblo (juego) o a escribir el código (app). No reconectes con ese token |
| `4008` | `No llegó auth a tiempo` | Pasaron 5 s (`vestigia.ws.auth-plazo`) sin `auth` | Reconecta y manda `auth` en cuanto se abra |

**Ya autenticado**, un mensaje que el servidor no acepta recibe un `error` y la conexión sigue abierta:

```json
{"type": "error", "payload": {"codigo": "json_invalido", "message": "El mensaje debe ser un objeto JSON de texto con la forma {\"type\": \"...\", \"payload\": {...}}"}}
```

| `codigo` | Cuándo |
|---|---|
| `json_invalido` | El texto no es JSON, repite una clave, no es un objeto, no trae `type` de texto o el mensaje es binario |
| `tipo_desconocido` | Cualquier `type` que no sea `auth`. Incluye `orientacion`, que está diferido: el servidor no recibe lecturas del giroscopio |
| `ya_autenticado` | Un segundo `auth` en la misma conexión |

**Otros cierres.**

| Código | Razón | Cuándo | Qué hacer |
|---|---|---|---|
| `1000` | `La sala se cerró` | Después de `sala.cerrada` | No reconectes: la sala terminó |
| `4009` | `Otra conexión del mismo rol tomó su lugar` | El mismo rol se autenticó en otra conexión | No reconectes en automático: otra copia del cliente está usando la sala |
| `1009` | La pone Tomcat | El mensaje pasa de 4096 caracteres (`vestigia.ws.mensaje-max-caracteres`) | Ningún mensaje válido se acerca a ese tamaño |
| `4500` | `El cliente no lee sus mensajes o falló un envío` | El servidor no pudo entregar un mensaje en 5 s (`vestigia.ws.envio-limite`), se juntaron más de 64 KB sin enviar (`vestigia.ws.envio-max-bytes`) o falló un envío | Reconecta y pide `GET estado`. Casi nunca llega: el cliente que provoca este cierre no está leyendo |

## Decisiones tomadas por los agentes

La especificación (`/ws`, `{type, payload}`, `auth` en menos de 5 s, `auth.ok` con `{rol, sala}`, presencia, pings, límite bajo de tamaño y los mensajes `puzzle.actualizado`, `descubrimiento.nuevo`, `escena.cambiada` y `sala.cerrada`) no fijaba estos detalles. Los decidieron los agentes que construyeron el Paso 4; Raúl puede cambiarlos.

| Tema | Decisión | Por qué |
|---|---|---|
| Segunda conexión del mismo rol | La nueva reemplaza a la vieja, que cierra con `4009`. El otro rol recibe `dispositivo.conectado` otra vez y nunca `desconectado`, porque el rol sigue conectado | Un teléfono que reconecta antes de que el servidor note la caída no debe quedar fuera |
| Formato de `error` | `{"type": "error", "payload": {"codigo", "message"}}`, con `codigo` estable como en el cuerpo de error de REST | El cliente decide por `codigo` sin leer `message` |
| Antes de `auth` | No hay `error`: cualquier mensaje que no sea un `auth` válido cierra la conexión | Lo pide la especificación |
| Códigos de cierre | `4000`, `4001`, `4008` y `4009`, que copian el `400`, `401`, `408` y `409` de HTTP; `1000` al cerrar la sala | Los `4xxx` son de la aplicación (RFC 6455). El cliente distingue "reconecta" (`4008`) de "no reconectes" (`4001`, `4009`, `1000`) sin leer la razón |
| `payload` de `escena.cambiada` | `{escena, anterior}`, lo mismo que guarda el evento | La app sabe de dónde viene el jugador sin pedir `GET estado` |
| `payload` de `sala.cerrada` | `{estado}` con `finalizada` o `expirada`, el estado que queda en `salas` | La app puede decir "el juego cerró la partida" o "la sala expiró" |
| Expiración | El job guarda un evento `sala.expirada` (origen `servidor`) y emite `sala.cerrada` después de su commit, igual que `DELETE` | Antes el job no publicaba nada; así las dos formas de terminar siguen el mismo camino y las métricas las distinguen |
| Quién recibe cada mensaje | También quien hizo la escritura | Es más simple y no estorba: el mensaje describe el estado, no una orden |
| Escrituras repetidas | No emiten mensaje | No publican evento: no cambió nada |
| Límite de tamaño | 4096 caracteres por mensaje de texto (4096 bytes si es binario), configurable. Más grande cierra con `1009` | Un `auth` mide menos de 100 caracteres |
| Mensajes binarios | Antes de `auth` cierran con `4000`; después responden `error` con `json_invalido` | Mismo trato que cualquier mensaje inválido |
| Ping | Ping del protocolo cada 25 s a las conexiones autenticadas. El servidor no cierra a quien no responde el pong | Algunas bibliotecas de Unity podrían no responder pongs; cerrar por eso provocaría reconexiones en bucle |
| Origen del handshake | Mismo origen o uno de `CORS_ALLOWED_ORIGINS`; sin `Origin`, siempre | Unity WebGL o Flutter web mandan `Origin`; no se inventan orígenes nuevos (ronda 1 de revisión, H6) |
| Cliente que no lee | Cada conexión tiene su cola de salida y un hilo virtual la escribe en orden. Un envío bloqueado se corta a los 5 s y una cola de más de 64 KB se descarta; en los dos casos se cierra solo esa conexión con `4500` | Un cliente atascado no debe frenar a las demás salas, a `GET estado` ni a las escrituras REST (ronda 1 de revisión, H1) |

## Limitaciones conocidas

- **Una sola instancia.** Las conexiones viven en la memoria del backend. Con dos instancias, cada una vería solo sus conexiones: los avisos y `dispositivos` saldrían incompletos. Railway debe correr una sola réplica (`docs/despliegue.md`). Al reiniciar el backend, todas las conexiones se cierran y los clientes reconectan.
- **Caída sin aviso.** Si un teléfono pierde la red sin cerrar la conexión, el servidor tarda en notarlo, hasta que falle un envío. Mientras tanto `dispositivos` lo sigue mostrando conectado.
- **Tareas programadas.** El cierre por falta de `auth`, los pings y el job de expiración comparten el programador de Spring, con dos hilos (`spring.task.scheduling.pool.size=2`). Ninguna de esas tareas escribe en la red: solo encolan, así que son breves.
- **Cómo se ordenan los envíos.** Un candado en memoria decide el orden: registrar una conexión, encolar sus mensajes y cerrar una sala nunca se intercalan. Dentro del candado no hay red ni base de datos: el token se valida antes de tomarlo y los envíos los hace después un hilo virtual por conexión, en el orden de su cola. `dispositivos` se lee sin el candado. Un cliente que no lee solo retrasa sus propios mensajes, hasta que su conexión se cierra con `4500`.
- **Navegadores.** Un navegador (Unity WebGL, Flutter web o la página de prueba) se conecta desde el mismo origen del backend o desde uno de `CORS_ALLOWED_ORIGINS`, la misma lista que usa REST. Desde otro origen, el handshake responde `403`. Unity y Flutter nativos no mandan el encabezado `Origin` y siempre se conectan.
- **Sin `orientacion`.** El reenvío del giroscopio al juego (Paso 5) está diferido.
