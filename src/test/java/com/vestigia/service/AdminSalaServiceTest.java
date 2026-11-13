package com.vestigia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.dto.PaginaSalas;
import com.vestigia.dto.Sala;
import com.vestigia.dto.SalaResumen;
import com.vestigia.model.DescubrimientoModel;
import com.vestigia.model.EventoModel;
import com.vestigia.model.PuzzleEstadoModel;
import com.vestigia.model.SalaModel;
import com.vestigia.repository.EventoRepository;
import com.vestigia.repository.PuzzleEstadoRepository;
import com.vestigia.repository.SalaRepository;

class AdminSalaServiceTest {

	private final SalaRepository salas = mock(SalaRepository.class);

	private final PuzzleEstadoRepository puzzles = mock(PuzzleEstadoRepository.class);

	private final EventoRepository eventos = mock(EventoRepository.class);

	private final SalaService salaService = mock(SalaService.class);

	private final ActividadService actividad = mock(ActividadService.class);

	private final SalaService.Presencia presencia = mock(SalaService.Presencia.class);

	private AdminSalaService servicio;

	private SalaModel viva;

	@BeforeEach
	void preparar() {
		servicio = new AdminSalaService(salas, puzzles, eventos, salaService, actividad, presencia);
		viva = sala("BACALAR", SalaModel.Estado.activa, "hash-app");
		when(presencia.dispositivos(any())).thenReturn(new Sala.Dispositivos(true, false));
	}

	@Test
	@DisplayName("El listado cuenta los puzzles resueltos de cada sala con una sola consulta de puzzles")
	void listarResumeCadaSala() {
		SalaModel esperando = sala("TULUM", SalaModel.Estado.esperando_app, null);
		when(salas.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(viva, esperando)));
		when(puzzles.findByClaveSalaIdIn(any())).thenReturn(List.of(
				puzzle(viva, PuzzleEstadoModel.Puzzle.radio, PuzzleEstadoModel.Estado.resuelto),
				puzzle(viva, PuzzleEstadoModel.Puzzle.photo, PuzzleEstadoModel.Estado.disponible),
				puzzle(esperando, PuzzleEstadoModel.Puzzle.radio, PuzzleEstadoModel.Estado.bloqueado)));

		List<SalaResumen> resumen = servicio.listar(null, null, null).contenido();

		assertThat(resumen).extracting(SalaResumen::codigo).containsExactly("BACALAR", "TULUM");
		assertThat(resumen.get(0).puzzlesResueltos()).isEqualTo(1);
		assertThat(resumen.get(0).puzzlesTotal()).isEqualTo(2);
		assertThat(resumen.get(0).appUnida()).isTrue();
		assertThat(resumen.get(1).appUnida()).isFalse();
		assertThat(resumen.get(0).dispositivos().juego()).isTrue();
		verify(puzzles).findByClaveSalaIdIn(any());
	}

	@Test
	@DisplayName("La página empieza en 0 y no baja de ahí; el tamaño vale 20 por defecto y se acota entre 1 y 100")
	void paginaYTamano() {
		when(salas.findAll(any(Pageable.class))).thenReturn(Page.empty());
		ArgumentCaptor<Pageable> pedida = ArgumentCaptor.forClass(Pageable.class);

		servicio.listar(null, null, null);
		servicio.listar(null, 3, 9999);
		servicio.listar(null, -2, -3);

		verify(salas, org.mockito.Mockito.times(3)).findAll(pedida.capture());
		assertThat(pedida.getAllValues()).extracting(Pageable::getPageSize).containsExactly(20, 100, 1);
		assertThat(pedida.getAllValues()).extracting(Pageable::getPageNumber).containsExactly(0, 3, 0);
	}

	@Test
	@DisplayName("Con estado filtra por él y la respuesta trae el total y el número de páginas")
	void filtroYTotales() {
		when(salas.findByEstado(eq(SalaModel.Estado.activa), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(viva), org.springframework.data.domain.PageRequest.of(1, 1), 3));
		when(puzzles.findByClaveSalaIdIn(any())).thenReturn(List.of());

		PaginaSalas respuesta = servicio.listar(SalaModel.Estado.activa, 1, 1);

		assertThat(respuesta.contenido()).extracting(SalaResumen::codigo).containsExactly("BACALAR");
		assertThat(respuesta.pagina()).isEqualTo(1);
		assertThat(respuesta.tamano()).isEqualTo(1);
		assertThat(respuesta.total()).isEqualTo(3);
		assertThat(respuesta.totalPaginas()).isEqualTo(3);
	}

	@Test
	@DisplayName("Cerrar una sala viva la deja finalizada y guarda sala.finalizada con origen servidor")
	void cerrarSalaViva() {
		UUID admin = UUID.randomUUID();
		when(salas.findById(viva.getId())).thenReturn(Optional.of(viva));
		when(salas.bloquear(viva.getId())).thenReturn(Optional.of(viva));

		servicio.cerrar(viva.getId(), admin);

		assertThat(viva.getEstado()).isEqualTo(SalaModel.Estado.finalizada);
		verify(actividad).registrar(viva, EventoModel.Tipo.sala_finalizada, DescubrimientoModel.Origen.servidor,
				Map.of("cerrada_por", "admin", "usuario", admin.toString()));
	}

	@Test
	@DisplayName("Cerrar una sala que ya terminó no cambia nada ni guarda otro evento")
	void cerrarSalaTerminadaEsIdempotente() {
		SalaModel terminada = sala("ACULCO", SalaModel.Estado.expirada, "hash");
		when(salas.findById(terminada.getId())).thenReturn(Optional.of(terminada));
		when(salas.bloquear(terminada.getId())).thenReturn(Optional.of(terminada));

		servicio.cerrar(terminada.getId(), UUID.randomUUID());

		assertThat(terminada.getEstado()).isEqualTo(SalaModel.Estado.expirada);
		verify(actividad, never()).registrar(any(), any(), any(), any());
	}

	@Test
	void salaInexistenteDa404() {
		UUID id = UUID.randomUUID();
		when(salas.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.cerrar(id, UUID.randomUUID()))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
		assertThatThrownBy(() -> servicio.detalle(id)).isInstanceOf(ResponseStatusException.class);
	}

	private static SalaModel sala(String codigo, SalaModel.Estado estado, String tokenApp) {
		SalaModel s = new SalaModel();
		ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
		s.setCodigo(codigo);
		s.setEstado(estado);
		s.setEscenaActual(SalaModel.Escena.introduccion);
		s.setTokenAppHash(tokenApp);
		s.setCreadaEn(Instant.parse("2026-11-08T22:40:00Z"));
		s.setUltimaActividad(Instant.parse("2026-11-08T23:05:00Z"));
		s.setExpiraEn(Instant.parse("2026-11-09T01:05:00Z"));
		return s;
	}

	private static PuzzleEstadoModel puzzle(SalaModel sala, PuzzleEstadoModel.Puzzle puzzle,
			PuzzleEstadoModel.Estado estado) {
		return new PuzzleEstadoModel(sala.getId(), puzzle, estado, Instant.parse("2026-11-08T23:05:00Z"));
	}

}
