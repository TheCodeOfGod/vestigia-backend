package com.vestigia.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.vestigia.PostgresTestConfig;
import com.vestigia.model.DescubrimientoModel;
import com.vestigia.model.PuzzleEstadoModel;
import com.vestigia.model.SalaModel;

/** Ida y vuelta de las entidades contra Postgres real: enums, CHAR(64), TIMESTAMPTZ y JSONB. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
class ModeloIT {

	@Autowired
	private SalaRepository salas;

	@Autowired
	private PuzzleEstadoRepository puzzles;

	@Autowired
	private DescubrimientoRepository descubrimientos;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void guardaYLeeSalaPuzzlesYDescubrimientosConJsonb() {
		jdbc.update("INSERT INTO codigos_pueblo (codigo) VALUES ('PRUEBAMODELO')");
		Instant ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
		SalaModel sala = new SalaModel();
		sala.setCodigo("PRUEBAMODELO");
		sala.setEstado(SalaModel.Estado.esperando_app);
		sala.setEscenaActual("introduccion");
		sala.setTokenJuegoHash("b".repeat(64));
		sala.setCreadaEn(ahora);
		sala.setUltimaActividad(ahora);
		sala.setExpiraEn(ahora.plusSeconds(60));
		sala = salas.save(sala);
		puzzles.save(new PuzzleEstadoModel(sala.getId(), PuzzleEstadoModel.Puzzle.lookout,
				PuzzleEstadoModel.Estado.bloqueado, ahora));
		DescubrimientoModel descubrimiento = new DescubrimientoModel();
		descubrimiento.setSalaId(sala.getId());
		descubrimiento.setClave("coordenada_a");
		descubrimiento.setDatos(Map.of("tipo", "azimut", "valor", 60));
		descubrimiento.setOrigen(DescubrimientoModel.Origen.servidor);
		descubrimiento.setCreadoEn(ahora);
		descubrimientos.save(descubrimiento);

		SalaModel leida = salas.findById(sala.getId()).orElseThrow();
		assertThat(leida.getEstado()).isEqualTo(SalaModel.Estado.esperando_app);
		assertThat(leida.getTokenJuegoHash()).isEqualTo("b".repeat(64));
		assertThat(leida.getTokenAppHash()).isNull();
		assertThat(leida.getExpiraEn()).isEqualTo(ahora.plusSeconds(60));
		assertThat(puzzles.findByClaveSalaId(sala.getId())).singleElement().satisfies(p -> {
			assertThat(p.getPuzzle()).isEqualTo(PuzzleEstadoModel.Puzzle.lookout);
			assertThat(p.getEstado()).isEqualTo(PuzzleEstadoModel.Estado.bloqueado);
			assertThat(p.getDatos()).isEmpty();
		});
		assertThat(descubrimientos.findBySalaIdOrderByCreadoEnAscIdAsc(sala.getId())).singleElement()
				.satisfies(d -> assertThat(d.getDatos()).containsEntry("tipo", "azimut").containsEntry("valor", 60));
		assertThat(jdbc.queryForObject("SELECT datos ->> 'tipo' FROM descubrimientos WHERE sala_id = ?", String.class,
				sala.getId())).isEqualTo("azimut");
	}

}
