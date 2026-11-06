package com.vestigia.service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.vestigia.model.DescubrimientoModel;
import com.vestigia.model.EventoModel;
import com.vestigia.model.PuzzleEstadoModel;
import com.vestigia.model.PuzzleEstadoModel.Estado;
import com.vestigia.model.PuzzleEstadoModel.Puzzle;
import com.vestigia.model.SalaModel;
import com.vestigia.repository.DescubrimientoRepository;
import com.vestigia.repository.PuzzleEstadoRepository;

/**
 * Decide qué puzzles bloqueados pasan a disponible. Lee el estado completo de la sala cada vez, así que da igual qué lo
 * dispare (un descubrimiento nuevo o, desde el Paso 3b, un puzzle resuelto) y en qué orden llegaron los requisitos.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class HabilitacionPuzzlesService {

	/** Descubrimientos que deben existir y puzzles que deben estar resueltos. */
	record Requisito(Set<String> descubrimientos, Set<Puzzle> resueltos) {

		boolean cumplido(Set<String> claves, Set<Puzzle> puzzlesResueltos) {
			return claves.containsAll(descubrimientos) && puzzlesResueltos.containsAll(resueltos);
		}

	}

	static final Map<Puzzle, Requisito> REQUISITOS = new EnumMap<>(Map.of(
			Puzzle.radio, new Requisito(Set.of("pescador_dialogo_completo"), Set.of()),
			Puzzle.photo, new Requisito(Set.of("pieza_1", "pieza_2", "pieza_3"), Set.of()),
			Puzzle.lookout, new Requisito(Set.of("lookout_enabled"), Set.of(Puzzle.photo)),
			Puzzle.observatory, new Requisito(Set.of("coordenada_a", "coordenada_b", "observatory_enabled"),
					Set.of(Puzzle.lookout))));

	private final PuzzleEstadoRepository puzzles;

	private final DescubrimientoRepository descubrimientos;

	private final ActividadService actividad;

	private final Clock clock;

	public HabilitacionPuzzlesService(PuzzleEstadoRepository puzzles, DescubrimientoRepository descubrimientos,
			ActividadService actividad, Clock clock) {
		this.puzzles = puzzles;
		this.descubrimientos = descubrimientos;
		this.actividad = actividad;
		this.clock = clock;
	}

	/**
	 * Pasa a disponible cada puzzle bloqueado cuyos requisitos ya se cumplen y guarda un evento puzzle.habilitado por
	 * cada uno, con {@link #payload}. La sala debe venir bloqueada por {@link ActividadService#bloquear}. Devuelve los puzzles que cambiaron.
	 */
	public List<Puzzle> recalcular(SalaModel sala) {
		List<PuzzleEstadoModel> filas = puzzles.findByClaveSalaId(sala.getId());
		Map<Puzzle, Estado> estados = filas.stream()
				.collect(Collectors.toMap(PuzzleEstadoModel::getPuzzle, PuzzleEstadoModel::getEstado));
		Set<Puzzle> nuevos = habilitables(new HashSet<>(descubrimientos.findClavesBySalaId(sala.getId())), estados);
		Instant ahora = clock.instant();
		for (PuzzleEstadoModel fila : filas) {
			if (nuevos.contains(fila.getPuzzle())) {
				fila.setEstado(Estado.disponible);
				fila.setActualizadoEn(ahora);
				actividad.registrar(sala, EventoModel.Tipo.puzzle_habilitado, DescubrimientoModel.Origen.servidor,
						payload(fila));
			}
		}
		return List.copyOf(nuevos);
	}

	/**
	 * Lo que guarda cada evento de cambio de estado de un puzzle: {@code {puzzle, estado, datos}}, la misma forma que
	 * {@code puzzle.actualizado} del Paso 4.
	 */
	static Map<String, Object> payload(PuzzleEstadoModel fila) {
		return Map.of("puzzle", fila.getPuzzle().name(), "estado", fila.getEstado().name(), "datos", fila.getDatos());
	}

	/**
	 * Los puzzles bloqueados con sus requisitos cumplidos, en el orden del juego. Basta una pasada: los requisitos piden
	 * puzzles resueltos, y habilitar uno no resuelve ninguno.
	 */
	static Set<Puzzle> habilitables(Set<String> claves, Map<Puzzle, Estado> estados) {
		Set<Puzzle> resueltos = EnumSet.noneOf(Puzzle.class);
		estados.forEach((puzzle, estado) -> {
			if (estado == Estado.resuelto) {
				resueltos.add(puzzle);
			}
		});
		Set<Puzzle> habilitables = EnumSet.noneOf(Puzzle.class);
		REQUISITOS.forEach((puzzle, requisito) -> {
			if (estados.get(puzzle) == Estado.bloqueado && requisito.cumplido(claves, resueltos)) {
				habilitables.add(puzzle);
			}
		});
		return habilitables;
	}

}
