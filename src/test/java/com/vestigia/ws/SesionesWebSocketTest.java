package com.vestigia.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import com.vestigia.dto.Sala;
import com.vestigia.service.SalaService.Credencial;
import com.vestigia.service.SalaService.Rol;

import tools.jackson.databind.json.JsonMapper;

class SesionesWebSocketTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final UUID salaA = UUID.randomUUID();

	private final UUID salaB = UUID.randomUUID();

	@Test
	@DisplayName("auth.ok es el primer mensaje de una conexión y los demás salen en el orden en que se encolaron")
	void ordenDeLosMensajes() throws Exception {
		SesionesWebSocket sesiones = new SesionesWebSocket(JSON, 65536, Runnable::run);
		WebSocketSession juego = sesion("juego");

		assertThat(sesiones.conectar(juego, new Credencial(salaA, "BACALAR", Rol.juego))).isTrue();
		sesiones.enviar(salaA, SesionesWebSocket.TODOS, new Mensaje("uno", Map.of()));
		sesiones.enviar(salaA, SesionesWebSocket.TODOS, new Mensaje("dos", Map.of()));

		assertThat(enviados(juego)).containsExactly("auth.ok", "uno", "dos");
	}

	@Test
	@DisplayName("Una sala que se cerró después de validar el token no registra la conexión: conectar devuelve false y no envía nada")
	void salaCerradaEntreValidarYRegistrar() throws Exception {
		SesionesWebSocket sesiones = new SesionesWebSocket(JSON, 65536, Runnable::run);
		WebSocketSession app = sesion("app");

		sesiones.cerrarSala(salaA, new Mensaje("sala.cerrada", Map.of("estado", "finalizada")));

		assertThat(sesiones.conectar(app, new Credencial(salaA, "BACALAR", Rol.app))).isFalse();
		assertThat(sesiones.autenticada(app)).isFalse();
		assertThat(sesiones.dispositivos(salaA)).isEqualTo(new Sala.Dispositivos(false, false));
		verify(app, never()).sendMessage(any());
	}

	@Test
	@DisplayName("Un envío que se bloquea no frena a nadie: enviar, dispositivos y la otra sala responden, y al soltarse sale todo en orden")
	void envioBloqueadoNoFrenaANadie() throws Exception {
		SesionesWebSocket sesiones = new SesionesWebSocket(JSON, 65536, Executors.newVirtualThreadPerTaskExecutor());
		CountDownLatch soltar = new CountDownLatch(1);
		WebSocketSession lenta = sesion("lenta");
		List<String> recibidos = new CopyOnWriteArrayList<>();
		doAnswer(inv -> {
			soltar.await();
			recibidos.add(tipo(inv.getArgument(0)));
			return null;
		}).when(lenta).sendMessage(any());
		WebSocketSession otra = sesion("otra");

		sesiones.conectar(lenta, new Credencial(salaA, "BACALAR", Rol.app));
		sesiones.conectar(otra, new Credencial(salaB, "TULA", Rol.juego));
		long inicio = System.nanoTime();
		sesiones.enviar(salaA, SesionesWebSocket.TODOS, new Mensaje("uno", Map.of()));
		sesiones.enviar(salaB, SesionesWebSocket.TODOS, new Mensaje("otra.sala", Map.of()));
		assertThat(sesiones.dispositivos(salaA)).isEqualTo(new Sala.Dispositivos(false, true));
		verify(otra, timeout(2000).times(2)).sendMessage(any());
		assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio)).isLessThan(2000);

		soltar.countDown();
		verify(lenta, timeout(2000).times(2)).sendMessage(any());
		assertThat(recibidos).containsExactly("auth.ok", "uno");
	}

	@Test
	@DisplayName("Si la cola de una conexión pasa del límite de bytes, se descarta y la conexión se cierra con 4500")
	void colaLlenaCierraCon4500() throws Exception {
		SesionesWebSocket sesiones = new SesionesWebSocket(JSON, 200, Executors.newVirtualThreadPerTaskExecutor());
		CountDownLatch soltar = new CountDownLatch(1);
		WebSocketSession lenta = sesion("lenta");
		doAnswer(inv -> {
			soltar.await();
			return null;
		}).when(lenta).sendMessage(any());
		sesiones.conectar(lenta, new Credencial(salaA, "BACALAR", Rol.app));

		for (int i = 0; i < 10; i++) {
			sesiones.enviar(salaA, SesionesWebSocket.TODOS, new Mensaje("relleno", Map.of("i", "x".repeat(40))));
		}
		soltar.countDown();

		verify(lenta, timeout(2000)).close(new CloseStatus(4500, "El cliente no lee sus mensajes o falló un envío"));
		verify(lenta, timeout(2000).times(1)).sendMessage(any());
	}

	@Test
	@DisplayName("Si un envío falla, la conexión se cierra con 4500 y lo que seguía en su cola se descarta")
	void envioFallidoCierraCon4500() throws Exception {
		SesionesWebSocket sesiones = new SesionesWebSocket(JSON, 65536, Runnable::run);
		WebSocketSession rota = sesion("rota");
		doThrow(new IOException("Broken pipe")).when(rota).sendMessage(any());

		sesiones.conectar(rota, new Credencial(salaA, "BACALAR", Rol.app));
		sesiones.enviar(salaA, SesionesWebSocket.TODOS, new Mensaje("uno", Map.of()));

		verify(rota).close(new CloseStatus(4500, "El cliente no lee sus mensajes o falló un envío"));
		verify(rota).sendMessage(any());
	}

	@Test
	@DisplayName("Una sesión que el servidor ya decidió cerrar sin auth no se registra aunque su auth llegue después")
	void sesionRechazadaNoSeRegistra() throws Exception {
		SesionesWebSocket sesiones = new SesionesWebSocket(JSON, 65536, Runnable::run);
		WebSocketSession tardia = sesion("tardia");

		assertThat(sesiones.cerrarSiNoSeAutentico(tardia, new CloseStatus(4008))).isTrue();

		assertThat(sesiones.conectar(tardia, new Credencial(salaA, "BACALAR", Rol.juego))).isFalse();
		verify(tardia).close(new CloseStatus(4008));
		verify(tardia, never()).sendMessage(any());
	}

	private static WebSocketSession sesion(String id) {
		WebSocketSession sesion = mock(WebSocketSession.class);
		when(sesion.getId()).thenReturn(id);
		when(sesion.isOpen()).thenReturn(true);
		return sesion;
	}

	private static List<String> enviados(WebSocketSession sesion) throws IOException {
		@SuppressWarnings("unchecked")
		ArgumentCaptor<WebSocketMessage<?>> mensajes = ArgumentCaptor.forClass(WebSocketMessage.class);
		verify(sesion, atLeastOnce()).sendMessage(mensajes.capture());
		return mensajes.getAllValues().stream().map(SesionesWebSocketTest::tipo).toList();
	}

	private static String tipo(WebSocketMessage<?> mensaje) {
		return JSON.readTree(((TextMessage) mensaje).getPayload()).get("type").asString();
	}

}
