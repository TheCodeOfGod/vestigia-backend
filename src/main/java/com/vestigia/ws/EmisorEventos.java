package com.vestigia.ws;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.vestigia.service.ActividadService.SalaEvento;
import com.vestigia.service.SalaService.Rol;

/**
 * Traduce cada evento de la sala a su mensaje del WebSocket y lo emite después del commit: un cliente que reacciona
 * pidiendo GET estado ya ve los datos confirmados, y una escritura que hace rollback no emite nada. Las escrituras
 * repetidas no publican evento, así que tampoco emiten mensaje.
 */
@Component
public class EmisorEventos {

	private final SesionesWebSocket sesiones;

	public EmisorEventos(SesionesWebSocket sesiones) {
		this.sesiones = sesiones;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void emitir(SalaEvento evento) {
		Map<String, Object> payload = evento.payload();
		switch (evento.tipo()) {
			case puzzle_habilitado, puzzle_iniciado, puzzle_resuelto ->
				sesiones.enviar(evento.salaId(), SesionesWebSocket.TODOS, new Mensaje("puzzle.actualizado", payload));
			case descubrimiento_registrado -> {
				Map<String, Object> descubrimiento = new LinkedHashMap<>(payload);
				descubrimiento.put("origen", evento.origen().name());
				sesiones.enviar(evento.salaId(), SesionesWebSocket.TODOS,
						new Mensaje("descubrimiento.nuevo", descubrimiento));
			}
			case escena_cambiada ->
				sesiones.enviar(evento.salaId(), Set.of(Rol.app), new Mensaje("escena.cambiada", payload));
			case sala_finalizada ->
				sesiones.cerrarSala(evento.salaId(), new Mensaje("sala.cerrada", Map.of("estado", "finalizada")));
			case sala_expirada ->
				sesiones.cerrarSala(evento.salaId(), new Mensaje("sala.cerrada", Map.of("estado", "expirada")));
			case sala_creada, app_unida -> {
				// Sin mensaje: nadie puede estar conectado antes de crear la sala, y el juego se entera de la app
				// por dispositivo.conectado cuando esta abre su WebSocket.
			}
		}
	}

}
