package com.vestigia.dto;

import java.util.Map;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Cuerpo de POST /salas/{codigo}/descubrimientos. {@code datos} es opcional y, si viene, es un objeto JSON. */
public record RegistrarDescubrimientoRequest(
		@NotNull(message = "es obligatoria") @Pattern(regexp = "^[a-z0-9_]{1,40}$",
				message = "solo minúsculas sin acentos, dígitos y guion bajo, de 1 a 40 caracteres") String clave,
		Map<String, Object> datos) {
}
