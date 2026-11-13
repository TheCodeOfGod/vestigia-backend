package com.vestigia;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.ArrayList;
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

import com.vestigia.dto.CrearUsuarioRequest;
import com.vestigia.model.UsuarioModel;
import com.vestigia.service.UsuarioService;
import com.vestigia.ws.ClienteWs;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Administración de salas por HTTP y WebSocket contra Postgres real. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
class AdminSalasIT {

	private static final String PUEBLO = "VALLADOLID";

	private static final String CORREO = "salas@vestigia.site";

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JsonMapper json;

	@Autowired
	private UsuarioService usuarios;

	private ClienteApi api;

	private String admin;

	@BeforeEach
	void preparar() throws Exception {
		api = new ClienteApi(port, json);
		jdbc.update("delete from usuarios");
		usuarios.crear(new CrearUsuarioRequest("Salas", CORREO, "contraseña-larga", UsuarioModel.Rol.admin));
		admin = api.cuerpo(api.enviar("POST", "/api/v1/auth/login",
				"{\"correo\":\"" + CORREO + "\",\"password\":\"contraseña-larga\"}", null)).get("token").asString();
	}

	@AfterEach
	void limpiar() {
		jdbc.update("delete from usuarios");
	}

	@Test
	@DisplayName("El listado y el detalle muestran la sala viva, con su bitácora y sin tokens")
	void listarYDetalle() throws Exception {
		String juego = api.tokenJuego(PUEBLO);
		try {
			api.tokenApp(PUEBLO);
			api.enviar("POST", "/api/v1/salas/" + PUEBLO + "/descubrimientos", "{\"clave\":\"pescador_dialogo_completo\"}",
					juego);

			HttpResponse<String> listado = api.enviar("GET", "/api/v1/admin/salas?estado=activa&tamano=100", null, admin);
			assertThat(listado.statusCode()).isEqualTo(200);
			JsonNode sala = buscar(api.cuerpo(listado).get("contenido"), PUEBLO);
			assertThat(sala.get("estado").asString()).isEqualTo("activa");
			assertThat(sala.get("app_unida").asBoolean()).isTrue();
			assertThat(sala.get("puzzles_total").asInt()).isEqualTo(4);
			assertThat(sala.get("puzzles_resueltos").asInt()).isZero();

			HttpResponse<String> detalle = api.enviar("GET", "/api/v1/admin/salas/" + sala.get("id").asString(), null,
					admin);
			assertThat(detalle.statusCode()).isEqualTo(200);
			assertThat(detalle.body()).doesNotContain("token");
			JsonNode cuerpo = api.cuerpo(detalle);
			List<String> tipos = new ArrayList<>();
			cuerpo.get("eventos").forEach(e -> tipos.add(e.get("tipo").asString()));
			assertThat(tipos).contains("sala.creada", "app.unida", "descubrimiento.registrado");
			assertThat(cuerpo.get("descubrimientos").size()).isEqualTo(1);
			assertThat(cuerpo.get("puzzles").size()).isEqualTo(4);
		}
		finally {
			api.enviar("DELETE", "/api/v1/salas/" + PUEBLO, null, juego);
		}
	}

	@Test
	@DisplayName("El listado se pagina: una sala por página, con el total y las páginas que hay")
	void listadoPaginado() throws Exception {
		String juego = api.tokenJuego(PUEBLO);
		try {
			JsonNode primera = api.cuerpo(api.enviar("GET", "/api/v1/admin/salas?tamano=1&pagina=0", null, admin));
			assertThat(primera.get("contenido").size()).isEqualTo(1);
			assertThat(primera.get("pagina").asInt()).isZero();
			assertThat(primera.get("tamano").asInt()).isEqualTo(1);
			assertThat(primera.get("total").asLong()).isGreaterThanOrEqualTo(1);
			assertThat(primera.get("total_paginas").asLong()).isEqualTo(primera.get("total").asLong());

			JsonNode fuera = api.cuerpo(api.enviar("GET",
					"/api/v1/admin/salas?tamano=1&pagina=" + primera.get("total").asLong(), null, admin));
			assertThat(fuera.get("contenido")).isEmpty();
			assertThat(fuera.get("total").asLong()).isEqualTo(primera.get("total").asLong());
		}
		finally {
			api.enviar("DELETE", "/api/v1/salas/" + PUEBLO, null, juego);
		}
	}

	@Test
	@DisplayName("El admin cierra la sala: queda finalizada, el pueblo se libera, los tokens dan 401 y ambos dispositivos reciben sala.cerrada")
	void cierreForzado() throws Exception {
		String juego = api.tokenJuego(PUEBLO);
		String app = api.tokenApp(PUEBLO);
		ClienteWs wsJuego = ClienteWs.autenticado(port, json, juego);
		ClienteWs wsApp = ClienteWs.autenticado(port, json, app);
		String id = buscar(api.cuerpo(api.enviar("GET", "/api/v1/admin/salas?estado=activa&tamano=100", null, admin)).get("contenido"), PUEBLO)
				.get("id").asString();

		assertThat(api.enviar("DELETE", "/api/v1/admin/salas/" + id, null, admin).statusCode()).isEqualTo(204);

		assertThat(wsJuego.siguienteDeTipo("sala.cerrada").get("payload").get("estado").asString())
				.isEqualTo("finalizada");
		assertThat(wsApp.siguienteDeTipo("sala.cerrada")).isNotNull();
		assertThat(wsJuego.cierre().codigo()).isEqualTo(1000);
		assertThat(api.enviar("GET", "/api/v1/salas/" + PUEBLO + "/estado", null, juego).statusCode()).isEqualTo(401);
		assertThat(api.enviar("GET", "/api/v1/salas/" + PUEBLO + "/estado", null, app).statusCode()).isEqualTo(401);
		assertThat(jdbc.queryForObject("select estado from salas where id = ?::uuid", String.class, id))
				.isEqualTo("finalizada");
		assertThat(api.cuerpo(api.enviar("GET", "/api/v1/pueblos?disponibles=true", null, null)).toString())
				.contains("\"" + PUEBLO + "\"");

		JsonNode detalle = api.cuerpo(api.enviar("GET", "/api/v1/admin/salas/" + id, null, admin));
		JsonNode ultimo = detalle.get("eventos").get(detalle.get("eventos").size() - 1);
		assertThat(ultimo.get("tipo").asString()).isEqualTo("sala.finalizada");
		assertThat(ultimo.get("origen").asString()).isEqualTo("servidor");
		assertThat(ultimo.get("payload").get("cerrada_por").asString()).isEqualTo("admin");

		assertThat(api.enviar("DELETE", "/api/v1/admin/salas/" + id, null, admin).statusCode()).isEqualTo(204);
		assertThat(jdbc.queryForObject("select count(*) from eventos where sala_id = ?::uuid and tipo = 'sala.finalizada'",
				Integer.class, id)).isEqualTo(1);
	}

	@Test
	void salaInexistenteIdInvalidoYSinPermiso() throws Exception {
		String juego = api.tokenJuego(PUEBLO);
		try {
			String inexistente = "00000000-0000-0000-0000-000000000000";
			assertThat(api.enviar("GET", "/api/v1/admin/salas/" + inexistente, null, admin).statusCode()).isEqualTo(404);
			assertThat(api.enviar("DELETE", "/api/v1/admin/salas/" + inexistente, null, admin).statusCode())
					.isEqualTo(404);
			assertThat(api.enviar("GET", "/api/v1/admin/salas/abc", null, admin).statusCode()).isEqualTo(400);
			assertThat(api.enviar("GET", "/api/v1/admin/salas?estado=otra", null, admin).statusCode()).isEqualTo(400);
			assertThat(api.enviar("GET", "/api/v1/admin/salas", null, juego).statusCode()).isEqualTo(401);
			assertThat(api.enviar("GET", "/api/v1/admin/salas", null, null).statusCode()).isEqualTo(401);
		}
		finally {
			api.enviar("DELETE", "/api/v1/salas/" + PUEBLO, null, juego);
		}
	}

	private static JsonNode buscar(JsonNode lista, String codigo) {
		for (JsonNode sala : lista) {
			if (sala.get("codigo").asString().equals(codigo)) {
				return sala;
			}
		}
		throw new AssertionError("No aparece " + codigo + " en " + lista);
	}

}
