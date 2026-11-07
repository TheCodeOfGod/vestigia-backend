package com.vestigia.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Un pueblo del catálogo")
public record Pueblo(@Schema(description = "Nombre en mayúsculas, sin acentos ni ñ", example = "BACALAR") String codigo,
		@Schema(description = "false si tiene una sala en esperando_app o activa") boolean libre) {
}
