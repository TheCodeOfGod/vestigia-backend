package com.vestigia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Comprueba V2__seed_pueblos.sql. Otras *IT comparten la base y agregan pueblos de prueba, así que se compara contra
 * los códigos que declara la migración y no contra el total de la tabla.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
class CatalogoPueblosIT {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void seedDeclara101CodigosValidosYSinDuplicados() throws IOException {
		List<String> codigos = codigosDelSeed();

		assertThat(codigos).hasSize(101).doesNotHaveDuplicates().allMatch(c -> c.matches("^[A-Z]+$"));
	}

	@Test
	void flywayAplicaV2YLosCodigosEstanEnLaTabla() throws IOException {
		List<String> codigos = codigosDelSeed();
		Boolean v2Aplicada = jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version = '2'",
				Boolean.class);
		Integer enTabla = jdbc.queryForObject("SELECT count(*) FROM codigos_pueblo WHERE codigo = ANY (?)",
				Integer.class, (Object) codigos.toArray(String[]::new));

		assertThat(v2Aplicada).isTrue();
		assertThat(enTabla).isEqualTo(101);
	}

	private List<String> codigosDelSeed() throws IOException {
		String sql = new ClassPathResource("db/migration/V2__seed_pueblos.sql").getContentAsString(
				StandardCharsets.UTF_8);
		Matcher matcher = Pattern.compile("\\('([^']*)'\\)").matcher(sql);
		return matcher.results().map(m -> m.group(1)).toList();
	}

}
