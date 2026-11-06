package com.vestigia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.dto.AppUnida;
import com.vestigia.dto.Pueblo;
import com.vestigia.dto.Sala;
import com.vestigia.dto.SalaCreada;
import com.vestigia.model.CodigoPuebloModel;
import com.vestigia.model.DescubrimientoModel;
import com.vestigia.model.EventoModel;
import com.vestigia.model.PuzzleEstadoModel;
import com.vestigia.model.SalaModel;
import com.vestigia.repository.CodigoPuebloRepository;
import com.vestigia.repository.DescubrimientoRepository;
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

	@Mock
	private DescubrimientoRepository descubrimientos;

	@Mock
	private ActividadService actividad;

	private SalaService service;

	@BeforeEach
	void setUp() {
		service = new SalaService(pueblos, salas, puzzles, descubrimientos, actividad,
				Clock.fixed(AHORA, ZoneOffset.UTC), Duration.ofHours(2));
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
		assertThat(sala.getValue().getEscenaActual()).isEqualTo(SalaModel.Escena.introduccion);
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

	@Test
	void unirseEnlazaConUnSoloUpdateYRenuevaLaExpiracion() throws Exception {
		ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
		when(salas.unirApp(eq("ALAMOS"), hash.capture(), eq(AHORA), eq(AHORA.plus(Duration.ofHours(2))),
				eq(SalaModel.Estado.esperando_app), eq(SalaModel.Estado.activa))).thenReturn(1);
		when(salas.findByCodigoAndEstadoIn("ALAMOS", SalaModel.Estado.VIVOS)).thenReturn(Optional.of(new SalaModel()));

		AppUnida unida = service.unirse(" alamos");

		assertThat(hash.getValue()).isEqualTo(sha256(unida.token()));
	}

	@Test
	@DisplayName("Crear la sala guarda el evento sala.creada con origen juego y el código en el payload")
	void crearRegistraElEventoSalaCreada() {
		when(pueblos.existsById("ALAMOS")).thenReturn(true);
		when(salas.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

		service.crear("alamos");

		ArgumentCaptor<SalaModel> sala = ArgumentCaptor.forClass(SalaModel.class);
		verify(actividad).registrar(sala.capture(), eq(EventoModel.Tipo.sala_creada),
				eq(DescubrimientoModel.Origen.juego), eq(Map.of("codigo", "ALAMOS")));
		assertThat(sala.getValue().getCodigo()).isEqualTo("ALAMOS");
	}

	@Test
	@DisplayName("Unirse guarda el evento app.unida con origen app sobre la sala enlazada")
	void unirseRegistraElEventoAppUnida() {
		SalaModel sala = new SalaModel();
		when(salas.unirApp(eq("ALAMOS"), anyString(), any(), any(), any(), any())).thenReturn(1);
		when(salas.findByCodigoAndEstadoIn("ALAMOS", SalaModel.Estado.VIVOS)).thenReturn(Optional.of(sala));

		service.unirse("ALAMOS");

		verify(actividad).registrar(sala, EventoModel.Tipo.app_unida, DescubrimientoModel.Origen.app, Map.of());
	}

	@Test
	@DisplayName("Si unirse pierde la carrera no guarda ningún evento")
	void unirseFallidoNoRegistraEvento() {
		when(salas.unirApp(eq("ALAMOS"), anyString(), any(), any(), any(), any())).thenReturn(0);
		when(salas.findByCodigoAndEstadoIn("ALAMOS", SalaModel.Estado.VIVOS)).thenReturn(Optional.of(new SalaModel()));

		assertThatThrownBy(() -> service.unirse("ALAMOS")).isInstanceOf(ResponseStatusException.class);
		verifyNoInteractions(actividad);
	}

	@Test
	void unirseASalaVivaQueYaTieneAppDa409() {
		when(salas.unirApp(eq("ALAMOS"), anyString(), any(), any(), any(), any())).thenReturn(0);
		when(salas.findByCodigoAndEstadoIn("ALAMOS", SalaModel.Estado.VIVOS)).thenReturn(Optional.of(new SalaModel()));

		assertThatThrownBy(() -> service.unirse("ALAMOS")).isInstanceOfSatisfying(ResponseStatusException.class,
				ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
	}

	@Test
	void unirseSinSalaVivaDa404() {
		when(salas.unirApp(eq("ALAMOS"), anyString(), any(), any(), any(), any())).thenReturn(0);
		when(salas.findByCodigoAndEstadoIn("ALAMOS", SalaModel.Estado.VIVOS)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.unirse("ALAMOS")).isInstanceOfSatisfying(ResponseStatusException.class,
				ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
	}

	@Test
	void autenticarDistingueElRolPorElHash() throws Exception {
		SalaModel sala = new SalaModel();
		sala.setCodigo("ALAMOS");
		sala.setTokenJuegoHash(sha256("tok-juego"));
		sala.setTokenAppHash(sha256("tok-app"));
		when(salas.findByTokenHashAndEstadoIn(anyString(), eq(SalaModel.Estado.VIVOS))).thenReturn(Optional.of(sala));

		assertThat(service.autenticar("tok-juego")).get().extracting(SalaService.Credencial::rol)
				.isEqualTo(SalaService.Rol.juego);
		assertThat(service.autenticar("tok-app")).get().extracting(SalaService.Credencial::rol)
				.isEqualTo(SalaService.Rol.app);
		verify(salas).findByTokenHashAndEstadoIn(sha256("tok-juego"), SalaModel.Estado.VIVOS);
	}

	@Test
	void autenticarConTokenDesconocidoNoDaCredencial() {
		when(salas.findByTokenHashAndEstadoIn(anyString(), any())).thenReturn(Optional.empty());

		assertThat(service.autenticar("otro")).isEmpty();
	}

	@Test
	void estadoOrdenaLosPuzzlesYReportaDispositivosDesconectados() {
		UUID id = UUID.randomUUID();
		SalaModel sala = new SalaModel();
		sala.setCodigo("ALAMOS");
		sala.setEstado(SalaModel.Estado.activa);
		sala.setEscenaActual(SalaModel.Escena.introduccion);
		sala.setCreadaEn(AHORA);
		sala.setExpiraEn(AHORA.plusSeconds(60));
		when(salas.findById(id)).thenReturn(Optional.of(sala));
		when(puzzles.findByClaveSalaId(id)).thenReturn(List.of(
				new PuzzleEstadoModel(id, PuzzleEstadoModel.Puzzle.observatory, PuzzleEstadoModel.Estado.bloqueado, AHORA),
				new PuzzleEstadoModel(id, PuzzleEstadoModel.Puzzle.radio, PuzzleEstadoModel.Estado.bloqueado, AHORA)));
		when(descubrimientos.findBySalaIdOrderByCreadoEnAscIdAsc(id)).thenReturn(List.of());

		Sala estado = service.estado(id);

		assertThat(estado.puzzles()).extracting(Sala.Puzzle::puzzle)
				.containsExactly(PuzzleEstadoModel.Puzzle.radio, PuzzleEstadoModel.Puzzle.observatory);
		assertThat(estado.dispositivos()).isEqualTo(new Sala.Dispositivos(false, false));
		assertThat(estado.expiraEn()).isEqualTo(AHORA.plusSeconds(60));
	}

	@Test
	@DisplayName("La app no puede cambiar la escena: 403 sin tocar la sala")
	void appNoCambiaLaEscena() {
		assertThatThrownBy(() -> service.cambiarEscena(credencial(SalaService.Rol.app), "faro"))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
		verifyNoInteractions(actividad);
	}

	@Test
	@DisplayName("Una escena fuera de introduccion, pueblo, faro, observatorio y desenlace da 400 con la lista de válidas")
	void escenaInvalidaDa400() {
		for (String escena : new String[] { "bosque", "Faro", " faro", "FARO" }) {
			assertThatThrownBy(() -> service.cambiarEscena(credencial(SalaService.Rol.juego), escena)).as(escena)
					.isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
						assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
						assertThat(ex.getReason()).contains("introduccion, pueblo, faro, observatorio, desenlace");
					});
		}
		verifyNoInteractions(actividad);
	}

	@Test
	@DisplayName("Cambiar la escena la guarda y registra escena.cambiada con la anterior y la nueva")
	void cambiarEscenaRegistraEvento() {
		SalaService.Credencial credencial = credencial(SalaService.Rol.juego);
		SalaModel sala = new SalaModel();
		sala.setEscenaActual(SalaModel.Escena.pueblo);
		when(actividad.bloquear(credencial.salaId())).thenReturn(sala);

		assertThat(service.cambiarEscena(credencial, "faro").escenaActual()).isEqualTo(SalaModel.Escena.faro);

		assertThat(sala.getEscenaActual()).isEqualTo(SalaModel.Escena.faro);
		verify(actividad).registrar(sala, EventoModel.Tipo.escena_cambiada, DescubrimientoModel.Origen.juego,
				Map.of("anterior", "pueblo", "escena", "faro"));
	}

	@Test
	@DisplayName("Repetir la escena actual responde igual y solo renueva la sala, sin evento")
	void repetirEscenaSoloRenueva() {
		SalaService.Credencial credencial = credencial(SalaService.Rol.juego);
		SalaModel sala = new SalaModel();
		sala.setEscenaActual(SalaModel.Escena.faro);
		when(actividad.bloquear(credencial.salaId())).thenReturn(sala);

		assertThat(service.cambiarEscena(credencial, "faro").escenaActual()).isEqualTo(SalaModel.Escena.faro);

		verify(actividad).renovar(sala);
		verify(actividad, never()).registrar(any(), any(), any(), any());
	}

	@Test
	@DisplayName("La app no puede cerrar la sala: 403 sin tocarla")
	void appNoCierraLaSala() {
		assertThatThrownBy(() -> service.finalizar(credencial(SalaService.Rol.app)))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
		verifyNoInteractions(actividad);
	}

	@Test
	@DisplayName("Cerrar la sala la pasa a finalizada y guarda sala.finalizada con origen juego")
	void finalizarPasaAFinalizadaYRegistraEvento() {
		SalaService.Credencial credencial = credencial(SalaService.Rol.juego);
		SalaModel sala = new SalaModel();
		sala.setEstado(SalaModel.Estado.activa);
		when(actividad.bloquear(credencial.salaId())).thenReturn(sala);

		service.finalizar(credencial);

		assertThat(sala.getEstado()).isEqualTo(SalaModel.Estado.finalizada);
		verify(actividad).registrar(sala, EventoModel.Tipo.sala_finalizada, DescubrimientoModel.Origen.juego,
				Map.of());
	}

	@Test
	void expirarVencidasUsaLaHoraDelReloj() {
		service.expirarVencidas();

		verify(salas).expirarVencidas(AHORA, SalaModel.Estado.VIVOS, SalaModel.Estado.expirada);
	}

	private static SalaService.Credencial credencial(SalaService.Rol rol) {
		return new SalaService.Credencial(UUID.randomUUID(), "ALAMOS", rol);
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
