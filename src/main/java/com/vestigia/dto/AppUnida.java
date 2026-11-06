package com.vestigia.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Respuesta de POST /salas/{codigo}/unirse. Es la única vez que el token de la app viaja en claro. */
@Schema(description = "Respuesta de unirse")
public record AppUnida(
		@Schema(description = "Token de la app. Se entrega una sola vez: guárdalo", example = "T6F973q3JsTu4-jKMXevAOeLiipbXCOu9wFDevt5-eE") String token) {
}
