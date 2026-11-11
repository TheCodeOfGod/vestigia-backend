package com.vestigia.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;

/**
 * Toda ruta bajo /api/v1/salas pide el token de la sala, salvo crear la sala y unirse a ella. Las rutas de
 * /api/v1/admin y las de sesión (/auth/yo y /auth/logout) piden el token de una sesión de administración.
 */
@Configuration
@SecuritySchemes({
		@SecurityScheme(name = SeguridadConfig.ESQUEMA, type = SecuritySchemeType.HTTP, scheme = "bearer",
				bearerFormat = "token opaco de 43 caracteres",
				description = "Token que devuelve POST /salas (juego) o POST /salas/{codigo}/unirse (app). "
						+ "Se envía como Authorization: Bearer <token>. Decide la sala y el rol, y solo sirve mientras "
						+ "la sala esté viva."),
		@SecurityScheme(name = SeguridadConfig.ESQUEMA_ADMIN, type = SecuritySchemeType.HTTP, scheme = "bearer",
				bearerFormat = "token opaco de 43 caracteres",
				description = "Token que devuelve POST /auth/login. Se envía como Authorization: Bearer <token>. "
						+ "Dura 8 horas, o hasta cerrar sesión, y no sirve en las rutas de las salas.") })
public class SeguridadConfig implements WebMvcConfigurer {

	public static final String ESQUEMA = "token";

	public static final String ESQUEMA_ADMIN = "sesion_admin";

	private final TokenInterceptor tokenInterceptor;

	private final AdminInterceptor adminInterceptor;

	public SeguridadConfig(TokenInterceptor tokenInterceptor, AdminInterceptor adminInterceptor) {
		this.tokenInterceptor = tokenInterceptor;
		this.adminInterceptor = adminInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(tokenInterceptor)
				.addPathPatterns("/api/v1/salas/**")
				.excludePathPatterns("/api/v1/salas", "/api/v1/salas/*/unirse");
		registry.addInterceptor(adminInterceptor)
				.addPathPatterns("/api/v1/admin/**", "/api/v1/auth/yo", "/api/v1/auth/logout");
	}

}
