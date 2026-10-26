package com.vestigia.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.vestigia.controller.ApiExceptionHandler.ErrorBody;
import com.vestigia.dto.CrearSalaRequest;
import com.vestigia.dto.SalaCreada;
import com.vestigia.service.SalaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/salas")
@Tag(name = "Salas", description = "Crear, unirse y consultar una sala")
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

}
