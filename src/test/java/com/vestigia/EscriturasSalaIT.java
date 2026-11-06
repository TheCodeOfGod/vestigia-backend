package com.vestigia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

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

	/** Eventos de la sala viva con ese código, en orden: {@code tipo:origen:payload}. */
	private List<String> eventos(String codigo) {
		return jdbc.queryForList("""
				SELECT e.tipo || ':' || e.origen || ':' || e.payload::text FROM eventos e
				JOIN salas s ON s.id = e.sala_id WHERE s.codigo = ? ORDER BY e.id
				""", String.class, codigo);
	}

}
