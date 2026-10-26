package com.vestigia.ws;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import com.vestigia.PostgresTestConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
class WebSocketIT {

	@LocalServerPort
	private int port;

	@Test
	void elServidorCierraLaConexionCon1008MientrasNoHayAutenticacion() throws Exception {
		CompletableFuture<CloseStatus> cierre = new CompletableFuture<>();
		AbstractWebSocketHandler cliente = new AbstractWebSocketHandler() {
			@Override
			public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
				cierre.complete(status);
			}
		};

		new StandardWebSocketClient().execute(cliente, "ws://localhost:" + port + "/ws").get(5, TimeUnit.SECONDS);
		CloseStatus status = cierre.get(5, TimeUnit.SECONDS);

		assertThat(status.getCode()).isEqualTo(1008);
		assertThat(status.getReason()).isEqualTo("WebSocket aún no implementado (Paso 4)");
	}

}
