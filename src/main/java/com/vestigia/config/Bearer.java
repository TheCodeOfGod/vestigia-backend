package com.vestigia.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Lee el token del encabezado {@code Authorization: Bearer <token>}. */
final class Bearer {

	private static final String PREFIJO = "Bearer ";

	private Bearer() {
	}

	static String token(String authorization) {
		if (authorization == null || !authorization.regionMatches(true, 0, PREFIJO, 0, PREFIJO.length())
				|| authorization.substring(PREFIJO.length()).isBlank()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
					"Falta el token: envía el encabezado Authorization: Bearer <token>");
		}
		return authorization.substring(PREFIJO.length()).trim();
	}

}
