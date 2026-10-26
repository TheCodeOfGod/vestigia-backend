package com.vestigia.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/** Toda ruta bajo /api/v1/salas pide token, salvo crear la sala y unirse a ella. */
@Configuration
@SecurityScheme(name = SeguridadConfig.ESQUEMA, type = SecuritySchemeType.HTTP, scheme = "bearer",
		description = "Token que devuelve POST /salas (juego) o POST /salas/{codigo}/unirse (app). "
				+ "Se envía como Authorization: Bearer <token>.")
public class SeguridadConfig implements WebMvcConfigurer {

	public static final String ESQUEMA = "token";

	private final TokenInterceptor tokenInterceptor;

	public SeguridadConfig(TokenInterceptor tokenInterceptor) {
		this.tokenInterceptor = tokenInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(tokenInterceptor)
				.addPathPatterns("/api/v1/salas/**")
				.excludePathPatterns("/api/v1/salas", "/api/v1/salas/*/unirse");
	}

}
