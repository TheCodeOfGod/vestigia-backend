package com.vestigia.service;

import java.time.Clock;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.dto.Sala;
import com.vestigia.model.EventoModel;
import com.vestigia.model.PuzzleEstadoModel;
import com.vestigia.model.PuzzleEstadoModel.Estado;
import com.vestigia.model.PuzzleEstadoModel.Puzzle;
import com.vestigia.model.SalaModel;
import com.vestigia.repository.PuzzleEstadoRepository;

/**
 * Los avisos de la app sobre los cuatro puzzles: los cuatro se juegan en el teléfono. La app dice cuándo empezó o se
 * completó cada uno; el servidor valida la forma del aviso y el orden (que el puzzle no esté bloqueado), lo guarda y
 * crea el resultado. El juego no avisa puzzles: su token recibe 403. No mide ni valida números.
 */
@Service
public class PuzzleService {

	private static final String PUZZLE_BLOQUEADO = "puzzle_bloqueado";

	private static final String PUZZLE_SOLO_APP = "puzzle_solo_app";

	private static final String PUZZLES = Arrays.stream(Puzzle.values())
			.map(Enum::name)
			.collect(Collectors.joining(", "));

	/** Lo que un cliente puede avisar en {@code datos}, el estado al que lleva el puzzle y el evento que guarda. */
	private enum Aviso {

		sintonizada(Estado.resuelto, EventoModel.Tipo.puzzle_resuelto),
		completada(Estado.resuelto, EventoModel.Tipo.puzzle_resuelto),
		iniciado(Estado.en_curso, EventoModel.Tipo.puzzle_iniciado);

		final Estado destino;

		final EventoModel.Tipo evento;

		Aviso(Estado destino, EventoModel.Tipo evento) {
			this.destino = destino;
			this.evento = evento;
		}

	}

	/** Los cuatro puzzles los juega la app: se inician y se completan. La radio se completa con {@code sintonizada}. */
	private static final Map<Puzzle, Set<Aviso>> AVISOS = new EnumMap<>(Map.of(
			Puzzle.radio, EnumSet.of(Aviso.sintonizada, Aviso.iniciado),
			Puzzle.photo, EnumSet.of(Aviso.completada, Aviso.iniciado),
			Puzzle.lookout, EnumSet.of(Aviso.completada, Aviso.iniciado),
			Puzzle.observatory, EnumSet.of(Aviso.completada, Aviso.iniciado)));

	/** El descubrimiento que crea el servidor al resolver un puzzle. */
	private record Resultado(String clave, Map<String, Object> datos) {
	}

	private final PuzzleEstadoRepository puzzles;

	private final ActividadService actividad;

	private final DescubrimientoService descubrimientos;

	private final Clock clock;

	private final Map<Puzzle, Resultado> resultados;

	public PuzzleService(PuzzleEstadoRepository puzzles, ActividadService actividad,
			DescubrimientoService descubrimientos, Clock clock,
			@Value("${vestigia.puzzles.coordenadas.ar}") String ascensionRecta,
			@Value("${vestigia.puzzles.coordenadas.dec}") String declinacion) {
		this.puzzles = puzzles;
		this.actividad = actividad;
		this.descubrimientos = descubrimientos;
		this.clock = clock;
		this.resultados = new EnumMap<>(Map.of(
				Puzzle.radio, new Resultado("coordenada_a", Map.of("tipo", "ascension_recta", "valor", ascensionRecta)),
				Puzzle.photo, new Resultado("coordenada_b", Map.of("tipo", "declinacion", "valor", declinacion)),
				Puzzle.lookout, new Resultado("observatorio_visible", Map.of()),
				Puzzle.observatory, new Resultado("magnetar_localizado", Map.of())));
	}

	/**
	 * Aplica el aviso de un cliente y devuelve el estado del puzzle. Un aviso solo mueve el puzzle hacia adelante:
	 * {@code iniciado} lleva de disponible a en_curso y {@code completada} o {@code sintonizada} llevan a resuelto. Un
	 * aviso que ya no lo mueve (en_curso más iniciado, o resuelto más cualquiera) responde el estado actual y solo
	 * renueva la sala. Al resolver, en la misma transacción, crea el resultado con origen servidor, que recalcula los
	 * puzzles dependientes. Las escrituras de la sala se serializan con el bloqueo de su fila, así que dos avisos
	 * simultáneos resuelven una sola vez. Solo la app avisa puzzles: el token del juego recibe 403 con
	 * {@code puzzle_solo_app}, después de validar el id y la forma del aviso.
	 */
	@Transactional
	public Sala.Puzzle reportar(SalaService.Credencial credencial, String id, Object datos) {
		Puzzle puzzle = puzzle(id);
		Aviso aviso = aviso(puzzle, datos);
		if (credencial.rol() != SalaService.Rol.app) {
			throw new ApiException(HttpStatus.FORBIDDEN, PUZZLE_SOLO_APP,
					"Los puzzles los avisa la app; el juego no puede reportar '" + puzzle + "'", List.of());
		}
		SalaModel sala = actividad.bloquear(credencial.salaId());
		PuzzleEstadoModel fila = puzzles.findById(new PuzzleEstadoModel.Clave(sala.getId(), puzzle))
				.orElseThrow(() -> new IllegalStateException("La sala " + sala.getId()
						+ " no tiene la fila del puzzle " + puzzle + "; crear la sala inserta las cuatro"));
		if (fila.getEstado() == Estado.bloqueado) {
			throw new ApiException(HttpStatus.CONFLICT, PUZZLE_BLOQUEADO,
					"El puzzle '" + puzzle + "' está bloqueado: todavía no se cumplen sus requisitos", List.of());
		}
		if (aviso.destino.compareTo(fila.getEstado()) <= 0) {
			actividad.renovar(sala);
			return dto(fila);
		}
		fila.setEstado(aviso.destino);
		fila.setDatos(Map.of(aviso.name(), true));
		fila.setActualizadoEn(clock.instant());
		actividad.registrar(sala, aviso.evento, credencial.rol().origen(), HabilitacionPuzzlesService.payload(fila));
		if (aviso.destino == Estado.resuelto) {
			Resultado resultado = resultados.get(puzzle);
			descubrimientos.registrarDelServidor(sala, resultado.clave(), resultado.datos());
		}
		return dto(fila);
	}

	/** El puzzle con ese id exacto, en minúsculas. Cualquier otro, incluidos los ids viejos en español, da 400. */
	private static Puzzle puzzle(String id) {
		return Arrays.stream(Puzzle.values())
				.filter(p -> p.name().equals(id))
				.findFirst()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"'" + id + "' no es un puzzle válido. Usa uno de: " + PUZZLES));
	}

	/**
	 * El aviso que trae {@code datos}: un objeto con exactamente una clave que el puzzle acepta, con el booleano
	 * {@code true}. Cualquier otra forma (un valor que no es objeto, texto "true", 1, false, null, claves extra u
	 * objeto vacío) da 400 con el detalle de lo que se esperaba.
	 */
	private static Aviso aviso(Puzzle puzzle, Object datos) {
		Set<Aviso> aceptados = AVISOS.get(puzzle);
		if (datos instanceof Map<?, ?> objeto && objeto.size() == 1) {
			Map.Entry<?, ?> unico = objeto.entrySet().iterator().next();
			for (Aviso aviso : aceptados) {
				if (aviso.name().equals(unico.getKey()) && Boolean.TRUE.equals(unico.getValue())) {
					return aviso;
				}
			}
		}
		String formas = aceptados.stream().map(a -> "{\"" + a + "\": true}").collect(Collectors.joining(" o "));
		throw new ApiException(HttpStatus.BAD_REQUEST, null, "Datos inválidos",
				List.of(new ApiException.Detalle("datos", "en " + puzzle + " debe ser exactamente " + formas)));
	}

	private static Sala.Puzzle dto(PuzzleEstadoModel fila) {
		return new Sala.Puzzle(fila.getPuzzle(), fila.getEstado(), fila.getDatos());
	}

}
