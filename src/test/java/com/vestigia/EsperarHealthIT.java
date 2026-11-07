package com.vestigia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.sun.net.httpserver.HttpServer;

/**
 * {@code scripts/esperar-health.sh}, el paso del workflow "Health de producción", con esperas de 0 s para que la
 * prueba sea rápida.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
class EsperarHealthIT {

	private static final Map<String, String> RAPIDO = Map.of("ESPERA_INICIAL", "0", "PAUSA", "0", "INTENTOS", "3");

	@LocalServerPort
	private int port;

	@Test
	@DisplayName("esperar-health.sh sale con 0 cuando el backend real responde UP")
	void backendArriba() throws Exception {
		SmokeTestIT.Corrida corrida = correr("http://localhost:" + port + "/");

		assertThat(corrida.salida()).as(corrida.texto()).isZero();
		assertThat(corrida.texto()).contains("Intento 1/3: UP");
	}

	@Test
	@DisplayName("esperar-health.sh sale con 1 y un ::error:: si el backend no responde UP en ningún intento")
	void backendCaido() throws Exception {
		SmokeTestIT.Corrida corrida = correr("http://localhost:1");

		assertThat(corrida.salida()).as(corrida.texto()).isEqualTo(1);
		assertThat(corrida.texto()).contains("Intento 3/3: todavía no responde UP",
				"::error title=Health de producción::http://localhost:1/actuator/health");
	}

	@Test
	@DisplayName("esperar-health.sh sin URL (la variable RAILWAY_PUBLIC_URL no existe) avisa con ::warning:: y sale con 0")
	void sinUrlSeSalta() throws Exception {
		SmokeTestIT.Corrida corrida = correr("");

		assertThat(corrida.salida()).as(corrida.texto()).isZero();
		assertThat(corrida.texto()).contains("::warning title=Health de producción omitido::", "RAILWAY_PUBLIC_URL")
				.doesNotContain("Intento");
	}

	@Test
	@DisplayName("esperar-health.sh reintenta mientras el health responde DOWN y sale con 0 cuando llega UP")
	void reintentaHastaUp() throws Exception {
		AtomicInteger llamadas = new AtomicInteger();
		HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		servidor.createContext("/actuator/health", intercambio -> {
			boolean arriba = llamadas.incrementAndGet() >= 2;
			byte[] cuerpo = ("{\"status\":\"" + (arriba ? "UP" : "DOWN") + "\"}").getBytes(StandardCharsets.UTF_8);
			intercambio.sendResponseHeaders(arriba ? 200 : 503, cuerpo.length);
			intercambio.getResponseBody().write(cuerpo);
			intercambio.close();
		});
		servidor.start();
		try {
			SmokeTestIT.Corrida corrida = correr("http://127.0.0.1:" + servidor.getAddress().getPort());

			assertThat(corrida.salida()).as(corrida.texto()).isZero();
			assertThat(corrida.texto()).contains("Intento 1/3: todavía no responde UP ({\"status\":\"DOWN\"})",
					"Intento 2/3: UP");
		}
		finally {
			servidor.stop(0);
		}
	}

	private static SmokeTestIT.Corrida correr(String url) throws IOException, InterruptedException {
		Path salida = Files.createTempFile("esperar-health", ".log");
		try {
			ProcessBuilder proceso = new ProcessBuilder(List.of("bash", "scripts/esperar-health.sh", url))
					.redirectErrorStream(true).redirectOutput(salida.toFile());
			proceso.environment().putAll(RAPIDO);
			Process corriendo = proceso.start();
			if (!corriendo.waitFor(60, TimeUnit.SECONDS)) {
				corriendo.destroyForcibly();
				throw new AssertionError("esperar-health.sh no terminó en 60 s:\n" + Files.readString(salida));
			}
			return new SmokeTestIT.Corrida(corriendo.exitValue(), Files.readString(salida, StandardCharsets.UTF_8));
		}
		finally {
			Files.deleteIfExists(salida);
		}
	}

}
