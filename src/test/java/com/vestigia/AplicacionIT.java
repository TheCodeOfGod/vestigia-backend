package com.vestigia;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
class AplicacionIT {

	@LocalServerPort
	private int port;

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	void healthResponde200ConEstadoUp() throws Exception {
		HttpResponse<String> respuesta = get("/actuator/health");

		assertThat(respuesta.statusCode()).isEqualTo(200);
		assertThat(respuesta.body()).contains("\"status\":\"UP\"");
	}

	@Test
	void swaggerUiYOpenApiEstanDisponibles() throws Exception {
		assertThat(get("/v3/api-docs").statusCode()).isEqualTo(200);
		assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
	}

	private HttpResponse<String> get(String ruta) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + ruta)).build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}

}
