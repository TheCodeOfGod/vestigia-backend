package com.vestigia.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * JSON de entrada estricto. Tipos: un número o booleano en un campo de texto, o un texto en un campo numérico o
 * booleano, da 400 en lugar de convertirse. Así {@code "clave": 123} no pasa por {@code "123"} y {@code "true"} (texto)
 * no pasa por {@code true}. Claves: una clave repetida en el mismo objeto, en cualquier nivel, da 400 en lugar de
 * quedarse con el último valor.
 */
@Configuration
public class JacksonConfig {

	@Bean
	JsonMapperBuilderCustomizer entradaEstricta() {
		return JacksonConfig::entradaEstricta;
	}

	static void entradaEstricta(JsonMapper.Builder builder) {
		builder.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
				.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
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
