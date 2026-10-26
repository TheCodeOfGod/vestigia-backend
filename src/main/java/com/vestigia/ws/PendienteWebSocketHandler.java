package com.vestigia.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/** Cierra toda conexión a /ws hasta que exista la autenticación por token (Paso 4). */
@Component
public class PendienteWebSocketHandler extends AbstractWebSocketHandler {

	private static final CloseStatus NO_IMPLEMENTADO = CloseStatus.POLICY_VIOLATION
			.withReason("WebSocket aún no implementado (Paso 4)");

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		session.close(NO_IMPLEMENTADO);
	}

}
