package com.vestigia.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.vestigia.model.DescubrimientoModel;
import com.vestigia.model.PuzzleEstadoModel;
import com.vestigia.model.SalaModel;

public record Sala(String codigo, SalaModel.Estado estado, SalaModel.Escena escenaActual, List<Puzzle> puzzles,
		List<Descubrimiento> descubrimientos, Dispositivos dispositivos, Instant creadaEn, Instant expiraEn) {

	public record Puzzle(PuzzleEstadoModel.Puzzle puzzle, PuzzleEstadoModel.Estado estado, Map<String, Object> datos) {
	}

	public record Descubrimiento(String clave, Map<String, Object> datos, DescubrimientoModel.Origen origen,
			Instant creadoEn) {
	}

	/** Presencia por WebSocket. Hasta el Paso 4 los dos valores son siempre false. */
	public record Dispositivos(boolean juego, boolean app) {
	}

}
