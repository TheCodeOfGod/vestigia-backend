package com.vestigia.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketSession;

import com.vestigia.ClienteApi;
import com.vestigia.PostgresTestConfig;
import com.vestigia.RelojDePrueba;
import com.vestigia.dto.Sala;
import com.vestigia.model.SalaModel;
import com.vestigia.service.ActividadService;
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

	@Autowired
	private ActividadService actividad;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transaccion;

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

	@Test
	@DisplayName("auth contra DELETE: si la sala se cierra entre validar el token y registrar la conexión, no queda conexión viva")
	void authContraDelete() throws Exception {
		String juego = api.tokenJuego("TLAXCO");
		String app = api.tokenApp("TLAXCO");

		Carrera carrera = authEnPausa(app);
		assertThat(api.enviar("DELETE", "/api/v1/salas/TLAXCO", null, juego).statusCode()).isEqualTo(204);

		carrera.comprobarSinConexion();
	}

	@Test
	@DisplayName("auth contra la expiración: si el job expira la sala entre validar el token y registrar la conexión, no queda conexión viva")
	void authContraExpiracion() throws Exception {
		api.tokenJuego("TLAYACAPAN");
		String app = api.tokenApp("TLAYACAPAN");

		Carrera carrera = authEnPausa(app);
		reloj.avanzar(Duration.ofHours(2).plusSeconds(1));
		salaService.expirarVencidas();

		carrera.comprobarSinConexion();
	}

	@Test
	@Timeout(20)
	@DisplayName("El job no expira una sala que una escritura simultánea renueva: espera su candado y vuelve a evaluar expira_en")
	void jobContraEscrituraQueRenueva() throws Exception {
		api.tokenJuego("TONATICO");
		UUID salaId = jdbc.queryForObject("SELECT id FROM salas WHERE codigo = 'TONATICO' AND estado = 'esperando_app'",
				UUID.class);
		CompletableFuture<Void> job = new CompletableFuture<>();

		transaccion.executeWithoutResult(status -> {
			SalaModel sala = actividad.bloquear(salaId);
			reloj.avanzar(Duration.ofHours(2).plusSeconds(1));
			actividad.renovar(sala);
			Thread.ofVirtual().start(() -> {
				try {
					salaService.expirarVencidas();
					job.complete(null);
				}
				catch (RuntimeException ex) {
					job.completeExceptionally(ex);
				}
			});
			esperarJobBloqueado();
		});
		job.get(10, TimeUnit.SECONDS);

		assertThat(jdbc.queryForObject("SELECT estado FROM salas WHERE id = ?", String.class, salaId))
				.isEqualTo("esperando_app");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM eventos WHERE sala_id = ? AND tipo = 'sala.expirada'",
				Integer.class, salaId)).isZero();
	}

	/** Espera a que una consulta de Postgres esté detenida por un candado de fila: el job esperando la escritura. */
	private void esperarJobBloqueado() {
		long limite = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'"
				+ " AND datname = current_database()", Integer.class) == 0) {
			assertThat(System.nanoTime()).as("el job espera el candado de la sala antes de 5 s").isLessThan(limite);
			Thread.onSpinWait();
		}
	}

	/** Un auth detenido entre validar el token en la base y registrar la conexión, como el handler. */
	private record Carrera(WebSocketSession sesion, UUID salaId, CountDownLatch seguir,
			CompletableFuture<Boolean> conectada, SesionesWebSocket sesiones) {

		void comprobarSinConexion() throws Exception {
			seguir.countDown();
			assertThat(conectada.get(5, TimeUnit.SECONDS)).as("conectar registra la sesión").isFalse();
			assertThat(sesiones.autenticada(sesion)).isFalse();
			assertThat(sesiones.dispositivos(salaId)).isEqualTo(new Sala.Dispositivos(false, false));
			verify(sesion, never()).sendMessage(any());
		}

	}

	private Carrera authEnPausa(String token) throws Exception {
		WebSocketSession sesion = mock(WebSocketSession.class);
		when(sesion.getId()).thenReturn("carrera-" + token.substring(0, 8));
		when(sesion.isOpen()).thenReturn(true);
		CountDownLatch validado = new CountDownLatch(1);
		CountDownLatch seguir = new CountDownLatch(1);
		CompletableFuture<Boolean> conectada = new CompletableFuture<>();
		Thread.ofVirtual().start(() -> {
			try {
				SalaService.Credencial credencial = salaService.autenticar(token).orElseThrow();
				validado.countDown();
				seguir.await();
				conectada.complete(sesiones.conectar(sesion, credencial));
			}
			catch (Exception ex) {
				conectada.completeExceptionally(ex);
			}
		});
		assertThat(validado.await(5, TimeUnit.SECONDS)).as("el token se validó").isTrue();
		UUID salaId = salaService.autenticar(token).orElseThrow().salaId();
		return new Carrera(sesion, salaId, seguir, conectada, sesiones);
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
