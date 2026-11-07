package com.vestigia.dto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.annotation.Value;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import tools.jackson.databind.json.JsonMapper;

/**
 * El valor, escrito como JSON compacto en UTF-8, no pasa de {@code vestigia.descubrimientos.datos-max-bytes} bytes.
 * Un valor nulo es válido.
 */
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = JsonAcotado.Validador.class)
public @interface JsonAcotado {

	String message() default "es demasiado grande";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validador implements ConstraintValidator<JsonAcotado, Object> {

		private final JsonMapper json;

		private final int maximoBytes;

		public Validador(JsonMapper json, @Value("${vestigia.descubrimientos.datos-max-bytes}") int maximoBytes) {
			this.json = json;
			this.maximoBytes = maximoBytes;
		}

		@Override
		public boolean isValid(Object valor, ConstraintValidatorContext context) {
			if (valor == null || json.writeValueAsBytes(valor).length <= maximoBytes) {
				return true;
			}
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate("no puede pasar de " + maximoBytes + " bytes escrito como JSON")
					.addConstraintViolation();
			return false;
		}

	}

}
