package com.vestigia.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.dto.Pueblo;
import com.vestigia.dto.SalaCreada;
import com.vestigia.model.SalaModel;
import com.vestigia.service.SalaService;

@WebMvcTest({ SalaController.class, PuebloController.class })
class SalaControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SalaService salaService;

	@Test
	void crearResponde201ConCodigoEstadoYToken() throws Exception {
		when(salaService.crear("alamos")).thenReturn(
				new SalaCreada("ALAMOS", SalaModel.Estado.esperando_app, "tok"));

		mockMvc.perform(post("/api/v1/salas").contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"alamos\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.codigo").value("ALAMOS"))
				.andExpect(jsonPath("$.estado").value("esperando_app"))
				.andExpect(jsonPath("$.token").value("tok"));
	}

	@Test
	void crearSinCodigoDa400ConDetalle() throws Exception {
		mockMvc.perform(post("/api/v1/salas").contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\" \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.details[0].field").value("codigo"));
		verifyNoInteractions(salaService);
	}

	@Test
	void crearConPuebloOcupadoDa409ConCuerpoUniforme() throws Exception {
		when(salaService.crear("ALAMOS"))
				.thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "'ALAMOS' ya tiene una sala en curso"));

		mockMvc.perform(post("/api/v1/salas").contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"ALAMOS\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("'ALAMOS' ya tiene una sala en curso"))
				.andExpect(jsonPath("$.path").value("/api/v1/salas"));
	}

	@Test
	void pueblosPasaElFiltroDeDisponibles() throws Exception {
		when(salaService.listarPueblos(true)).thenReturn(List.of(new Pueblo("ALAMOS", true)));

		mockMvc.perform(get("/api/v1/pueblos").queryParam("disponibles", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].codigo").value("ALAMOS"))
				.andExpect(jsonPath("$[0].libre").value(true));
		mockMvc.perform(get("/api/v1/pueblos")).andExpect(status().isOk());
		verify(salaService).listarPueblos(false);
	}

}
