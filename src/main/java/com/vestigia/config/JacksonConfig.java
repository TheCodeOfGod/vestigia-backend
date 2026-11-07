package com.vestigia.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * Tipos estrictos en el JSON de entrada: un número o booleano en un campo de texto, o un texto en un campo numérico o
 * booleano, da 400 en lugar de convertirse. Así {@code "clave": 123} no pasa por {@code "123"} y, en el Paso 3b,
 * {@code "true"} (texto) no pasa por {@code true}.
 */
@Configuration
public class JacksonConfig {

	@Bean
	JsonMapperBuilderCustomizer tiposEstrictos() {
		return JacksonConfig::tiposEstrictos;
	}

	static void tiposEstrictos(JsonMapper.Builder builder) {
		builder.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
				.withCoercionConfig(LogicalType.Textual, c -> c.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
						.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
						.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
				.withCoercionConfig(LogicalType.Boolean, c -> c.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
						.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail))
				.withCoercionConfig(LogicalType.Integer, c -> c.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
						.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
				.withCoercionConfig(LogicalType.Float, c -> c.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
						.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
	}

}
