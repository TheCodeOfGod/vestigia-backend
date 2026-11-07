package com.vestigia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * V3__ids_puzzles_en_ingles.sql contra un Postgres real. La migración se prueba en un esquema propio, con su propio
 * DataSource, para no tocar el esquema que usan las demás *IT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
class MigracionIdsPuzzlesIT {

	private static final String ESQUEMA = "migracion_ids_puzzles";

	private static final String HASH = "a".repeat(64);

	@Autowired
	private PostgreSQLContainer postgres;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void borrarEsquema() {
		jdbc.execute("DROP SCHEMA IF EXISTS " + ESQUEMA + " CASCADE");
	}

	@Test
	@DisplayName("V3 renombra a inglés los puzzles de una sala creada con V2, conserva su estado y sus datos, y ese esquema rechaza fotografia")
	void v3RenombraLosPuzzlesDeUnaSalaExistente() {
		DataSource dataSource = dataSourceDelEsquema();
		JdbcTemplate migrada = new JdbcTemplate(dataSource);
		assertThat(flyway(dataSource, "2").migrate().targetSchemaVersion).isEqualTo("2");
		UUID sala = insertarSala(migrada, "BACALAR");
		insertarPuzzle(migrada, sala, "radio", "resuelto", "{\"sintonizada\": true}");
		insertarPuzzle(migrada, sala, "fotografia", "en_curso", "{}");
		insertarPuzzle(migrada, sala, "mirador", "disponible", "{}");
		insertarPuzzle(migrada, sala, "observatorio", "bloqueado", "{}");

		assertThat(flyway(dataSource, "3").migrate().targetSchemaVersion).isEqualTo("3");

		assertThat(migrada.queryForList("""
				SELECT puzzle || ':' || estado || ':' || datos::text FROM puzzles_estado
				WHERE sala_id = ? ORDER BY puzzle
				""", String.class, sala)).containsExactly("lookout:disponible:{}", "observatory:bloqueado:{}",
				"photo:en_curso:{}", "radio:resuelto:{\"sintonizada\": true}");
		assertThat(migrada.queryForObject("SELECT count(*) FROM puzzles_estado", Integer.class)).isEqualTo(4);
		assertThatThrownBy(() -> insertarPuzzle(migrada, sala, "fotografia", "bloqueado", "{}"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("puzzles_estado_puzzle_check");
	}

	@Test
	@DisplayName("Tras V3, el CHECK de puzzles_estado rechaza fotografia, mirador y observatorio y acepta los ids nuevos")
	void elCheckRechazaLosIdsViejosYAceptaLosNuevos() {
		jdbc.update("INSERT INTO codigos_pueblo (codigo) VALUES ('PRUEBAIDSINGLES')");
		UUID sala = insertarSala(jdbc, "PRUEBAIDSINGLES");

		for (String viejo : List.of("fotografia", "mirador", "observatorio")) {
			assertThatThrownBy(() -> insertarPuzzle(jdbc, sala, viejo, "bloqueado", "{}")).as(viejo)
					.isInstanceOf(DataIntegrityViolationException.class)
					.hasMessageContaining("puzzles_estado_puzzle_check");
		}
		for (String nuevo : List.of("radio", "photo", "lookout", "observatory")) {
			insertarPuzzle(jdbc, sala, nuevo, "bloqueado", "{}");
		}
		assertThat(jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version = '3'", Boolean.class))
				.isTrue();
	}

	private DataSource dataSourceDelEsquema() {
		jdbc.execute("CREATE SCHEMA " + ESQUEMA);
		DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword());
		dataSource.setSchema(ESQUEMA);
		return dataSource;
	}

	private static Flyway flyway(DataSource dataSource, String version) {
		return Flyway.configure().dataSource(dataSource).schemas(ESQUEMA).target(version).load();
	}

	private static UUID insertarSala(JdbcTemplate jdbc, String codigo) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO salas (id, codigo, estado, token_juego_hash, expira_en)
				VALUES (?, ?, 'activa', ?, now() + interval '2 hours')
				""", id, codigo, HASH);
		return id;
	}

	private static void insertarPuzzle(JdbcTemplate jdbc, UUID sala, String puzzle, String estado, String datos) {
		jdbc.update("INSERT INTO puzzles_estado (sala_id, puzzle, estado, datos) VALUES (?, ?, ?, ?::jsonb)", sala,
				puzzle, estado, datos);
	}

}
