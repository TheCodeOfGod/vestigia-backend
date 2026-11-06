package com.vestigia.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.vestigia.config.SeguridadConfig;
import com.vestigia.config.TokenInterceptor;
import com.vestigia.controller.ApiExceptionHandler.ErrorBody;
import com.vestigia.dto.AppUnida;
import com.vestigia.dto.CambiarEscenaRequest;
import com.vestigia.dto.CrearSalaRequest;
import com.vestigia.dto.EscenaActual;
import com.vestigia.dto.Sala;
import com.vestigia.dto.SalaCreada;
import com.vestigia.service.SalaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/salas")
@Tag(name = "Salas", description = "Crear, unirse, consultar, cambiar de escena y cerrar una sala")
public class SalaController {

	private final SalaService salaService;

	public SalaController(SalaService salaService) {
		this.salaService = salaService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Reclama un pueblo y crea la sala (juego)", description = "Sin token. El código se recorta y "
			+ "se pasa a mayúsculas. Devuelve el token del juego una sola vez: guárdalo.")
	@ApiResponse(responseCode = "201", description = "Sala creada en esperando_app")
	@ApiResponse(responseCode = "400", description = "Código vacío o fuera del catálogo",
			content = @Content(schema = @Schema(implementation = ErrorBody.class)))
	@ApiResponse(responseCode = "409", description = "El pueblo ya tiene una sala en curso",
			content = @Content(schema = @Schema(implementation = ErrorBody.class)))
	public SalaCreada crear(@Valid @RequestBody CrearSalaRequest request) {
		return salaService.crear(request.codigo());
	}

	@PostMapping("/{codigo}/unirse")
	@Operation(summary = "Enlaza la app a la sala (app)", description = "Sin token. La sala pasa a activa. Devuelve el "
			+ "token de la app una sola vez: guárdalo.")
	@ApiResponse(responseCode = "200", description = "App enlazada")
	@ApiResponse(responseCode = "404", description = "No hay sala en curso con ese código",
			content = @Content(schema = @Schema(implementation = ErrorBody.class)))
	@ApiResponse(responseCode = "409", description = "La sala ya tiene una app enlazada",
			content = @Content(schema = @Schema(implementation = ErrorBody.class)))
	public AppUnida unirse(@PathVariable String codigo) {
		return salaService.unirse(codigo);
	}

	@GetMapping("/{codigo}/estado")
	@Operation(summary = "Estado completo de la sala (juego o app)",
			description = "Pide el token de esa sala. Úsalo al reconectar para ponerte al día.",
			security = @SecurityRequirement(name = SeguridadConfig.ESQUEMA))
	@Parameter(name = "codigo", in = ParameterIn.PATH, required = true, description = "Código de la sala",
			schema = @Schema(type = "string"))
	@ApiResponse(responseCode = "200", description = "Estado actual")
	@ApiResponse(responseCode = "401", description = "Falta el token, es inválido o la sala ya terminó",
			content = @Content(schema = @Schema(implementation = ErrorBody.class)))
	@ApiResponse(responseCode = "403", description = "El token es de otra sala",
			content = @Content(schema = @Schema(implementation = ErrorBody.class)))
	public Sala estado(@Parameter(hidden = true) @RequestAttribute(TokenInterceptor.CREDENCIAL)
	SalaService.Credencial credencial) {
		return salaService.estado(credencial.salaId());
	}

	@PutMapping("/{codigo}/escena")
	@Operation(summary = "Cambia la escena actual (solo juego)", description = "Escenas: introduccion, pueblo, faro, "
			+ "observatorio, desenlace. Repetir la escena actual responde 200 sin cambios.",
			security = @SecurityRequirement(name = SeguridadConfig.ESQUEMA))
	@Parameter(name = "codigo", in = ParameterIn.PATH, required = true, description = "Código de la sala",
			schema = @Schema(type = "string"))
	@ApiResponse(responseCode = "200", description = "Escena actual")
	@ApiResponse(responseCode = "400", description = "Falta la escena o no es una de las válidas",
			content = @Content(schema = @Schema(implementation = ErrorBody.class)))
	@ApiResponse(responseCode = "401", description = "Falta el token, es inválido o la sala ya terminó",
			content = @Content(schema = @Schema(implementation = ErrorBody.class)))
	@ApiResponse(responseCode = "403", description = "El token es de otra sala o es el de la app",
			content = @Content(schema = @Schema(implementation = ErrorBody.class)))
	public EscenaActual cambiarEscena(@Valid @RequestBody CambiarEscenaRequest request,
			@Parameter(hidden = true) @RequestAttribute(TokenInterceptor.CREDENCIAL) SalaService.Credencial credencial) {
		return salaService.cambiarEscena(credencial, request.escena());
	}

}
