package com.vestigia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Corre {@code scripts/smoke-test.sh} contra un servidor HTTP falso, para provocar respuestas que el backend real no da:
 * un 201 con un cuerpo que no cumple la comprobación, una página HTML de un proxy y una petición lenta que deja
 * interrumpir el script. No levanta Spring ni Postgres.
 */
class SmokeTestServidorFalsoIT {

	private static final String TOKEN = "falso_TOKEN_de_43_caracteres_0123456789abcd";

	private HttpServer servidor;

	private final List<String> peticiones = new CopyOnWriteArrayList<>();

	private final CountDownLatch unirseRecibido = new CountDownLatch(1);

	private volatile String cuerpoSala = "{\"codigo\":\"FALSO\",\"estado\":\"activa\",\"token\":\"" + TOKEN + "\"}";

	private volatile int estadoHealth = 200;

	@BeforeEach
	void arrancar() throws IOException {
		servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		servidor.createContext("/", this::atender);
		servidor.start();
	}

	@AfterEach
	void detener() {
		servidor.stop(0);
	}

	@Test
	@DisplayName("smoke-test.sh guarda el token antes de comprobar la respuesta de POST /salas: si la comprobación falla, igual cierra la sala")
	void cierraLaSalaSiFallaLaComprobacionDeCrear() throws Exception {
		SmokeTestIT.Corrida corrida = SmokeTestIT.correr(url(), "falso");

		assertThat(corrida.salida()).as(corrida.texto()).isEqualTo(1);
		assertThat(corrida.texto()).contains("no se cumple: sala en esperando_app con token",
				"la sala FALSO se cerró (DELETE 204)", "RESULTADO: ❌ falló en el paso 2");
		assertThat(corrida.texto()).doesNotContain(TOKEN).contains("\"token\":\"<token>\"");
		assertThat(peticiones).contains("DELETE /api/v1/salas/FALSO Bearer " + TOKEN);
	}

	@Test
	@DisplayName("smoke-test.sh interrumpido con HUP sale con 129, dice que se interrumpió y cierra la sala")
	void hupCierraLaSalaYSaleCon129() throws Exception {
		cuerpoSala = "{\"codigo\":\"FALSO\",\"estado\":\"esperando_app\",\"token\":\"" + TOKEN + "\"}";
		Path salida = Files.createTempFile("smoke-test", ".log");
		try {
			Process proceso = new ProcessBuilder("bash", "scripts/smoke-test.sh", url(), "falso")
					.redirectErrorStream(true).redirectOutput(salida.toFile()).start();
			assertThat(unirseRecibido.await(30, TimeUnit.SECONDS)).as("el script llegó a unirse").isTrue();
			new ProcessBuilder("kill", "-HUP", String.valueOf(proceso.pid())).start().waitFor();
			assertThat(proceso.waitFor(60, TimeUnit.SECONDS)).isTrue();
			String texto = Files.readString(salida, StandardCharsets.UTF_8);

			assertThat(proceso.exitValue()).as(texto).isEqualTo(129);
			assertThat(texto).contains("la sala FALSO se cerró (DELETE 204)", "RESULTADO: ❌ se interrumpió en el paso 3")
					.doesNotContain("✅ la cadena completa");
			assertThat(peticiones).contains("DELETE /api/v1/salas/FALSO Bearer " + TOKEN);
		}
		finally {
			Files.deleteIfExists(salida);
		}
	}

	@Test
	@DisplayName("smoke-test.sh muestra el cuerpo de una respuesta que no es JSON, como el 502 en HTML de un proxy")
	void muestraUnaRespuestaHtml() throws Exception {
		estadoHealth = 502;

		SmokeTestIT.Corrida corrida = SmokeTestIT.correr(url());

		assertThat(corrida.salida()).as(corrida.texto()).isEqualTo(1);
		assertThat(corrida.texto()).contains("se esperaba 200 y llegó 502",
				"Respuesta 502: <html><body><h1>502 Bad Gateway</h1>");
		assertThat(peticiones).noneMatch(p -> p.startsWith("POST"));
	}

	private void atender(HttpExchange intercambio) throws IOException {
		String metodo = intercambio.getRequestMethod();
		String ruta = intercambio.getRequestURI().getPath();
		String autorizacion = intercambio.getRequestHeaders().getFirst("Authorization");
		peticiones.add(metodo + " " + ruta + (autorizacion == null ? "" : " " + autorizacion));
		intercambio.getRequestBody().readAllBytes();
		switch (metodo + " " + ruta) {
			case "GET /actuator/health" -> {
				if (estadoHealth == 200) {
					responder(intercambio, 200, "application/json", "{\"status\":\"UP\"}");
				}
				else {
					responder(intercambio, estadoHealth, "text/html",
							"<html><body><h1>502 Bad Gateway</h1>\n<p>proxy</p></body></html>");
				}
			}
			case "POST /api/v1/salas" -> responder(intercambio, 201, "application/json", cuerpoSala);
			case "POST /api/v1/salas/FALSO/unirse" -> {
				unirseRecibido.countDown();
				dormir(1500);
				responder(intercambio, 200, "application/json", "{\"token\":\"" + TOKEN + "\"}");
			}
			case "DELETE /api/v1/salas/FALSO" -> responder(intercambio, 204, null, null);
			default -> responder(intercambio, 404, "application/json", "{}");
		}
	}

	private static void responder(HttpExchange intercambio, int estado, String tipo, String cuerpo) throws IOException {
		if (cuerpo == null) {
			intercambio.sendResponseHeaders(estado, -1);
			intercambio.close();
			return;
		}
		byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
		intercambio.getResponseHeaders().set("Content-Type", tipo);
		intercambio.sendResponseHeaders(estado, bytes.length);
		intercambio.getResponseBody().write(bytes);
		intercambio.close();
	}

	private static void dormir(long ms) {
		try {
			Thread.sleep(ms);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private String url() {
		return "http://127.0.0.1:" + servidor.getAddress().getPort();
	}

}
