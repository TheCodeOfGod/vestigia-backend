package com.vestigia;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Swagger UI y /v3/api-docs contra las rutas reales de los controladores. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
class DocumentacionIT {

	/** Rutas que no piden token: crear, unirse, pueblos e iniciar sesión. */
	private static final Set<String> PUBLICAS = Set.of("POST /api/v1/salas", "POST /api/v1/salas/{codigo}/unirse",
			"GET /api/v1/pueblos", "POST /api/v1/auth/login");

	@LocalServerPort
	private int port;

	@Autowired
	private JsonMapper json;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mapeos;

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	@DisplayName("/v3/api-docs documenta exactamente las rutas y métodos reales de los controladores de la API")
	void openApiTieneLasRutasReales() throws Exception {
		Set<String> reales = new TreeSet<>();
		for (Map.Entry<RequestMappingInfo, HandlerMethod> mapeo : mapeos.getHandlerMethods().entrySet()) {
			if (!mapeo.getValue().getBeanType().getPackageName().equals("com.vestigia.controller")) {
				continue;
			}
			for (String ruta : mapeo.getKey().getPathPatternsCondition().getPatternValues()) {
				mapeo.getKey().getMethodsCondition().getMethods().forEach(m -> reales.add(m.name() + " " + ruta));
			}
		}

		assertThat(reales).hasSize(18);
		assertThat(operaciones(apiDocs()).keySet()).isEqualTo(reales);
	}

	@Test
	@DisplayName("Cada operación tiene resumen, descripción y ejemplos; cada error usa ErrorBody con codigo en su ejemplo")
	void cadaOperacionEstaDocumentada() throws Exception {
		JsonNode docs = apiDocs();

		operaciones(docs).forEach((nombre, operacion) -> {
			assertThat(operacion.path("summary").asString()).as(nombre + " summary").isNotBlank();
			assertThat(operacion.path("description").asString()).as(nombre + " description").isNotBlank();
			if (operacion.has("requestBody")) {
				assertThat(ejemplos(operacion.path("requestBody"))).as(nombre + " ejemplos de petición").isNotEmpty();
			}
			operacion.path("responses").properties().forEach(respuesta -> {
				String como = nombre + " " + respuesta.getKey();
				assertThat(respuesta.getValue().path("description").asString()).as(como).isNotBlank();
				if (respuesta.getKey().equals("204")) {
					assertThat(respuesta.getValue().has("content")).as(como + " sin cuerpo").isFalse();
					return;
				}
				List<String> ejemplos = ejemplos(respuesta.getValue());
				assertThat(ejemplos).as(como + " ejemplos").isNotEmpty();
				if (respuesta.getKey().startsWith("4")) {
					assertThat(respuesta.getValue().path("content").path("application/json").path("schema")
							.path("$ref").asString()).as(como + " esquema").endsWith("/ErrorBody");
					ejemplos.forEach(e -> assertThat(json.readTree(e).has("codigo")).as(como + " codigo").isTrue());
				}
			});
		});
	}

	@Test
	@DisplayName("Las rutas protegidas declaran su esquema Bearer (sala o sesión de admin) y las públicas ninguno")
	void seguridadBearer() throws Exception {
		JsonNode docs = apiDocs();

		for (String nombre : List.of("token", "sesion_admin")) {
			JsonNode esquema = docs.path("components").path("securitySchemes").path(nombre);
			assertThat(esquema.path("type").asString()).as(nombre).isEqualTo("http");
			assertThat(esquema.path("scheme").asString()).as(nombre).isEqualTo("bearer");
		}
		operaciones(docs).forEach((nombre, operacion) -> {
			String seguridad = operacion.path("security").toString();
			boolean admin = nombre.contains("/api/v1/admin/") || nombre.equals("POST /api/v1/auth/logout")
					|| nombre.equals("GET /api/v1/auth/yo");
			boolean conToken = seguridad.contains("\"token\"");
			boolean conSesion = seguridad.contains("\"sesion_admin\"");
			assertThat(conToken).as(nombre + " token de sala").isEqualTo(!PUBLICAS.contains(nombre) && !admin);
			assertThat(conSesion).as(nombre + " sesión de admin").isEqualTo(admin);
			if (conToken || conSesion) {
				assertThat(operacion.path("responses").has("401")).as(nombre + " documenta 401").isTrue();
				assertThat(operacion.path("responses").has("403")).as(nombre + " documenta 403").isTrue();
			}
		});
		assertThat(docs.path("info").path("title").asString()).isEqualTo("API de Vestigia");
	}

	@Test
	@DisplayName("Swagger UI se sirve en /swagger-ui/index.html")
	void swaggerUi() throws Exception {
		HttpResponse<String> respuesta = get("/swagger-ui/index.html");

		assertThat(respuesta.statusCode()).isEqualTo(200);
		assertThat(respuesta.body()).contains("swagger-ui");
	}

	@Test
	@DisplayName("La página de prueba /prueba-ws.html se sirve como HTML, manda el token en auth y no lo pone en la URL ni en el navegador")
	void paginaDePrueba() throws Exception {
		HttpResponse<String> respuesta = get("/prueba-ws.html");

		assertThat(respuesta.statusCode()).isEqualTo(200);
		assertThat(respuesta.headers().firstValue("Content-Type")).hasValueSatisfying(
				tipo -> assertThat(tipo).startsWith("text/html"));
		assertThat(respuesta.body()).contains("new WebSocket(url)", "+ '/ws'", "type: 'auth', payload: { token: valor }")
				.doesNotContain("localStorage", "sessionStorage", "document.cookie", "?token", "token=");
	}

	private JsonNode apiDocs() throws Exception {
		HttpResponse<String> respuesta = get("/v3/api-docs");
		assertThat(respuesta.statusCode()).isEqualTo(200);
		return json.readTree(respuesta.body());
	}

	/** "METODO /ruta" → operación, para cada operación de {@code paths}. */
	private static Map<String, JsonNode> operaciones(JsonNode docs) {
		Map<String, JsonNode> operaciones = new java.util.TreeMap<>();
		docs.path("paths").properties().forEach(ruta -> ruta.getValue().properties()
				.forEach(metodo -> operaciones.put(metodo.getKey().toUpperCase() + " " + ruta.getKey(),
						metodo.getValue())));
		return operaciones;
	}

	/** Los valores de los ejemplos de un requestBody o una respuesta, como texto JSON. */
	private List<String> ejemplos(JsonNode conContenido) {
		List<String> valores = new ArrayList<>();
		conContenido.path("content").properties().forEach(tipo -> {
			tipo.getValue().path("examples").properties()
					.forEach(e -> valores.add(texto(e.getValue().path("value"))));
			if (tipo.getValue().has("example")) {
				valores.add(texto(tipo.getValue().path("example")));
			}
		});
		return valores;
	}

	private String texto(JsonNode valor) {
		return valor.isString() ? valor.asString() : json.writeValueAsString(valor);
	}

	private HttpResponse<String> get(String ruta) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + ruta)).build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}

}
