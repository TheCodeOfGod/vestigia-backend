package com.vestigia.dto;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de PUT /salas/{codigo}/escena. El servicio valida que sea una escena conocida. */
public record CambiarEscenaRequest(@NotBlank(message = "es obligatoria") String escena) {
}
