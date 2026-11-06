package com.vestigia.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.dto.AppUnida;
import com.vestigia.dto.EscenaActual;
import com.vestigia.dto.Pueblo;
import com.vestigia.dto.Sala;
import com.vestigia.model.PuzzleEstadoModel;
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
				.andExpect(jsonPath("$.details[0].field").value("codigo"))
				.andExpect(jsonPath("$.details[0].message").value("es obligatorio"));
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
	void unirseNoPideTokenYDevuelveElDeLaApp() throws Exception {
		when(salaService.unirse("alamos")).thenReturn(new AppUnida("tok-app"));

		mockMvc.perform(post("/api/v1/salas/alamos/unirse"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").value("tok-app"));
		verify(salaService, never()).autenticar(any());
	}

	@Test
	void estadoSinTokenDa401() throws Exception {
		mockMvc.perform(get("/api/v1/salas/ALAMOS/estado"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.path").value("/api/v1/salas/ALAMOS/estado"));
		mockMvc.perform(get("/api/v1/salas/ALAMOS/estado").header("Authorization", "Basic abc"))
				.andExpect(status().isUnauthorized());
		verify(salaService, never()).estado(any());
	}

	@Test
	void estadoConTokenDesconocidoDa401() throws Exception {
		when(salaService.autenticar("malo")).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/v1/salas/ALAMOS/estado").header("Authorization", "Bearer malo"))
				.andExpect(status().isUnauthorized());
		verify(salaService, never()).estado(any());
	}

	@Test
	void estadoConTokenDeOtraSalaDa403() throws Exception {
		when(salaService.autenticar("tok")).thenReturn(Optional.of(credencial("BERNAL")));

		mockMvc.perform(get("/api/v1/salas/ALAMOS/estado").header("Authorization", "Bearer tok"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
		verify(salaService, never()).estado(any());
	}

	@Test
	void estadoConTokenDeLaSalaDevuelveElEstadoEnSnakeCase() throws Exception {
		SalaService.Credencial credencial = credencial("ALAMOS");
		when(salaService.autenticar("tok")).thenReturn(Optional.of(credencial));
		when(salaService.estado(credencial.salaId())).thenReturn(new Sala("ALAMOS", SalaModel.Estado.activa,
				SalaModel.Escena.introduccion,
				List.of(new Sala.Puzzle(PuzzleEstadoModel.Puzzle.radio, PuzzleEstadoModel.Estado.bloqueado, Map.of())),
				List.of(), new Sala.Dispositivos(false, false), Instant.parse("2026-10-26T18:00:00Z"),
				Instant.parse("2026-10-26T20:00:00Z")));

		mockMvc.perform(get("/api/v1/salas/alamos/estado").header("Authorization", "bearer tok"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.escena_actual").value("introduccion"))
				.andExpect(jsonPath("$.puzzles[0].puzzle").value("radio"))
				.andExpect(jsonPath("$.puzzles[0].estado").value("bloqueado"))
				.andExpect(jsonPath("$.dispositivos.juego").value(false))
				.andExpect(jsonPath("$.dispositivos.app").value(false))
				.andExpect(jsonPath("$.creada_en").value("2026-10-26T18:00:00Z"))
				.andExpect(jsonPath("$.expira_en").value("2026-10-26T20:00:00Z"));
	}

	@Test
	@DisplayName("PUT escena responde 200 con escena_actual")
	void cambiarEscenaResponde200() throws Exception {
		SalaService.Credencial credencial = credencial("ALAMOS");
		when(salaService.autenticar("tok")).thenReturn(Optional.of(credencial));
		when(salaService.cambiarEscena(credencial, "faro")).thenReturn(new EscenaActual(SalaModel.Escena.faro));

		mockMvc.perform(put("/api/v1/salas/ALAMOS/escena").header("Authorization", "Bearer tok")
				.contentType(MediaType.APPLICATION_JSON).content("{\"escena\":\"faro\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.escena_actual").value("faro"));
	}

	@Test
	@DisplayName("PUT escena sin escena o con escena vacía da 400 con detalle, sin llamar al servicio")
	void cambiarEscenaSinValorDa400() throws Exception {
		when(salaService.autenticar("tok")).thenReturn(Optional.of(credencial("ALAMOS")));

		for (String cuerpo : new String[] { "{}", "{\"escena\":\"\"}", "{\"escena\":null}" }) {
			mockMvc.perform(put("/api/v1/salas/ALAMOS/escena").header("Authorization", "Bearer tok")
					.contentType(MediaType.APPLICATION_JSON).content(cuerpo))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.details[0].field").value("escena"));
		}
		verify(salaService, never()).cambiarEscena(any(), any());
	}

	@Test
	@DisplayName("PUT escena sin token da 401")
	void cambiarEscenaSinTokenDa401() throws Exception {
		mockMvc.perform(put("/api/v1/salas/ALAMOS/escena").contentType(MediaType.APPLICATION_JSON)
				.content("{\"escena\":\"faro\"}")).andExpect(status().isUnauthorized());
		verify(salaService, never()).cambiarEscena(any(), any());
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

	private static SalaService.Credencial credencial(String codigo) {
		return new SalaService.Credencial(UUID.randomUUID(), codigo, SalaService.Rol.juego);
	}

}
