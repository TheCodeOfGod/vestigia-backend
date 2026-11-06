package com.vestigia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.model.DescubrimientoModel;
import com.vestigia.model.EventoModel;
import com.vestigia.model.SalaModel;
import com.vestigia.repository.EventoRepository;
import com.vestigia.repository.SalaRepository;

@ExtendWith(MockitoExtension.class)
class ActividadServiceTest {

	private static final Instant AHORA = Instant.parse("2026-11-05T18:00:00Z");

	@Mock
	private SalaRepository salas;

	@Mock
	private EventoRepository eventos;

	@Mock
	private ApplicationEventPublisher publicador;

	private ActividadService service;

	@BeforeEach
	void setUp() {
		service = new ActividadService(salas, eventos, publicador, Clock.fixed(AHORA, ZoneOffset.UTC),
				Duration.ofHours(2));
	}

	@Test
	@DisplayName("Bloquear devuelve la sala si sigue viva")
	void bloquearDevuelveLaSalaViva() {
		UUID id = UUID.randomUUID();
		SalaModel sala = sala(SalaModel.Estado.activa);
		when(salas.bloquear(id)).thenReturn(Optional.of(sala));

		assertThat(service.bloquear(id)).isSameAs(sala);
	}

	@Test
	@DisplayName("Bloquear una sala finalizada, expirada o inexistente responde 401")
	void bloquearSalaTerminadaDa401() {
		UUID id = UUID.randomUUID();
		for (Optional<SalaModel> sala : new Optional[] { Optional.of(sala(SalaModel.Estado.finalizada)),
				Optional.of(sala(SalaModel.Estado.expirada)), Optional.empty() }) {
			when(salas.bloquear(id)).thenReturn(sala);

			assertThatThrownBy(() -> service.bloquear(id)).isInstanceOfSatisfying(ResponseStatusException.class,
					ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
		}
	}

	@Test
	@DisplayName("Bloquear una sala viva cuyo expira_en ya pasó responde 401 aunque el job no la haya marcado")
	void bloquearSalaVencidaDa401() {
		UUID id = UUID.randomUUID();
		SalaModel sala = sala(SalaModel.Estado.activa);
		sala.setExpiraEn(AHORA.minusNanos(1000));
		when(salas.bloquear(id)).thenReturn(Optional.of(sala));

		assertThatThrownBy(() -> service.bloquear(id)).isInstanceOfSatisfying(ResponseStatusException.class,
				ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
		assertThat(sala.getExpiraEn()).isEqualTo(AHORA.minusNanos(1000));

		sala.setExpiraEn(AHORA);
		assertThat(service.bloquear(id)).isSameAs(sala);
	}

	@Test
	@DisplayName("Renovar pone ultima_actividad en ahora y expira_en en ahora más la expiración configurada")
	void renovarUsaLaExpiracionConfigurada() {
		SalaModel sala = sala(SalaModel.Estado.activa);

		service.renovar(sala);

		assertThat(sala.getUltimaActividad()).isEqualTo(AHORA);
		assertThat(sala.getExpiraEn()).isEqualTo(AHORA.plus(Duration.ofHours(2)));
	}

	@Test
	@DisplayName("Registrar el cierre de una sala finalizada guarda el evento y ultima_actividad, pero no renueva expira_en")
	void registrarSobreSalaFinalizadaNoRenuevaExpiraEn() {
		SalaModel sala = sala(SalaModel.Estado.finalizada);
		Instant expiraEn = sala.getExpiraEn();
		when(eventos.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.registrar(sala, EventoModel.Tipo.sala_finalizada, DescubrimientoModel.Origen.juego, Map.of());

		assertThat(sala.getExpiraEn()).isEqualTo(expiraEn);
		assertThat(sala.getUltimaActividad()).isEqualTo(AHORA);
		verify(eventos).save(any());
	}

	@Test
	@DisplayName("Registrar renueva la sala, guarda el evento y lo publica para el WebSocket del Paso 4")
	void registrarRenuevaGuardaYPublica() {
		SalaModel sala = sala(SalaModel.Estado.activa);
		when(eventos.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.registrar(sala, EventoModel.Tipo.escena_cambiada, DescubrimientoModel.Origen.juego,
				Map.of("escena", "faro"));

		ArgumentCaptor<EventoModel> evento = ArgumentCaptor.forClass(EventoModel.class);
		verify(eventos).save(evento.capture());
		assertThat(evento.getValue().getSalaId()).isEqualTo(sala.getId());
		assertThat(evento.getValue().getTipo()).isEqualTo(EventoModel.Tipo.escena_cambiada);
		assertThat(evento.getValue().getOrigen()).isEqualTo(DescubrimientoModel.Origen.juego);
		assertThat(evento.getValue().getPayload()).isEqualTo(Map.of("escena", "faro"));
		assertThat(evento.getValue().getCreadoEn()).isEqualTo(AHORA);
		assertThat(sala.getExpiraEn()).isEqualTo(AHORA.plus(Duration.ofHours(2)));
		verify(publicador).publishEvent(new ActividadService.SalaEvento(sala.getId(), "ALAMOS",
				EventoModel.Tipo.escena_cambiada, DescubrimientoModel.Origen.juego, Map.of("escena", "faro"), AHORA));
	}

	@Test
	@DisplayName("Los tipos de evento se guardan con su punto: sala.creada, app.unida, descubrimiento.registrado...")
	void elConvertidorGuardaElTipoConPunto() {
		EventoModel.TipoConverter conversor = new EventoModel.TipoConverter();

		for (EventoModel.Tipo tipo : EventoModel.Tipo.values()) {
			assertThat(conversor.convertToDatabaseColumn(tipo)).isEqualTo(tipo.name().replace('_', '.'));
			assertThat(conversor.convertToEntityAttribute(tipo.valor())).isEqualTo(tipo);
		}
	}

	private static SalaModel sala(SalaModel.Estado estado) {
		SalaModel sala = new SalaModel();
		sala.setCodigo("ALAMOS");
		sala.setEstado(estado);
		sala.setExpiraEn(AHORA.plus(Duration.ofHours(1)));
		return sala;
	}

}
