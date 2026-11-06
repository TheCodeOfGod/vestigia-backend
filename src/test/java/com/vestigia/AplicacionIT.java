package com.vestigia;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
class AplicacionIT {

	@LocalServerPort
	private int port;

	@Autowired
	private ScheduledTaskHolder tareas;

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

	@Test
	void openApiDocumentaLosEndpointsDeSalasYElToken() throws Exception {
		String docs = get("/v3/api-docs").body();

		assertThat(docs).contains("/api/v1/pueblos", "/api/v1/salas", "/api/v1/salas/{codigo}/unirse",
				"/api/v1/salas/{codigo}/estado", "/api/v1/salas/{codigo}/descubrimientos",
				"/api/v1/salas/{codigo}/escena", "\"/api/v1/salas/{codigo}\":{\"delete\"", "\"scheme\":\"bearer\"");
	}

	@Test
	void rutaInexistenteBajoSalasDa404SinPedirToken() throws Exception {
		assertThat(get("/api/v1/salas/ALAMOS/no-existe").statusCode()).isEqualTo(404);
	}

	@Test
	void elJobDeExpiracionEstaProgramadoCadaMinuto() {
		assertThat(tareas.getScheduledTasks()).anySatisfy(tarea -> {
			assertThat(tarea.toString()).contains("SalaService.expirarVencidas");
			assertThat(tarea.getTask()).isInstanceOfSatisfying(FixedDelayTask.class,
					t -> assertThat(t.getIntervalDuration()).isEqualTo(Duration.ofMinutes(1)));
		});
	}

	private HttpResponse<String> get(String ruta) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + ruta)).build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}

}
