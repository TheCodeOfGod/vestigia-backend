package com.vestigia;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.vestigia.model.DescubrimientoModel;
import com.vestigia.model.PuzzleEstadoModel;
import com.vestigia.dto.CrearUsuarioRequest;
import com.vestigia.model.SalaModel;
import com.vestigia.model.UsuarioModel;
import com.vestigia.service.SalaService;
import com.vestigia.service.UsuarioService;
import com.vestigia.ws.ClienteWs;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cada bloque JSON de respuesta de la documentación tiene los mismos campos, con los mismos tipos, que una respuesta
 * real del backend. Cada bloque ```json de los documentos lleva justo antes una marca: {@code <!-- respuesta: tipo -->}
 * si es una respuesta o un mensaje del servidor, o {@code <!-- peticion -->} si es algo que manda el cliente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
class JsonDocumentadoIT {

	private static final List<String> DOCUMENTOS = List.of("docs/api.md", "docs/tiempo-real.md",
			"docs/integracion-unity.md", "docs/integracion-flutter.md", "docs/integracion-pwa.md");

	private static final Pattern MARCA = Pattern.compile("<!-- (respuesta: ([a-z-]+)|peticion) -->");

	/** Un pueblo que ninguna otra *IT reclama. */
	private static final String PUEBLO = "YURIRIA";

	/** Valores válidos de los campos cerrados, tomados de los enums del backend. */
	private static final Map<String, Set<String>> VALORES = Map.of(
			"puzzle", nombres(PuzzleEstadoModel.Puzzle.values()),
			"estado", nombres(Stream.concat(Stream.of(SalaModel.Estado.values()),
					Stream.of(PuzzleEstadoModel.Estado.values())).toArray(Enum<?>[]::new)),
			"escena_actual", nombres(SalaModel.Escena.values()),
			"escena", nombres(SalaModel.Escena.values()),
			"anterior", nombres(SalaModel.Escena.values()),
			"origen", nombres(DescubrimientoModel.Origen.values()),
			"rol", Stream.concat(nombres(SalaService.Rol.values()).stream(),
					nombres(UsuarioModel.Rol.values()).stream()).collect(Collectors.toSet()));

	/** Avisos de puzzle: dentro de {@code datos} siempre son booleanos. */
	private static final Set<String> AVISOS = Set.of("completada", "iniciado", "sintonizada");

	/** Qué respuesta real corresponde a cada operación de /v3/api-docs con 2xx. */
	private static final Map<String, String> TIPO_POR_OPERACION = Map.ofEntries(
			Map.entry("GET /api/v1/pueblos", "pueblos"),
			Map.entry("POST /api/v1/salas", "sala-creada"),
			Map.entry("POST /api/v1/salas/{codigo}/unirse", "app-unida"),
			Map.entry("GET /api/v1/salas/{codigo}/estado", "estado"),
			Map.entry("POST /api/v1/salas/{codigo}/descubrimientos", "descubrimiento"),
			Map.entry("PUT /api/v1/salas/{codigo}/puzzles/{puzzle}", "puzzle"),
			Map.entry("PUT /api/v1/salas/{codigo}/escena", "escena"),
			Map.entry("POST /api/v1/auth/login", "sesion"),
			Map.entry("GET /api/v1/auth/yo", "usuario"),
			Map.entry("GET /api/v1/admin/usuarios", "usuarios"),
			Map.entry("POST /api/v1/admin/usuarios", "usuario"),
			Map.entry("PUT /api/v1/admin/usuarios/{id}", "usuario"));

	/** Las respuestas reales se piden una vez para toda la clase. */
	private static Map<String, JsonNode> reales;

	/** Un bloque cercado con ``` de un documento, con la marca que lo precede si la hay. */
	record Cercado(String documento, int linea, String info, Matcher marca, String texto) {

		/** ```json, ```JSON, ```jsonc o ```json title, o un bloque sin lenguaje que empieza con { o [. */
		boolean esJson() {
			String lenguaje = info.isBlank() ? "" : info.strip().split("\\s+")[0].toLowerCase();
			String inicio = texto.strip();
			return lenguaje.startsWith("json")
					|| (lenguaje.isEmpty() && (inicio.startsWith("{") || inicio.startsWith("[")));
		}

	}

	record Bloque(String documento, int linea, String tipo, String texto) {

		String donde() {
			return documento + ":" + linea + " (" + tipo + ")";
		}

	}

	@LocalServerPort
	private int port;

	@Autowired
	private JsonMapper json;

	@Autowired
	private UsuarioService usuarios;

	@Autowired
	private JdbcTemplate jdbc;

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	@DisplayName("Cada bloque JSON de api.md, tiempo-real.md y las tres guías está marcado como respuesta o como petición")
	void todosLosBloquesEstanMarcados() throws Exception {
		List<String> sinMarca = new ArrayList<>();
		int json = 0;
		for (String documento : DOCUMENTOS) {
			for (Cercado cercado : cercados(documento)) {
				if (cercado.esJson()) {
					json++;
					if (cercado.marca() == null) {
						sinMarca.add(documento + ":" + cercado.linea());
					}
				}
			}
		}

		assertThat(sinMarca).as("bloques JSON sin <!-- respuesta: … --> ni <!-- peticion -->").isEmpty();
		assertThat(json).isGreaterThan(60);
	}

	@Test
	@DisplayName("Cada bloque JSON de respuesta de la documentación tiene los campos y tipos de una respuesta real")
	void cadaRespuestaDocumentadaCoincideConUnaReal() throws Exception {
		Map<String, JsonNode> reales = respuestasReales();
		List<Bloque> bloques = bloquesDeRespuesta();
		List<String> diferencias = new ArrayList<>();
		Map<String, Integer> porDocumento = new HashMap<>();

		for (Bloque bloque : bloques) {
			porDocumento.merge(bloque.documento(), 1, Integer::sum);
			List<JsonNode> valores = leerValores(bloque, diferencias);
			for (JsonNode valor : valores) {
				String clave = bloque.tipo().equals("ws") ? "ws:" + valor.path("type").asString() : bloque.tipo();
				JsonNode real = reales.get(clave);
				if (real == null) {
					diferencias.add(bloque.donde() + ": no hay respuesta real de tipo '" + clave + "'");
					continue;
				}
				comparar(valor, real, "$", bloque.donde(), diferencias);
			}
		}

		assertThat(diferencias).as("bloques que no coinciden con la respuesta real").isEmpty();
		assertThat(porDocumento.keySet()).containsExactlyInAnyOrderElementsOf(DOCUMENTOS);
		assertThat(bloques).hasSizeGreaterThan(60);
	}

	@Test
	@DisplayName("La comparación detecta campos de más o de menos, tipos distintos, null fuera de codigo, coordenadas numéricas, avisos que no son booleanos y valores fuera de los enums")
	void laComparacionDetectaDiferencias() {
		JsonNode real = json.readTree("""
				{"clave": "coordenada_a", "datos": {"tipo": "ascension_recta", "valor": "137"}, "origen": "servidor",
				 "creado_en": "2026-11-06T15:45:32.329811Z", "lista": [{"a": 1}], "codigo": null, "estado": "activa",
				 "puzzle": "photo"}""");
		List<String> diferencias = new ArrayList<>();

		comparar(json.readTree("""
				{"clave": 1, "datos": {"valor": 137, "completada": "true"}, "origen": null, "extra": true,
				 "lista": [{"b": 1}], "codigo": "puzzle_bloqueado", "estado": "terminada", "puzzle": "fotografia"}"""),
				real, "$", "prueba", diferencias);

		assertThat(diferencias).hasSize(8).anySatisfy(d -> assertThat(d).contains("$.clave", "STRING"))
				.anySatisfy(d -> assertThat(d).contains("$.datos.valor"))
				.anySatisfy(d -> assertThat(d).contains("$.datos.completada", "booleano"))
				.anySatisfy(d -> assertThat(d).contains("$.origen", "NULL"))
				.anySatisfy(d -> assertThat(d).contains("extra"))
				.anySatisfy(d -> assertThat(d).contains("creado_en"))
				.anySatisfy(d -> assertThat(d).contains("$.lista[0]"))
				.anySatisfy(d -> assertThat(d).contains("$.estado", "terminada"))
				.anySatisfy(d -> assertThat(d).contains("$.puzzle", "fotografia"));
		assertThat(diferencias).noneMatch(d -> d.contains("$.codigo"));
	}

	@Test
	@DisplayName("Cada ejemplo de respuesta de Swagger (/v3/api-docs) tiene los campos y tipos de una respuesta real")
	void cadaEjemploDeSwaggerCoincideConUnaReal() throws Exception {
		Map<String, JsonNode> reales = respuestasReales();
		JsonNode docs = ok(enviar("GET", "/v3/api-docs", null, null), 200);
		List<String> diferencias = new ArrayList<>();
		int ejemplos = 0;

		for (Map.Entry<String, JsonNode> ruta : docs.path("paths").properties()) {
			for (Map.Entry<String, JsonNode> metodo : ruta.getValue().properties()) {
				String operacion = metodo.getKey().toUpperCase() + " " + ruta.getKey();
				for (Map.Entry<String, JsonNode> respuesta : metodo.getValue().path("responses").properties()) {
					String tipo = respuesta.getKey().startsWith("4") ? "error" : TIPO_POR_OPERACION.get(operacion);
					JsonNode contenido = respuesta.getValue().path("content").path("application/json");
					List<JsonNode> valores = new ArrayList<>();
					contenido.path("examples").properties().forEach(e -> valores.add(e.getValue().path("value")));
					if (contenido.has("example")) {
						valores.add(contenido.path("example")); // springdoc pone así un ejemplo único
					}
					for (JsonNode ejemplo : valores) {
						ejemplos++;
						JsonNode valor = ejemplo;
						if (valor.isString()) {
							valor = json.readTree(valor.asString());
						}
						String donde = operacion + " " + respuesta.getKey() + " (" + tipo + ")";
						if (tipo == null || !reales.containsKey(tipo)) {
							diferencias.add(donde + ": no hay respuesta real con que compararlo");
							continue;
						}
						comparar(valor, reales.get(tipo), "$", donde, diferencias);
					}
				}
			}
		}

		assertThat(diferencias).as("ejemplos de Swagger que no coinciden con la respuesta real").isEmpty();
		assertThat(ejemplos).isGreaterThan(20);
	}

	/**
	 * Recorre una partida completa y guarda una respuesta real de cada tipo que usan las marcas. Se hace una vez para
	 * toda la clase; al final cierra la sala, así que el pueblo vuelve a quedar libre.
	 */
	private synchronized Map<String, JsonNode> respuestasReales() throws Exception {
		if (reales == null) {
			reales = partidaReal();
		}
		return reales;
	}

	private Map<String, JsonNode> partidaReal() throws Exception {
		Map<String, JsonNode> reales = new HashMap<>();
		reales.put("health", ok(enviar("GET", "/actuator/health", null, null), 200));
		reales.put("pueblos", ok(enviar("GET", "/api/v1/pueblos?disponibles=true", null, null), 200));
		sesionAdminReal(reales);

		JsonNode creada = ok(enviar("POST", "/api/v1/salas", "{\"codigo\":\"" + PUEBLO + "\"}", null), 201);
		reales.put("sala-creada", creada);
		String juego = creada.get("token").asString();
		ClienteWs wsJuego = ClienteWs.abrir(port, json);
		guardar(reales, wsJuego.autenticar(juego));

		JsonNode unida = ok(enviar("POST", "/api/v1/salas/" + PUEBLO + "/unirse", null, null), 200);
		reales.put("app-unida", unida);
		String app = unida.get("token").asString();
		ClienteWs wsApp = ClienteWs.abrir(port, json);
		guardar(reales, wsApp.autenticar(app));
		guardar(reales, wsJuego.siguienteDeTipo("dispositivo.conectado"));

		reales.put("escena", ok(enviar("PUT", "/api/v1/salas/" + PUEBLO + "/escena", "{\"escena\":\"faro\"}", juego), 200));
		guardar(reales, wsApp.siguienteDeTipo("escena.cambiada"));
		reales.put("descubrimiento", ok(enviar("POST", "/api/v1/salas/" + PUEBLO + "/descubrimientos",
				"{\"clave\":\"pescador_dialogo_completo\"}", juego), 201));
		reales.put("puzzle", ok(enviar("PUT", "/api/v1/salas/" + PUEBLO + "/puzzles/radio",
				"{\"datos\":{\"sintonizada\":true}}", app), 200));
		guardar(reales, wsJuego.siguienteDeTipo("descubrimiento.nuevo"));
		guardar(reales, wsJuego.siguienteDeTipo("puzzle.actualizado"));

		HttpResponse<String> invalido = enviar("PUT", "/api/v1/salas/" + PUEBLO + "/puzzles/photo",
				"{\"datos\":{\"completada\":\"true\"}}", app);
		assertThat(invalido.statusCode()).isEqualTo(400);
		reales.put("error", json.readTree(invalido.body()));
		assertThat(reales.get("error").get("details")).as("el error de referencia trae details").isNotEmpty();

		wsApp.enviar("{\"type\":\"orientacion\",\"payload\":{}}");
		guardar(reales, wsApp.siguienteDeTipo("error"));

		JsonNode estado = ok(enviar("GET", "/api/v1/salas/" + PUEBLO + "/estado", null, app), 200);
		assertThat(estado.get("descubrimientos")).as("el estado de referencia trae descubrimientos").isNotEmpty();
		reales.put("estado", estado);

		wsApp.cerrar();
		guardar(reales, wsJuego.siguienteDeTipo("dispositivo.desconectado"));
		assertThat(enviar("DELETE", "/api/v1/salas/" + PUEBLO, null, juego).statusCode()).isEqualTo(204);
		guardar(reales, wsJuego.siguienteDeTipo("sala.cerrada"));
		assertThat(wsJuego.cierre().codigo()).isEqualTo(1000);
		return reales;
	}

	/** Crea un admin, abre sesión con él y guarda las respuestas de auth y de usuarios. Lo borra al terminar. */
	private void sesionAdminReal(Map<String, JsonNode> reales) throws Exception {
		usuarios.crear(new CrearUsuarioRequest("Documentación", "documentacion@vestigia.site", "contraseña-larga",
				UsuarioModel.Rol.admin));
		try {
			JsonNode sesion = ok(enviar("POST", "/api/v1/auth/login",
					"{\"correo\":\"documentacion@vestigia.site\",\"password\":\"contraseña-larga\"}", null), 200);
			reales.put("sesion", sesion);
			String token = sesion.get("token").asString();
			reales.put("usuario", ok(enviar("GET", "/api/v1/auth/yo", null, token), 200));
			reales.put("usuarios", ok(enviar("GET", "/api/v1/admin/usuarios", null, token), 200));
			assertThat(enviar("POST", "/api/v1/auth/logout", null, token).statusCode()).isEqualTo(204);
		}
		finally {
			jdbc.update("delete from usuarios where correo = 'documentacion@vestigia.site'");
		}
	}

	private static void guardar(Map<String, JsonNode> reales, JsonNode mensaje) {
		reales.putIfAbsent("ws:" + mensaje.get("type").asString(), mensaje);
	}

	private List<Bloque> bloquesDeRespuesta() throws Exception {
		List<Bloque> bloques = new ArrayList<>();
		for (String documento : DOCUMENTOS) {
			for (Cercado cercado : cercados(documento)) {
				if (cercado.marca() != null && cercado.marca().group(2) != null) {
					bloques.add(new Bloque(documento, cercado.linea(), cercado.marca().group(2), cercado.texto()));
				}
			}
		}
		return bloques;
	}

	/** Todos los bloques cercados con ``` del documento, de cualquier lenguaje. */
	private static List<Cercado> cercados(String documento) throws Exception {
		List<String> lineas = Files.readAllLines(Path.of(documento));
		List<Cercado> cercados = new ArrayList<>();
		for (int i = 0; i < lineas.size(); i++) {
			String apertura = lineas.get(i).strip();
			if (!apertura.startsWith("```")) {
				continue;
			}
			StringBuilder texto = new StringBuilder();
			int j = i + 1;
			while (j < lineas.size() && !lineas.get(j).strip().equals("```")) {
				texto.append(lineas.get(j)).append('\n');
				j++;
			}
			Matcher marca = MARCA.matcher(anterior(lineas, i));
			cercados.add(new Cercado(documento, i + 1, apertura.substring(3), marca.matches() ? marca : null,
					texto.toString()));
			i = j;
		}
		return cercados;
	}

	/** La línea no vacía anterior, sin sangría. */
	private static String anterior(List<String> lineas, int i) {
		for (int j = i - 1; j >= 0; j--) {
			if (!lineas.get(j).isBlank()) {
				return lineas.get(j).strip();
			}
		}
		return "";
	}

	/** Un bloque puede traer un valor JSON o varios seguidos (un mensaje de WebSocket por línea). */
	private List<JsonNode> leerValores(Bloque bloque, List<String> diferencias) {
		List<JsonNode> valores = new ArrayList<>();
		// Un parser y readTree uno por uno: readValues desenvolvería un arreglo raíz en sus elementos. El mapper de la
		// app rechaza lo que sigue a un valor, así que se usa una copia que lo permite (y sigue rechazando claves repetidas).
		JsonMapper lector = json.rebuild().disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
		try (JsonParser parser = lector.createParser(bloque.texto())) {
			for (JsonNode valor = lector.readTree(parser); valor != null; valor = lector.readTree(parser)) {
				valores.add(valor);
			}
		}
		catch (RuntimeException ex) {
			diferencias.add(bloque.donde() + ": no es JSON válido: " + ex.getMessage());
		}
		if (valores.isEmpty()) {
			diferencias.add(bloque.donde() + ": bloque vacío");
		}
		return valores;
	}

	/**
	 * Mismos campos y mismos tipos, recursivamente. Un arreglo documentado vacío vale; si no, cada elemento se compara
	 * con el primero real. Solo en {@code codigo} se aceptan entre sí {@code null} y texto. Los {@code datos} son libres:
	 * se exige que sean objetos, que una coordenada ({@code valor}) vaya en texto y que los avisos ({@code completada},
	 * {@code iniciado}, {@code sintonizada}) sean booleanos. Los campos cerrados ({@code estado}, {@code puzzle},
	 * {@code escena_actual}, {@code origen}...) solo pueden tener los valores de los enums del backend.
	 */
	void comparar(JsonNode doc, JsonNode real, String ruta, String donde, List<String> diferencias) {
		String campo = ruta.substring(ruta.lastIndexOf('.') + 1);
		if (ruta.endsWith(".valor") && !doc.isString()) {
			diferencias.add(donde + " " + ruta + ": una coordenada va en texto, no " + doc.getNodeType());
			return;
		}
		if (ruta.endsWith(".codigo") && (doc.isNull() || real.isNull())) {
			if (!(doc.isNull() || doc.isString()) || !(real.isNull() || real.isString())) {
				diferencias.add(donde + " " + ruta + ": " + doc.getNodeType() + " en el documento, " + real.getNodeType()
						+ " en la respuesta real");
			}
			return;
		}
		if (doc.getNodeType() != real.getNodeType()) {
			diferencias.add(donde + " " + ruta + ": " + doc.getNodeType() + " en el documento, " + real.getNodeType()
					+ " en la respuesta real");
			return;
		}
		if (doc.isString() && VALORES.containsKey(campo) && !VALORES.get(campo).contains(doc.asString())) {
			diferencias.add(donde + " " + ruta + ": '" + doc.asString() + "' no es un valor válido; usa uno de "
					+ new TreeSet<>(VALORES.get(campo)));
		}
		if (doc.isObject()) {
			if (ruta.endsWith(".datos")) {
				doc.properties().forEach(c -> {
					if (c.getKey().equals("valor")) {
						comparar(c.getValue(), c.getValue(), ruta + ".valor", donde, diferencias);
					}
					if (AVISOS.contains(c.getKey()) && !c.getValue().isBoolean()) {
						diferencias.add(donde + " " + ruta + "." + c.getKey() + ": un aviso va como booleano, no "
								+ c.getValue().getNodeType());
					}
				});
				return;
			}
			Set<String> enDoc = new TreeSet<>(doc.propertyNames());
			Set<String> enReal = new TreeSet<>(real.propertyNames());
			if (!enDoc.equals(enReal)) {
				Set<String> sobran = new TreeSet<>(enDoc);
				sobran.removeAll(enReal);
				Set<String> faltan = new TreeSet<>(enReal);
				faltan.removeAll(enDoc);
				diferencias.add(donde + " " + ruta + ": campos de más " + sobran + ", campos que faltan " + faltan);
			}
			for (String hijo : enDoc) {
				if (enReal.contains(hijo)) {
					comparar(doc.get(hijo), real.get(hijo), ruta + "." + hijo, donde, diferencias);
				}
			}
		}
		else if (doc.isArray() && !doc.isEmpty()) {
			if (real.isEmpty()) {
				diferencias.add(donde + " " + ruta + ": el documento trae elementos y la respuesta real no");
				return;
			}
			for (int i = 0; i < doc.size(); i++) {
				comparar(doc.get(i), real.get(0), ruta + "[" + i + "]", donde, diferencias);
			}
		}
	}

	private static Set<String> nombres(Enum<?>[] valores) {
		return Stream.of(valores).map(Enum::name).collect(Collectors.toSet());
	}

	private JsonNode ok(HttpResponse<String> respuesta, int status) {
		assertThat(respuesta.statusCode()).as(respuesta.uri() + " " + respuesta.body()).isEqualTo(status);
		return json.readTree(respuesta.body());
	}

	private HttpResponse<String> enviar(String metodo, String ruta, String cuerpo, String token) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + ruta))
				.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
						: HttpRequest.BodyPublishers.ofString(cuerpo));
		if (cuerpo != null) {
			request.header("Content-Type", "application/json");
		}
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

}
