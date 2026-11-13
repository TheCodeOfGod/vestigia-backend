package com.vestigia.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.service.AdminSalaService;
import com.vestigia.service.AuthService;
import com.vestigia.service.UsuarioService;
import com.vestigia.service.ApiException;
import com.vestigia.service.DescubrimientoService;
import com.vestigia.service.PuzzleService;
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
	private AuthService authService;

	@MockitoBean
	private UsuarioService usuarioService;

	@MockitoBean
	private AdminSalaService adminSalaService;

	@MockitoBean
	private DescubrimientoService descubrimientoService;

	@MockitoBean
	private PuzzleService puzzleService;

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

	@Test
	@DisplayName("Un error con código lo lleva en codigo y sus detalles en details, con el mismo cuerpo uniforme")
	void errorConCodigoLlenaCodigoYDetails() throws Exception {
		mockMvc.perform(get("/api/prueba/bloqueado"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.error").value("Conflict"))
				.andExpect(jsonPath("$.codigo").value("puzzle_bloqueado"))
				.andExpect(jsonPath("$.message").value("El puzzle 'lookout' está bloqueado"))
				.andExpect(jsonPath("$.path").value("/api/prueba/bloqueado"))
				.andExpect(jsonPath("$.details").isEmpty());
		mockMvc.perform(get("/api/prueba/datos"))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(containsString("\"codigo\":null")))
				.andExpect(jsonPath("$.message").value("Datos inválidos"))
				.andExpect(jsonPath("$.details[0].field").value("datos"))
				.andExpect(jsonPath("$.details[0].message").value("debe ser {\"completada\": true}"));
	}

	@Test
	@DisplayName("Los errores sin código traen \"codigo\": null en 400, 401, 403, 404, 405, 409 y 500")
	void erroresSinCodigoTraenCodigoNulo() throws Exception {
		List<RequestBuilder> peticiones = List.of(
				post("/api/prueba").contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":"),
				post("/api/prueba").contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"\"}"),
				get("/api/prueba/estado/401"), get("/api/prueba/estado/403"), get("/api/prueba/estado/404"),
				get("/api/no-existe"), delete("/api/prueba/conflicto"), get("/api/prueba/conflicto"),
				get("/api/prueba/falla"));

		List<Integer> estados = List.of(400, 400, 401, 403, 404, 404, 405, 409, 500);

		for (int i = 0; i < peticiones.size(); i++) {
			mockMvc.perform(peticiones.get(i))
					.andExpect(status().is(estados.get(i)))
					.andExpect(content().string(containsString("\"codigo\":null,\"message\":")));
		}
	}

	@Test
	@DisplayName("Los errores salen en JSON aunque el cliente pida otro formato: 406 con Accept XML y 409 con Accept HTML")
	void erroresSiempreEnJson() throws Exception {
		mockMvc.perform(get("/api/prueba/objeto").accept(MediaType.APPLICATION_XML))
				.andExpect(status().isNotAcceptable())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.status").value(406))
				.andExpect(content().string(containsString("\"codigo\":null,\"message\":")));
		mockMvc.perform(get("/api/prueba/conflicto").accept(MediaType.TEXT_HTML))
				.andExpect(status().isConflict())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.message").value("Pueblo ocupado"));
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

		@GetMapping("/api/prueba/objeto")
		PruebaRequest objeto() {
			return new PruebaRequest("ALAMOS");
		}

		@GetMapping("/api/prueba/falla")
		String falla() {
			throw new IllegalStateException("detalle interno");
		}

		@GetMapping("/api/prueba/estado/{codigo}")
		String conEstado(@PathVariable int codigo) {
			throw new ResponseStatusException(HttpStatus.valueOf(codigo), "Error " + codigo);
		}

		@GetMapping("/api/prueba/bloqueado")
		String bloqueado() {
			throw new ApiException(HttpStatus.CONFLICT, "puzzle_bloqueado", "El puzzle 'lookout' está bloqueado",
					List.of());
		}

		@GetMapping("/api/prueba/datos")
		String datos() {
			throw new ApiException(HttpStatus.BAD_REQUEST, null, "Datos inválidos",
					List.of(new ApiException.Detalle("datos", "debe ser {\"completada\": true}")));
		}

	}

}
