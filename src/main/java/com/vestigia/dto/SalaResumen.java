package com.vestigia.dto;

import java.time.Instant;
import java.util.UUID;

import com.vestigia.model.SalaModel;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Una sala en el listado de administración")
public record SalaResumen(UUID id, @Schema(example = "BACALAR") String codigo, SalaModel.Estado estado,
		SalaModel.Escena escenaActual, @Schema(description = "Puzzles en resuelto, de 0 a 4", example = "1") int puzzlesResueltos,
		@Schema(example = "4") int puzzlesTotal, @Schema(description = "true si la app ya se unió") boolean appUnida,
		Sala.Dispositivos dispositivos, Instant creadaEn, Instant ultimaActividad,
		@Schema(description = "Cuándo expira si nadie escribe antes") Instant expiraEn) {
}
