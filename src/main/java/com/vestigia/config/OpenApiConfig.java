package com.vestigia.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

/** Datos generales del documento OpenAPI de /v3/api-docs y de Swagger UI. */
@Configuration
@OpenAPIDefinition(info = @Info(title = "API de Vestigia", version = "v1",
		description = "Backend que enlaza el juego (Unity) con la app móvil (Flutter). Todas las rutas cuelgan de "
				+ "/api/v1. JSON en snake_case y fechas en UTC con formato ISO-8601. Los errores tienen siempre el "
				+ "mismo cuerpo (timestamp, status, error, codigo, message, path, details); codigo vale null salvo en "
				+ "los errores que definen uno, como puzzle_bloqueado, clave_solo_juego o puzzle_solo_app. Las rutas con candado piden "
				+ "el token de la sala en Authorization: Bearer <token>. El tiempo real va por el WebSocket de /ws, que OpenAPI no "
				+ "describe: ver docs/tiempo-real.md."))
public class OpenApiConfig {

}
