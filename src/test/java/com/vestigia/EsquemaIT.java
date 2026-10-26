package com.vestigia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Comprueba V1__esquema.sql contra un Postgres real. Cada prueba usa su propio código de pueblo. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
class EsquemaIT {

	private static final String HASH = "a".repeat(64);

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void flywayAplicaV1YCreaLasCincoTablas() {
		List<String> tablas = jdbc.queryForList("""
				SELECT table_name FROM information_schema.tables
				WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
				""", String.class);
		Boolean v1Aplicada = jdbc.queryForObject(
				"SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);

		assertThat(tablas).containsExactlyInAnyOrder("codigos_pueblo", "salas", "puzzles_estado", "descubrimientos",
				"eventos");
		assertThat(v1Aplicada).isTrue();
	}

	@Test
	void indiceEventosPorSalaYTiempoExiste() {
		Integer indices = jdbc.queryForObject(
				"SELECT count(*) FROM pg_indexes WHERE tablename = 'eventos' AND indexname = 'eventos_sala_tiempo'",
				Integer.class);

		assertThat(indices).isEqualTo(1);
	}

	@Test
	void noPuedeHaberDosSalasVivasConElMismoCodigo() {
		insertarPueblo("PRUEBAVIVA");
		insertarSala("PRUEBAVIVA", "esperando_app");

		assertThatThrownBy(() -> insertarSala("PRUEBAVIVA", "activa")).isInstanceOf(DuplicateKeyException.class);
		assertThatThrownBy(() -> insertarSala("PRUEBAVIVA", "esperando_app"))
				.isInstanceOf(DuplicateKeyException.class);
		assertThat(salasVivas("PRUEBAVIVA")).isEqualTo(1);
	}

	@Test
	void salasTerminadasNoBloqueanUnaNuevaSalaViva() {
		insertarPueblo("PRUEBAHISTORIAL");
		insertarSala("PRUEBAHISTORIAL", "finalizada");
		insertarSala("PRUEBAHISTORIAL", "expirada");
		insertarSala("PRUEBAHISTORIAL", "finalizada");

		insertarSala("PRUEBAHISTORIAL", "esperando_app");

		assertThat(salasVivas("PRUEBAHISTORIAL")).isEqualTo(1);
	}

	@Test
	void alFinalizarUnaSalaElCodigoSePuedeReclamarDeNuevo() {
		insertarPueblo("PRUEBALIBERA");
		UUID primera = insertarSala("PRUEBALIBERA", "activa");

		jdbc.update("UPDATE salas SET estado = 'finalizada' WHERE id = ?", primera);
		insertarSala("PRUEBALIBERA", "esperando_app");

		assertThat(salasVivas("PRUEBALIBERA")).isEqualTo(1);
	}

	@Test
	void carreraPorElMismoPuebloDejaUnaSolaSalaViva() throws Exception {
		insertarPueblo("PRUEBACARRERA");
		int hilos = 8;
		CountDownLatch salida = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(hilos);
		List<Future<Boolean>> resultados = new ArrayList<>();
		Callable<Boolean> reclamar = () -> {
			salida.await();
			try {
				insertarSala("PRUEBACARRERA", "esperando_app");
				return true;
			}
			catch (DuplicateKeyException ex) {
				return false;
			}
		};
		try {
			for (int i = 0; i < hilos; i++) {
				resultados.add(executor.submit(reclamar));
			}
			salida.countDown();
			int exitos = 0;
			for (Future<Boolean> resultado : resultados) {
				if (resultado.get()) {
					exitos++;
				}
			}

			assertThat(exitos).isEqualTo(1);
			assertThat(salasVivas("PRUEBACARRERA")).isEqualTo(1);
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void catalogoRechazaCodigosConMinusculasAcentosOEnie() {
		for (String codigo : List.of("Oaxaca", "ALAMOS2", "ÁLAMOS", "PEÑA", "SAN JUAN", "")) {
			assertThatThrownBy(() -> insertarPueblo(codigo)).as(codigo)
					.isInstanceOf(DataIntegrityViolationException.class);
		}
	}

	@Test
	void salasRechazaEstadoInvalido() {
		insertarPueblo("PRUEBAESTADO");

		assertThatThrownBy(() -> insertarSala("PRUEBAESTADO", "pausada"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void salasRechazaCodigoFueraDelCatalogo() {
		assertThatThrownBy(() -> insertarSala("NOEXISTE", "esperando_app"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private void insertarPueblo(String codigo) {
		jdbc.update("INSERT INTO codigos_pueblo (codigo) VALUES (?)", codigo);
	}

	private UUID insertarSala(String codigo, String estado) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO salas (id, codigo, estado, token_juego_hash, expira_en)
				VALUES (?, ?, ?, ?, now() + interval '2 hours')
				""", id, codigo, estado, HASH);
		return id;
	}

	private int salasVivas(String codigo) {
		return jdbc.queryForObject(
				"SELECT count(*) FROM salas WHERE codigo = ? AND estado IN ('esperando_app', 'activa')", Integer.class,
				codigo);
	}

}
