package com.vestigia.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest
@Import(ApiHttpConfigTest.PruebaController.class)
@ExtendWith(OutputCaptureExtension.class)
class ApiHttpConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void registraUnaLineaPorPeticionSinTokens(CapturedOutput output) throws Exception {
		mockMvc.perform(get("/api/prueba").queryParam("token", "secreto-query")
				.header("Authorization", "Bearer secreto-header"))
				.andExpect(status().isOk());

		assertThat(output.getOut()).containsOnlyOnce("[API] GET /api/prueba -> 200");
		assertThat(output.getOut()).doesNotContain("secreto");
	}

	@Test
	void noRegistraRutasFueraDeApi(CapturedOutput output) throws Exception {
		mockMvc.perform(get("/fuera"));

		assertThat(output.getOut()).doesNotContain("[API]");
	}

	@Test
	void corsPermiteOrigenConfigurado() throws Exception {
		mockMvc.perform(options("/api/prueba").header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "PUT")
				.header("Access-Control-Request-Headers", "Authorization"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
	}

	@Test
	void corsRechazaOrigenNoConfigurado() throws Exception {
		mockMvc.perform(options("/api/prueba").header("Origin", "https://otro.example")
				.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@RestController
	static class PruebaController {

		@GetMapping("/api/prueba")
		String prueba() {
			return "ok";
		}

	}

}
