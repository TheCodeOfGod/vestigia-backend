package com.vestigia.ws;

import org.springframework.web.socket.CloseStatus;

/**
 * Los cierres que decide el servidor, con su código y su razón. Los 4xxx son códigos de la aplicación (RFC 6455,
 * sección 7.4.2): 4000 a 4009 copian el estado HTTP equivalente y 4500 es el de Spring para una sesión que dejó de
 * ser fiable. Tomcat cierra por su cuenta con 1009 un mensaje demasiado
 * grande.
 */
enum Cierre {

	SALA_CERRADA(1000, "La sala se cerró"),
	PRIMER_MENSAJE_NO_ES_AUTH(4000, "El primer mensaje debe ser auth"),
	TOKEN_INVALIDO(4001, "Token inválido o de una sala que ya terminó"),
	SIN_AUTH_A_TIEMPO(4008, "No llegó auth a tiempo"),
	REEMPLAZADA(4009, "Otra conexión del mismo rol tomó su lugar"),
	CLIENTE_LENTO(4500, "El cliente no lee sus mensajes o falló un envío");

	private final CloseStatus status;

	Cierre(int codigo, String razon) {
		this.status = new CloseStatus(codigo, razon);
	}

	CloseStatus status() {
		return status;
	}

}
