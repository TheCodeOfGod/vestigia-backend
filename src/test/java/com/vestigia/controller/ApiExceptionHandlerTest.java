package com.vestigia.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.service.DescubrimientoService;
import com.vestigia.service.SalaService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@WebMvcTest
@Import(ApiExceptionHandlerTest.PruebaController.class)
class ApiExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SalaService salaService;

	@MockitoBean
	private DescubrimientoService descubrimientoService;

	@Test
	void jsonMalformadoDevuelve400ConCuerpoUniforme() throws Exception {
		mockMvc.perform(post("/api/prueba").contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.timestamp").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z")))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").value("JSON malformado o ilegible"))
				.andExpect(jsonPath("$.path").value("/api/prueba"))
				.andExpect(jsonPath("$.details").isEmpty());
	}

	@Test
	void validacionDevuelve400ConDetallePorCampo() throws Exception {
		mockMvc.perform(post("/api/prueba").contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.details.length()").value(1))
				.andExpect(jsonPath("$.details[0].field").value("codigo"));
	}

	@Test
	void conflictoConservaSuCodigoYMensaje() throws Exception {
		mockMvc.perform(get("/api/prueba/conflicto"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.message").value("Pueblo ocupado"));
	}

	@Test
	void rutaInexistenteDevuelve404ConCuerpoUniforme() throws Exception {
		mockMvc.perform(get("/api/no-existe"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.path").value("/api/no-existe"));
	}

	@Test
	void errorInesperadoDevuelve500SinDetallesInternos() throws Exception {
		mockMvc.perform(get("/api/prueba/falla"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.message").value("Error interno del servidor"))
				.andExpect(content().string(not(containsString("detalle interno"))));
	}

	record PruebaRequest(@NotBlank String codigo) {
	}

	@RestController
	static class PruebaController {

		@PostMapping("/api/prueba")
		String crear(@Valid @RequestBody PruebaRequest request) {
			return request.codigo();
		}

		@GetMapping("/api/prueba/conflicto")
		String conflicto() {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Pueblo ocupado");
		}

		@GetMapping("/api/prueba/falla")
		String falla() {
			throw new IllegalStateException("detalle interno");
		}

	}

}
