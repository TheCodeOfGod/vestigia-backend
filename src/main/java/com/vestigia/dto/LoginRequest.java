package com.vestigia.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

@Schema(description = "Cuerpo de iniciar sesión")
public record LoginRequest(
		@Schema(description = "Correo del usuario. Se recorta y se pasa a minúsculas", example = "raul@vestigia.site")
		@NotBlank(message = "es obligatorio") String correo,
		@Schema(example = "una-contraseña-larga") @NotBlank(message = "es obligatorio") String password) {
}
