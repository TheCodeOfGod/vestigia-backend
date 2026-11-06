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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

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

	record Bloque(String documento, int linea, String tipo, String texto) {

		String donde() {
			return documento + ":" + linea + " (" + tipo + ")";
		}

	}

	@LocalServerPort
	private int port;

	@Autowired
	private JsonMapper json;

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	@DisplayName("Cada bloque JSON de api.md, tiempo-real.md y las tres guías está marcado como respuesta o como petición")
	void todosLosBloquesEstanMarcados() throws Exception {
		List<String> sinMarca = new ArrayList<>();
		for (String documento : DOCUMENTOS) {
			List<String> lineas = Files.readAllLines(Path.of(documento));
			for (int i = 0; i < lineas.size(); i++) {
				if (lineas.get(i).strip().equals("```json") && !MARCA.matcher(anterior(lineas, i)).matches()) {
					sinMarca.add(documento + ":" + (i + 1));
				}
			}
		}

		assertThat(sinMarca).as("bloques ```json sin <!-- respuesta: … --> ni <!-- peticion -->").isEmpty();
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
	@DisplayName("La comparación de forma detecta un campo de más, uno que falta, un tipo distinto y una coordenada numérica")
	void laComparacionDetectaDiferencias() {
		JsonNode real = json.readTree("""
				{"clave": "coordenada_a", "datos": {"tipo": "ascension_recta", "valor": "137"}, "origen": "servidor",
				 "creado_en": "2026-11-06T15:45:32.329811Z", "lista": [{"a": 1}], "codigo": null}""");
		List<String> diferencias = new ArrayList<>();

		comparar(json.readTree("""
				{"clave": 1, "datos": {"valor": 137}, "origen": "servidor", "extra": true, "lista": [{"b": 1}],
				 "codigo": "puzzle_bloqueado"}"""), real, "$", "prueba", diferencias);

		assertThat(diferencias).hasSize(4).anySatisfy(d -> assertThat(d).contains("$.clave", "STRING"))
				.anySatisfy(d -> assertThat(d).contains("$.datos.valor"))
				.anySatisfy(d -> assertThat(d).contains("extra"))
				.anySatisfy(d -> assertThat(d).contains("creado_en"))
				.anySatisfy(d -> assertThat(d).contains("$.lista[0]"));
	}

	/** Recorre una partida completa y guarda una respuesta real de cada tipo que usan las marcas. */
	private Map<String, JsonNode> respuestasReales() throws Exception {
		Map<String, JsonNode> reales = new HashMap<>();
		reales.put("health", ok(enviar("GET", "/actuator/health", null, null), 200));
		reales.put("pueblos", ok(enviar("GET", "/api/v1/pueblos?disponibles=true", null, null), 200));

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
				"{\"datos\":{\"sintonizada\":true}}", juego), 200));
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

	private static void guardar(Map<String, JsonNode> reales, JsonNode mensaje) {
		reales.putIfAbsent("ws:" + mensaje.get("type").asString(), mensaje);
	}

	private List<Bloque> bloquesDeRespuesta() throws Exception {
		List<Bloque> bloques = new ArrayList<>();
		for (String documento : DOCUMENTOS) {
			List<String> lineas = Files.readAllLines(Path.of(documento));
			for (int i = 0; i < lineas.size(); i++) {
				if (!lineas.get(i).strip().equals("```json")) {
					continue;
				}
				Matcher marca = MARCA.matcher(anterior(lineas, i));
				if (!marca.matches() || marca.group(2) == null) {
					continue;
				}
				StringBuilder texto = new StringBuilder();
				int j = i + 1;
				while (!lineas.get(j).strip().equals("```")) {
					texto.append(lineas.get(j)).append('\n');
					j++;
				}
				bloques.add(new Bloque(documento, i + 1, marca.group(2), texto.toString()));
				i = j;
			}
		}
		return bloques;
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
	 * con el primero real. {@code null} y texto se aceptan entre sí ({@code codigo}). Los {@code datos} son libres: solo
	 * se exige que sean objetos. Toda coordenada ({@code valor}) va en texto.
	 */
	void comparar(JsonNode doc, JsonNode real, String ruta, String donde, List<String> diferencias) {
		if (ruta.endsWith(".valor") && !doc.isString()) {
			diferencias.add(donde + " " + ruta + ": una coordenada va en texto, no " + doc.getNodeType());
			return;
		}
		if (doc.isNull() || real.isNull()) {
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
		if (doc.isObject()) {
			if (ruta.endsWith(".datos")) {
				doc.properties().forEach(c -> {
					if (c.getKey().equals("valor")) {
						comparar(c.getValue(), c.getValue(), ruta + ".valor", donde, diferencias);
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
			for (String campo : enDoc) {
				if (enReal.contains(campo)) {
					comparar(doc.get(campo), real.get(campo), ruta + "." + campo, donde, diferencias);
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
