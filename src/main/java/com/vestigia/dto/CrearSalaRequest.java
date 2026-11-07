package com.vestigia.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

@Schema(description = "Cuerpo de crear sala")
public record CrearSalaRequest(@Schema(description = "Pueblo libre del catálogo. Se recorta y se pasa a mayúsculas", example = "BACALAR") @NotBlank(message = "es obligatorio") String codigo) {
}
