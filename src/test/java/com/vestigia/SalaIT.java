package com.vestigia;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Flujo de salas por HTTP contra Postgres real. Cada prueba usa un pueblo distinto del seed. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
class SalaIT {

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JsonMapper json;

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	void crearDevuelveElTokenUnaVezYGuardaSoloSuHash() throws Exception {
		HttpResponse<String> respuesta = crear("ACULCO");

		assertThat(respuesta.statusCode()).isEqualTo(201);
		JsonNode cuerpo = json.readTree(respuesta.body());
		String token = cuerpo.get("token").asString();
		assertThat(cuerpo.get("codigo").asString()).isEqualTo("ACULCO");
		assertThat(cuerpo.get("estado").asString()).isEqualTo("esperando_app");
		String hash = jdbc.queryForObject("SELECT token_juego_hash FROM salas WHERE codigo = 'ACULCO'", String.class);
		assertThat(hash).hasSize(64).isEqualTo(sha256(token));
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM salas WHERE token_juego_hash = ? OR token_app_hash = ? OR id::text = ?",
				Integer.class, token, token, token)).isZero();
	}

	@Test
	void crearInsertaLosCuatroPuzzlesBloqueados() throws Exception {
		assertThat(crear("ALAMOS").statusCode()).isEqualTo(201);

		List<String> puzzles = jdbc.queryForList("""
				SELECT p.puzzle || ':' || p.estado || ':' || p.datos::text FROM puzzles_estado p
				JOIN salas s ON s.id = p.sala_id WHERE s.codigo = 'ALAMOS' ORDER BY p.puzzle
				""", String.class);
		assertThat(puzzles).containsExactly("fotografia:bloqueado:{}", "mirador:bloqueado:{}",
				"observatorio:bloqueado:{}", "radio:bloqueado:{}");
	}

	@Test
	void crearAceptaMinusculasYEspacios() throws Exception {
		HttpResponse<String> respuesta = crear("  ajijic ");

		assertThat(respuesta.statusCode()).isEqualTo(201);
		assertThat(json.readTree(respuesta.body()).get("codigo").asString()).isEqualTo("AJIJIC");
	}

	@Test
	void crearFueraDelCatalogoDa400() throws Exception {
		HttpResponse<String> respuesta = crear("ATLANTIDA");

		assertThat(respuesta.statusCode()).isEqualTo(400);
		assertThat(json.readTree(respuesta.body()).get("message").asString()).contains("catálogo");
	}

	@Test
	void carreraPorElMismoPuebloDaUn201YSiete409() throws Exception {
		List<Integer> codigos = enParalelo(8, () -> crear("AQUISMON").statusCode());

		assertThat(codigos).filteredOn(c -> c == 201).hasSize(1);
		assertThat(codigos).filteredOn(c -> c == 409).hasSize(7);
		assertThat(salasVivas("AQUISMON")).isEqualTo(1);
	}

	@Test
	void pueblosReflejaLasSalasVivas() throws Exception {
		assertThat(crear("ARTEAGA").statusCode()).isEqualTo(201);

		JsonNode todos = json.readTree(get("/api/v1/pueblos", null).body());
		JsonNode libres = json.readTree(get("/api/v1/pueblos?disponibles=true", null).body());

		assertThat(libre(todos, "ARTEAGA")).isEqualTo("false");
		assertThat(libre(todos, "BACALAR")).isEqualTo("true");
		assertThat(libre(libres, "ARTEAGA")).isNull();
		assertThat(libre(libres, "BACALAR")).isEqualTo("true");
	}

	private HttpResponse<String> crear(String codigo) throws Exception {
		return post("/api/v1/salas", "{\"codigo\":\"" + codigo + "\"}", null);
	}

	private HttpResponse<String> post(String ruta, String cuerpo, String token) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri(ruta)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(cuerpo));
		return enviar(request, token);
	}

	private HttpResponse<String> get(String ruta, String token) throws Exception {
		return enviar(HttpRequest.newBuilder(uri(ruta)).GET(), token);
	}

	private HttpResponse<String> enviar(HttpRequest.Builder request, String token) throws Exception {
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String ruta) {
		return URI.create("http://localhost:" + port + ruta);
	}

	private static String libre(JsonNode pueblos, String codigo) {
		for (JsonNode pueblo : pueblos) {
			if (pueblo.get("codigo").asString().equals(codigo)) {
				return pueblo.get("libre").asString();
			}
		}
		return null;
	}

	private int salasVivas(String codigo) {
		return jdbc.queryForObject(
				"SELECT count(*) FROM salas WHERE codigo = ? AND estado IN ('esperando_app', 'activa')", Integer.class,
				codigo);
	}

	private static <T> List<T> enParalelo(int hilos, Callable<T> tarea) throws Exception {
		CountDownLatch salida = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(hilos);
		try {
			List<Future<T>> futuros = new ArrayList<>();
			for (int i = 0; i < hilos; i++) {
				futuros.add(executor.submit(() -> {
					salida.await();
					return tarea.call();
				}));
			}
			salida.countDown();
			List<T> resultados = new ArrayList<>();
			for (Future<T> futuro : futuros) {
				resultados.add(futuro.get());
			}
			return resultados;
		}
		finally {
			executor.shutdownNow();
		}
	}

	private static String sha256(String texto) throws Exception {
		return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
	}

}
