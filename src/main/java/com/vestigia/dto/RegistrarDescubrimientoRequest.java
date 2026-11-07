package com.vestigia.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Cuerpo de POST /salas/{codigo}/descubrimientos. {@code datos} es opcional y, si viene, es un objeto JSON de tamaño
 * acotado.
 */
public record RegistrarDescubrimientoRequest(
		@Schema(description = "Clave del descubrimiento: minúsculas sin acentos, dígitos y guion bajo", example = "pieza_1")
		@NotNull(message = "es obligatoria") @Pattern(regexp = "^[a-z0-9_]{1,40}$",
				message = "solo minúsculas sin acentos, dígitos y guion bajo, de 1 a 40 caracteres") String clave,
		@Schema(description = "Objeto JSON opcional, de 4096 bytes como máximo. Si falta se guarda {}", example = "{\"lugar\": \"muelle\"}") @JsonAcotado Map<String, Object> datos) {
}
