package com.vestigia.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.vestigia.model.DescubrimientoModel;
import com.vestigia.model.PuzzleEstadoModel;
import com.vestigia.model.SalaModel;

@Schema(description = "Estado completo de una sala")
public record Sala(@Schema(example = "BACALAR") String codigo, SalaModel.Estado estado, SalaModel.Escena escenaActual, List<Puzzle> puzzles,
		@Schema(description = "En orden de llegada") List<Descubrimiento> descubrimientos, Dispositivos dispositivos, Instant creadaEn,
		@Schema(description = "Cuándo expira si nadie escribe antes") Instant expiraEn) {

	@Schema(description = "Estado de un puzzle")
	public record Puzzle(PuzzleEstadoModel.Puzzle puzzle, PuzzleEstadoModel.Estado estado,
			@Schema(description = "El aviso que dejó el puzzle en su estado actual; {} hasta el primero", example = "{\"completada\": true}") Map<String, Object> datos) {
	}

	@Schema(description = "Un descubrimiento de la libreta")
	public record Descubrimiento(@Schema(example = "coordenada_a") String clave,
			@Schema(description = "Objeto JSON libre. En las coordenadas, {tipo, valor} con valor en texto", example = "{\"tipo\": \"ascension_recta\", \"valor\": \"137\"}") Map<String, Object> datos,
			@Schema(description = "Quién lo registró: juego, app o servidor") DescubrimientoModel.Origen origen,
			Instant creadoEn) {
	}

	/** Qué dispositivos tienen una conexión autenticada en /ws en este momento. */
	@Schema(description = "true si ese rol tiene ahora una conexión autenticada en /ws")
	public record Dispositivos(boolean juego, boolean app) {
	}

}
