package com.vestigia.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class JacksonConfigTest {

	record Campos(String texto, Boolean bandera, Integer entero, Double decimal, boolean primitivo) {
	}

	private final JsonMapper mapper = mapper();

	@Test
	@DisplayName("Un número o un booleano en un campo de texto no se convierte a texto: falla")
	void numeroOBooleanoEnTextoFalla() {
		for (String valor : new String[] { "12345", "1.5", "true", "false" }) {
			assertThatThrownBy(() -> mapper.readValue("{\"texto\":" + valor + "}", Campos.class)).as(valor)
					.isInstanceOf(JacksonException.class);
		}
	}

	@Test
	@DisplayName("Un texto o un número en un campo booleano no se convierte: falla, también \"true\" entre comillas")
	void textoONumeroEnBooleanoFalla() {
		for (String json : new String[] { "{\"bandera\":\"true\"}", "{\"bandera\":1}", "{\"primitivo\":\"true\"}",
				"{\"primitivo\":0}" }) {
			assertThatThrownBy(() -> mapper.readValue(json, Campos.class)).as(json).isInstanceOf(JacksonException.class);
		}
	}

	@Test
	@DisplayName("Un texto o un booleano en un campo numérico no se convierte: falla")
	void textoOBooleanoEnNumeroFalla() {
		for (String json : new String[] { "{\"entero\":\"5\"}", "{\"entero\":true}", "{\"decimal\":\"1.5\"}",
				"{\"decimal\":false}" }) {
			assertThatThrownBy(() -> mapper.readValue(json, Campos.class)).as(json).isInstanceOf(JacksonException.class);
		}
	}

	@Test
	@DisplayName("Los tipos correctos se siguen leyendo, y en un objeto libre (Map) cada valor conserva su tipo JSON")
	void tiposCorrectosSiguenFuncionando() {
		assertThat(mapper.readValue("{\"texto\":\"a\",\"bandera\":true,\"entero\":5,\"decimal\":1.5,\"primitivo\":true}",
				Campos.class)).isEqualTo(new Campos("a", true, 5, 1.5, true));
		assertThat(mapper.readValue("{\"completada\":\"true\",\"n\":1}", Map.class))
				.containsEntry("completada", "true").containsEntry("n", 1);
	}

	@Test
	@DisplayName("Una clave repetida en el mismo objeto falla en la raíz, anidada, en un Map o en un objeto libre, aunque los dos valores coincidan")
	void claveRepetidaFalla() {
		for (String json : new String[] { "{\"texto\":\"a\",\"texto\":\"a\"}", "{\"texto\":\"a\",\"texto\":\"b\"}" }) {
			assertThatThrownBy(() -> mapper.readValue(json, Campos.class)).as(json).isInstanceOf(JacksonException.class);
		}
		for (String json : new String[] { "{\"completada\":true,\"completada\":true}",
				"{\"completada\":true,\"completada\":false}", "{\"datos\":{\"a\":1,\"a\":1}}",
				"[{\"a\":1},{\"b\":2,\"b\":2}]" }) {
			assertThatThrownBy(() -> mapper.readValue(json, Object.class)).as(json).isInstanceOf(JacksonException.class);
		}
	}

	@Test
	@DisplayName("La misma clave en objetos distintos, hermanos o anidados, se sigue leyendo")
	void mismaClaveEnObjetosDistintosSeLee() {
		assertThat(mapper.readValue("{\"a\":{\"a\":1},\"b\":[{\"a\":1},{\"a\":2}]}", Map.class))
				.containsEntry("a", Map.of("a", 1))
				.containsEntry("b", List.of(Map.of("a", 1), Map.of("a", 2)));
	}

	private static JsonMapper mapper() {
		JsonMapper.Builder builder = JsonMapper.builder();
		JacksonConfig.entradaEstricta(builder);
		return builder.build();
	}

}
