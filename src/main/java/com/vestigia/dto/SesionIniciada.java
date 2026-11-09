package com.vestigia.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Sesión de administración recién abierta")
public record SesionIniciada(
		@Schema(description = "Token opaco. Se envía como Authorization: Bearer <token>") String token,
		Instant expiraEn, Usuario usuario) {
}
