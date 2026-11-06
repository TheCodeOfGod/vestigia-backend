package com.vestigia;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.vestigia.model.PuzzleEstadoModel;
import com.vestigia.service.ActividadService;
import com.vestigia.service.HabilitacionPuzzlesService;
import com.vestigia.service.SalaService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escrituras de la sala por HTTP contra Postgres real: eventos, descubrimientos, escena y cierre. Cada prueba usa un
 * pueblo distinto del seed, que ninguna otra *IT reclama.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
class EscriturasSalaIT {

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JsonMapper json;

	@Autowired
	private RelojDePrueba reloj;

	@Autowired
	private TransactionTemplate transaccion;

	@Autowired
	private ActividadService actividad;

	@Autowired
	private HabilitacionPuzzlesService habilitacion;

	@Autowired
	private SalaService salaService;

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
	@DisplayName("Crear y unirse guardan los eventos sala.creada (origen juego) y app.unida (origen app)")
	void crearYUnirseGuardanSuEvento() throws Exception {
		api.tokenJuego("MALINALCO");
		api.tokenApp("MALINALCO");

		assertThat(eventos("MALINALCO")).containsExactly("sala.creada:juego:{\"codigo\": \"MALINALCO\"}",
				"app.unida:app:{}");
	}

	@Test
	@DisplayName("Un descubrimiento nuevo responde 201, aparece en GET estado y guarda descubrimiento.registrado con el origen del rol")
	void descubrimientoNuevoResponde201() throws Exception {
		String juego = api.tokenJuego("MANI");
		String app = api.tokenApp("MANI");

		HttpResponse<String> respuesta = descubrir("MANI", "pieza_1", "{\"nota\":\"bajo el muelle\"}", app);

		assertThat(respuesta.statusCode()).isEqualTo(201);
		JsonNode cuerpo = api.cuerpo(respuesta);
		assertThat(cuerpo.get("clave").asString()).isEqualTo("pieza_1");
		assertThat(cuerpo.get("origen").asString()).isEqualTo("app");
		assertThat(cuerpo.get("datos").get("nota").asString()).isEqualTo("bajo el muelle");
		JsonNode estado = api.cuerpo(api.enviar("GET", "/api/v1/salas/MANI/estado", null, juego));
		assertThat(estado.get("descubrimientos").valueStream().map(d -> d.get("clave").asString()))
				.containsExactly("pieza_1");
		assertThat(eventos("MANI")).last()
				.isEqualTo("descubrimiento.registrado:app:{\"clave\": \"pieza_1\", \"datos\": {\"nota\": \"bajo el muelle\"}}");
	}

	@Test
	@DisplayName("Diez peticiones simultáneas con la misma clave dan un 201 y nueve 200, una sola fila y un solo evento")
	void idempotenciaConcurrente() throws Exception {
		String juego = api.tokenJuego("MAPIMI");

		List<Integer> codigos = ClienteApi.enParalelo(10,
				() -> descubrir("MAPIMI", "pieza_1", null, juego).statusCode());

		assertThat(codigos).filteredOn(c -> c == 201).hasSize(1);
		assertThat(codigos).filteredOn(c -> c == 200).hasSize(9);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM descubrimientos WHERE sala_id = ? AND clave = 'pieza_1'",
				Integer.class, salaViva("MAPIMI"))).isEqualTo(1);
		assertThat(eventos("MAPIMI")).filteredOn(e -> e.startsWith("descubrimiento.registrado")).hasSize(1);
	}

	@Test
	@DisplayName("Las tres piezas enviadas a la vez, repetidas, habilitan photo una sola vez")
	void piezasSimultaneasHabilitanPhoto() throws Exception {
		String juego = api.tokenJuego("MASCOTA");
		AtomicInteger turno = new AtomicInteger();

		List<Integer> codigos = ClienteApi.enParalelo(9,
				() -> descubrir("MASCOTA", "pieza_" + (turno.getAndIncrement() % 3 + 1), null, juego).statusCode());

		assertThat(codigos).filteredOn(c -> c == 201).hasSize(3);
		assertThat(codigos).filteredOn(c -> c == 200).hasSize(6);
		assertThat(estadoPuzzle("MASCOTA", "photo")).isEqualTo("disponible");
		assertThat(eventos("MASCOTA")).filteredOn(e -> e.startsWith("puzzle.habilitado"))
				.containsExactly("puzzle.habilitado:servidor:{\"puzzle\": \"photo\"}");
	}

	@Test
	@DisplayName("Las cuatro claves del servidor dan 403 al juego y a la app y no se guardan")
	void clavesDelServidorDan403() throws Exception {
		String juego = api.tokenJuego("MAZAMITLA");
		String app = api.tokenApp("MAZAMITLA");

		for (String clave : List.of("coordenada_a", "coordenada_b", "observatorio_visible", "magnetar_localizado")) {
			for (String token : List.of(juego, app)) {
				HttpResponse<String> respuesta = descubrir("MAZAMITLA", clave, "{\"valor\":\"137\"}", token);
				assertThat(respuesta.statusCode()).as(clave).isEqualTo(403);
				assertThat(api.cuerpo(respuesta).get("message").asString()).contains(clave);
			}
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM descubrimientos WHERE sala_id = ?", Integer.class,
				salaViva("MAZAMITLA"))).isZero();
	}

	@Test
	@DisplayName("pescador_dialogo_completo habilita radio y GET estado lo muestra disponible")
	void pescadorHabilitaRadio() throws Exception {
		String juego = api.tokenJuego("MAZUNTE");

		assertThat(descubrir("MAZUNTE", "pescador_dialogo_completo", null, juego).statusCode()).isEqualTo(201);

		JsonNode estado = api.cuerpo(api.enviar("GET", "/api/v1/salas/MAZUNTE/estado", null, juego));
		assertThat(estado.get("puzzles").valueStream().map(p -> p.get("puzzle").asString() + ":" + p.get("estado").asString()))
				.containsExactly("radio:disponible", "photo:bloqueado", "lookout:bloqueado", "observatory:bloqueado");
	}

	@Test
	@DisplayName("Repetir una clave con otros datos responde 200 con los datos originales y no guarda otro evento")
	void repetidoConservaLosDatosOriginales() throws Exception {
		String juego = api.tokenJuego("METEPEC");
		descubrir("METEPEC", "pieza_2", "{\"v\":1}", juego);
		int eventosAntes = eventos("METEPEC").size();

		HttpResponse<String> repetido = descubrir("METEPEC", "pieza_2", "{\"v\":2}", juego);

		assertThat(repetido.statusCode()).isEqualTo(200);
		assertThat(api.cuerpo(repetido).get("datos").get("v").asInt()).isEqualTo(1);
		assertThat(eventos("METEPEC")).hasSize(eventosAntes);
	}

	@Test
	@DisplayName("Un descubrimiento, nuevo o repetido, renueva ultima_actividad y expira_en con la duración configurada")
	void descubrimientoRenuevaLaExpiracion() throws Exception {
		String juego = api.tokenJuego("METZTITLAN");

		reloj.avanzar(Duration.ofHours(1));
		Instant antes = reloj.instant();
		descubrir("METZTITLAN", "pieza_1", null, juego);
		assertThat(expiraEn("METZTITLAN")).isBetween(antes.plus(Duration.ofHours(2)),
				antes.plus(Duration.ofHours(2)).plusSeconds(5));

		reloj.avanzar(Duration.ofHours(1));
		antes = reloj.instant();
		assertThat(descubrir("METZTITLAN", "pieza_1", null, juego).statusCode()).isEqualTo(200);
		assertThat(expiraEn("METZTITLAN")).isBetween(antes.plus(Duration.ofHours(2)),
				antes.plus(Duration.ofHours(2)).plusSeconds(5));
		assertThat(jdbc.queryForObject("SELECT ultima_actividad FROM salas WHERE id = ?", Instant.class,
				salaViva("METZTITLAN"))).isBetween(antes, antes.plusSeconds(5));
	}

	@Test
	@DisplayName("lookout y observatory se habilitan por servicio cuando se resuelve el puzzle previo, aunque la bandera haya llegado antes")
	void lookoutYObservatoryConBanderaPrevia() throws Exception {
		String juego = api.tokenJuego("MEXCALTITAN");
		UUID sala = salaViva("MEXCALTITAN");
		descubrir("MEXCALTITAN", "lookout_enabled", null, juego);
		descubrir("MEXCALTITAN", "observatory_enabled", null, juego);
		assertThat(estadoPuzzle("MEXCALTITAN", "lookout")).isEqualTo("bloqueado");

		resolver(sala, "photo");
		assertThat(recalcular(sala)).containsExactly(PuzzleEstadoModel.Puzzle.lookout);

		jdbc.update("""
				INSERT INTO descubrimientos (sala_id, clave, datos, origen) VALUES
				(?, 'coordenada_a', '{}', 'servidor'), (?, 'coordenada_b', '{}', 'servidor')
				""", sala, sala);
		assertThat(recalcular(sala)).isEmpty();
		resolver(sala, "lookout");
		assertThat(recalcular(sala)).containsExactly(PuzzleEstadoModel.Puzzle.observatory);
		assertThat(estadoPuzzle("MEXCALTITAN", "observatory")).isEqualTo("disponible");
	}

	@Test
	@DisplayName("lookout_enabled que llega después de photo resuelto habilita lookout en la misma petición")
	void banderaDespuesDelRequisitoHabilitaAlInstante() throws Exception {
		String juego = api.tokenJuego("MIER");
		resolver(salaViva("MIER"), "photo");

		descubrir("MIER", "lookout_enabled", null, juego);

		assertThat(estadoPuzzle("MIER", "lookout")).isEqualTo("disponible");
	}

	@Test
	@DisplayName("El juego cambia la escena: 200, GET estado la muestra, guarda escena.cambiada y renueva expira_en")
	void juegoCambiaLaEscena() throws Exception {
		String juego = api.tokenJuego("MOCORITO");
		reloj.avanzar(Duration.ofHours(1));
		Instant antes = reloj.instant();

		HttpResponse<String> respuesta = escena("MOCORITO", "\"faro\"", juego);

		assertThat(respuesta.statusCode()).isEqualTo(200);
		assertThat(api.cuerpo(respuesta).get("escena_actual").asString()).isEqualTo("faro");
		assertThat(api.cuerpo(api.enviar("GET", "/api/v1/salas/MOCORITO/estado", null, juego)).get("escena_actual")
				.asString()).isEqualTo("faro");
		assertThat(eventos("MOCORITO")).last()
				.isEqualTo("escena.cambiada:juego:{\"escena\": \"faro\", \"anterior\": \"introduccion\"}");
		assertThat(expiraEn("MOCORITO")).isBetween(antes.plus(Duration.ofHours(2)),
				antes.plus(Duration.ofHours(2)).plusSeconds(5));
	}

	@Test
	@DisplayName("La app recibe 403 al cambiar la escena, con cuerpo válido, inválido o vacío, y la escena no cambia")
	void appNoCambiaLaEscena() throws Exception {
		api.tokenJuego("MOTUL");
		String app = api.tokenApp("MOTUL");

		assertThat(escena("MOTUL", "\"faro\"", app).statusCode()).isEqualTo(403);
		assertThat(escena("MOTUL", "\"bosque\"", app).statusCode()).isEqualTo(403);
		assertThat(api.enviar("PUT", "/api/v1/salas/MOTUL/escena", "{}", app).statusCode()).isEqualTo(403);
		assertThat(jdbc.queryForObject("SELECT escena_actual FROM salas WHERE id = ?", String.class,
				salaViva("MOTUL"))).isEqualTo("introduccion");
	}

	@Test
	@DisplayName("Una escena inválida, vacía, de otro tipo o ausente da 400 y no cambia nada")
	void escenaInvalidaDa400() throws Exception {
		String juego = api.tokenJuego("ORIZABA");

		for (String valor : List.of("\"bosque\"", "\"FARO\"", "\"\"", "5", "null", "[\"faro\"]")) {
			assertThat(escena("ORIZABA", valor, juego).statusCode()).as(valor).isEqualTo(400);
		}
		assertThat(api.enviar("PUT", "/api/v1/salas/ORIZABA/escena", "{}", juego).statusCode()).isEqualTo(400);
		assertThat(jdbc.queryForObject("SELECT escena_actual FROM salas WHERE id = ?", String.class,
				salaViva("ORIZABA"))).isEqualTo("introduccion");
		assertThat(eventos("ORIZABA")).containsExactly("sala.creada:juego:{\"codigo\": \"ORIZABA\"}");
	}

	@Test
	@DisplayName("El juego cierra la sala: 204, queda finalizada sin renovar expira_en, guarda sala.finalizada y los dos tokens dan 401")
	void cerrarInvalidaLosTokens() throws Exception {
		String juego = api.tokenJuego("OTUMBA");
		String app = api.tokenApp("OTUMBA");
		UUID sala = salaViva("OTUMBA");
		Instant expiraEn = expiraEn("OTUMBA");
		reloj.avanzar(Duration.ofMinutes(30));

		assertThat(api.enviar("DELETE", "/api/v1/salas/OTUMBA", null, juego).statusCode()).isEqualTo(204);
		assertThat(jdbc.queryForObject("SELECT expira_en FROM salas WHERE id = ?", Instant.class, sala))
				.as("el cierre no renueva expira_en").isEqualTo(expiraEn);

		assertThat(jdbc.queryForObject("SELECT estado FROM salas WHERE id = ?", String.class, sala))
				.isEqualTo("finalizada");
		assertThat(jdbc.queryForObject("SELECT tipo || ':' || origen FROM eventos WHERE sala_id = ? ORDER BY id DESC LIMIT 1",
				String.class, sala)).isEqualTo("sala.finalizada:juego");
		for (String token : List.of(juego, app)) {
			assertThat(api.enviar("GET", "/api/v1/salas/OTUMBA/estado", null, token).statusCode()).isEqualTo(401);
			assertThat(descubrir("OTUMBA", "pieza_1", null, token).statusCode()).isEqualTo(401);
			assertThat(escena("OTUMBA", "\"faro\"", token).statusCode()).isEqualTo(401);
			assertThat(api.enviar("DELETE", "/api/v1/salas/OTUMBA", null, token).statusCode()).isEqualTo(401);
		}
	}

	@Test
	@DisplayName("Cerrar la sala libera el código: se puede crear otra sala con el mismo pueblo y la cerrada queda como historial")
	void cerrarLiberaElCodigo() throws Exception {
		String juego = api.tokenJuego("PAHUATLAN");
		assertThat(api.enviar("DELETE", "/api/v1/salas/pahuatlan", null, juego).statusCode()).isEqualTo(204);

		JsonNode libres = api.cuerpo(api.enviar("GET", "/api/v1/pueblos?disponibles=true", null, null));
		assertThat(libres.valueStream().map(p -> p.get("codigo").asString())).contains("PAHUATLAN");
		assertThat(api.crear("PAHUATLAN").statusCode()).isEqualTo(201);
		assertThat(jdbc.queryForList("SELECT estado FROM salas WHERE codigo = 'PAHUATLAN' ORDER BY creada_en",
				String.class)).containsExactly("finalizada", "esperando_app");
	}

	@Test
	@DisplayName("La app recibe 403 al cerrar la sala y la sala sigue activa")
	void appNoCierraLaSala() throws Exception {
		String juego = api.tokenJuego("PALENQUE");
		String app = api.tokenApp("PALENQUE");

		assertThat(api.enviar("DELETE", "/api/v1/salas/PALENQUE", null, app).statusCode()).isEqualTo(403);
		assertThat(api.enviar("GET", "/api/v1/salas/PALENQUE/estado", null, juego).statusCode()).isEqualTo(200);
		assertThat(jdbc.queryForObject("SELECT estado FROM salas WHERE codigo = 'PALENQUE'", String.class))
				.isEqualTo("activa");
	}

	@Test
	@DisplayName("Una sala en esperando_app también se puede cerrar")
	void cerrarSalaSinApp() throws Exception {
		String juego = api.tokenJuego("PALIZADA");

		assertThat(api.enviar("DELETE", "/api/v1/salas/PALIZADA", null, juego).statusCode()).isEqualTo(204);
		assertThat(api.enviar("POST", "/api/v1/salas/PALIZADA/unirse", "", null).statusCode()).isEqualTo(404);
	}

	@Test
	@DisplayName("Cada escritura deja su evento en orden: sala.creada, app.unida, descubrimiento.registrado, puzzle.habilitado, escena.cambiada y sala.finalizada")
	void cadaEscrituraGuardaSuEvento() throws Exception {
		String juego = api.tokenJuego("PAPANTLA");
		api.tokenApp("PAPANTLA");
		UUID sala = salaViva("PAPANTLA");
		descubrir("PAPANTLA", "pescador_dialogo_completo", null, juego);
		escena("PAPANTLA", "\"pueblo\"", juego);
		api.enviar("DELETE", "/api/v1/salas/PAPANTLA", null, juego);

		assertThat(jdbc.queryForList("SELECT tipo || ':' || origen FROM eventos WHERE sala_id = ? ORDER BY id",
				String.class, sala)).containsExactly("sala.creada:juego", "app.unida:app",
				"descubrimiento.registrado:juego", "puzzle.habilitado:servidor", "escena.cambiada:juego",
				"sala.finalizada:juego");
	}

	private HttpResponse<String> escena(String codigo, String valorJson, String token) throws Exception {
		return api.enviar("PUT", "/api/v1/salas/" + codigo + "/escena", "{\"escena\":" + valorJson + "}", token);
	}

	@Test
	@DisplayName("Un número o booleano JSON donde va texto da 400 sin convertirse: clave, escena y código de pueblo")
	void tiposEstrictosEnLaEntrada() throws Exception {
		String juego = api.tokenJuego("PATZCUARO");

		for (String valor : List.of("12345", "true", "1.5")) {
			HttpResponse<String> respuesta = api.enviar("POST", "/api/v1/salas/PATZCUARO/descubrimientos",
					"{\"clave\":" + valor + "}", juego);
			assertThat(respuesta.statusCode()).as("clave " + valor).isEqualTo(400);
			assertThat(api.cuerpo(respuesta).get("message").asString()).isEqualTo("JSON malformado o ilegible");
			assertThat(escena("PATZCUARO", valor, juego).statusCode()).as("escena " + valor).isEqualTo(400);
			assertThat(api.enviar("POST", "/api/v1/salas", "{\"codigo\":" + valor + "}", null).statusCode())
					.as("codigo " + valor).isEqualTo(400);
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM descubrimientos WHERE sala_id = ?", Integer.class,
				salaViva("PATZCUARO"))).isZero();
		assertThat(descubrir("PATZCUARO", "12345", null, juego).statusCode()).isEqualTo(201);
	}

	@Test
	@DisplayName("Una sala con expira_en vencido que el job aún no marcó responde 401 a toda escritura y no revive")
	void salaVencidaNoRevive() throws Exception {
		String juego = api.tokenJuego("PINOS");
		UUID sala = salaViva("PINOS");
		Instant expiraEn = expiraEn("PINOS");

		reloj.avanzar(Duration.ofHours(2).plusSeconds(1));

		assertThat(descubrir("PINOS", "pieza_1", null, juego).statusCode()).isEqualTo(401);
		assertThat(escena("PINOS", "\"faro\"", juego).statusCode()).isEqualTo(401);
		assertThat(api.enviar("DELETE", "/api/v1/salas/PINOS", null, juego).statusCode()).isEqualTo(401);
		assertThat(jdbc.queryForObject("SELECT expira_en FROM salas WHERE id = ?", Instant.class, sala))
				.isEqualTo(expiraEn);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM descubrimientos WHERE sala_id = ?", Integer.class, sala))
				.isZero();
		salaService.expirarVencidas();
		assertThat(jdbc.queryForObject("SELECT estado FROM salas WHERE id = ?", String.class, sala))
				.isEqualTo("expirada");
	}

	@Test
	@DisplayName("Unos datos que pasan de 4096 bytes en JSON dan 400 y no se guardan; justo en el límite responden 201")
	void datosConTamanoAcotado() throws Exception {
		String juego = api.tokenJuego("SALVATIERRA");
		String relleno = "x".repeat(4096 - "{\"nota\":\"\"}".length());

		HttpResponse<String> grande = descubrir("SALVATIERRA", "pieza_1", "{\"nota\":\"" + relleno + "x\"}", juego);
		assertThat(grande.statusCode()).isEqualTo(400);
		assertThat(api.cuerpo(grande).get("details").get(0).get("field").asString()).isEqualTo("datos");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM descubrimientos WHERE sala_id = ?", Integer.class,
				salaViva("SALVATIERRA"))).isZero();

		assertThat(descubrir("SALVATIERRA", "pieza_1", "{\"nota\":\"" + relleno + "\"}", juego).statusCode())
				.isEqualTo(201);
	}

	private HttpResponse<String> descubrir(String codigo, String clave, String datos, String token) throws Exception {
		String cuerpo = "{\"clave\":\"" + clave + "\"" + (datos == null ? "" : ",\"datos\":" + datos) + "}";
		return api.enviar("POST", "/api/v1/salas/" + codigo + "/descubrimientos", cuerpo, token);
	}

	private UUID salaViva(String codigo) {
		return jdbc.queryForObject("SELECT id FROM salas WHERE codigo = ? AND estado IN ('esperando_app', 'activa')",
				UUID.class, codigo);
	}

	private String estadoPuzzle(String codigo, String puzzle) {
		return jdbc.queryForObject("SELECT estado FROM puzzles_estado WHERE sala_id = ? AND puzzle = ?", String.class,
				salaViva(codigo), puzzle);
	}

	private Instant expiraEn(String codigo) {
		return jdbc.queryForObject("SELECT expira_en FROM salas WHERE id = ?", Instant.class, salaViva(codigo));
	}

	/** Lo que hará PUT puzzles en el Paso 3b, escrito directo en la base. */
	private void resolver(UUID sala, String puzzle) {
		jdbc.update("UPDATE puzzles_estado SET estado = 'resuelto' WHERE sala_id = ? AND puzzle = ?", sala, puzzle);
	}

	private List<PuzzleEstadoModel.Puzzle> recalcular(UUID sala) {
		return transaccion.execute(t -> habilitacion.recalcular(actividad.bloquear(sala)));
	}

	/** Eventos de la sala viva con ese código, en orden: {@code tipo:origen:payload}. */
	private List<String> eventos(String codigo) {
		return jdbc.queryForList("""
				SELECT e.tipo || ':' || e.origen || ':' || e.payload::text FROM eventos e
				JOIN salas s ON s.id = e.sala_id WHERE s.codigo = ? ORDER BY e.id
				""", String.class, codigo);
	}

}
