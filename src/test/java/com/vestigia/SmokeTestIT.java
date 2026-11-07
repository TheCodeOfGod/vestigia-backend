package com.vestigia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Corre {@code scripts/smoke-test.sh} contra el backend levantado en un puerto aleatorio, igual que Raúl lo correrá
 * contra la URL de Railway. Necesita bash, curl y jq. Usa URES, un pueblo que ninguna otra *IT reclama.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
class SmokeTestIT {

	/** Un token es de 43 caracteres base64url; ninguna otra palabra de la salida del script es tan larga. */
	static final String PATRON_TOKEN = "[A-Za-z0-9_-]{43}";

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@DisplayName("smoke-test.sh recorre la partida completa contra el backend real, sale con 0, cierra la sala y no imprime tokens")
	void cadenaCompleta() throws Exception {
		Corrida corrida = correr("http://localhost:" + port + "/", "ures");

		assertThat(corrida.salida()).as(corrida.texto()).isZero();
		assertThat(corrida.texto()).contains("sala URES").doesNotContain("elige un pueblo libre");
		assertThat(corrida.texto()).contains("radio = resuelto", "photo = resuelto", "lookout = resuelto",
				"observatory = resuelto", "AR 137, DEC +042", "401: el token ya no sirve", "URES libre",
				"RESULTADO: ✅");
		assertThat(corrida.texto()).doesNotContainPattern(PATRON_TOKEN);
		assertThat(jdbc.queryForList("SELECT estado FROM salas WHERE codigo = 'URES'", String.class))
				.containsExactly("finalizada");
		assertThat(jdbc.queryForList("""
				SELECT d.clave FROM descubrimientos d JOIN salas s ON s.id = d.sala_id
				WHERE s.codigo = 'URES' AND d.origen = 'servidor' ORDER BY d.id""", String.class))
				.containsExactly("coordenada_a", "coordenada_b", "observatorio_visible", "magnetar_localizado");
	}

	@Test
	@DisplayName("smoke-test.sh sale con 1 si el backend no responde y con 2 si falta la URL")
	void salidaDistintaDeCeroSinBackendOSinUrl() throws Exception {
		Corrida sinBackend = correr("http://localhost:1");
		Corrida sinUrl = correr();

		assertThat(sinBackend.salida()).as(sinBackend.texto()).isEqualTo(1);
		assertThat(sinBackend.texto()).contains("no hubo respuesta", "RESULTADO: ❌ falló en el paso 1");
		assertThat(sinUrl.salida()).as(sinUrl.texto()).isEqualTo(2);
		assertThat(sinUrl.texto()).contains("Uso:");
	}

	record Corrida(int salida, String texto) {
	}

	/** Corre el script desde la raíz del repo, con la salida de error mezclada en la estándar. */
	static Corrida correr(String... argumentos) throws IOException, InterruptedException {
		List<String> comando = new ArrayList<>(List.of("bash", "scripts/smoke-test.sh"));
		comando.addAll(List.of(argumentos));
		Path salida = Files.createTempFile("smoke-test", ".log");
		try {
			Process proceso = new ProcessBuilder(comando).redirectErrorStream(true).redirectOutput(salida.toFile())
					.start();
			if (!proceso.waitFor(120, TimeUnit.SECONDS)) {
				proceso.destroyForcibly();
				throw new AssertionError("smoke-test.sh no terminó en 120 s:\n" + Files.readString(salida));
			}
			return new Corrida(proceso.exitValue(), Files.readString(salida, StandardCharsets.UTF_8));
		}
		finally {
			Files.deleteIfExists(salida);
		}
	}

}
