package com.vestigia.dto;

import jakarta.validation.constraints.NotBlank;

public record CrearSalaRequest(@NotBlank String codigo) {
}
