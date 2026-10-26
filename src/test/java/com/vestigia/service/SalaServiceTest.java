package com.vestigia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.sql.SQLException;
import java.util.List;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.dto.Pueblo;
import com.vestigia.dto.SalaCreada;
import com.vestigia.model.CodigoPuebloModel;
import com.vestigia.model.PuzzleEstadoModel;
import com.vestigia.model.SalaModel;
import com.vestigia.repository.CodigoPuebloRepository;
import com.vestigia.repository.PuzzleEstadoRepository;
import com.vestigia.repository.SalaRepository;

@ExtendWith(MockitoExtension.class)
class SalaServiceTest {

	private static final Instant AHORA = Instant.parse("2026-10-26T18:00:00Z");

	@Mock
	private CodigoPuebloRepository pueblos;

	@Mock
	private SalaRepository salas;

	@Mock
	private PuzzleEstadoRepository puzzles;

	private SalaService service;

	@BeforeEach
	void setUp() {
		service = new SalaService(pueblos, salas, puzzles, Clock.fixed(AHORA, ZoneOffset.UTC), Duration.ofHours(2));
	}

	@Test
	void crearNormalizaElCodigoYGuardaSoloElHashDelToken() throws Exception {
		when(pueblos.existsById("ALAMOS")).thenReturn(true);
		when(salas.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

		SalaCreada creada = service.crear("  alamos ");

		ArgumentCaptor<SalaModel> sala = ArgumentCaptor.forClass(SalaModel.class);
		verify(salas).saveAndFlush(sala.capture());
		assertThat(creada.codigo()).isEqualTo("ALAMOS");
		assertThat(creada.estado()).isEqualTo(SalaModel.Estado.esperando_app);
		assertThat(creada.token()).matches("[A-Za-z0-9_-]{43}");
		assertThat(sala.getValue().getCodigo()).isEqualTo("ALAMOS");
		assertThat(sala.getValue().getTokenJuegoHash()).isEqualTo(sha256(creada.token())).hasSize(64);
		assertThat(sala.getValue().getTokenAppHash()).isNull();
		assertThat(sala.getValue().getEscenaActual()).isEqualTo("introduccion");
		assertThat(sala.getValue().getUltimaActividad()).isEqualTo(AHORA);
		assertThat(sala.getValue().getExpiraEn()).isEqualTo(AHORA.plus(Duration.ofHours(2)));
	}

	@Test
	void crearInsertaLosCuatroPuzzlesBloqueados() {
		when(pueblos.existsById("ALAMOS")).thenReturn(true);
		when(salas.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

		service.crear("ALAMOS");

		ArgumentCaptor<PuzzleEstadoModel> puzzle = ArgumentCaptor.forClass(PuzzleEstadoModel.class);
		verify(puzzles, times(4)).save(puzzle.capture());
		assertThat(puzzle.getAllValues()).extracting(PuzzleEstadoModel::getPuzzle)
				.containsExactly(PuzzleEstadoModel.Puzzle.values());
		assertThat(puzzle.getAllValues()).extracting(PuzzleEstadoModel::getEstado)
				.containsOnly(PuzzleEstadoModel.Estado.bloqueado);
	}

	@Test
	void cadaSalaRecibeUnTokenDistinto() {
		when(pueblos.existsById(any())).thenReturn(true);
		when(salas.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

		assertThat(service.crear("ALAMOS").token()).isNotEqualTo(service.crear("BERNAL").token());
	}

	@Test
	void crearFueraDelCatalogoDa400SinTocarSalas() {
		when(pueblos.existsById("ATLANTIDA")).thenReturn(false);

		assertThatThrownBy(() -> service.crear("Atlantida")).isInstanceOfSatisfying(ResponseStatusException.class,
				ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
		verify(salas, never()).saveAndFlush(any());
	}

	@Test
	void crearConPuebloOcupadoDa409() {
		when(pueblos.existsById("ALAMOS")).thenReturn(true);
		when(salas.saveAndFlush(any())).thenThrow(violacion("salas_codigo_viva"));

		assertThatThrownBy(() -> service.crear("ALAMOS")).isInstanceOfSatisfying(ResponseStatusException.class,
				ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
		verify(puzzles, never()).save(any());
	}

	@Test
	void otraViolacionDeIntegridadNoSeDisfrazaDe409() {
		when(pueblos.existsById("ALAMOS")).thenReturn(true);
		DataIntegrityViolationException otra = violacion("salas_estado_check");
		when(salas.saveAndFlush(any())).thenThrow(otra);

		assertThatThrownBy(() -> service.crear("ALAMOS")).isSameAs(otra);
	}

	@Test
	void listarPueblosMarcaOcupadosLosQueTienenSalaViva() {
		when(pueblos.findAll(Sort.by("codigo"))).thenReturn(
				List.of(new CodigoPuebloModel("ALAMOS"), new CodigoPuebloModel("BERNAL")));
		when(salas.findCodigosByEstadoIn(SalaModel.Estado.VIVOS)).thenReturn(List.of("BERNAL"));

		assertThat(service.listarPueblos(false)).containsExactly(new Pueblo("ALAMOS", true),
				new Pueblo("BERNAL", false));
		assertThat(service.listarPueblos(true)).containsExactly(new Pueblo("ALAMOS", true));
	}

	private static DataIntegrityViolationException violacion(String constraint) {
		return new DataIntegrityViolationException("violación",
				new ConstraintViolationException("violación", new SQLException(), constraint));
	}

	private static String sha256(String texto) throws Exception {
		return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
	}

}
