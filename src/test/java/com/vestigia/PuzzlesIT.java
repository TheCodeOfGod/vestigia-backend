package com.vestigia;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeType;

/**
 * PUT /salas/{codigo}/puzzles/{puzzle} por HTTP contra Postgres real. Cada prueba usa un pueblo distinto del seed, que
 * ninguna otra *IT reclama.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
class PuzzlesIT {

	private static final String COMPLETADA = "{\"completada\":true}";

	private static final String INICIADO = "{\"iniciado\":true}";

	private static final String SINTONIZADA = "{\"sintonizada\":true}";

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JsonMapper json;

	@Autowired
	private RelojDePrueba reloj;

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
	@DisplayName("La cadena completa por HTTP: pescador → radio → coordenada_a; tres piezas → photo → coordenada_b; lookout_enabled → lookout; observatory_enabled → observatory → magnetar_localizado")
	void cadenaCompleta() throws Exception {
		String juego = api.tokenJuego("COSALA");
		String app = api.tokenApp("COSALA");

		assertThat(descubrir("COSALA", "pescador_dialogo_completo", juego).statusCode()).isEqualTo(201);
		assertThat(estados("COSALA")).containsExactly("radio:disponible", "photo:bloqueado", "lookout:bloqueado",
				"observatory:bloqueado");
		HttpResponse<String> radio = reportar("COSALA", "radio", SINTONIZADA, app);
		assertThat(radio.statusCode()).isEqualTo(200);
		assertThat(api.cuerpo(radio)).isEqualTo(json.readTree(
				"{\"puzzle\":\"radio\",\"estado\":\"resuelto\",\"datos\":{\"sintonizada\":true}}"));

		for (String pieza : List.of("pieza_1", "pieza_2", "pieza_3")) {
			descubrir("COSALA", pieza, juego);
		}
		assertThat(estados("COSALA")).containsExactly("radio:resuelto", "photo:disponible", "lookout:bloqueado",
				"observatory:bloqueado");
		assertThat(api.cuerpo(reportar("COSALA", "photo", INICIADO, app)).get("estado").asString())
				.isEqualTo("en_curso");
		assertThat(api.cuerpo(reportar("COSALA", "photo", COMPLETADA, app)).get("estado").asString())
				.isEqualTo("resuelto");
		assertThat(estados("COSALA")).containsExactly("radio:resuelto", "photo:resuelto", "lookout:bloqueado",
				"observatory:bloqueado");

		descubrir("COSALA", "lookout_enabled", juego);
		assertThat(estados("COSALA")).contains("lookout:disponible");
		assertThat(reportar("COSALA", "lookout", INICIADO, app).statusCode()).isEqualTo(200);
		assertThat(reportar("COSALA", "lookout", COMPLETADA, app).statusCode()).isEqualTo(200);
		assertThat(estados("COSALA")).contains("lookout:resuelto", "observatory:bloqueado");

		descubrir("COSALA", "observatory_enabled", juego);
		assertThat(estados("COSALA")).contains("observatory:disponible");
		assertThat(api.cuerpo(reportar("COSALA", "observatory", COMPLETADA, app)).get("estado").asString())
				.isEqualTo("resuelto");

		JsonNode estado = api.cuerpo(api.enviar("GET", "/api/v1/salas/COSALA/estado", null, juego));
		assertThat(estado.get("puzzles").valueStream()
				.map(p -> p.get("puzzle").asString() + ":" + p.get("estado").asString() + ":" + p.get("datos")))
				.containsExactly("radio:resuelto:{\"sintonizada\":true}", "photo:resuelto:{\"completada\":true}",
						"lookout:resuelto:{\"completada\":true}", "observatory:resuelto:{\"completada\":true}");
		Map<String, JsonNode> descubrimientos = new LinkedHashMap<>();
		estado.get("descubrimientos").valueStream().forEach(d -> descubrimientos.put(d.get("clave").asString(), d));
		assertThat(descubrimientos.keySet()).containsExactly("pescador_dialogo_completo", "coordenada_a", "pieza_1",
				"pieza_2", "pieza_3", "coordenada_b", "lookout_enabled", "observatorio_visible", "observatory_enabled",
				"magnetar_localizado");
		for (String clave : List.of("coordenada_a", "coordenada_b", "observatorio_visible", "magnetar_localizado")) {
			assertThat(descubrimientos.get(clave).get("origen").asString()).as(clave).isEqualTo("servidor");
		}
		assertThat(descubrimientos.get("coordenada_a").get("datos"))
				.isEqualTo(json.readTree("{\"tipo\":\"ascension_recta\",\"valor\":\"137\"}"));
		assertThat(descubrimientos.get("coordenada_b").get("datos"))
				.isEqualTo(json.readTree("{\"tipo\":\"declinacion\",\"valor\":\"+042\"}"));
		assertThat(descubrimientos.get("magnetar_localizado").get("datos")).isEqualTo(json.readTree("{}"));

		assertThat(eventos("COSALA")).containsExactly("sala.creada:juego", "app.unida:app",
				"descubrimiento.registrado:juego", "puzzle.habilitado:servidor",
				"puzzle.resuelto:app", "descubrimiento.registrado:servidor",
				"descubrimiento.registrado:juego", "descubrimiento.registrado:juego",
				"descubrimiento.registrado:juego", "puzzle.habilitado:servidor",
				"puzzle.iniciado:app", "puzzle.resuelto:app", "descubrimiento.registrado:servidor",
				"descubrimiento.registrado:juego", "puzzle.habilitado:servidor",
				"puzzle.iniciado:app", "puzzle.resuelto:app", "descubrimiento.registrado:servidor",
				"descubrimiento.registrado:juego", "puzzle.habilitado:servidor",
				"puzzle.resuelto:app", "descubrimiento.registrado:servidor");
	}

	@Test
	@DisplayName("Cada cambio de estado de un puzzle guarda {puzzle, estado, datos} y cada resultado guarda {clave, datos} con origen servidor")
	void payloadDeLosEventos() throws Exception {
		String juego = api.tokenJuego("ATLIXCO");
		String app = api.tokenApp("ATLIXCO");
		piezas("ATLIXCO", juego);

		reportar("ATLIXCO", "photo", INICIADO, app);
		reportar("ATLIXCO", "photo", COMPLETADA, app);

		assertThat(jdbc.queryForList("""
				SELECT tipo || ':' || origen || ':' || payload::text FROM eventos WHERE sala_id = ?
				AND (tipo LIKE 'puzzle.%' OR (tipo = 'descubrimiento.registrado' AND origen = 'servidor'))
				ORDER BY id
				""", String.class, salaViva("ATLIXCO"))).containsExactly(
				"puzzle.habilitado:servidor:{\"datos\": {}, \"estado\": \"disponible\", \"puzzle\": \"photo\"}",
				"puzzle.iniciado:app:{\"datos\": {\"iniciado\": true}, \"estado\": \"en_curso\", \"puzzle\": \"photo\"}",
				"puzzle.resuelto:app:{\"datos\": {\"completada\": true}, \"estado\": \"resuelto\", \"puzzle\": \"photo\"}",
				"descubrimiento.registrado:servidor:{\"clave\": \"coordenada_b\", \"datos\": {\"tipo\": \"declinacion\", \"valor\": \"+042\"}}");
	}

	@Test
	@DisplayName("lookout_enabled y observatory_enabled que llegan antes que sus requisitos habilitan su puzzle en el PUT que completa el último requisito")
	void banderasAntesDeSusRequisitos() throws Exception {
		String juego = api.tokenJuego("COTIJA");
		String app = api.tokenApp("COTIJA");
		descubrir("COTIJA", "lookout_enabled", juego);
		descubrir("COTIJA", "observatory_enabled", juego);
		radioResuelta("COTIJA", juego, app);
		piezas("COTIJA", juego);
		assertThat(estados("COTIJA")).containsExactly("radio:resuelto", "photo:disponible", "lookout:bloqueado",
				"observatory:bloqueado");

		reportar("COTIJA", "photo", COMPLETADA, app);
		assertThat(estados("COTIJA")).containsExactly("radio:resuelto", "photo:resuelto", "lookout:disponible",
				"observatory:bloqueado");

		reportar("COTIJA", "lookout", COMPLETADA, app);
		assertThat(estados("COTIJA")).containsExactly("radio:resuelto", "photo:resuelto", "lookout:resuelto",
				"observatory:disponible");
	}

	@Test
	@DisplayName("lookout_enabled y observatory_enabled que llegan después de sus requisitos habilitan su puzzle en ese mismo POST")
	void banderasDespuesDeSusRequisitos() throws Exception {
		String juego = api.tokenJuego("COZUMEL");
		String app = api.tokenApp("COZUMEL");
		radioResuelta("COZUMEL", juego, app);
		piezas("COZUMEL", juego);
		reportar("COZUMEL", "photo", COMPLETADA, app);
		assertThat(estados("COZUMEL")).contains("lookout:bloqueado");

		descubrir("COZUMEL", "lookout_enabled", juego);
		assertThat(estados("COZUMEL")).contains("lookout:disponible", "observatory:bloqueado");

		reportar("COZUMEL", "lookout", COMPLETADA, app);
		assertThat(estados("COZUMEL")).contains("lookout:resuelto", "observatory:bloqueado");

		descubrir("COZUMEL", "observatory_enabled", juego);
		assertThat(estados("COZUMEL")).contains("observatory:disponible");
	}

	@Test
	@DisplayName("observatory espera coordenada_a: con lookout resuelto y su bandera sigue bloqueado hasta que se resuelve radio, aunque llegue al final")
	void observatoryEsperaLaCoordenadaA() throws Exception {
		String juego = api.tokenJuego("CREEL");
		String app = api.tokenApp("CREEL");
		piezas("CREEL", juego);
		descubrir("CREEL", "lookout_enabled", juego);
		descubrir("CREEL", "observatory_enabled", juego);
		reportar("CREEL", "photo", COMPLETADA, app);
		reportar("CREEL", "lookout", COMPLETADA, app);
		assertThat(estados("CREEL")).containsExactly("radio:bloqueado", "photo:resuelto", "lookout:resuelto",
				"observatory:bloqueado");

		radioResuelta("CREEL", juego, app);

		assertThat(estados("CREEL")).containsExactly("radio:resuelto", "photo:resuelto", "lookout:resuelto",
				"observatory:disponible");
	}

	@Test
	@DisplayName("Matriz de estados: bloqueado da 409; disponible+iniciado pasa a en_curso; en_curso+iniciado no cambia; en_curso+completada resuelve; resuelto+cualquier aviso no cambia ni duplica")
	void matrizDeEstados() throws Exception {
		String juego = api.tokenJuego("CORDOBA");
		String app = api.tokenApp("CORDOBA");

		for (String datos : List.of(INICIADO, COMPLETADA)) {
			HttpResponse<String> bloqueado = reportar("CORDOBA", "photo", datos, app);
			assertThat(bloqueado.statusCode()).as(datos).isEqualTo(409);
			assertThat(api.cuerpo(bloqueado).get("codigo").asString()).isEqualTo("puzzle_bloqueado");
		}
		assertThat(fila("CORDOBA", "photo")).isEqualTo("bloqueado:{}");

		piezas("CORDOBA", juego);
		assertThat(fila("CORDOBA", "photo")).isEqualTo("disponible:{}");

		assertThat(respuesta(reportar("CORDOBA", "photo", INICIADO, app)))
				.isEqualTo("200:en_curso:{\"iniciado\":true}");
		int eventosEnCurso = eventos("CORDOBA").size();
		assertThat(respuesta(reportar("CORDOBA", "photo", INICIADO, app)))
				.isEqualTo("200:en_curso:{\"iniciado\":true}");
		assertThat(eventos("CORDOBA")).hasSize(eventosEnCurso);

		assertThat(respuesta(reportar("CORDOBA", "photo", COMPLETADA, app)))
				.isEqualTo("200:resuelto:{\"completada\":true}");
		List<String> eventosResuelto = eventos("CORDOBA");
		for (String datos : List.of(INICIADO, COMPLETADA, COMPLETADA)) {
			assertThat(respuesta(reportar("CORDOBA", "photo", datos, app))).as(datos)
					.isEqualTo("200:resuelto:{\"completada\":true}");
		}
		assertThat(fila("CORDOBA", "photo")).isEqualTo("resuelto:{\"completada\": true}");
		assertThat(eventos("CORDOBA")).isEqualTo(eventosResuelto);
		assertThat(eventosResuelto).filteredOn("puzzle.iniciado:app"::equals).hasSize(1);
		assertThat(eventosResuelto).filteredOn("puzzle.resuelto:app"::equals).hasSize(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM descubrimientos WHERE sala_id = ? AND clave = 'coordenada_b'",
				Integer.class, salaViva("CORDOBA"))).isEqualTo(1);

		radioResuelta("CORDOBA", juego, app);
		assertThat(respuesta(reportar("CORDOBA", "radio", SINTONIZADA, app)))
				.isEqualTo("200:resuelto:{\"sintonizada\":true}");
	}

	@Test
	@DisplayName("Solo el booleano true: texto \"true\", 1, false, null, claves extra, objeto vacío, datos que no es objeto o datos ausente dan 400 con details en datos y no cambian nada; completada en radio también")
	void soloTrue() throws Exception {
		String juego = api.tokenJuego("COCULA");
		String app = api.tokenApp("COCULA");
		piezas("COCULA", juego);
		descubrir("COCULA", "pescador_dialogo_completo", juego);
		List<String> eventosAntes = eventos("COCULA");

		for (String datos : List.of("{\"completada\":\"true\"}", "{\"completada\":1}", "{\"completada\":false}",
				"{\"completada\":null}", "{\"completada\":true,\"extra\":1}", "{\"completada\":true,\"iniciado\":true}",
				"{}", "{\"sintonizada\":true}", "{\"completada\":\"si\"}", "true", "[true]", "[]", "\"completada\"",
				"123")) {
			HttpResponse<String> respuesta = reportar("COCULA", "photo", datos, app);
			assertThat(respuesta.statusCode()).as(datos).isEqualTo(400);
			JsonNode cuerpo = api.cuerpo(respuesta);
			assertThat(cuerpo.get("message").asString()).isEqualTo("Datos inválidos");
			assertThat(cuerpo.get("details").get(0).get("field").asString()).isEqualTo("datos");
			assertThat(cuerpo.get("details").get(0).get("message").asString())
					.isEqualTo("en photo debe ser exactamente {\"completada\": true} o {\"iniciado\": true}");
		}
		for (String cuerpo : List.of("{}", "{\"datos\":null}")) {
			HttpResponse<String> respuesta = api.enviar("PUT", "/api/v1/salas/COCULA/puzzles/photo", cuerpo, app);
			assertThat(respuesta.statusCode()).as(cuerpo).isEqualTo(400);
			assertThat(api.cuerpo(respuesta).get("details").get(0).get("field").asString()).isEqualTo("datos");
		}
		HttpResponse<String> radio = reportar("COCULA", "radio", COMPLETADA, app);
		assertThat(radio.statusCode()).as("radio " + COMPLETADA).isEqualTo(400);
		assertThat(api.cuerpo(radio).get("details").get(0).get("message").asString())
				.isEqualTo("en radio debe ser exactamente {\"sintonizada\": true} o {\"iniciado\": true}");

		assertThat(estados("COCULA")).containsExactly("radio:disponible", "photo:disponible", "lookout:bloqueado",
				"observatory:bloqueado");
		assertThat(eventos("COCULA")).isEqualTo(eventosAntes);
	}

	@Test
	@DisplayName("Un id de puzzle desconocido da 400 con la lista de ids, igual para los viejos fotografia, mirador y observatorio, y no toca la sala")
	void puzzleDesconocidoDa400() throws Exception {
		String juego = api.tokenJuego("COPAINALA");
		List<String> eventosAntes = eventos("COPAINALA");

		for (String id : List.of("fotografia", "mirador", "observatorio", "PHOTO", "Radio", "telescopio")) {
			HttpResponse<String> respuesta = reportar("COPAINALA", id, COMPLETADA, juego);
			assertThat(respuesta.statusCode()).as(id).isEqualTo(400);
			assertThat(api.cuerpo(respuesta).get("message").asString())
					.isEqualTo("'" + id + "' no es un puzzle válido. Usa uno de: radio, photo, lookout, observatory");
		}
		assertThat(eventos("COPAINALA")).isEqualTo(eventosAntes);
	}

	@Test
	@DisplayName("Diez completada simultáneos sobre photo responden 200 y dejan una sola coordenada_b, un solo puzzle.resuelto y lookout habilitado una vez")
	void completadaSimultaneos() throws Exception {
		String juego = api.tokenJuego("COSCOMATEPEC");
		String app = api.tokenApp("COSCOMATEPEC");
		piezas("COSCOMATEPEC", juego);
		descubrir("COSCOMATEPEC", "lookout_enabled", juego);

		List<String> respuestas = ClienteApi.enParalelo(10,
				() -> respuesta(reportar("COSCOMATEPEC", "photo", COMPLETADA, app)));

		assertThat(respuestas).containsOnly("200:resuelto:{\"completada\":true}").hasSize(10);
		UUID sala = salaViva("COSCOMATEPEC");
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM descubrimientos WHERE sala_id = ? AND clave = 'coordenada_b'", Integer.class,
				sala)).isEqualTo(1);
		List<String> eventos = eventos("COSCOMATEPEC");
		assertThat(eventos).filteredOn("puzzle.resuelto:app"::equals).hasSize(1);
		assertThat(eventos).filteredOn("descubrimiento.registrado:servidor"::equals).hasSize(1);
		assertThat(eventos).filteredOn("puzzle.habilitado:servidor"::equals).hasSize(2);
		assertThat(estados("COSCOMATEPEC")).contains("photo:resuelto", "lookout:disponible");
	}

	@Test
	@DisplayName("Un cliente no puede fabricar un resultado del servidor: POST de las cuatro claves da 403 y PUT de un puzzle bloqueado da 409, sin crear nada; el resultado real tiene origen servidor y los datos de la configuración")
	void clienteNoFabricaResultados() throws Exception {
		String juego = api.tokenJuego("ESPITA");
		String app = api.tokenApp("ESPITA");
		UUID sala = salaViva("ESPITA");

		for (String clave : List.of("coordenada_a", "coordenada_b", "observatorio_visible", "magnetar_localizado")) {
			for (String token : List.of(juego, app)) {
				assertThat(api.enviar("POST", "/api/v1/salas/ESPITA/descubrimientos",
						"{\"clave\":\"" + clave + "\",\"datos\":{\"valor\":\"999\"}}", token).statusCode())
						.as(clave).isEqualTo(403);
			}
		}
		assertThat(reportar("ESPITA", "radio", SINTONIZADA, app).statusCode()).isEqualTo(409);
		assertThat(reportar("ESPITA", "observatory", COMPLETADA, app).statusCode()).isEqualTo(409);
		assertThat(reportar("ESPITA", "radio", "{\"sintonizada\":true,\"valor\":\"999\"}", app).statusCode())
				.isEqualTo(400);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM descubrimientos WHERE sala_id = ?", Integer.class, sala))
				.isZero();

		radioResuelta("ESPITA", juego, app);
		assertThat(api.enviar("POST", "/api/v1/salas/ESPITA/descubrimientos", "{\"clave\":\"coordenada_a\"}", app)
				.statusCode()).isEqualTo(403);
		assertThat(jdbc.queryForObject(
				"SELECT origen || ':' || datos::text FROM descubrimientos WHERE sala_id = ? AND clave = 'coordenada_a'",
				String.class, sala)).isEqualTo("servidor:{\"tipo\": \"ascension_recta\", \"valor\": \"137\"}");
	}

	@Test
	@DisplayName("\"+042\" llega intacto como texto en GET estado y en la base (jsonb string), igual que \"137\"")
	void coordenadasComoTexto() throws Exception {
		String juego = api.tokenJuego("FRONTERA");
		String app = api.tokenApp("FRONTERA");
		radioResuelta("FRONTERA", juego, app);
		piezas("FRONTERA", juego);
		reportar("FRONTERA", "photo", COMPLETADA, app);

		JsonNode estado = api.cuerpo(api.enviar("GET", "/api/v1/salas/FRONTERA/estado", null, app));
		Map<String, JsonNode> valores = new LinkedHashMap<>();
		estado.get("descubrimientos").valueStream()
				.filter(d -> d.get("clave").asString().startsWith("coordenada_"))
				.forEach(d -> valores.put(d.get("clave").asString(), d.get("datos").get("valor")));
		assertThat(valores.get("coordenada_a").getNodeType()).isEqualTo(JsonNodeType.STRING);
		assertThat(valores.get("coordenada_a").asString()).isEqualTo("137");
		assertThat(valores.get("coordenada_b").getNodeType()).isEqualTo(JsonNodeType.STRING);
		assertThat(valores.get("coordenada_b").asString()).isEqualTo("+042");
		assertThat(jdbc.queryForList("""
				SELECT clave || ':' || (datos->>'valor') || ':' || jsonb_typeof(datos->'valor') FROM descubrimientos
				WHERE sala_id = ? AND clave LIKE 'coordenada_%' ORDER BY clave
				""", String.class, salaViva("FRONTERA")))
				.containsExactly("coordenada_a:137:string", "coordenada_b:+042:string");
	}

	@Test
	@DisplayName("Resolver un puzzle actualiza ultima_actividad y renueva expira_en; repetir el aviso también renueva, sin guardar eventos")
	void resolverRenuevaLaExpiracion() throws Exception {
		String juego = api.tokenJuego("GUACHOCHI");
		String app = api.tokenApp("GUACHOCHI");
		descubrir("GUACHOCHI", "pescador_dialogo_completo", juego);

		reloj.avanzar(Duration.ofHours(1));
		Instant antes = reloj.instant();
		reportar("GUACHOCHI", "radio", SINTONIZADA, app);
		assertThat(actividad("GUACHOCHI")).allSatisfy(t -> assertThat(t).isBetween(antes, antes.plusSeconds(5)));
		assertThat(expiraEn("GUACHOCHI")).isBetween(antes.plus(Duration.ofHours(2)),
				antes.plus(Duration.ofHours(2)).plusSeconds(5));

		int eventosAntes = eventos("GUACHOCHI").size();
		reloj.avanzar(Duration.ofHours(1));
		Instant repetido = reloj.instant();
		assertThat(reportar("GUACHOCHI", "radio", SINTONIZADA, app).statusCode()).isEqualTo(200);
		assertThat(expiraEn("GUACHOCHI")).isBetween(repetido.plus(Duration.ofHours(2)),
				repetido.plus(Duration.ofHours(2)).plusSeconds(5));
		assertThat(eventos("GUACHOCHI")).hasSize(eventosAntes);
	}

	@Test
	@DisplayName("Los errores reales de PUT puzzles traen codigo null en 400, 401, 403, 404 y 405, y puzzle_bloqueado en el 409")
	void codigoEnLosErrores() throws Exception {
		String juego = api.tokenJuego("GUADALUPE");
		String app = api.tokenApp("GUADALUPE");
		String otraSala = api.tokenJuego("GUERRERO");

		Map<String, HttpResponse<String>> errores = new LinkedHashMap<>();
		errores.put("400 datos", reportar("GUADALUPE", "photo", "{\"completada\":1}", app));
		errores.put("400 json", api.enviar("PUT", "/api/v1/salas/GUADALUPE/puzzles/photo", "{\"datos\":", app));
		errores.put("400 puzzle", reportar("GUADALUPE", "fotografia", COMPLETADA, app));
		errores.put("401", reportar("GUADALUPE", "photo", COMPLETADA, null));
		errores.put("403", reportar("GUADALUPE", "photo", COMPLETADA, otraSala));
		errores.put("404", api.enviar("GET", "/api/v1/salas/GUADALUPE/no-existe", null, null));
		errores.put("405", api.enviar("GET", "/api/v1/salas/GUADALUPE/puzzles/photo", null, juego));

		errores.forEach((caso, respuesta) -> {
			assertThat(respuesta.statusCode()).as(caso).isEqualTo(Integer.parseInt(caso.substring(0, 3)));
			JsonNode cuerpo = api.cuerpo(respuesta);
			assertThat(cuerpo.has("codigo")).as(caso).isTrue();
			assertThat(cuerpo.get("codigo").isNull()).as(caso).isTrue();
		});
		HttpResponse<String> bloqueado = reportar("GUADALUPE", "photo", COMPLETADA, app);
		assertThat(bloqueado.statusCode()).isEqualTo(409);
		assertThat(api.cuerpo(bloqueado)).isEqualTo(json.readTree(String.format("""
				{"timestamp": %s, "status": 409, "error": "Conflict", "codigo": "puzzle_bloqueado",
				 "message": "El puzzle 'photo' está bloqueado: todavía no se cumplen sus requisitos",
				 "path": "/api/v1/salas/GUADALUPE/puzzles/photo", "details": []}
				""", api.cuerpo(bloqueado).get("timestamp"))));
	}

	@Test
	@DisplayName("La radio la juega la app: {iniciado: true} la pone en_curso y {sintonizada: true} la resuelve y crea coordenada_a con origen servidor; repetir avisos no cambia nada")
	void laRadioLaJuegaLaApp() throws Exception {
		String juego = api.tokenJuego("XICO");
		String app = api.tokenApp("XICO");
		assertThat(reportar("XICO", "radio", INICIADO, app).statusCode()).as("bloqueada hasta hablar con el pescador")
				.isEqualTo(409);
		descubrir("XICO", "pescador_dialogo_completo", juego);
		assertThat(estados("XICO")).contains("radio:disponible");

		HttpResponse<String> iniciada = reportar("XICO", "radio", INICIADO, app);
		assertThat(iniciada.statusCode()).isEqualTo(200);
		assertThat(api.cuerpo(iniciada))
				.isEqualTo(json.readTree("{\"puzzle\":\"radio\",\"estado\":\"en_curso\",\"datos\":{\"iniciado\":true}}"));
		HttpResponse<String> sintonizada = reportar("XICO", "radio", SINTONIZADA, app);
		assertThat(sintonizada.statusCode()).isEqualTo(200);
		assertThat(api.cuerpo(sintonizada)).isEqualTo(
				json.readTree("{\"puzzle\":\"radio\",\"estado\":\"resuelto\",\"datos\":{\"sintonizada\":true}}"));
		assertThat(eventos("XICO")).containsExactly("sala.creada:juego", "app.unida:app",
				"descubrimiento.registrado:juego", "puzzle.habilitado:servidor", "puzzle.iniciado:app",
				"puzzle.resuelto:app", "descubrimiento.registrado:servidor");

		int eventosAntes = eventos("XICO").size();
		assertThat(reportar("XICO", "radio", INICIADO, app).statusCode()).isEqualTo(200);
		assertThat(reportar("XICO", "radio", SINTONIZADA, app).statusCode()).isEqualTo(200);
		assertThat(fila("XICO", "radio")).isEqualTo("resuelto:{\"sintonizada\": true}");
		assertThat(eventos("XICO")).hasSize(eventosAntes);
		JsonNode estado = api.cuerpo(api.enviar("GET", "/api/v1/salas/XICO/estado", null, juego));
		assertThat(estado.get("descubrimientos").valueStream().filter(d -> d.get("clave").asString().equals("coordenada_a"))
				.map(d -> d.get("origen").asString() + ":" + d.get("datos"))).containsExactly(
						"servidor:{\"tipo\":\"ascension_recta\",\"valor\":\"137\"}");
	}

	@Test
	@DisplayName("El juego no avisa puzzles: PUT de los cuatro con su token da 403 con codigo puzzle_solo_app, aunque el puzzle esté disponible, y no cambia nada; con el token de la app sí avanza")
	void elJuegoNoAvisaPuzzles() throws Exception {
		String juego = api.tokenJuego("XICOTEPEC");
		String app = api.tokenApp("XICOTEPEC");
		descubrir("XICOTEPEC", "pescador_dialogo_completo", juego);
		piezas("XICOTEPEC", juego);
		List<String> estadosAntes = estados("XICOTEPEC");
		List<String> eventosAntes = eventos("XICOTEPEC");

		for (Map.Entry<String, String> caso : Map.of("radio", SINTONIZADA, "photo", COMPLETADA, "lookout", INICIADO,
				"observatory", COMPLETADA).entrySet()) {
			HttpResponse<String> respuesta = reportar("XICOTEPEC", caso.getKey(), caso.getValue(), juego);
			assertThat(respuesta.statusCode()).as(caso.getKey()).isEqualTo(403);
			assertThat(api.cuerpo(respuesta)).isEqualTo(json.readTree(String.format("""
					{"timestamp": %s, "status": 403, "error": "Forbidden", "codigo": "puzzle_solo_app",
					 "message": "Los puzzles los avisa la app; el juego no puede reportar '%s'",
					 "path": "/api/v1/salas/XICOTEPEC/puzzles/%s", "details": []}
					""", api.cuerpo(respuesta).get("timestamp"), caso.getKey(), caso.getKey())));
		}
		assertThat(estados("XICOTEPEC")).isEqualTo(estadosAntes);
		assertThat(eventos("XICOTEPEC")).isEqualTo(eventosAntes);
		assertThat(reportar("XICOTEPEC", "radio", SINTONIZADA, app).statusCode()).isEqualTo(200);
		assertThat(reportar("XICOTEPEC", "photo", COMPLETADA, app).statusCode()).isEqualTo(200);
	}

	@Test
	@DisplayName("Resolver es una sola transacción: si crear el resultado falla, el puzzle no queda resuelto, no se guarda ningún evento y la sala no se renueva")
	void resolverEsUnaSolaTransaccion() throws Exception {
		String juego = api.tokenJuego("HUAUCHINANGO");
		String app = api.tokenApp("HUAUCHINANGO");
		piezas("HUAUCHINANGO", juego);
		descubrir("HUAUCHINANGO", "lookout_enabled", juego);
		UUID sala = salaViva("HUAUCHINANGO");
		jdbc.update("INSERT INTO descubrimientos (sala_id, clave, datos, origen) VALUES (?, 'coordenada_b', '{}', 'servidor')",
				sala);
		List<String> eventosAntes = eventos("HUAUCHINANGO");
		Instant expiraAntes = expiraEn("HUAUCHINANGO");
		reloj.avanzar(Duration.ofMinutes(10));

		HttpResponse<String> respuesta = reportar("HUAUCHINANGO", "photo", COMPLETADA, app);

		assertThat(respuesta.statusCode()).isEqualTo(500);
		assertThat(api.cuerpo(respuesta).get("codigo").isNull()).isTrue();
		assertThat(fila("HUAUCHINANGO", "photo")).isEqualTo("disponible:{}");
		assertThat(estados("HUAUCHINANGO")).contains("lookout:bloqueado");
		assertThat(eventos("HUAUCHINANGO")).isEqualTo(eventosAntes);
		assertThat(expiraEn("HUAUCHINANGO")).isEqualTo(expiraAntes);
	}

	@Test
	@DisplayName("Con expira_en vencido y sin que corra el job, PUT puzzles responde 401 y no resuelve nada")
	void salaVencidaDa401() throws Exception {
		String juego = api.tokenJuego("HUAMANTLA");
		descubrir("HUAMANTLA", "pescador_dialogo_completo", juego);
		UUID sala = salaViva("HUAMANTLA");

		reloj.avanzar(Duration.ofHours(2).plusSeconds(1));

		assertThat(reportar("HUAMANTLA", "radio", SINTONIZADA, juego).statusCode()).isEqualTo(401);
		assertThat(jdbc.queryForObject("SELECT estado FROM puzzles_estado WHERE sala_id = ? AND puzzle = 'radio'",
				String.class, sala)).isEqualTo("disponible");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM descubrimientos WHERE sala_id = ? AND clave = 'coordenada_a'",
				Integer.class, sala)).isZero();
	}

	private HttpResponse<String> reportar(String codigo, String puzzle, String datos, String token) throws Exception {
		return api.enviar("PUT", "/api/v1/salas/" + codigo + "/puzzles/" + puzzle, "{\"datos\":" + datos + "}", token);
	}

	private HttpResponse<String> descubrir(String codigo, String clave, String token) throws Exception {
		return api.enviar("POST", "/api/v1/salas/" + codigo + "/descubrimientos", "{\"clave\":\"" + clave + "\"}",
				token);
	}

	private void piezas(String codigo, String juego) throws Exception {
		for (String pieza : List.of("pieza_1", "pieza_2", "pieza_3")) {
			descubrir(codigo, pieza, juego);
		}
	}

	/** El juego registra al pescador; la app sintoniza la radio. */
	private void radioResuelta(String codigo, String juego, String app) throws Exception {
		descubrir(codigo, "pescador_dialogo_completo", juego);
		assertThat(reportar(codigo, "radio", SINTONIZADA, app).statusCode()).isEqualTo(200);
	}

	/** {@code status:estado:datos} de la respuesta de PUT puzzles, o {@code status:message} si es un error. */
	private String respuesta(HttpResponse<String> respuesta) {
		JsonNode cuerpo = api.cuerpo(respuesta);
		return respuesta.statusCode() + ":" + (cuerpo.has("estado")
				? cuerpo.get("estado").asString() + ":" + cuerpo.get("datos") : cuerpo.get("message").asString());
	}

	/** Los cuatro puzzles en el orden del juego, como los devuelve GET estado: {@code puzzle:estado}. */
	private List<String> estados(String codigo) {
		return jdbc.queryForList("""
				SELECT puzzle || ':' || estado FROM puzzles_estado WHERE sala_id = ?
				ORDER BY array_position(ARRAY['radio', 'photo', 'lookout', 'observatory']::varchar[], puzzle)
				""", String.class, salaViva(codigo));
	}

	/** {@code estado:datos} de la fila del puzzle en la base. */
	private String fila(String codigo, String puzzle) {
		return jdbc.queryForObject("SELECT estado || ':' || datos::text FROM puzzles_estado WHERE sala_id = ? AND puzzle = ?",
				String.class, salaViva(codigo), puzzle);
	}

	private UUID salaViva(String codigo) {
		return jdbc.queryForObject("SELECT id FROM salas WHERE codigo = ? AND estado IN ('esperando_app', 'activa')",
				UUID.class, codigo);
	}

	private Instant expiraEn(String codigo) {
		return jdbc.queryForObject("SELECT expira_en FROM salas WHERE id = ?", Instant.class, salaViva(codigo));
	}

	/** ultima_actividad de la sala y actualizado_en de radio. */
	private List<Instant> actividad(String codigo) {
		UUID sala = salaViva(codigo);
		return List.of(jdbc.queryForObject("SELECT ultima_actividad FROM salas WHERE id = ?", Instant.class, sala),
				jdbc.queryForObject("SELECT actualizado_en FROM puzzles_estado WHERE sala_id = ? AND puzzle = 'radio'",
						Instant.class, sala));
	}

	/** Eventos de la sala viva con ese código, en orden: {@code tipo:origen}. */
	private List<String> eventos(String codigo) {
		return jdbc.queryForList("SELECT tipo || ':' || origen FROM eventos WHERE sala_id = ? ORDER BY id",
				String.class, salaViva(codigo));
	}

}
