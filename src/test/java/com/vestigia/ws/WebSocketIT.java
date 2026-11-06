package com.vestigia.ws;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import com.vestigia.ClienteApi;
import com.vestigia.PostgresTestConfig;
import com.vestigia.RelojDePrueba;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * El protocolo de /ws con un cliente WebSocket real (java.net.http) contra la app levantada y Postgres real: auth,
 * plazo, presencia, reconexión, errores, tamaño, pings y logs. Cada prueba usa un pueblo que ninguna otra *IT reclama.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class WebSocketIT {

	@LocalServerPort
	private int port;

	@Autowired
	private JsonMapper json;

	@Autowired
	private RelojDePrueba reloj;

	@Autowired
	private SesionesWebSocket sesiones;

	@Autowired
	private ScheduledTaskHolder tareas;

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
	@DisplayName("auth con el token del juego o de la app responde auth.ok con su rol y la sala")
	void authCorrecto() throws Exception {
		String juego = api.tokenJuego("ACAXOCHITLAN");
		String app = api.tokenApp("ACAXOCHITLAN");

		ClienteWs clienteJuego = ClienteWs.abrir(port, json);
		assertThat(clienteJuego.autenticar(juego)).isEqualTo(mensaje("auth.ok",
				Map.of("rol", "juego", "sala", "ACAXOCHITLAN")));
		ClienteWs clienteApp = ClienteWs.abrir(port, json);
		assertThat(clienteApp.autenticar(app)).isEqualTo(mensaje("auth.ok", Map.of("rol", "app", "sala", "ACAXOCHITLAN")));

		clienteApp.cerrar();
		clienteJuego.cerrar();
	}

	@Test
	@DisplayName("Un token desconocido, vacío, que no es texto o ausente cierra con 4001 sin auth.ok")
	void tokenInvalidoCierraCon4001() throws Exception {
		for (String auth : new String[] { "{\"type\":\"auth\",\"payload\":{\"token\":\"no-es-un-token\"}}",
				"{\"type\":\"auth\",\"payload\":{\"token\":\"  \"}}", "{\"type\":\"auth\",\"payload\":{\"token\":123}}",
				"{\"type\":\"auth\",\"payload\":{}}", "{\"type\":\"auth\"}" }) {
			ClienteWs cliente = ClienteWs.abrir(port, json);
			cliente.enviar(auth);

			assertThat(cliente.cierre()).as(auth)
					.isEqualTo(new ClienteWs.Cierre(4001, "Token inválido o de una sala que ya terminó"));
			cliente.sinMensajes(Duration.ZERO);
		}
	}

	@Test
	@DisplayName("El token de una sala cerrada o con expira_en vencido cierra con 4001, aunque el job no la haya marcado")
	void tokenDeSalaTerminadaCierraCon4001() throws Exception {
		String cerrada = api.tokenJuego("AHUACATLAN");
		api.enviar("DELETE", "/api/v1/salas/AHUACATLAN", null, cerrada);
		String vencida = api.tokenJuego("CANDELARIA");
		reloj.avanzar(Duration.ofHours(2).plusSeconds(1));

		for (String token : new String[] { cerrada, vencida }) {
			ClienteWs cliente = ClienteWs.abrir(port, json);
			cliente.enviar("{\"type\":\"auth\",\"payload\":{\"token\":\"" + token + "\"}}");

			assertThat(cliente.cierre().codigo()).isEqualTo(4001);
		}
	}

	@Test
	@DisplayName("Antes de auth, otro tipo, JSON inválido o un mensaje binario cierran con 4000")
	void antesDeAuthCualquierOtroMensajeCierraCon4000() throws Exception {
		for (String texto : new String[] { "{\"type\":\"hola\",\"payload\":{}}", "no es json", "[1,2]",
				"{\"type\":\"orientacion\",\"payload\":{\"azimut\":10}}" }) {
			ClienteWs cliente = ClienteWs.abrir(port, json);
			cliente.enviar(texto);

			assertThat(cliente.cierre()).as(texto)
					.isEqualTo(new ClienteWs.Cierre(4000, "El primer mensaje debe ser auth"));
		}
		ClienteWs binario = ClienteWs.abrir(port, json);
		binario.enviarBinario(new byte[] { 1, 2, 3 });
		assertThat(binario.cierre().codigo()).isEqualTo(4000);
	}

	@Test
	@DisplayName("Sin auth en 5 s el servidor cierra con 4008; una conexión que se autenticó a tiempo sigue abierta")
	void sinAuthATiempoCierraCon4008() throws Exception {
		String juego = api.tokenJuego("HUEJOTZINGO");
		long inicio = System.nanoTime();
		ClienteWs callado = ClienteWs.abrir(port, json);
		ClienteWs puntual = ClienteWs.abrir(port, json);
		Thread.sleep(1000);
		assertThat(puntual.autenticar(juego).get("type").asString()).isEqualTo("auth.ok");

		ClienteWs.Cierre cierre = callado.cierre(Duration.ofSeconds(10));
		Duration espera = Duration.ofNanos(System.nanoTime() - inicio);

		assertThat(cierre).isEqualTo(new ClienteWs.Cierre(4008, "No llegó auth a tiempo"));
		assertThat(espera).isBetween(Duration.ofMillis(4500), Duration.ofSeconds(9));
		Thread.sleep(500);
		assertThat(puntual.cerrado()).isFalse();
		assertThat(dispositivos("HUEJOTZINGO", juego)).isEqualTo(Map.of("juego", true, "app", false));
		puntual.cerrar();
	}

	@Test
	@DisplayName("Presencia: cada conexión avisa dispositivo.conectado al otro rol, cada cierre dispositivo.desconectado, y GET estado lo refleja")
	void presencia() throws Exception {
		String juego = api.tokenJuego("IXTENCO");
		assertThat(dispositivos("IXTENCO", juego)).isEqualTo(Map.of("juego", false, "app", false));

		ClienteWs clienteJuego = ClienteWs.autenticado(port, json, juego);
		assertThat(dispositivos("IXTENCO", juego)).isEqualTo(Map.of("juego", true, "app", false));

		String app = api.tokenApp("IXTENCO");
		ClienteWs clienteApp = ClienteWs.autenticado(port, json, app);
		assertThat(clienteJuego.siguiente()).isEqualTo(mensaje("dispositivo.conectado", Map.of("rol", "app")));
		assertThat(dispositivos("IXTENCO", app)).isEqualTo(Map.of("juego", true, "app", true));

		clienteApp.cerrar();
		assertThat(clienteJuego.siguiente()).isEqualTo(mensaje("dispositivo.desconectado", Map.of("rol", "app")));
		assertThat(dispositivos("IXTENCO", juego)).isEqualTo(Map.of("juego", true, "app", false));

		clienteApp = ClienteWs.autenticado(port, json, app);
		assertThat(clienteJuego.siguiente()).isEqualTo(mensaje("dispositivo.conectado", Map.of("rol", "app")));
		clienteJuego.cerrar();
		assertThat(clienteApp.siguiente()).isEqualTo(mensaje("dispositivo.desconectado", Map.of("rol", "juego")));
		assertThat(dispositivos("IXTENCO", app)).isEqualTo(Map.of("juego", false, "app", true));
		clienteApp.sinMensajes(Duration.ofMillis(200));
		clienteApp.cerrar();
	}

	@Test
	@DisplayName("Reconexión: la app vuelve con el mismo token, recibe auth.ok y no el mensaje que se perdió, y GET estado trae lo que pasó mientras no estaba")
	void reconexionYEstadoConsistente() throws Exception {
		String juego = api.tokenJuego("IZAMAL");
		String app = api.tokenApp("IZAMAL");
		ClienteWs clienteJuego = ClienteWs.autenticado(port, json, juego);
		ClienteWs clienteApp = ClienteWs.autenticado(port, json, app);
		clienteJuego.siguiente();

		clienteApp.cerrar();
		clienteJuego.siguiente();
		assertThat(api.enviar("POST", "/api/v1/salas/IZAMAL/descubrimientos", "{\"clave\":\"pieza_1\"}", juego)
				.statusCode()).isEqualTo(201);
		assertThat(clienteJuego.siguiente().get("type").asString()).isEqualTo("descubrimiento.nuevo");
		clienteApp = ClienteWs.autenticado(port, json, app);

		assertThat(clienteJuego.siguiente()).isEqualTo(mensaje("dispositivo.conectado", Map.of("rol", "app")));
		clienteApp.sinMensajes(Duration.ofMillis(200));
		JsonNode estado = api.cuerpo(api.enviar("GET", "/api/v1/salas/IZAMAL/estado", null, app));
		assertThat(estado.get("dispositivos")).isEqualTo(json.valueToTree(Map.of("juego", true, "app", true)));
		assertThat(estado.get("descubrimientos").valueStream().map(d -> d.get("clave").asString()))
				.containsExactly("pieza_1");
		clienteApp.cerrar();
		clienteJuego.cerrar();
	}

	@Test
	@DisplayName("Una segunda conexión del mismo rol reemplaza a la primera: la vieja cierra con 4009 y el otro rol no recibe desconectado")
	void segundaConexionDelMismoRolReemplaza() throws Exception {
		String juego = api.tokenJuego("JALA");
		String app = api.tokenApp("JALA");
		ClienteWs clienteJuego = ClienteWs.autenticado(port, json, juego);
		ClienteWs vieja = ClienteWs.autenticado(port, json, app);
		clienteJuego.siguiente();

		ClienteWs nueva = ClienteWs.autenticado(port, json, app);

		assertThat(vieja.cierre()).isEqualTo(new ClienteWs.Cierre(4009, "Otra conexión del mismo rol tomó su lugar"));
		assertThat(clienteJuego.siguiente()).isEqualTo(mensaje("dispositivo.conectado", Map.of("rol", "app")));
		clienteJuego.sinMensajes(Duration.ofMillis(300));
		assertThat(dispositivos("JALA", juego)).isEqualTo(Map.of("juego", true, "app", true));

		nueva.cerrar();
		assertThat(clienteJuego.siguiente()).isEqualTo(mensaje("dispositivo.desconectado", Map.of("rol", "app")));
		clienteJuego.cerrar();
	}

	@Test
	@DisplayName("Ya autenticado, JSON inválido, binario, tipo desconocido, orientacion o un segundo auth responden error y la conexión sigue")
	void erroresNoTumbanLaConexion() throws Exception {
		String juego = api.tokenJuego("JILOTEPEC");
		ClienteWs cliente = ClienteWs.autenticado(port, json, juego);

		cliente.enviar("{no es json");
		assertThat(error(cliente.siguiente())).isEqualTo("json_invalido");
		cliente.enviar("{\"type\":\"a\",\"type\":\"b\"}");
		assertThat(error(cliente.siguiente())).isEqualTo("json_invalido");
		cliente.enviar("{\"payload\":{}}");
		assertThat(error(cliente.siguiente())).isEqualTo("json_invalido");
		cliente.enviarBinario(new byte[] { 1, 2, 3 });
		assertThat(error(cliente.siguiente())).isEqualTo("json_invalido");
		cliente.enviar("{\"type\":\"puzzle.actualizado\",\"payload\":{}}");
		assertThat(error(cliente.siguiente())).isEqualTo("tipo_desconocido");
		cliente.enviar("{\"type\":\"orientacion\",\"payload\":{\"azimut\":10,\"altitud\":20,\"t\":1}}");
		JsonNode orientacion = cliente.siguiente();
		assertThat(orientacion.get("payload")).isEqualTo(json.valueToTree(Map.of("codigo", "tipo_desconocido",
				"message", "El mensaje orientacion está diferido: el servidor no lo recibe por ahora")));
		cliente.enviar("{\"type\":\"auth\",\"payload\":{\"token\":\"" + juego + "\"}}");
		assertThat(error(cliente.siguiente())).isEqualTo("ya_autenticado");

		assertThat(cliente.cerrado()).isFalse();
		assertThat(dispositivos("JILOTEPEC", juego)).isEqualTo(Map.of("juego", true, "app", false));
		cliente.cerrar();
	}

	@Test
	@DisplayName("Un mensaje de 4096 caracteres recibe su error; uno de 4097 cierra con 1009 y avisa desconectado al otro rol")
	void mensajeDemasiadoGrandeCierraCon1009() throws Exception {
		String juego = api.tokenJuego("LINARES");
		String app = api.tokenApp("LINARES");
		ClienteWs clienteJuego = ClienteWs.autenticado(port, json, juego);
		ClienteWs clienteApp = ClienteWs.autenticado(port, json, app);
		clienteJuego.siguiente();

		clienteApp.enviar(deLargo(4096));
		assertThat(error(clienteApp.siguiente())).isEqualTo("tipo_desconocido");
		clienteApp.enviar(deLargo(4097));

		assertThat(clienteApp.cierre().codigo()).isEqualTo(1009);
		assertThat(clienteJuego.siguiente()).isEqualTo(mensaje("dispositivo.desconectado", Map.of("rol", "app")));
		clienteJuego.cerrar();
	}

	@Test
	@DisplayName("El servidor manda pings a las conexiones autenticadas, programados cada 25 s")
	void pings() throws Exception {
		String juego = api.tokenJuego("LORETO");
		ClienteWs cliente = ClienteWs.autenticado(port, json, juego);
		int antes = cliente.pings();

		sesiones.ping();

		long limite = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (cliente.pings() == antes && System.nanoTime() < limite) {
			Thread.sleep(20);
		}
		assertThat(cliente.pings()).isGreaterThan(antes);
		assertThat(tareas.getScheduledTasks()).anySatisfy(tarea -> {
			assertThat(tarea.toString()).contains("SesionesWebSocket.ping");
			assertThat(tarea.getTask()).isInstanceOfSatisfying(FixedRateTask.class,
					t -> assertThat(t.getIntervalDuration()).isEqualTo(Duration.ofSeconds(25)));
		});
		cliente.cerrar();
	}

	@Test
	@DisplayName("Ni los tokens ni Authorization aparecen en los logs durante auth, auth rechazada, errores y desconexión")
	void tokensAusentesDeLosLogs(CapturedOutput output) throws Exception {
		String juego = api.tokenJuego("TACAMBARO");
		String app = api.tokenApp("TACAMBARO");
		String falso = "TokenFalsoQueNoDebeVerse_0123456789abcdefghij";

		ClienteWs clienteJuego = ClienteWs.autenticado(port, json, juego);
		ClienteWs clienteApp = ClienteWs.autenticado(port, json, app);
		clienteJuego.siguiente();
		clienteApp.enviar("{\"type\":\"auth\",\"payload\":{\"token\":\"" + app + "\"}}");
		clienteApp.siguiente();
		clienteApp.enviar("{\"token\":\"" + app + "\"");
		clienteApp.siguiente();
		ClienteWs rechazado = ClienteWs.abrir(port, json);
		rechazado.enviar("{\"type\":\"auth\",\"payload\":{\"token\":\"" + falso + "\"}}");
		rechazado.cierre();
		ClienteWs sinAuth = ClienteWs.abrir(port, json);
		sinAuth.enviar("{\"type\":\"hola\",\"payload\":{\"token\":\"" + falso + "\"}}");
		sinAuth.cierre();
		clienteApp.cerrar();
		clienteJuego.siguiente();
		clienteJuego.cerrar();

		assertThat(output.getAll()).contains("[WS] conectado sala=TACAMBARO rol=juego", "[WS] auth rechazada",
				"[WS] primer mensaje que no es auth", "[WS] desconectado sala=TACAMBARO rol=app");
		assertThat(output.getAll()).doesNotContain(juego, app, falso, "Authorization");
	}

	private Map<String, Object> dispositivos(String codigo, String token) throws Exception {
		JsonNode estado = api.cuerpo(api.enviar("GET", "/api/v1/salas/" + codigo + "/estado", null, token));
		return Map.of("juego", estado.get("dispositivos").get("juego").asBoolean(), "app",
				estado.get("dispositivos").get("app").asBoolean());
	}

	private JsonNode mensaje(String type, Map<String, Object> payload) {
		return json.valueToTree(Map.of("type", type, "payload", payload));
	}

	private static String error(JsonNode mensaje) {
		assertThat(mensaje.get("type").asString()).isEqualTo("error");
		assertThat(mensaje.get("payload").get("message").asString()).isNotBlank();
		return mensaje.get("payload").get("codigo").asString();
	}

	/** Un mensaje JSON válido de tipo desconocido con exactamente {@code caracteres} caracteres. */
	private static String deLargo(int caracteres) {
		String inicio = "{\"type\":\"relleno\",\"payload\":{\"x\":\"";
		String fin = "\"}}";
		String mensaje = inicio + "a".repeat(caracteres - inicio.length() - fin.length()) + fin;
		assertThat(mensaje).hasSize(caracteres);
		return mensaje;
	}

}
