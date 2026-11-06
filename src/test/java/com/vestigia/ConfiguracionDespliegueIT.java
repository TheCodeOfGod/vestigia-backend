package com.vestigia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/** Lo que haría fallar o exponer el primer despliegue en Railway (Paso 7). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
class ConfiguracionDespliegueIT {

	@LocalServerPort
	private int port;

	@Autowired
	private Environment entorno;

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	@DisplayName("Actuator publica solo health: info, env, beans, metrics, heapdump y los demás responden 404")
	void actuatorSoloPublicaHealth() throws Exception {
		assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
		for (String endpoint : List.of("info", "env", "beans", "configprops", "metrics", "heapdump", "threaddump",
				"loggers", "mappings", "scheduledtasks", "shutdown")) {
			assertThat(get("/actuator/" + endpoint).statusCode()).as("/actuator/" + endpoint).isEqualTo(404);
		}
		assertThat(get("/actuator").body()).contains("\"health\"").doesNotContain("\"env\"", "\"info\"");
	}

	@Test
	@DisplayName("Hibernate solo valida el esquema (ddl-auto=validate) y Flyway lo crea al arrancar")
	void hibernateSoloValida() {
		assertThat(entorno.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
		assertThat(entorno.getProperty("spring.flyway.enabled", Boolean.class, true)).isTrue();
	}

	@Test
	@DisplayName("Los errores fuera de /api no muestran stacktrace, excepción ni versión del servidor")
	void erroresSinDetallesInternos() throws Exception {
		// /error lo atiende Spring Boot; %2F lo rechaza Tomcat antes de llegar a la aplicación.
		for (String ruta : List.of("/error", "/api/v1/salas/A%2FB/estado")) {
			HttpResponse<String> respuesta = get(ruta);
			assertThat(respuesta.statusCode()).as(ruta).isGreaterThanOrEqualTo(400);
			assertThat(respuesta.body()).as(ruta).doesNotContain("Exception", "\tat ", "trace", "Tomcat/", "Spring");
			assertThat(respuesta.headers().firstValue("Server")).as(ruta).isEmpty();
		}
	}

	@Test
	@DisplayName("application.properties toma DB_URL, DB_USER, DB_PASSWORD, PORT y CORS_ALLOWED_ORIGINS del entorno")
	void variablesDeEntorno() throws Exception {
		Properties propiedades = new Properties();
		try (InputStream archivo = getClass().getResourceAsStream("/application.properties")) {
			propiedades.load(archivo);
		}

		assertThat(propiedades.getProperty("spring.datasource.url")).startsWith("${DB_URL:");
		assertThat(propiedades.getProperty("spring.datasource.username")).startsWith("${DB_USER:");
		assertThat(propiedades.getProperty("spring.datasource.password")).startsWith("${DB_PASSWORD:");
		assertThat(propiedades.getProperty("server.port")).isEqualTo("${PORT:8080}");
		assertThat(propiedades.getProperty("vestigia.cors.allowed-origins")).startsWith("${CORS_ALLOWED_ORIGINS:");
		assertThat(propiedades.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
	}

	private HttpResponse<String> get(String ruta) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + ruta)).build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}

}
