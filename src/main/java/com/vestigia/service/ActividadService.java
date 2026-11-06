package com.vestigia.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.model.DescubrimientoModel;
import com.vestigia.model.EventoModel;
import com.vestigia.model.SalaModel;
import com.vestigia.repository.EventoRepository;
import com.vestigia.repository.SalaRepository;

/**
 * Lo que comparten todas las escrituras de una sala: bloquear su fila, renovar su expiración y dejar un evento en la
 * bitácora. Cada evento también se publica como {@link SalaEvento} dentro de la transacción, para que el WebSocket del
 * Paso 4 lo emita con {@code @TransactionalEventListener} después del commit.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ActividadService {

	/** Evento de aplicación de Spring con lo mismo que se guardó en la tabla eventos. */
	public record SalaEvento(UUID salaId, String codigo, EventoModel.Tipo tipo, DescubrimientoModel.Origen origen,
			Map<String, Object> payload, Instant creadoEn) {
	}

	private final SalaRepository salas;

	private final EventoRepository eventos;

	private final ApplicationEventPublisher publicador;

	private final Clock clock;

	private final Duration expiracion;

	public ActividadService(SalaRepository salas, EventoRepository eventos, ApplicationEventPublisher publicador,
			Clock clock, @Value("${vestigia.salas.expiracion}") Duration expiracion) {
		this.salas = salas;
		this.eventos = eventos;
		this.publicador = publicador;
		this.clock = clock;
		this.expiracion = expiracion;
	}

	/**
	 * Bloquea la sala hasta el commit. Responde 401, como lo haría el token en la siguiente petición, si la sala dejó de
	 * estar viva entre la autenticación y este punto (otra petición la cerró o el job la expiró) o si su expira_en ya
	 * pasó aunque el job todavía no la haya marcado: una escritura no puede revivir una sala vencida.
	 */
	public SalaModel bloquear(UUID salaId) {
		Instant ahora = clock.instant();
		return salas.bloquear(salaId)
				.filter(s -> SalaModel.Estado.VIVOS.contains(s.getEstado()) && !s.getExpiraEn().isBefore(ahora))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
						"Token inválido o de una sala que ya terminó"));
	}

	/** Renueva ultima_actividad y expira_en. La sala debe estar gestionada por la transacción en curso. */
	public void renovar(SalaModel sala) {
		Instant ahora = clock.instant();
		sala.setUltimaActividad(ahora);
		sala.setExpiraEn(ahora.plus(expiracion));
	}

	/** Renueva la sala, guarda el evento y lo publica. */
	public void registrar(SalaModel sala, EventoModel.Tipo tipo, DescubrimientoModel.Origen origen,
			Map<String, Object> payload) {
		renovar(sala);
		EventoModel evento = eventos.save(new EventoModel(sala.getId(), tipo, origen, payload, sala.getUltimaActividad()));
		publicador.publishEvent(new SalaEvento(sala.getId(), sala.getCodigo(), tipo, origen, evento.getPayload(),
				evento.getCreadoEn()));
	}

}
