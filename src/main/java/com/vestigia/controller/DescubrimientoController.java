package com.vestigia.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vestigia.config.SeguridadConfig;
import com.vestigia.config.TokenInterceptor;
import com.vestigia.controller.ApiExceptionHandler.ErrorBody;
import com.vestigia.dto.RegistrarDescubrimientoRequest;
import com.vestigia.dto.Sala;
import com.vestigia.service.DescubrimientoService;
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
@RequestMapping("/api/v1/salas/{codigo}/descubrimientos")
@Tag(name = "Descubrimientos", description = "Lo que aparece en la libreta: piezas, banderas del juego, etc.")
public class DescubrimientoController {

	private final DescubrimientoService descubrimientoService;

	public DescubrimientoController(DescubrimientoService descubrimientoService) {
		this.descubrimientoService = descubrimientoService;
	}

	@PostMapping
	@Operation(summary = "Registra un descubrimiento (juego o app)", description = "Idempotente: repetir la clave "
			+ "devuelve 200 con el descubrimiento que ya existía, sin cambiar sus datos. Tras uno nuevo, el servidor "
			+ "recalcula qué puzzles pasan a disponible.", security = @SecurityRequirement(name = SeguridadConfig.ESQUEMA))
	@Parameter(name = "codigo", in = ParameterIn.PATH, required = true, description = "Código de la sala",
			schema = @Schema(type = "string"))
	@ApiResponse(responseCode = "201", description = "Descubrimiento nuevo")
	@ApiResponse(responseCode = "200", description = "La clave ya existía; se devuelve tal como estaba")
	@ApiResponse(responseCode = "400", description = "Clave con formato inválido o datos que no son un objeto JSON",
			content = @Content(schema = @Schema(implementation = ErrorBody.class)))
	@ApiResponse(responseCode = "401", description = "Falta el token, es inválido o la sala ya terminó",
			content = @Content(schema = @Schema(implementation = ErrorBody.class)))
	@ApiResponse(responseCode = "403", description = "El token es de otra sala o la clave la genera el servidor",
			content = @Content(schema = @Schema(implementation = ErrorBody.class)))
	public ResponseEntity<Sala.Descubrimiento> registrar(@Valid @RequestBody RegistrarDescubrimientoRequest request,
			@Parameter(hidden = true) @RequestAttribute(TokenInterceptor.CREDENCIAL) SalaService.Credencial credencial) {
		DescubrimientoService.Registro registro = descubrimientoService.registrar(credencial, request.clave(),
				request.datos());
		return ResponseEntity.status(registro.nuevo() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(registro.descubrimiento());
	}

}
