package com.vestigia.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.vestigia.model.DescubrimientoModel;
import com.vestigia.model.SalaModel;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Una sala completa vista por un administrador, viva o terminada")
public record SalaAdmin(UUID id, @Schema(example = "BACALAR") String codigo, SalaModel.Estado estado,
		SalaModel.Escena escenaActual, List<Sala.Puzzle> puzzles,
		@Schema(description = "En orden de llegada") List<Sala.Descubrimiento> descubrimientos,
		Sala.Dispositivos dispositivos, boolean appUnida, Instant creadaEn, Instant ultimaActividad, Instant expiraEn,
		@Schema(description = "Bitácora de la sala, en orden") List<Evento> eventos) {

	@Schema(description = "Una fila de la bitácora")
	public record Evento(@Schema(example = "puzzle.resuelto") String tipo, DescubrimientoModel.Origen origen,
			@Schema(description = "Objeto JSON libre, según el tipo", example = "{\"puzzle\": \"radio\"}") Map<String, Object> payload,
			Instant creadoEn) {
	}

}
