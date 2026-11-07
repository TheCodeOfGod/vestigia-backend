package com.vestigia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.model.DescubrimientoModel;
import com.vestigia.model.EventoModel;
import com.vestigia.model.SalaModel;
import com.vestigia.repository.DescubrimientoRepository;

@ExtendWith(MockitoExtension.class)
class DescubrimientoServiceTest {

	private static final Instant AHORA = Instant.parse("2026-11-05T18:00:00Z");

	/** Una clave que no es del juego ni del servidor: la app puede registrarla. */
	private static final String CLAVE_LIBRE = "nota_de_prueba";

	static final List<String> CLAVES_DEL_JUEGO = List.of("pescador_dialogo_completo", "pieza_1", "pieza_2", "pieza_3",
			"lookout_enabled", "observatory_enabled");

	@Mock
	private DescubrimientoRepository descubrimientos;

	@Mock
	private ActividadService actividad;

	@Mock
	private HabilitacionPuzzlesService habilitacion;

	private DescubrimientoService service;

	private final SalaModel sala = new SalaModel();

	private final UUID salaId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(sala, "id", UUID.randomUUID());
		service = new DescubrimientoService(descubrimientos, actividad, habilitacion,
				Clock.fixed(AHORA, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("Un descubrimiento nuevo se guarda con el origen del rol, guarda su evento y luego recalcula los puzzles")
	void nuevoGuardaEventoYRecalcula() {
		when(actividad.bloquear(salaId)).thenReturn(sala);
		when(descubrimientos.findBySalaIdAndClave(sala.getId(), CLAVE_LIBRE)).thenReturn(Optional.empty());
		when(descubrimientos.save(any())).thenAnswer(inv -> inv.getArgument(0));

		DescubrimientoService.Registro registro = service.registrar(credencial(SalaService.Rol.app), CLAVE_LIBRE,
				Map.of("nota", "bajo el muelle"));

		assertThat(registro.nuevo()).isTrue();
		assertThat(registro.descubrimiento().clave()).isEqualTo(CLAVE_LIBRE);
		assertThat(registro.descubrimiento().origen()).isEqualTo(DescubrimientoModel.Origen.app);
		assertThat(registro.descubrimiento().datos()).isEqualTo(Map.of("nota", "bajo el muelle"));
		assertThat(registro.descubrimiento().creadoEn()).isEqualTo(AHORA);
		InOrder orden = inOrder(descubrimientos, actividad, habilitacion);
		orden.verify(descubrimientos).save(any());
		orden.verify(actividad).registrar(sala, EventoModel.Tipo.descubrimiento_registrado,
				DescubrimientoModel.Origen.app, Map.of("clave", CLAVE_LIBRE, "datos", Map.of("nota", "bajo el muelle")));
		orden.verify(habilitacion).recalcular(sala);
	}

	@Test
	@DisplayName("Sin datos, el descubrimiento se guarda con un objeto vacío y origen juego si lo manda el juego")
	void sinDatosGuardaObjetoVacio() {
		when(actividad.bloquear(salaId)).thenReturn(sala);
		when(descubrimientos.findBySalaIdAndClave(any(), eq("lookout_enabled"))).thenReturn(Optional.empty());
		when(descubrimientos.save(any())).thenAnswer(inv -> inv.getArgument(0));

		DescubrimientoService.Registro registro = service.registrar(credencial(SalaService.Rol.juego),
				"lookout_enabled", null);

		assertThat(registro.descubrimiento().datos()).isEmpty();
		assertThat(registro.descubrimiento().origen()).isEqualTo(DescubrimientoModel.Origen.juego);
	}

	@Test
	@DisplayName("Repetir una clave devuelve el descubrimiento original sin guardar otro, sin evento y renovando la sala")
	void repetidoDevuelveElOriginal() {
		DescubrimientoModel original = new DescubrimientoModel();
		original.setClave("pieza_1");
		original.setDatos(Map.of("v", 1));
		original.setOrigen(DescubrimientoModel.Origen.juego);
		original.setCreadoEn(AHORA.minusSeconds(60));
		when(actividad.bloquear(salaId)).thenReturn(sala);
		when(descubrimientos.findBySalaIdAndClave(sala.getId(), "pieza_1")).thenReturn(Optional.of(original));

		DescubrimientoService.Registro registro = service.registrar(credencial(SalaService.Rol.juego), "pieza_1",
				Map.of("v", 2));

		assertThat(registro.nuevo()).isFalse();
		assertThat(registro.descubrimiento().datos()).isEqualTo(Map.of("v", 1));
		assertThat(registro.descubrimiento().origen()).isEqualTo(DescubrimientoModel.Origen.juego);
		verify(descubrimientos, never()).save(any());
		verify(actividad).renovar(sala);
		verify(actividad, never()).registrar(any(), any(), any(), any());
		verifyNoInteractions(habilitacion);
	}

	@Test
	@DisplayName("Las cuatro claves del servidor dan 403 con cualquier rol y no tocan la sala")
	void clavesDelServidorDan403() {
		for (String clave : new String[] { "coordenada_a", "coordenada_b", "observatorio_visible",
				"magnetar_localizado" }) {
			for (SalaService.Rol rol : SalaService.Rol.values()) {
				assertThatThrownBy(() -> service.registrar(credencial(rol), clave, Map.of())).as(clave + " " + rol)
						.isInstanceOfSatisfying(ResponseStatusException.class,
								ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
			}
		}
		verifyNoInteractions(actividad, descubrimientos, habilitacion);
	}

	@Test
	@DisplayName("CLAVES_DEL_JUEGO son exactamente las seis claves que el diseño reserva al juego")
	void clavesDelJuego() {
		assertThat(DescubrimientoService.CLAVES_DEL_JUEGO).containsExactlyInAnyOrderElementsOf(CLAVES_DEL_JUEGO);
	}

	@ParameterizedTest(name = "{0}")
	@FieldSource("CLAVES_DEL_JUEGO")
	@DisplayName("Una clave del juego enviada por la app da 403 clave_solo_juego sin bloquear la sala ni leer el repositorio")
	void claveDelJuegoDesdeLaAppDa403(String clave) {
		assertThatThrownBy(() -> service.registrar(credencial(SalaService.Rol.app), clave, Map.of()))
				.isInstanceOfSatisfying(ApiException.class, ex -> {
					assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
					assertThat(ex.getCodigo()).isEqualTo("clave_solo_juego");
					assertThat(ex.getReason())
							.isEqualTo("La clave '" + clave + "' la registra el juego; la app no puede enviarla");
					assertThat(ex.getDetalles()).isEmpty();
				});
		verifyNoInteractions(actividad, descubrimientos, habilitacion);
	}

	@ParameterizedTest(name = "{0}")
	@FieldSource("CLAVES_DEL_JUEGO")
	@DisplayName("Una clave del juego enviada por el juego se registra con origen juego")
	void claveDelJuegoDesdeElJuegoSeRegistra(String clave) {
		when(actividad.bloquear(salaId)).thenReturn(sala);
		when(descubrimientos.findBySalaIdAndClave(sala.getId(), clave)).thenReturn(Optional.empty());
		when(descubrimientos.save(any())).thenAnswer(inv -> inv.getArgument(0));

		DescubrimientoService.Registro registro = service.registrar(credencial(SalaService.Rol.juego), clave, null);

		assertThat(registro.nuevo()).isTrue();
		assertThat(registro.descubrimiento().clave()).isEqualTo(clave);
		assertThat(registro.descubrimiento().origen()).isEqualTo(DescubrimientoModel.Origen.juego);
		verify(habilitacion).recalcular(sala);
	}

	@Test
	@DisplayName("Si la sala terminó entre el token y la escritura, el 401 de bloquear se propaga sin guardar nada")
	void salaTerminadaNoGuarda() {
		when(actividad.bloquear(salaId))
				.thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido"));

		assertThatThrownBy(() -> service.registrar(credencial(SalaService.Rol.juego), "pieza_1", Map.of()))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
		verifyNoInteractions(descubrimientos, habilitacion);
	}

	@Test
	@DisplayName("El descubrimiento se guarda en la sala del token con la clave recibida")
	void guardaEnLaSalaDelToken() {
		when(actividad.bloquear(salaId)).thenReturn(sala);
		when(descubrimientos.findBySalaIdAndClave(any(), any())).thenReturn(Optional.empty());
		when(descubrimientos.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.registrar(credencial(SalaService.Rol.juego), "pescador_dialogo_completo", Map.of());

		ArgumentCaptor<DescubrimientoModel> guardado = ArgumentCaptor.forClass(DescubrimientoModel.class);
		verify(descubrimientos).save(guardado.capture());
		assertThat(guardado.getValue().getSalaId()).isEqualTo(sala.getId());
		assertThat(guardado.getValue().getClave()).isEqualTo("pescador_dialogo_completo");
	}

	@Test
	@DisplayName("Un resultado del servidor se guarda con origen servidor y sus datos, guarda su evento y luego recalcula los puzzles")
	void resultadoDelServidorGuardaEventoYRecalcula() {
		when(descubrimientos.save(any())).thenAnswer(inv -> inv.getArgument(0));
		Map<String, Object> datos = Map.of("tipo", "declinacion", "valor", "+042");

		service.registrarDelServidor(sala, "coordenada_b", datos);

		ArgumentCaptor<DescubrimientoModel> guardado = ArgumentCaptor.forClass(DescubrimientoModel.class);
		InOrder orden = inOrder(descubrimientos, actividad, habilitacion);
		orden.verify(descubrimientos).save(guardado.capture());
		orden.verify(actividad).registrar(sala, EventoModel.Tipo.descubrimiento_registrado,
				DescubrimientoModel.Origen.servidor, Map.of("clave", "coordenada_b", "datos", datos));
		orden.verify(habilitacion).recalcular(sala);
		assertThat(guardado.getValue().getSalaId()).isEqualTo(sala.getId());
		assertThat(guardado.getValue().getOrigen()).isEqualTo(DescubrimientoModel.Origen.servidor);
		assertThat(guardado.getValue().getDatos()).isEqualTo(datos);
		assertThat(guardado.getValue().getCreadoEn()).isEqualTo(AHORA);
		verify(actividad, never()).bloquear(any());
	}

	private SalaService.Credencial credencial(SalaService.Rol rol) {
		return new SalaService.Credencial(salaId, "ALAMOS", rol);
	}

}
