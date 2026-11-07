package com.vestigia.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un endpoint que solo puede usar el token del juego. {@link TokenInterceptor} responde 403 con {@link #value()}
 * antes de leer y validar el cuerpo, así que la app recibe siempre 403, mande lo que mande.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SoloJuego {

	/** Mensaje del 403. */
	String value();

}
