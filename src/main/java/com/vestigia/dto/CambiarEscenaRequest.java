package com.vestigia.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de PUT /salas/{codigo}/escena. El servicio valida que sea una escena conocida. */
@Schema(description = "Cuerpo de cambiar escena")
public record CambiarEscenaRequest(@Schema(description = "Escena nueva, en minúsculas", allowableValues = { "introduccion", "pueblo", "faro", "observatorio", "desenlace" }, example = "faro") @NotBlank(message = "es obligatoria") String escena) {
}
