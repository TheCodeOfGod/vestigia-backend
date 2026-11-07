package com.vestigia.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.vestigia.config.JacksonConfig;
import com.vestigia.dto.Sala;
import com.vestigia.model.PuzzleEstadoModel;
import com.vestigia.service.ApiException;
import com.vestigia.service.PuzzleService;
import com.vestigia.service.SalaService;

@WebMvcTest(PuzzleController.class)
@Import(JacksonConfig.class)
class PuzzleControllerTest {

	private static final String RUTA = "/api/v1/salas/ALAMOS/puzzles/";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PuzzleService puzzleService;

	@MockitoBean
	private SalaService salaService;

	private final SalaService.Credencial credencial = new SalaService.Credencial(UUID.randomUUID(), "ALAMOS",
			SalaService.Rol.app);

	@BeforeEach
	void token() {
		when(salaService.autenticar("tok")).thenReturn(Optional.of(credencial));
	}

	@Test
	@DisplayName("PUT puzzle responde 200 con {puzzle, estado, datos} y pasa al servicio el id de la ruta y los datos")
	void reportarResponde200() throws Exception {
		when(puzzleService.reportar(credencial, "photo", Map.of("completada", true))).thenReturn(
				new Sala.Puzzle(PuzzleEstadoModel.Puzzle.photo, PuzzleEstadoModel.Estado.resuelto,
						Map.of("completada", true)));

		enviar("photo", "{\"datos\":{\"completada\":true}}").andExpect(status().isOk())
				.andExpect(jsonPath("$.puzzle").value("photo"))
				.andExpect(jsonPath("$.estado").value("resuelto"))
				.andExpect(jsonPath("$.datos.completada").value(true));
	}

	@Test
	@DisplayName("Sin datos, con datos null o con el aviso fuera de datos da 400 con details en datos y codigo null, sin llamar al servicio")
	void sinDatosDa400ConDetalle() throws Exception {
		for (String cuerpo : List.of("{}", "{\"datos\":null}", "{\"completada\":true}")) {
			enviar("photo", cuerpo).andExpect(status().isBadRequest())
					.andExpect(content().string(containsString("\"codigo\":null")))
					.andExpect(jsonPath("$.message").value("Datos inválidos"))
					.andExpect(jsonPath("$.details[0].field").value("datos"))
					.andExpect(jsonPath("$.details[0].message").value("es obligatorio"));
		}
		verifyNoInteractions(puzzleService);
	}

	@Test
	@DisplayName("datos que no es un objeto JSON llega al servicio tal cual, para que responda 400 con details")
	void datosQueNoSonObjetoLleganAlServicio() throws Exception {
		Map<String, Object> esperados = Map.of("true", true, "\"completada\"", "completada", "[true]", List.of(true),
				"1", 1);
		for (Map.Entry<String, Object> caso : esperados.entrySet()) {
			enviar("photo", "{\"datos\":" + caso.getKey() + "}");
			verify(puzzleService).reportar(credencial, "photo", caso.getValue());
		}
	}

	@Test
	@DisplayName("Los valores de datos llegan al servicio con su tipo JSON: \"true\" es texto, 1 es número y true es booleano")
	void datosConservanSuTipoJson() throws Exception {
		enviar("photo", "{\"datos\":{\"a\":\"true\",\"b\":1,\"c\":true,\"d\":null}}");

		ArgumentCaptor<Object> capturado = ArgumentCaptor.forClass(Object.class);
		verify(puzzleService).reportar(eq(credencial), eq("photo"), capturado.capture());
		assertThat(capturado.getValue()).isInstanceOfSatisfying(Map.class, datos -> {
			assertThat(datos.get("a")).isEqualTo("true");
			assertThat(datos.get("b")).isEqualTo(1);
			assertThat(datos.get("c")).isEqualTo(Boolean.TRUE);
			assertThat(datos).containsEntry("d", null);
		});
	}

	@Test
	@DisplayName("Un puzzle bloqueado responde 409 con codigo puzzle_bloqueado en el cuerpo uniforme")
	void bloqueadoDa409ConCodigo() throws Exception {
		when(puzzleService.reportar(any(), eq("lookout"), any())).thenThrow(new ApiException(HttpStatus.CONFLICT,
				"puzzle_bloqueado", "El puzzle 'lookout' está bloqueado: todavía no se cumplen sus requisitos",
				List.of()));

		enviar("lookout", "{\"datos\":{\"completada\":true}}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.error").value("Conflict"))
				.andExpect(jsonPath("$.codigo").value("puzzle_bloqueado"))
				.andExpect(jsonPath("$.message")
						.value("El puzzle 'lookout' está bloqueado: todavía no se cumplen sus requisitos"))
				.andExpect(jsonPath("$.path").value(RUTA + "lookout"))
				.andExpect(jsonPath("$.details").isEmpty());
	}

	@Test
	@DisplayName("Un aviso inválido para el puzzle responde 400 con el details del servicio y codigo null")
	void avisoInvalidoDa400ConDetails() throws Exception {
		when(puzzleService.reportar(any(), eq("radio"), any())).thenThrow(new ApiException(HttpStatus.BAD_REQUEST,
				null, "Datos inválidos",
				List.of(new ApiException.Detalle("datos", "en radio debe ser exactamente {\"sintonizada\": true}"))));

		enviar("radio", "{\"datos\":{\"iniciado\":true}}").andExpect(status().isBadRequest())
				.andExpect(content().string(containsString("\"codigo\":null")))
				.andExpect(jsonPath("$.details[0].field").value("datos"))
				.andExpect(jsonPath("$.details[0].message")
						.value("en radio debe ser exactamente {\"sintonizada\": true}"));
	}

	@Test
	@DisplayName("Sin token da 401 y con el token de otra sala da 403, con codigo null y sin llamar al servicio")
	void tokenAusenteODeOtraSala() throws Exception {
		mockMvc.perform(put(RUTA + "photo").contentType(MediaType.APPLICATION_JSON)
				.content("{\"datos\":{\"completada\":true}}"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().string(containsString("\"codigo\":null")));
		mockMvc.perform(put("/api/v1/salas/BERNAL/puzzles/photo").header("Authorization", "Bearer tok")
				.contentType(MediaType.APPLICATION_JSON).content("{\"datos\":{\"completada\":true}}"))
				.andExpect(status().isForbidden())
				.andExpect(content().string(containsString("\"codigo\":null")));
		verify(puzzleService, never()).reportar(any(), any(), any());
	}

	private ResultActions enviar(String puzzle, String cuerpo) throws Exception {
		return mockMvc.perform(put(RUTA + puzzle).header("Authorization", "Bearer tok")
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

}
