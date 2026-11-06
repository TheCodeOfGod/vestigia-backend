package com.vestigia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Un fallo a mitad de la cadena: con un límite de 10 bytes en los datos de un descubrimiento, el primer descubrimiento
 * del script ({@code {"origen_prueba":"smoke-test"}}) responde 400 cuando la sala ya existe. Tiene su propio contexto y
 * su propia base, porque el script elige el pueblo al azar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "vestigia.descubrimientos.datos-max-bytes=10")
@Import(PostgresTestConfig.class)
class SmokeTestFalloIT {

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@DisplayName("smoke-test.sh sin pueblo elige uno libre; si un paso falla a mitad, sale con 1, dice cuál y cierra la sala que creó")
	void falloAMitadCierraLaSala() throws Exception {
		SmokeTestIT.Corrida corrida = SmokeTestIT.correr("http://localhost:" + port);

		assertThat(corrida.salida()).as(corrida.texto()).isEqualTo(1);
		assertThat(corrida.texto()).contains("101 libres; se usa",
				"descubrimientos pescador_dialogo_completo (juego)", "se esperaba 201 y llegó 400",
				"se cerró (DELETE 204)", "RESULTADO: ❌ falló en el paso 7");
		assertThat(corrida.texto()).doesNotContainPattern(SmokeTestIT.PATRON_TOKEN);
		Map<String, Object> sala = jdbc.queryForMap("SELECT codigo, estado FROM salas");
		assertThat(sala).containsEntry("estado", "finalizada");
		assertThat(corrida.texto()).contains("la sala " + sala.get("codigo") + " se cerró");
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM salas WHERE estado IN ('esperando_app', 'activa')", Integer.class)).isZero();
	}

}
