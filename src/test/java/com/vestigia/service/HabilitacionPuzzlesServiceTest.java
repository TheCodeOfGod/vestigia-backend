package com.vestigia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vestigia.model.DescubrimientoModel;
import com.vestigia.model.EventoModel;
import com.vestigia.model.PuzzleEstadoModel;
import com.vestigia.model.PuzzleEstadoModel.Estado;
import com.vestigia.model.PuzzleEstadoModel.Puzzle;
import com.vestigia.model.SalaModel;
import com.vestigia.repository.DescubrimientoRepository;
import com.vestigia.repository.PuzzleEstadoRepository;

@ExtendWith(MockitoExtension.class)
class HabilitacionPuzzlesServiceTest {

	private static final Instant AHORA = Instant.parse("2026-11-05T18:00:00Z");

	private static final Set<String> COORDENADAS_Y_BANDERA = Set.of("coordenada_a", "coordenada_b",
			"observatory_enabled");

	@Mock
	private PuzzleEstadoRepository puzzles;

	@Mock
	private DescubrimientoRepository descubrimientos;

	@Mock
	private ActividadService actividad;

	private HabilitacionPuzzlesService service;

	@BeforeEach
	void setUp() {
		service = new HabilitacionPuzzlesService(puzzles, descubrimientos, actividad,
				Clock.fixed(AHORA, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("radio se habilita con pescador_dialogo_completo y no antes")
	void radioPideElDialogoDelPescador() {
		assertThat(HabilitacionPuzzlesService.habilitables(Set.of(), todosBloqueados())).isEmpty();
		assertThat(HabilitacionPuzzlesService.habilitables(Set.of("pescador_dialogo_completo"), todosBloqueados()))
				.containsExactly(Puzzle.radio);
	}

	@Test
	@DisplayName("photo se habilita solo con las tres piezas, lleguen en el orden que lleguen")
	void photoPideLasTresPiezas() {
		assertThat(HabilitacionPuzzlesService.habilitables(Set.of("pieza_1", "pieza_3"), todosBloqueados()))
				.isEmpty();
		assertThat(HabilitacionPuzzlesService.habilitables(Set.of("pieza_3", "pieza_1", "pieza_2"),
				todosBloqueados())).containsExactly(Puzzle.photo);
	}

	@Test
	@DisplayName("lookout pide photo resuelto y lookout_enabled; la bandera puede llegar antes o después")
	void lookoutPidePhotoResueltoYSuBandera() {
		Map<Puzzle, Estado> photoResuelto = estados(Estado.resuelto, Estado.resuelto, Estado.bloqueado,
				Estado.bloqueado);
		Map<Puzzle, Estado> photoEnCurso = estados(Estado.resuelto, Estado.en_curso, Estado.bloqueado,
				Estado.bloqueado);

		assertThat(HabilitacionPuzzlesService.habilitables(Set.of("lookout_enabled"), photoEnCurso)).isEmpty();
		assertThat(HabilitacionPuzzlesService.habilitables(Set.of(), photoResuelto)).isEmpty();
		assertThat(HabilitacionPuzzlesService.habilitables(Set.of("lookout_enabled"), photoResuelto))
				.containsExactly(Puzzle.lookout);
	}

	@Test
	@DisplayName("observatory pide coordenada_a, coordenada_b, observatory_enabled y lookout resuelto; falta uno y no se habilita")
	void observatoryPideCoordenadasBanderaYLookoutResuelto() {
		Map<Puzzle, Estado> lookoutResuelto = estados(Estado.resuelto, Estado.resuelto, Estado.resuelto,
				Estado.bloqueado);
		Map<Puzzle, Estado> lookoutDisponible = estados(Estado.resuelto, Estado.resuelto, Estado.disponible,
				Estado.bloqueado);

		assertThat(HabilitacionPuzzlesService.habilitables(COORDENADAS_Y_BANDERA, lookoutResuelto))
				.containsExactly(Puzzle.observatory);
		assertThat(HabilitacionPuzzlesService.habilitables(COORDENADAS_Y_BANDERA, lookoutDisponible)).isEmpty();
		for (String falta : COORDENADAS_Y_BANDERA) {
			Set<String> claves = new HashSet<>(COORDENADAS_Y_BANDERA);
			claves.remove(falta);
			assertThat(HabilitacionPuzzlesService.habilitables(claves, lookoutResuelto)).as("sin " + falta)
					.isEmpty();
		}
	}

	@Test
	@DisplayName("Solo un puzzle bloqueado pasa a disponible: los que están disponibles, en curso o resueltos no cambian")
	void soloSeHabilitaLoBloqueado() {
		Set<String> todo = Set.of("pescador_dialogo_completo", "pieza_1", "pieza_2", "pieza_3", "lookout_enabled",
				"coordenada_a", "coordenada_b", "observatory_enabled");

		assertThat(HabilitacionPuzzlesService.habilitables(todo,
				estados(Estado.disponible, Estado.resuelto, Estado.en_curso, Estado.bloqueado))).isEmpty();
		assertThat(HabilitacionPuzzlesService.habilitables(todo,
				estados(Estado.resuelto, Estado.resuelto, Estado.resuelto, Estado.bloqueado)))
				.containsExactly(Puzzle.observatory);
	}

	@Test
	@DisplayName("Recalcular pasa a disponible los puzzles habilitados y guarda un evento puzzle.habilitado de origen servidor por cada uno")
	void recalcularActualizaFilasYGuardaEventos() {
		SalaModel sala = new SalaModel();
		UUID id = sala.getId();
		List<PuzzleEstadoModel> filas = filas(id, Estado.bloqueado, Estado.bloqueado, Estado.bloqueado,
				Estado.bloqueado);
		when(puzzles.findByClaveSalaId(id)).thenReturn(filas);
		when(descubrimientos.findClavesBySalaId(id))
				.thenReturn(List.of("pieza_2", "pescador_dialogo_completo", "pieza_1", "pieza_3"));

		assertThat(service.recalcular(sala)).containsExactly(Puzzle.radio, Puzzle.photo);

		assertThat(filas).extracting(PuzzleEstadoModel::getEstado)
				.containsExactly(Estado.disponible, Estado.disponible, Estado.bloqueado, Estado.bloqueado);
		assertThat(filas.get(0).getActualizadoEn()).isEqualTo(AHORA);
		assertThat(filas.get(2).getActualizadoEn()).isEqualTo(Instant.EPOCH);
		verify(actividad).registrar(sala, EventoModel.Tipo.puzzle_habilitado, DescubrimientoModel.Origen.servidor,
				Map.of("puzzle", "radio"));
		verify(actividad).registrar(sala, EventoModel.Tipo.puzzle_habilitado, DescubrimientoModel.Origen.servidor,
				Map.of("puzzle", "photo"));
	}

	@Test
	@DisplayName("Recalcular sin requisitos nuevos no cambia nada ni guarda eventos")
	void recalcularSinCambiosNoGuardaEventos() {
		SalaModel sala = new SalaModel();
		when(puzzles.findByClaveSalaId(sala.getId())).thenReturn(filas(sala.getId(), Estado.disponible,
				Estado.bloqueado, Estado.bloqueado, Estado.bloqueado));
		when(descubrimientos.findClavesBySalaId(sala.getId())).thenReturn(List.of("pescador_dialogo_completo"));

		assertThat(service.recalcular(sala)).isEmpty();
		verifyNoInteractions(actividad);
	}

	private static Map<Puzzle, Estado> todosBloqueados() {
		return estados(Estado.bloqueado, Estado.bloqueado, Estado.bloqueado, Estado.bloqueado);
	}

	/** Estados en el orden del juego: radio, photo, lookout, observatory. */
	private static Map<Puzzle, Estado> estados(Estado... enOrden) {
		Map<Puzzle, Estado> estados = new EnumMap<>(Puzzle.class);
		for (Puzzle puzzle : Puzzle.values()) {
			estados.put(puzzle, enOrden[puzzle.ordinal()]);
		}
		return estados;
	}

	private static List<PuzzleEstadoModel> filas(UUID salaId, Estado... enOrden) {
		return estados(enOrden).entrySet().stream()
				.map(e -> new PuzzleEstadoModel(salaId, e.getKey(), e.getValue(), Instant.EPOCH))
				.toList();
	}

}
