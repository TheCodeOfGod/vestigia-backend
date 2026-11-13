package com.vestigia;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Duration;

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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Inicio de sesión y administración de usuarios por HTTP contra Postgres real. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfig.class)
class AdminIT {

	private static final String CLAVE = "contraseña-larga";

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JsonMapper json;

	@Autowired
	private RelojDePrueba reloj;

	@Autowired
	private UsuarioService usuarios;

	private ClienteApi api;

	@BeforeEach
	void preparar() {
		api = new ClienteApi(port, json);
		jdbc.update("delete from usuarios");
	}

	@AfterEach
	void limpiar() {
		reloj.reiniciar();
		jdbc.update("delete from usuarios");
	}

	@Test
	@DisplayName("Login, yo, listar, crear y logout; la contraseña y el token se guardan solo como hash")
	void flujoCompleto() throws Exception {
		crearAdmin("flujo@vestigia.site");

		HttpResponse<String> login = login("Flujo@Vestigia.site", CLAVE);
		assertThat(login.statusCode()).isEqualTo(200);
		String token = api.cuerpo(login).get("token").asString();
		assertThat(jdbc.queryForObject("select count(*) from sesiones_admin where token_hash = ?", Integer.class, token))
				.isZero();
		assertThat(jdbc.queryForObject("select password_hash from usuarios", String.class)).startsWith("$2")
				.doesNotContain(CLAVE);

		assertThat(api.cuerpo(api.enviar("GET", "/api/v1/auth/yo", null, token)).get("correo").asString())
				.isEqualTo("flujo@vestigia.site");
		HttpResponse<String> nuevo = api.enviar("POST", "/api/v1/admin/usuarios",
				"{\"nombre\":\"María\",\"correo\":\"maria@vestigia.site\",\"password\":\"otra-contraseña\",\"rol\":\"admin\"}",
				token);
		assertThat(nuevo.statusCode()).isEqualTo(201);
		assertThat(nuevo.body()).doesNotContain("password");
		assertThat(api.cuerpo(api.enviar("GET", "/api/v1/admin/usuarios", null, token)).size()).isEqualTo(2);
		assertThat(login("maria@vestigia.site", "otra-contraseña").statusCode()).isEqualTo(200);

		assertThat(api.enviar("POST", "/api/v1/auth/logout", null, token).statusCode()).isEqualTo(204);
		assertThat(api.enviar("GET", "/api/v1/auth/yo", null, token).statusCode()).isEqualTo(401);
	}

	@Test
	@DisplayName("Los tokens de sala no abren la administración y el de admin no abre las rutas de una sala")
	void tokensSeparados() throws Exception {
		crearAdmin("separados@vestigia.site");
		String admin = api.cuerpo(login("separados@vestigia.site", CLAVE)).get("token").asString();
		String juego = api.tokenJuego("XOCHITEPEC");

		try {
			assertThat(api.enviar("GET", "/api/v1/admin/usuarios", null, juego).statusCode()).isEqualTo(401);
			assertThat(api.enviar("GET", "/api/v1/auth/yo", null, juego).statusCode()).isEqualTo(401);
			assertThat(api.enviar("GET", "/api/v1/salas/XOCHITEPEC/estado", null, admin).statusCode()).isEqualTo(401);
			assertThat(api.enviar("GET", "/api/v1/admin/usuarios", null, null).statusCode()).isEqualTo(401);
		}
		finally {
			api.enviar("DELETE", "/api/v1/salas/XOCHITEPEC", null, juego);
		}
	}

	@Test
	void laSesionVenceALas8Horas() throws Exception {
		crearAdmin("vence@vestigia.site");
		String token = api.cuerpo(login("vence@vestigia.site", CLAVE)).get("token").asString();

		reloj.avanzar(Duration.ofHours(7).plusMinutes(59));
		assertThat(api.enviar("GET", "/api/v1/auth/yo", null, token).statusCode()).isEqualTo(200);

		reloj.avanzar(Duration.ofMinutes(2));
		assertThat(api.enviar("GET", "/api/v1/auth/yo", null, token).statusCode()).isEqualTo(401);
	}

	@Test
	@DisplayName("Desactivar a un usuario cierra su sesión y ya no puede entrar; el último admin no se puede quitar")
	void desactivarYUltimoAdmin() throws Exception {
		crearAdmin("uno@vestigia.site");
		crearAdmin("dos@vestigia.site");
		String uno = api.cuerpo(login("uno@vestigia.site", CLAVE)).get("token").asString();
		String dos = api.cuerpo(login("dos@vestigia.site", CLAVE)).get("token").asString();
		String idDos = api.cuerpo(api.enviar("GET", "/api/v1/auth/yo", null, dos)).get("id").asString();
		String idUno = api.cuerpo(api.enviar("GET", "/api/v1/auth/yo", null, uno)).get("id").asString();

		HttpResponse<String> desactivar = api.enviar("PUT", "/api/v1/admin/usuarios/" + idDos, "{\"activo\":false}", uno);
		assertThat(desactivar.statusCode()).isEqualTo(200);
		assertThat(api.enviar("GET", "/api/v1/auth/yo", null, dos).statusCode()).isEqualTo(401);
		assertThat(login("dos@vestigia.site", CLAVE).statusCode()).isEqualTo(401);

		HttpResponse<String> ultimo = api.enviar("PUT", "/api/v1/admin/usuarios/" + idUno, "{\"activo\":false}", uno);
		assertThat(ultimo.statusCode()).isEqualTo(409);
		assertThat(api.cuerpo(ultimo).get("codigo").asString()).isEqualTo("ultimo_admin");
		assertThat(api.enviar("DELETE", "/api/v1/admin/usuarios/" + idUno, null, uno).statusCode()).isEqualTo(409);
	}

	@Test
	void correoRepetidoDa409YContrasenaCortaDa400() throws Exception {
		crearAdmin("repe@vestigia.site");
		String token = api.cuerpo(login("repe@vestigia.site", CLAVE)).get("token").asString();

		HttpResponse<String> repetido = api.enviar("POST", "/api/v1/admin/usuarios",
				"{\"nombre\":\"X\",\"correo\":\"REPE@vestigia.site\",\"password\":\"otra-contraseña\",\"rol\":\"admin\"}",
				token);
		assertThat(repetido.statusCode()).isEqualTo(409);
		assertThat(api.cuerpo(repetido).get("codigo").asString()).isEqualTo("correo_duplicado");

		HttpResponse<String> corta = api.enviar("POST", "/api/v1/admin/usuarios",
				"{\"nombre\":\"X\",\"correo\":\"x@vestigia.site\",\"password\":\"corta\",\"rol\":\"admin\"}", token);
		assertThat(corta.statusCode()).isEqualTo(400);
		JsonNode detalles = api.cuerpo(corta).get("details");
		assertThat(detalles.get(0).get("field").asString()).isEqualTo("password");
	}

	@Test
	void cincoFallosSeguidosDan429() throws Exception {
		crearAdmin("limite@vestigia.site");

		for (int i = 0; i < 5; i++) {
			assertThat(login("limite@vestigia.site", "equivocada").statusCode()).isEqualTo(401);
		}
		HttpResponse<String> bloqueado = login("limite@vestigia.site", CLAVE);

		assertThat(bloqueado.statusCode()).isEqualTo(429);
		assertThat(api.cuerpo(bloqueado).get("codigo").asString()).isEqualTo("demasiados_intentos");
	}

	private void crearAdmin(String correo) {
		usuarios.crear(new CrearUsuarioRequest("Prueba", correo, CLAVE, UsuarioModel.Rol.admin));
	}

	private HttpResponse<String> login(String correo, String password) throws Exception {
		return api.enviar("POST", "/api/v1/auth/login",
				"{\"correo\":\"" + correo + "\",\"password\":\"" + password + "\"}", null);
	}

}
