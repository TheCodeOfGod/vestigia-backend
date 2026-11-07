package com.vestigia.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vestigia.model.DescubrimientoModel.Origen;
import com.vestigia.model.EventoModel.Tipo;
import com.vestigia.service.ActividadService.SalaEvento;
import com.vestigia.service.SalaService.Rol;

@ExtendWith(MockitoExtension.class)
class EmisorEventosTest {

	private static final UUID SALA = UUID.randomUUID();

	private static final Map<String, Object> PUZZLE = Map.of("puzzle", "photo", "estado", "resuelto", "datos",
			Map.of("completada", true));

	@Mock
	private SesionesWebSocket sesiones;

	@Test
	@DisplayName("Cada tipo de evento tiene su emisión: puzzle.actualizado y descubrimiento.nuevo a todos, escena.cambiada solo a la app, sala.cerrada cierra la sala, crear y unirse no emiten")
	void cadaTipoTieneSuEmision() {
		Map<Tipo, Consumer<SesionesWebSocket>> esperado = new EnumMap<>(Tipo.class);
		esperado.put(Tipo.puzzle_habilitado, s -> s.enviar(SALA, Set.of(Rol.juego, Rol.app),
				new Mensaje("puzzle.actualizado", PUZZLE)));
		esperado.put(Tipo.puzzle_iniciado, esperado.get(Tipo.puzzle_habilitado));
		esperado.put(Tipo.puzzle_resuelto, esperado.get(Tipo.puzzle_habilitado));
		esperado.put(Tipo.descubrimiento_registrado, s -> s.enviar(SALA, Set.of(Rol.juego, Rol.app),
				new Mensaje("descubrimiento.nuevo", Map.of("clave", "pieza_1", "datos", Map.of(), "origen", "juego"))));
		esperado.put(Tipo.escena_cambiada, s -> s.enviar(SALA, Set.of(Rol.app),
				new Mensaje("escena.cambiada", Map.of("anterior", "introduccion", "escena", "faro"))));
		esperado.put(Tipo.sala_finalizada, s -> s.cerrarSala(SALA,
				new Mensaje("sala.cerrada", Map.of("estado", "finalizada"))));
		esperado.put(Tipo.sala_expirada, s -> s.cerrarSala(SALA,
				new Mensaje("sala.cerrada", Map.of("estado", "expirada"))));
		esperado.put(Tipo.sala_creada, null);
		esperado.put(Tipo.app_unida, null);
		assertThat(esperado.keySet()).as("todos los tipos de evento").containsExactlyInAnyOrder(Tipo.values());

		esperado.forEach((tipo, emision) -> {
			Mockito.reset(sesiones);
			new EmisorEventos(sesiones).emitir(evento(tipo));
			if (emision == null) {
				verifyNoInteractions(sesiones);
			}
			else {
				emision.accept(verify(sesiones));
			}
			Mockito.verifyNoMoreInteractions(sesiones);
		});
	}

	private static SalaEvento evento(Tipo tipo) {
		Map<String, Object> payload = switch (tipo) {
			case puzzle_habilitado, puzzle_iniciado, puzzle_resuelto -> PUZZLE;
			case descubrimiento_registrado -> Map.of("clave", "pieza_1", "datos", Map.of());
			case escena_cambiada -> Map.of("anterior", "introduccion", "escena", "faro");
			case sala_creada -> Map.of("codigo", "BACALAR");
			case app_unida, sala_finalizada, sala_expirada -> Map.of();
		};
		return new SalaEvento(SALA, "BACALAR", tipo, Origen.juego, payload, Instant.EPOCH);
	}

}
