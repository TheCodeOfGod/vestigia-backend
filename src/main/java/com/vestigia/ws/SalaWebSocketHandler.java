package com.vestigia.ws;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import com.vestigia.service.SalaService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * El protocolo de /ws. El primer mensaje debe ser {@code {"type":"auth","payload":{"token":"..."}}} y llegar dentro
 * del plazo; cualquier otra cosa cierra la conexión. Después los clientes no tienen nada que mandar, porque las
 * escrituras van por REST: un mensaje de una sesión autenticada recibe un {@code error} y la conexión sigue abierta.
 * Nunca registra el contenido de un mensaje, que en auth lleva el token.
 */
@Component
public class SalaWebSocketHandler extends AbstractWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(SalaWebSocketHandler.class);

	private final SesionesWebSocket sesiones;

	private final SalaService salaService;

	private final JsonMapper json;

	private final TaskScheduler scheduler;

	private final Duration plazoAuth;

	public SalaWebSocketHandler(SesionesWebSocket sesiones, SalaService salaService, JsonMapper json,
			TaskScheduler scheduler, @Value("${vestigia.ws.auth-plazo}") Duration plazoAuth) {
		this.sesiones = sesiones;
		this.salaService = salaService;
		this.json = json;
		this.scheduler = scheduler;
		this.plazoAuth = plazoAuth;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		log.info("[WS] conexión abierta {}", session.getId());
		scheduler.schedule(() -> {
			if (sesiones.cerrarSiNoSeAutentico(session, Cierre.SIN_AUTH_A_TIEMPO.status())) {
				log.info("[WS] sin auth a tiempo {}", session.getId());
			}
		}, Instant.now().plus(plazoAuth));
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		recibir(session, leer(message.getPayload()));
	}

	@Override
	protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
		recibir(session, Optional.empty());
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		sesiones.desconectar(session);
		log.info("[WS] conexión cerrada {} código={}", session.getId(), status.getCode());
	}

	/** {@code mensaje} es el sobre {type, payload} ya leído, o vacío si no es un objeto JSON con type de texto. */
	private void recibir(WebSocketSession session, Optional<JsonNode> mensaje) {
		if (!sesiones.autenticada(session)) {
			autenticar(session, mensaje);
			return;
		}
		sesiones.responder(session, error(mensaje));
	}

	private void autenticar(WebSocketSession session, Optional<JsonNode> mensaje) {
		if (mensaje.isEmpty() || !"auth".equals(mensaje.get().get("type").asString())) {
			log.info("[WS] primer mensaje que no es auth {}", session.getId());
			sesiones.cerrarSiNoSeAutentico(session, Cierre.PRIMER_MENSAJE_NO_ES_AUTH.status());
			return;
		}
		JsonNode token = mensaje.get().path("payload").path("token");
		Optional<SalaService.Credencial> credencial = sesiones.conectar(session,
				() -> token.isString() && !token.asString().isBlank() ? salaService.autenticar(token.asString())
						: Optional.empty());
		if (credencial.isEmpty() && sesiones.cerrarSiNoSeAutentico(session, Cierre.TOKEN_INVALIDO.status())) {
			log.info("[WS] auth rechazada {}", session.getId());
		}
	}

	private static Mensaje error(Optional<JsonNode> mensaje) {
		if (mensaje.isEmpty()) {
			return Mensaje.error("json_invalido",
					"El mensaje debe ser un objeto JSON de texto con la forma {\"type\": \"...\", \"payload\": {...}}");
		}
		return switch (mensaje.get().get("type").asString()) {
			case "auth" -> Mensaje.error("ya_autenticado", "La conexión ya está autenticada");
			case "orientacion" -> Mensaje.error("tipo_desconocido",
					"El mensaje orientacion está diferido: el servidor no lo recibe por ahora");
			default -> Mensaje.error("tipo_desconocido",
					"Tipo de mensaje desconocido. Los clientes solo envían auth; las escrituras van por REST");
		};
	}

	private Optional<JsonNode> leer(String texto) {
		try {
			JsonNode nodo = json.readTree(texto);
			return nodo.isObject() && nodo.path("type").isString() ? Optional.of(nodo) : Optional.empty();
		}
		catch (JacksonException ex) {
			return Optional.empty();
		}
	}

}
