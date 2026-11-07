package com.vestigia.ws;

import java.util.Map;

/** Un mensaje del WebSocket, en JSON {@code {"type": "...", "payload": {...}}}. */
public record Mensaje(String type, Map<String, Object> payload) {

	static Mensaje error(String codigo, String message) {
		return new Mensaje("error", Map.of("codigo", codigo, "message", message));
	}

}
