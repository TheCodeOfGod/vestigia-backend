package com.vestigia.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.NativeWebSocketSession;

import com.vestigia.service.SalaService;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class SalaWebSocketHandlerTest {

	@Mock
	private SesionesWebSocket sesiones;

	@Mock
	private SalaService salaService;

	@Mock
	private TaskScheduler scheduler;

	@Mock
	private ScheduledFuture<?> plazo;

	@Mock(extraInterfaces = NativeWebSocketSession.class)
	private WebSocketSession sesion;

	private SalaWebSocketHandler handler;

	@BeforeEach
	void setUp() {
		handler = new SalaWebSocketHandler(sesiones, salaService, JsonMapper.builder().build(), scheduler,
				Duration.ofSeconds(5), Duration.ofMillis(4321));
		when(sesion.getId()).thenReturn("s1");
		doReturn(plazo).when(scheduler).schedule(any(Runnable.class), any(Instant.class));
	}

	@Test
	@DisplayName("Al abrir la conexión fija BLOCKING_SEND_TIMEOUT de Tomcat en la sesión con vestigia.ws.envio-limite, en milisegundos")
	void fijaElLimiteDeEnvioDeTomcat() {
		jakarta.websocket.Session tomcat = mock(jakarta.websocket.Session.class);
		Map<String, Object> propiedades = new HashMap<>();
		when(tomcat.getUserProperties()).thenReturn(propiedades);
		when(((NativeWebSocketSession) sesion).getNativeSession()).thenReturn(tomcat);

		handler.afterConnectionEstablished(sesion);

		assertThat(propiedades).containsEntry("org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT", 4321L);
	}

	@Test
	@DisplayName("Al autenticarse, el cierre programado por el plazo de auth se cancela")
	void authCancelaElPlazo() throws Exception {
		SalaService.Credencial credencial = new SalaService.Credencial(UUID.randomUUID(), "BACALAR",
				SalaService.Rol.juego);
		when(salaService.autenticar("t")).thenReturn(Optional.of(credencial));
		when(sesiones.conectar(sesion, credencial)).thenReturn(true);
		handler.afterConnectionEstablished(sesion);

		handler.handleMessage(sesion, new TextMessage("{\"type\":\"auth\",\"payload\":{\"token\":\"t\"}}"));

		verify(plazo).cancel(false);
		verify(sesiones, never()).cerrarSiNoSeAutentico(any(), any());
	}

	@Test
	@DisplayName("Al cerrarse una conexión sin autenticar, el cierre programado por el plazo de auth se cancela")
	void cierreCancelaElPlazo() {
		handler.afterConnectionEstablished(sesion);

		handler.afterConnectionClosed(sesion, CloseStatus.NORMAL);

		verify(plazo).cancel(false);
		verify(sesiones).desconectar(sesion);
	}

	@Test
	@DisplayName("Un auth rechazado no cancela el plazo: la sesión se cierra con 4001 y el plazo ya no hace nada")
	void authRechazadoNoCancela() throws Exception {
		when(salaService.autenticar("t")).thenReturn(Optional.empty());
		handler.afterConnectionEstablished(sesion);

		handler.handleMessage(sesion, new TextMessage("{\"type\":\"auth\",\"payload\":{\"token\":\"t\"}}"));

		verify(sesiones).cerrarSiNoSeAutentico(eq(sesion), eq(Cierre.TOKEN_INVALIDO.status()));
		verify(plazo, never()).cancel(false);
		verify(sesiones, never()).conectar(any(), any());
	}

}
