# Tiempo real (`/ws`)

Guía para quien integra el juego (Unity) o la app (Flutter). El backend avisa por WebSocket lo que cambia en la sala para que el otro dispositivo se entere sin preguntar. Las escrituras siguen yendo por REST (`docs/api.md`): por el WebSocket el cliente solo manda `auth`.

Es un WebSocket simple, sin STOMP ni SockJS. Cada mensaje es un texto JSON con la forma:

```json
{"type": "puzzle.actualizado", "payload": {"puzzle": "radio", "estado": "disponible", "datos": {}}}
```

Los ejemplos de esta página salieron de la prueba `WebSocketIT`, que usa un cliente WebSocket real contra el backend.

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

**Reconectar.** Si la conexión se cae, vuelve a conectarte con el mismo token, con espera creciente entre intentos (por ejemplo 1, 2, 4 y 8 s, sin pasar de 30 s), y pide `GET estado` en cuanto llegue `auth.ok`. Los mensajes que se emitieron mientras no estabas no se repiten: `GET estado` los trae todos.

## Mensajes del servidor

| `type` | A quién | Cuándo | `payload` |
|---|---|---|---|
| `auth.ok` | A quien se autenticó | `auth` con un token válido | `{rol, sala}` |
| `dispositivo.conectado` | Al otro rol | Un dispositivo se autenticó | `{rol}` del que se conectó |
| `dispositivo.desconectado` | Al otro rol | Un dispositivo cerró o perdió su conexión | `{rol}` del que se fue |
| `error` | A quien mandó el mensaje | Un mensaje que el servidor no acepta, ya autenticado | `{codigo, message}` |

El servidor también manda un **ping** del protocolo WebSocket cada 25 segundos (`vestigia.ws.ping`). Las bibliotecas de WebSocket responden el pong solas; no es un mensaje JSON y no hay que hacer nada.

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
| `4009` | `Otra conexión del mismo rol tomó su lugar` | El mismo rol se autenticó en otra conexión | No reconectes en automático: otra copia del cliente está usando la sala |
| `1009` | La pone Tomcat | El mensaje pasa de 4096 caracteres (`vestigia.ws.mensaje-max-caracteres`) | Ningún mensaje válido se acerca a ese tamaño |

## Decisiones tomadas por los agentes

La especificación (`/ws`, `{type, payload}`, `auth` en menos de 5 s, `auth.ok` con `{rol, sala}`, presencia, pings y límite bajo de tamaño) no fijaba estos detalles. Los decidieron los agentes que construyeron el Paso 4; Raúl puede cambiarlos.

| Tema | Decisión | Por qué |
|---|---|---|
| Segunda conexión del mismo rol | La nueva reemplaza a la vieja, que cierra con `4009`. El otro rol recibe `dispositivo.conectado` otra vez y nunca `desconectado`, porque el rol sigue conectado | Un teléfono que reconecta antes de que el servidor note la caída no debe quedar fuera |
| Formato de `error` | `{"type": "error", "payload": {"codigo", "message"}}`, con `codigo` estable como en el cuerpo de error de REST | El cliente decide por `codigo` sin leer `message` |
| Antes de `auth` | No hay `error`: cualquier mensaje que no sea un `auth` válido cierra la conexión | Lo pide la especificación |
| Códigos de cierre | `4000`, `4001`, `4008` y `4009`, que copian el `400`, `401`, `408` y `409` de HTTP | Los `4xxx` son de la aplicación (RFC 6455). El cliente distingue "reconecta" (`4008`) de "no reconectes" (`4001`, `4009`) sin leer la razón |
| Límite de tamaño | 4096 caracteres por mensaje de texto (4096 bytes si es binario), configurable. Más grande cierra con `1009` | Un `auth` mide menos de 100 caracteres |
| Mensajes binarios | Antes de `auth` cierran con `4000`; después responden `error` con `json_invalido` | Mismo trato que cualquier mensaje inválido |
| Ping | Ping del protocolo cada 25 s a las conexiones autenticadas. El servidor no cierra a quien no responde el pong | Algunas bibliotecas de Unity podrían no responder pongs; cerrar por eso provocaría reconexiones en bucle |

## Limitaciones conocidas

- **Una sola instancia.** Las conexiones viven en la memoria del backend. Con dos instancias, cada una vería solo sus conexiones: los avisos y `dispositivos` saldrían incompletos. Railway debe correr una sola réplica (`docs/despliegue.md`). Al reiniciar el backend, todas las conexiones se cierran y los clientes reconectan.
- **Caída sin aviso.** Si un teléfono pierde la red sin cerrar la conexión, el servidor tarda en notarlo, hasta que falle un envío. Mientras tanto `dispositivos` lo sigue mostrando conectado.
- **Envíos en serie.** Todos los envíos pasan por un solo candado, para que el orden sea siempre el mismo. Con dos dispositivos por sala y pocas salas a la vez no se nota; un cliente que deja de leer por mucho tiempo podría retrasar los avisos de las demás salas.
- **Navegadores.** El servidor solo acepta conexiones de navegador desde su mismo origen. Unity y Flutter no mandan el encabezado `Origin`, así que no les afecta.
- **Sin `orientacion`.** El reenvío del giroscopio al juego (Paso 5) está diferido.
