package com.vestigia.ws;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.vestigia.ClienteApi;
import com.vestigia.PostgresTestConfig;
import com.vestigia.RelojDePrueba;
import com.vestigia.service.DescubrimientoService;
import com.vestigia.service.SalaService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lo que emite /ws por cada escritura, con clientes WebSocket reales contra la app levantada y Postgres real. Cada
 * prueba usa un pueblo que ninguna otra *IT reclama.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
class EmisionWebSocketIT {

	@LocalServerPort
	private int port;

	@Autowired
	private JsonMapper json;

	@Autowired
	private RelojDePrueba reloj;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transaccion;

	@Autowired
	private SalaService salaService;

	@Autowired
	private DescubrimientoService descubrimientoService;

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
	@DisplayName("Cada escritura REST de la cadena completa emite a los dos dispositivos su descubrimiento.nuevo y un puzzle.actualizado por cada cambio de estado, incluido bloqueado → disponible; otra sala no recibe nada")
	void cadenaCompletaEmiteCadaCambio() throws Exception {
		String juego = api.tokenJuego("TECATE");
		String app = api.tokenApp("TECATE");
		Conectados sala = conectar(juego, app);
		ClienteWs otraSala = ClienteWs.autenticado(port, json, api.tokenJuego("TECOZAUTLA"));

		descubrir("TECATE", "pescador_dialogo_completo", juego);
		sala.reciben(descubrimiento("pescador_dialogo_completo", "{}", "juego"), puzzle("radio", "disponible", "{}"));
		reportar("TECATE", "radio", "sintonizada", juego);
		sala.reciben(puzzle("radio", "resuelto", "{'sintonizada':true}"),
				descubrimiento("coordenada_a", "{'tipo':'ascension_recta','valor':'137'}", "servidor"));
		descubrir("TECATE", "pieza_1", juego);
		descubrir("TECATE", "pieza_2", juego);
		descubrir("TECATE", "pieza_3", juego);
		sala.reciben(descubrimiento("pieza_1", "{}", "juego"), descubrimiento("pieza_2", "{}", "juego"),
				descubrimiento("pieza_3", "{}", "juego"), puzzle("photo", "disponible", "{}"));
		reportar("TECATE", "photo", "iniciado", app);
		sala.reciben(puzzle("photo", "en_curso", "{'iniciado':true}"));
		descubrir("TECATE", "lookout_enabled", juego);
		sala.reciben(descubrimiento("lookout_enabled", "{}", "juego"));
		reportar("TECATE", "photo", "completada", app);
		sala.reciben(puzzle("photo", "resuelto", "{'completada':true}"),
				descubrimiento("coordenada_b", "{'tipo':'declinacion','valor':'+042'}", "servidor"),
				puzzle("lookout", "disponible", "{}"));
		reportar("TECATE", "lookout", "completada", app);
		sala.reciben(puzzle("lookout", "resuelto", "{'completada':true}"),
				descubrimiento("observatorio_visible", "{}", "servidor"));
		descubrir("TECATE", "observatory_enabled", juego);
		sala.reciben(descubrimiento("observatory_enabled", "{}", "juego"), puzzle("observatory", "disponible", "{}"));
		reportar("TECATE", "observatory", "completada", app);
		sala.reciben(puzzle("observatory", "resuelto", "{'completada':true}"),
				descubrimiento("magnetar_localizado", "{}", "servidor"));

		otraSala.sinMensajes(Duration.ofMillis(200));
		sala.cerrar();
		otraSala.cerrar();
	}

	@Test
	@DisplayName("Las escrituras repetidas (descubrimiento, aviso de puzzle, misma escena) responden con éxito y no emiten nada; una clave del juego enviada por la app da 403 y tampoco emite")
	void escriturasRepetidasNoEmiten() throws Exception {
		String juego = api.tokenJuego("TEMACAPULIN");
		String app = api.tokenApp("TEMACAPULIN");
		descubrir("TEMACAPULIN", "pescador_dialogo_completo", juego);
		reportar("TEMACAPULIN", "radio", "sintonizada", juego);
		Conectados sala = conectar(juego, app);

		assertThat(descubrir("TEMACAPULIN", "pescador_dialogo_completo", juego)).isEqualTo(200);
		assertThat(reportar("TEMACAPULIN", "radio", "sintonizada", juego)).isEqualTo(200);
		assertThat(api.enviar("PUT", "/api/v1/salas/TEMACAPULIN/escena", "{\"escena\":\"introduccion\"}", juego)
				.statusCode()).isEqualTo(200);
		assertThat(descubrir("TEMACAPULIN", "pieza_1", app)).isEqualTo(403);
		descubrir("TEMACAPULIN", "nota_de_prueba", app);

		sala.reciben(descubrimiento("nota_de_prueba", "{}", "app"));
		sala.cerrar();
	}

	@Test
	@DisplayName("escena.cambiada llega solo a la app, con la escena nueva y la anterior; el juego no la recibe")
	void escenaCambiadaSoloALaApp() throws Exception {
		String juego = api.tokenJuego("TEPOTZOTLAN");
		String app = api.tokenApp("TEPOTZOTLAN");
		Conectados sala = conectar(juego, app);

		assertThat(api.enviar("PUT", "/api/v1/salas/TEPOTZOTLAN/escena", "{\"escena\":\"faro\"}", juego).statusCode())
				.isEqualTo(200);
		assertThat(sala.app.siguiente()).isEqualTo(m("{'type':'escena.cambiada','payload':"
				+ "{'escena':'faro','anterior':'introduccion'}}"));
		descubrir("TEPOTZOTLAN", "pieza_1", juego);

		assertThat(sala.juego.siguiente()).isEqualTo(descubrimiento("pieza_1", "{}", "juego"));
		assertThat(sala.app.siguiente()).isEqualTo(descubrimiento("pieza_1", "{}", "juego"));
		sala.juego.sinMensajes(Duration.ofMillis(200));
		sala.cerrar();
	}

	@Test
	@DisplayName("Al cerrar la sala, los dos reciben sala.cerrada con estado finalizada y luego el servidor cierra con 1000")
	void salaCerradaAlFinalizar() throws Exception {
		String juego = api.tokenJuego("TEPOZTLAN");
		String app = api.tokenApp("TEPOZTLAN");
		Conectados sala = conectar(juego, app);

		assertThat(api.enviar("DELETE", "/api/v1/salas/TEPOZTLAN", null, juego).statusCode()).isEqualTo(204);

		for (ClienteWs cliente : new ClienteWs[] { sala.juego, sala.app }) {
			assertThat(cliente.siguiente()).isEqualTo(m("{'type':'sala.cerrada','payload':{'estado':'finalizada'}}"));
			assertThat(cliente.cierre()).isEqualTo(new ClienteWs.Cierre(1000, "La sala se cerró"));
		}
		assertThat(api.enviar("GET", "/api/v1/salas/TEPOZTLAN/estado", null, juego).statusCode()).isEqualTo(401);
		ClienteWs tarde = ClienteWs.abrir(port, json);
		tarde.enviar("{\"type\":\"auth\",\"payload\":{\"token\":\"" + app + "\"}}");
		assertThat(tarde.cierre().codigo()).isEqualTo(4001);
	}

	@Test
	@DisplayName("Al expirar la sala, el job hace que los dos reciban sala.cerrada con estado expirada y cierra con 1000")
	void salaCerradaAlExpirar() throws Exception {
		String juego = api.tokenJuego("TEQUILA");
		String app = api.tokenApp("TEQUILA");
		Conectados sala = conectar(juego, app);

		reloj.avanzar(Duration.ofHours(2).plusSeconds(1));
		salaService.expirarVencidas();

		for (ClienteWs cliente : new ClienteWs[] { sala.juego, sala.app }) {
			assertThat(cliente.siguiente()).isEqualTo(m("{'type':'sala.cerrada','payload':{'estado':'expirada'}}"));
			assertThat(cliente.cierre()).isEqualTo(new ClienteWs.Cierre(1000, "La sala se cerró"));
		}
	}

	@Test
	@DisplayName("Los mensajes salen después del commit: nada mientras la transacción sigue abierta, y un GET estado pedido al recibir el mensaje ya ve el cambio aunque el commit tarde; un rollback no emite nada")
	void losMensajesSalenDespuesDelCommit() throws Exception {
		String juego = api.tokenJuego("TEQUISQUIAPAN");
		String app = api.tokenApp("TEQUISQUIAPAN");
		Conectados sala = conectar(juego, app);
		SalaService.Credencial credencial = salaService.autenticar(juego).orElseThrow();
		CompletableFuture<JsonNode> estadoAlRecibir = CompletableFuture.supplyAsync(() -> {
			try {
				JsonNode mensaje = sala.app.siguiente();
				JsonNode estado = api.cuerpo(api.enviar("GET", "/api/v1/salas/TEQUISQUIAPAN/estado", null, app));
				assertThat(mensaje).isEqualTo(descubrimiento("pieza_1", "{}", "juego"));
				return estado;
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		});

		transaccion.executeWithoutResult(status -> {
			descubrimientoService.registrar(credencial, "pieza_1", Map.of());
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void beforeCommit(boolean readOnly) {
					try {
						Thread.sleep(1000);
					}
					catch (InterruptedException ex) {
						throw new IllegalStateException(ex);
					}
				}
			});
			try {
				sala.juego.sinMensajes(Duration.ofMillis(500));
			}
			catch (InterruptedException ex) {
				throw new IllegalStateException(ex);
			}
		});

		assertThat(estadoAlRecibir.get(10, TimeUnit.SECONDS).get("descubrimientos").valueStream()
				.map(d -> d.get("clave").asString())).containsExactly("pieza_1");
		assertThat(sala.juego.siguiente()).isEqualTo(descubrimiento("pieza_1", "{}", "juego"));

		transaccion.executeWithoutResult(status -> {
			descubrimientoService.registrar(credencial, "pieza_2", Map.of());
			status.setRollbackOnly();
		});
		sala.juego.sinMensajes(Duration.ofMillis(500));
		sala.app.sinMensajes(Duration.ZERO);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM descubrimientos WHERE sala_id = ? AND clave = 'pieza_2'",
				Integer.class, credencial.salaId())).isZero();
		sala.cerrar();
	}

	/** El juego y la app de una sala, conectados y autenticados. */
	private record Conectados(ClienteWs juego, ClienteWs app) {

		/** Los dos reciben exactamente estos mensajes, en este orden. */
		void reciben(JsonNode... esperados) throws InterruptedException {
			for (JsonNode esperado : esperados) {
				assertThat(juego.siguiente()).isEqualTo(esperado);
				assertThat(app.siguiente()).isEqualTo(esperado);
			}
		}

		void cerrar() throws Exception {
			app.cerrar();
			juego.cerrar();
		}

	}

	private Conectados conectar(String juego, String app) throws Exception {
		ClienteWs clienteJuego = ClienteWs.autenticado(port, json, juego);
		ClienteWs clienteApp = ClienteWs.autenticado(port, json, app);
		assertThat(clienteJuego.siguiente()).isEqualTo(m("{'type':'dispositivo.conectado','payload':{'rol':'app'}}"));
		return new Conectados(clienteJuego, clienteApp);
	}

	private int descubrir(String codigo, String clave, String token) throws Exception {
		return api.enviar("POST", "/api/v1/salas/" + codigo + "/descubrimientos", "{\"clave\":\"" + clave + "\"}", token)
				.statusCode();
	}

	private int reportar(String codigo, String puzzle, String aviso, String token) throws Exception {
		int status = api.enviar("PUT", "/api/v1/salas/" + codigo + "/puzzles/" + puzzle,
				"{\"datos\":{\"" + aviso + "\":true}}", token).statusCode();
		assertThat(status).isEqualTo(200);
		return status;
	}

	private JsonNode puzzle(String puzzle, String estado, String datos) {
		return m("{'type':'puzzle.actualizado','payload':{'puzzle':'" + puzzle + "','estado':'" + estado
				+ "','datos':" + datos + "}}");
	}

	private JsonNode descubrimiento(String clave, String datos, String origen) {
		return m("{'type':'descubrimiento.nuevo','payload':{'clave':'" + clave + "','datos':" + datos + ",'origen':'"
				+ origen + "'}}");
	}

	/** JSON escrito con comillas simples para que se lea mejor. */
	private JsonNode m(String jsonConComillasSimples) {
		return json.readTree(jsonConComillasSimples.replace('\'', '"'));
	}

}
