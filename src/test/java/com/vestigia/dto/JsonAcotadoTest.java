package com.vestigia.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintValidatorContext;
import tools.jackson.databind.json.JsonMapper;

class JsonAcotadoTest {

	private final JsonAcotado.Validador validador = new JsonAcotado.Validador(JsonMapper.builder().build(), 20);

	private final ConstraintValidatorContext context = mock(ConstraintValidatorContext.class, RETURNS_DEEP_STUBS);

	@Test
	@DisplayName("Los datos que caben en el límite, en bytes de su JSON compacto, son válidos; nulos también")
	void dentroDelLimite() {
		assertThat(validador.isValid(null, context)).isTrue();
		assertThat(validador.isValid(Map.of(), context)).isTrue();
		assertThat(validador.isValid(Map.of("a", "x".repeat(12)), context)).as("{\"a\":\"xxxxxxxxxxxx\"} = 20 bytes")
				.isTrue();
	}

	@Test
	@DisplayName("Un byte más del límite es inválido, y los acentos cuentan como dos bytes")
	void fueraDelLimite() {
		assertThat(validador.isValid(Map.of("a", "x".repeat(13)), context)).isFalse();
		assertThat(validador.isValid(Map.of("a", "ñ".repeat(7)), context)).as("{\"a\":\"ñññññññ\"} = 22 bytes")
				.isFalse();
	}

}
