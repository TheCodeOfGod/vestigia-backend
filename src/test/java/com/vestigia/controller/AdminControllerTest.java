package com.vestigia.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.config.JacksonConfig;
import com.vestigia.dto.SesionIniciada;
import com.vestigia.dto.Usuario;
import com.vestigia.model.UsuarioModel;
import com.vestigia.service.ApiException;
import com.vestigia.service.AuthService;
import com.vestigia.service.SalaService;
import com.vestigia.service.UsuarioService;

@WebMvcTest({ AuthController.class, UsuarioController.class })
@Import(JacksonConfig.class)
class AdminControllerTest {

	private static final UUID ID = UUID.fromString("5b0c1f3e-7d0b-4f5e-9a39-2f0f5e8f6a11");

	private static final Usuario RAUL = new Usuario(ID, "Raúl", "raul@vestigia.site", UsuarioModel.Rol.admin, true,
			Instant.parse("2026-11-08T23:10:00Z"));

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private UsuarioService usuarioService;

	@MockitoBean
	private SalaService salaService;

	@BeforeEach
	void sesionValida() {
		when(authService.autenticar("sesion"))
				.thenReturn(Optional.of(new AuthService.CredencialAdmin(ID, UsuarioModel.Rol.admin, "hash")));
	}

	@Test
	void loginDevuelveTokenVigenciaYUsuarioEnSnakeCase() throws Exception {
		when(authService.iniciarSesion("raul@vestigia.site", "contraseña-larga"))
				.thenReturn(new SesionIniciada("tok", Instant.parse("2026-11-09T07:10:00Z"), RAUL));

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"raul@vestigia.site\",\"password\":\"contraseña-larga\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").value("tok"))
				.andExpect(jsonPath("$.expira_en").value("2026-11-09T07:10:00Z"))
				.andExpect(jsonPath("$.usuario.creado_en").value("2026-11-08T23:10:00Z"))
				.andExpect(jsonPath("$.usuario.password").doesNotExist())
				.andExpect(jsonPath("$.usuario.password_hash").doesNotExist());
	}

	@Test
	void loginSinCamposDa400ConDetalles() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.details.length()").value(2));
	}

	@Test
	void loginPropagaElCodigoDelLimiteDeIntentos() throws Exception {
		when(authService.iniciarSesion(any(), any())).thenThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS,
				AuthService.DEMASIADOS_INTENTOS, "Demasiados intentos", List.of()));

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"a@b.c\",\"password\":\"x\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.codigo").value("demasiados_intentos"));
	}

	@Test
	@DisplayName("Las rutas de admin y de sesión dan 401 sin token, con token desconocido y con esquema distinto de Bearer")
	void sinSesionDa401() throws Exception {
		for (var peticion : List.of(get("/api/v1/admin/usuarios"), get("/api/v1/auth/yo"),
				post("/api/v1/auth/logout"), delete("/api/v1/admin/usuarios/" + ID))) {
			mockMvc.perform(peticion).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").isEmpty());
			mockMvc.perform(peticion.header("Authorization", "Bearer de-una-sala")).andExpect(status().isUnauthorized());
			mockMvc.perform(peticion.header("Authorization", "Basic sesion")).andExpect(status().isUnauthorized());
		}
	}

	@Test
	void listarYYoConSesionValida() throws Exception {
		when(usuarioService.listar()).thenReturn(List.of(RAUL));
		when(usuarioService.obtener(ID)).thenReturn(RAUL);

		mockMvc.perform(get("/api/v1/admin/usuarios").header("Authorization", "Bearer sesion"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].correo").value("raul@vestigia.site"));
		mockMvc.perform(get("/api/v1/auth/yo").header("Authorization", "Bearer sesion"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Raúl"));
	}

	@Test
	void logoutDa204YCierraLaSesion() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer sesion"))
				.andExpect(status().isNoContent());

		verify(authService).cerrarSesion(new AuthService.CredencialAdmin(ID, UsuarioModel.Rol.admin, "hash"));
	}

	@Test
	void crearValidaCamposYResponde201() throws Exception {
		when(usuarioService.crear(any())).thenReturn(RAUL);

		mockMvc.perform(post("/api/v1/admin/usuarios").header("Authorization", "Bearer sesion")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"María\",\"correo\":\"maria@vestigia.site\",\"password\":\"otra-contraseña\",\"rol\":\"admin\"}"))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/admin/usuarios").header("Authorization", "Bearer sesion")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"M\",\"correo\":\"no-es-correo\",\"password\":\"corta\",\"rol\":\"admin\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.details.length()").value(2));

		mockMvc.perform(post("/api/v1/admin/usuarios").header("Authorization", "Bearer sesion")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"M\",\"correo\":\"m@vestigia.site\",\"password\":\"otra-contraseña\",\"rol\":\"root\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void actualizarAceptaCamposOpcionalesYRechazaNombreVacio() throws Exception {
		when(usuarioService.actualizar(any(), any())).thenReturn(RAUL);

		mockMvc.perform(put("/api/v1/admin/usuarios/" + ID).header("Authorization", "Bearer sesion")
				.contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
				.andExpect(status().isOk());
		mockMvc.perform(put("/api/v1/admin/usuarios/" + ID).header("Authorization", "Bearer sesion")
				.contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"  \"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(put("/api/v1/admin/usuarios/" + ID).header("Authorization", "Bearer sesion")
				.contentType(MediaType.APPLICATION_JSON).content("{\"activo\":\"true\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void eliminarPropaga404Y204() throws Exception {
		mockMvc.perform(delete("/api/v1/admin/usuarios/" + ID).header("Authorization", "Bearer sesion"))
				.andExpect(status().isNoContent());

		org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe"))
				.when(usuarioService).eliminar(ID);
		mockMvc.perform(delete("/api/v1/admin/usuarios/" + ID).header("Authorization", "Bearer sesion"))
				.andExpect(status().isNotFound());
	}

}
