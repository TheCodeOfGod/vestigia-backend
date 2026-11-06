package com.vestigia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.dto.Sala;
import com.vestigia.model.DescubrimientoModel;
import com.vestigia.model.EventoModel;
import com.vestigia.model.PuzzleEstadoModel;
import com.vestigia.model.PuzzleEstadoModel.Estado;
import com.vestigia.model.PuzzleEstadoModel.Puzzle;
import com.vestigia.model.SalaModel;
import com.vestigia.repository.PuzzleEstadoRepository;

@ExtendWith(MockitoExtension.class)
class PuzzleServiceTest {

	private static final Instant AHORA = Instant.parse("2026-11-06T18:00:00Z");

	private static final Map<String, Object> COMPLETADA = Map.of("completada", true);

	private static final Map<String, Object> INICIADO = Map.of("iniciado", true);

	private static final Map<String, Object> SINTONIZADA = Map.of("sintonizada", true);

	@Mock
	private PuzzleEstadoRepository puzzles;

	@Mock
	private ActividadService actividad;

	@Mock
	private DescubrimientoService descubrimientos;

	private PuzzleService service;

	private final SalaModel sala = new SalaModel();

	private final UUID salaId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(sala, "id", UUID.randomUUID());
		service = new PuzzleService(puzzles, actividad, descubrimientos, Clock.fixed(AHORA, ZoneOffset.UTC), "137",
				"+042");
	}

	@Test
	@DisplayName("Solo vale una clave aceptada con el booleano true: texto, número, false, null, claves extra, vacío o una clave ajena dan 400 con details en datos")
	void soloTrueEsValido() {
		List<Map<String, Object>> invalidos = new ArrayList<>(List.of(Map.of("completada", "true"),
				Map.of("completada", 1), Map.of("completada", false), Map.of("completada", "si"),
				Map.of("completada", Map.of()), Map.of("completada", List.of(true)), Map.of(),
				Map.of("completada", true, "iniciado", true), Map.of("completada", true, "extra", 1),
				Map.of("terminada", true), Map.of("Completada", true)));
		invalidos.add(Collections.singletonMap("completada", null));

		for (Map<String, Object> datos : invalidos) {
			assertThatThrownBy(() -> service.reportar(credencial(SalaService.Rol.app), "photo", datos)).as("%s", datos)
					.isInstanceOfSatisfying(ApiException.class, ex -> {
						assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
						assertThat(ex.getCodigo()).isNull();
						assertThat(ex.getReason()).isEqualTo("Datos inválidos");
						assertThat(ex.getDetalles()).containsExactly(new ApiException.Detalle("datos",
								"en photo debe ser exactamente {\"completada\": true} o {\"iniciado\": true}"));
					});
		}
		verifyNoInteractions(actividad, puzzles, descubrimientos);
	}

	@Test
	@DisplayName("radio solo acepta {sintonizada: true}: iniciado o completada dan 400; los puzzles de la app no aceptan sintonizada")
	void avisosPorPuzzle() {
		for (Map<String, Object> datos : List.of(INICIADO, COMPLETADA)) {
			assertThatThrownBy(() -> service.reportar(credencial(SalaService.Rol.juego), "radio", datos)).as("%s", datos)
					.isInstanceOfSatisfying(ApiException.class, ex -> {
						assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
						assertThat(ex.getDetalles()).containsExactly(
								new ApiException.Detalle("datos", "en radio debe ser exactamente {\"sintonizada\": true}"));
					});
		}
		for (String puzzle : List.of("photo", "lookout", "observatory")) {
			assertThatThrownBy(() -> service.reportar(credencial(SalaService.Rol.app), puzzle, SINTONIZADA)).as(puzzle)
					.isInstanceOfSatisfying(ApiException.class,
							ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
		}
		verifyNoInteractions(actividad, puzzles, descubrimientos);
	}

	@Test
	@DisplayName("Un id de puzzle desconocido da 400 con la lista de ids, también los viejos fotografia, mirador y observatorio y las variantes de mayúsculas o espacios")
	void puzzleDesconocidoDa400() {
		for (String id : List.of("fotografia", "mirador", "observatorio", "Radio", "PHOTO", " radio", "radio ", "",
				"telescopio")) {
			assertThatThrownBy(() -> service.reportar(credencial(SalaService.Rol.app), id, COMPLETADA)).as("'%s'", id)
					.isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
						assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
						assertThat(ex.getReason()).isEqualTo(
								"'" + id + "' no es un puzzle válido. Usa uno de: radio, photo, lookout, observatory");
					});
		}
		verifyNoInteractions(actividad, puzzles, descubrimientos);
	}

	@Test
	@DisplayName("Un puzzle bloqueado da 409 con codigo puzzle_bloqueado ante cualquier aviso válido y no cambia nada")
	void bloqueadoDa409() {
		for (Map.Entry<String, Map<String, Object>> caso : Map.of("radio", SINTONIZADA, "photo", INICIADO, "lookout",
				COMPLETADA, "observatory", COMPLETADA).entrySet()) {
			PuzzleEstadoModel fila = fila(Puzzle.valueOf(caso.getKey()), Estado.bloqueado);

			assertThatThrownBy(() -> service.reportar(credencial(SalaService.Rol.app), caso.getKey(), caso.getValue()))
					.isInstanceOfSatisfying(ApiException.class, ex -> {
						assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
						assertThat(ex.getCodigo()).isEqualTo("puzzle_bloqueado");
						assertThat(ex.getReason()).isEqualTo("El puzzle '" + caso.getKey()
								+ "' está bloqueado: todavía no se cumplen sus requisitos");
					});
			assertThat(fila.getEstado()).isEqualTo(Estado.bloqueado);
			assertThat(fila.getDatos()).isEmpty();
		}
		verify(actividad, never()).renovar(any());
		verify(actividad, never()).registrar(any(), any(), any(), any());
		verifyNoInteractions(descubrimientos);
	}

	@Test
	@DisplayName("disponible más iniciado pasa a en_curso, guarda los datos y puzzle.iniciado con {puzzle, estado, datos} y el origen del rol, sin crear descubrimiento")
	void disponibleMasIniciadoPasaAEnCurso() {
		PuzzleEstadoModel fila = fila(Puzzle.lookout, Estado.disponible);

		Sala.Puzzle respuesta = service.reportar(credencial(SalaService.Rol.app), "lookout", INICIADO);

		assertThat(respuesta).isEqualTo(new Sala.Puzzle(Puzzle.lookout, Estado.en_curso, INICIADO));
		assertThat(fila.getEstado()).isEqualTo(Estado.en_curso);
		assertThat(fila.getActualizadoEn()).isEqualTo(AHORA);
		verify(actividad).registrar(sala, EventoModel.Tipo.puzzle_iniciado, DescubrimientoModel.Origen.app,
				Map.of("puzzle", "lookout", "estado", "en_curso", "datos", INICIADO));
		verifyNoInteractions(descubrimientos);
	}

	@Test
	@DisplayName("disponible o en_curso más completada pasa a resuelto: guarda puzzle.resuelto y después crea el resultado")
	void completadaResuelve() {
		for (Estado inicial : List.of(Estado.disponible, Estado.en_curso)) {
			PuzzleEstadoModel fila = fila(Puzzle.observatory, inicial);

			Sala.Puzzle respuesta = service.reportar(credencial(SalaService.Rol.app), "observatory", COMPLETADA);

			assertThat(respuesta).as("desde %s", inicial)
					.isEqualTo(new Sala.Puzzle(Puzzle.observatory, Estado.resuelto, COMPLETADA));
			assertThat(fila.getEstado()).isEqualTo(Estado.resuelto);
			InOrder orden = inOrder(actividad, descubrimientos);
			orden.verify(actividad).registrar(sala, EventoModel.Tipo.puzzle_resuelto, DescubrimientoModel.Origen.app,
					Map.of("puzzle", "observatory", "estado", "resuelto", "datos", COMPLETADA));
			orden.verify(descubrimientos).registrarDelServidor(sala, "magnetar_localizado", Map.of());
		}
	}

	@Test
	@DisplayName("en_curso más iniciado y resuelto más cualquier aviso responden el estado actual, solo renuevan la sala y no guardan eventos ni descubrimientos")
	void avisoQueNoMueveEsIdempotente() {
		Map<String, Object> datosResuelto = Map.of("completada", true);
		record Caso(Puzzle puzzle, Estado estado, Map<String, Object> datosGuardados, Map<String, Object> aviso) {
		}
		for (Caso caso : List.of(new Caso(Puzzle.photo, Estado.en_curso, INICIADO, INICIADO),
				new Caso(Puzzle.photo, Estado.resuelto, datosResuelto, INICIADO),
				new Caso(Puzzle.photo, Estado.resuelto, datosResuelto, COMPLETADA),
				new Caso(Puzzle.radio, Estado.resuelto, SINTONIZADA, SINTONIZADA))) {
			PuzzleEstadoModel fila = fila(caso.puzzle(), caso.estado());
			fila.setDatos(caso.datosGuardados());
			fila.setActualizadoEn(Instant.EPOCH);

			Sala.Puzzle respuesta = service.reportar(credencial(SalaService.Rol.app), caso.puzzle().name(),
					caso.aviso());

			assertThat(respuesta).as("%s", caso)
					.isEqualTo(new Sala.Puzzle(caso.puzzle(), caso.estado(), caso.datosGuardados()));
			assertThat(fila.getActualizadoEn()).isEqualTo(Instant.EPOCH);
		}
		verify(actividad, times(4)).renovar(sala);
		verify(actividad, never()).registrar(any(), any(), any(), any());
		verifyNoInteractions(descubrimientos);
	}

	@Test
	@DisplayName("Cada puzzle crea su resultado con origen servidor: coordenada_a con AR \"137\", coordenada_b con DEC \"+042\" como texto, observatorio_visible y magnetar_localizado")
	void cadaPuzzleCreaSuResultado() {
		Map<String, Map<String, Object>> creados = new HashMap<>();
		for (Puzzle puzzle : Puzzle.values()) {
			fila(puzzle, Estado.disponible);
			service.reportar(credencial(SalaService.Rol.juego), puzzle.name(),
					puzzle == Puzzle.radio ? SINTONIZADA : COMPLETADA);
		}
		ArgumentCaptor<String> clave = ArgumentCaptor.forClass(String.class);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
		verify(descubrimientos, times(4)).registrarDelServidor(eq(sala), clave.capture(),
				datos.capture());
		for (int i = 0; i < 4; i++) {
			creados.put(clave.getAllValues().get(i), datos.getAllValues().get(i));
		}

		assertThat(creados).containsExactlyInAnyOrderEntriesOf(Map.of(
				"coordenada_a", Map.of("tipo", "ascension_recta", "valor", "137"),
				"coordenada_b", Map.of("tipo", "declinacion", "valor", "+042"),
				"observatorio_visible", Map.of(),
				"magnetar_localizado", Map.of()));
		assertThat(creados.get("coordenada_b").get("valor")).isInstanceOf(String.class).isEqualTo("+042");
		assertThat(creados.keySet()).as("las claves que los clientes no pueden enviar")
				.isEqualTo(DescubrimientoService.CLAVES_DEL_SERVIDOR);
	}

	@Test
	@DisplayName("Las coordenadas salen de la configuración, sin convertirse a número")
	void coordenadasDeLaConfiguracion() {
		service = new PuzzleService(puzzles, actividad, descubrimientos, Clock.fixed(AHORA, ZoneOffset.UTC), "0099",
				"-007");
		fila(Puzzle.radio, Estado.disponible);
		fila(Puzzle.photo, Estado.disponible);

		service.reportar(credencial(SalaService.Rol.juego), "radio", SINTONIZADA);
		service.reportar(credencial(SalaService.Rol.app), "photo", COMPLETADA);

		verify(descubrimientos).registrarDelServidor(sala, "coordenada_a",
				Map.of("tipo", "ascension_recta", "valor", "0099"));
		verify(descubrimientos).registrarDelServidor(sala, "coordenada_b", Map.of("tipo", "declinacion", "valor", "-007"));
	}

	@Test
	@DisplayName("El evento lleva el origen del rol que avisó: juego o app")
	void origenDelRol() {
		fila(Puzzle.radio, Estado.disponible);
		service.reportar(credencial(SalaService.Rol.juego), "radio", SINTONIZADA);
		fila(Puzzle.photo, Estado.disponible);
		service.reportar(credencial(SalaService.Rol.app), "photo", INICIADO);

		verify(actividad).registrar(eq(sala), eq(EventoModel.Tipo.puzzle_resuelto),
				eq(DescubrimientoModel.Origen.juego), anyMap());
		verify(actividad).registrar(eq(sala), eq(EventoModel.Tipo.puzzle_iniciado), eq(DescubrimientoModel.Origen.app),
				anyMap());
	}

	@Test
	@DisplayName("Si la sala terminó entre el token y la escritura, el 401 de bloquear se propaga sin leer ni tocar el puzzle")
	void salaTerminadaNoTocaElPuzzle() {
		when(actividad.bloquear(salaId)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido"));

		assertThatThrownBy(() -> service.reportar(credencial(SalaService.Rol.app), "photo", COMPLETADA))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
		verifyNoInteractions(puzzles, descubrimientos);
	}

	/** Una fila del puzzle en ese estado, que devuelve el repositorio para la sala bloqueada. */
	private PuzzleEstadoModel fila(Puzzle puzzle, Estado estado) {
		PuzzleEstadoModel fila = new PuzzleEstadoModel(sala.getId(), puzzle, estado, Instant.EPOCH);
		when(actividad.bloquear(salaId)).thenReturn(sala);
		when(puzzles.findById(new PuzzleEstadoModel.Clave(sala.getId(), puzzle))).thenReturn(Optional.of(fila));
		return fila;
	}

	private SalaService.Credencial credencial(SalaService.Rol rol) {
		return new SalaService.Credencial(salaId, "ALAMOS", rol);
	}

}
