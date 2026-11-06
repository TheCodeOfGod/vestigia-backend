package com.vestigia.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.dto.Sala;
import com.vestigia.model.DescubrimientoModel;
import com.vestigia.service.DescubrimientoService;
import com.vestigia.service.SalaService;

@WebMvcTest(DescubrimientoController.class)
class DescubrimientoControllerTest {

	private static final String RUTA = "/api/v1/salas/ALAMOS/descubrimientos";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DescubrimientoService descubrimientoService;

	@MockitoBean
	private SalaService salaService;

	private final SalaService.Credencial credencial = new SalaService.Credencial(UUID.randomUUID(), "ALAMOS",
			SalaService.Rol.app);

	@BeforeEach
	void token() {
		when(salaService.autenticar("tok")).thenReturn(Optional.of(credencial));
	}

	@Test
	@DisplayName("Un descubrimiento nuevo responde 201 con clave, datos, origen y creado_en")
	void nuevoResponde201() throws Exception {
		when(descubrimientoService.registrar(credencial, "pieza_1", Map.of("nota", "muelle")))
				.thenReturn(registro(true));

		enviar("{\"clave\":\"pieza_1\",\"datos\":{\"nota\":\"muelle\"}}").andExpect(status().isCreated())
				.andExpect(jsonPath("$.clave").value("pieza_1"))
				.andExpect(jsonPath("$.datos.nota").value("muelle"))
				.andExpect(jsonPath("$.origen").value("app"))
				.andExpect(jsonPath("$.creado_en").value("2026-11-05T18:00:00Z"));
	}

	@Test
	@DisplayName("Repetir una clave responde 200 con el descubrimiento que ya existía")
	void repetidoResponde200() throws Exception {
		when(descubrimientoService.registrar(eq(credencial), eq("pieza_1"), isNull())).thenReturn(registro(false));

		enviar("{\"clave\":\"pieza_1\"}").andExpect(status().isOk()).andExpect(jsonPath("$.clave").value("pieza_1"));
	}

	@Test
	@DisplayName("Una clave vacía, con mayúsculas, guiones, acentos, espacios o de más de 40 caracteres da 400 con detalle")
	void claveConFormatoInvalidoDa400() throws Exception {
		for (String clave : new String[] { "", "Pieza_1", "pieza-1", "señal", "pieza 1", "a".repeat(41) }) {
			enviar("{\"clave\":\"" + clave + "\"}").andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.details[0].field").value("clave"));
		}
		when(descubrimientoService.registrar(any(), eq("a".repeat(40)), any())).thenReturn(registro(true));
		enviar("{\"clave\":\"" + "a".repeat(40) + "\"}").andExpect(status().isCreated());
	}

	@Test
	@DisplayName("Sin clave o con datos que no son un objeto JSON da 400 sin llamar al servicio")
	void sinClaveODatosQueNoSonObjetoDa400() throws Exception {
		enviar("{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details[0].message").value("es obligatoria"));
		enviar("{\"clave\":\"pieza_1\",\"datos\":[1,2]}").andExpect(status().isBadRequest());
		enviar("{\"clave\":\"pieza_1\",\"datos\":\"texto\"}").andExpect(status().isBadRequest());
		enviar("{\"clave\":\"pieza_1\",\"datos\":5}").andExpect(status().isBadRequest());
		verifyNoInteractions(descubrimientoService);
	}

	@Test
	@DisplayName("Una clave del servidor responde 403 con el cuerpo de error uniforme")
	void claveDelServidorDa403() throws Exception {
		when(descubrimientoService.registrar(any(), eq("coordenada_a"), any())).thenThrow(
				new ResponseStatusException(HttpStatus.FORBIDDEN, "La clave 'coordenada_a' la genera el servidor"));

		enviar("{\"clave\":\"coordenada_a\"}").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.path").value(RUTA))
				.andExpect(jsonPath("$.message").value("La clave 'coordenada_a' la genera el servidor"));
	}

	@Test
	@DisplayName("Sin token da 401 y con el token de otra sala da 403, sin llamar al servicio")
	void tokenAusenteODeOtraSala() throws Exception {
		mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"clave\":\"pieza_1\"}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/salas/BERNAL/descubrimientos").header("Authorization", "Bearer tok")
				.contentType(MediaType.APPLICATION_JSON).content("{\"clave\":\"pieza_1\"}"))
				.andExpect(status().isForbidden());
		verify(descubrimientoService, never()).registrar(any(), any(), any());
	}

	private ResultActions enviar(String cuerpo) throws Exception {
		return mockMvc.perform(post(RUTA).header("Authorization", "Bearer tok")
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private static DescubrimientoService.Registro registro(boolean nuevo) {
		return new DescubrimientoService.Registro(new Sala.Descubrimiento("pieza_1", Map.of("nota", "muelle"),
				DescubrimientoModel.Origen.app, Instant.parse("2026-11-05T18:00:00Z")), nuevo);
	}

}
