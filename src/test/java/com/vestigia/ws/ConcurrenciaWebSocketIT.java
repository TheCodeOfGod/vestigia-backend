package com.vestigia.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.socket.WebSocketSession;

import com.vestigia.ClienteApi;
import com.vestigia.PostgresTestConfig;
import com.vestigia.RelojDePrueba;
import com.vestigia.service.SalaService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Concurrencia del WebSocket con la app levantada y Postgres real: un cliente que no lee y las carreras contra el
 * cierre de la sala. Cada prueba usa un pueblo que ninguna otra *IT reclama.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
class ConcurrenciaWebSocketIT {

	private static final Duration RAPIDO = Duration.ofSeconds(1);

	@LocalServerPort
	private int port;

	@Autowired
	private JsonMapper json;

	@Autowired
	private RelojDePrueba reloj;

	@Autowired
	private SesionesWebSocket sesiones;

	@Autowired
	private SalaService salaService;

	private ClienteApi api;

	@BeforeEach
	void cliente() {
		api = new ClienteApi(port, json);
	}

	@AfterEach
	void reiniciarReloj() {
		reloj.reiniciar();
	}

	@Test
	@Timeout(20)
	@DisplayName("Una conexión cuyo envío se queda bloqueado no frena GET estado, ni las escrituras de su sala, ni al otro dispositivo, ni a otra sala")
	void clienteQueNoLeeNoFrenaANadie() throws Exception {
		String juego = api.tokenJuego("TEZIUTLAN");
		String app = api.tokenApp("TEZIUTLAN");
		ClienteWs clienteJuego = ClienteWs.autenticado(port, json, juego);
		CountDownLatch soltar = new CountDownLatch(1);
		WebSocketSession appQueNoLee = sesionBloqueada("app-que-no-lee", soltar);
		assertThat(sesiones.conectar(appQueNoLee, salaService.autenticar(app).orElseThrow())).isTrue();
		assertThat(clienteJuego.siguiente().get("type").asString()).isEqualTo("dispositivo.conectado");
		String otroJuego = api.tokenJuego("TLATLAUQUITEPEC");
		ClienteWs otraSala = ClienteWs.autenticado(port, json, otroJuego);

		try {
			long inicio = System.nanoTime();
			JsonNode estado = api.cuerpo(api.enviar("GET", "/api/v1/salas/TEZIUTLAN/estado", null, juego));
			assertThat(estado.get("dispositivos")).isEqualTo(json.valueToTree(Map.of("juego", true, "app", true)));
			assertThat(api.enviar("POST", "/api/v1/salas/TEZIUTLAN/descubrimientos", "{\"clave\":\"pieza_1\"}", juego)
					.statusCode()).isEqualTo(201);
			assertThat(clienteJuego.siguiente().get("payload").get("clave").asString()).isEqualTo("pieza_1");
			assertThat(api.enviar("POST", "/api/v1/salas/TLATLAUQUITEPEC/descubrimientos",
					"{\"clave\":\"pieza_2\"}", otroJuego).statusCode()).isEqualTo(201);
			assertThat(otraSala.siguiente().get("payload").get("clave").asString()).isEqualTo("pieza_2");
			assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(RAPIDO.multipliedBy(3));
		}
		finally {
			soltar.countDown();
			sesiones.desconectar(appQueNoLee);
			clienteJuego.cerrar();
			otraSala.cerrar();
		}
	}

	/** Una sesión simulada cuyo envío se queda bloqueado hasta que la prueba la suelta, como un cliente que no lee. */
	private static WebSocketSession sesionBloqueada(String id, CountDownLatch soltar) throws Exception {
		WebSocketSession sesion = mock(WebSocketSession.class);
		when(sesion.getId()).thenReturn(id);
		when(sesion.isOpen()).thenReturn(true);
		doAnswer(inv -> {
			soltar.await();
			return null;
		}).when(sesion).sendMessage(any());
		return sesion;
	}

}
