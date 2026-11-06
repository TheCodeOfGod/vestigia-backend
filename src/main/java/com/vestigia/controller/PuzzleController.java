package com.vestigia.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vestigia.config.SeguridadConfig;
import com.vestigia.config.TokenInterceptor;
import com.vestigia.controller.ApiExceptionHandler.ErrorBody;
import com.vestigia.dto.ReportarPuzzleRequest;
import com.vestigia.dto.Sala;
import com.vestigia.service.PuzzleService;
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
@RequestMapping("/api/v1/salas/{codigo}/puzzles")
@Tag(name = "Puzzles", description = "Avisos de que un puzzle empezó o se completó")
public class PuzzleController {

	private final PuzzleService puzzleService;

	public PuzzleController(PuzzleService puzzleService) {
		this.puzzleService = puzzleService;
	}

	@PutMapping("/{puzzle}")
	@Operation(summary = "Reporta un puzzle (juego o app)", description = "radio acepta {\"sintonizada\": true}; "
			+ "photo, lookout y observatory aceptan {\"iniciado\": true} o {\"completada\": true}. Solo vale el "
			+ "booleano true. Repetir un aviso que ya no mueve el puzzle responde 200 con el estado actual. Al "
			+ "resolverse, el servidor crea el descubrimiento resultado y habilita los puzzles que dependen de él.",
			security = @SecurityRequirement(name = SeguridadConfig.ESQUEMA))
	@Parameter(name = "codigo", in = ParameterIn.PATH, required = true, description = "Código de la sala",
			schema = @Schema(type = "string"))
	@ApiResponse(responseCode = "200", description = "Estado del puzzle tras el aviso")
	@ApiResponse(responseCode = "400", description = "Puzzle desconocido, o datos que no son un aviso válido para él",
			content = @Content(schema = @Schema(implementation = ErrorBody.class)))
	@ApiResponse(responseCode = "401", description = "Falta el token, es inválido o la sala ya terminó",
			content = @Content(schema = @Schema(implementation = ErrorBody.class)))
	@ApiResponse(responseCode = "403", description = "El token es de otra sala",
			content = @Content(schema = @Schema(implementation = ErrorBody.class)))
	@ApiResponse(responseCode = "409", description = "El puzzle está bloqueado (codigo: puzzle_bloqueado)",
			content = @Content(schema = @Schema(implementation = ErrorBody.class)))
	public Sala.Puzzle reportar(
			@Parameter(description = "Id del puzzle",
					schema = @Schema(allowableValues = { "radio", "photo", "lookout", "observatory" }))
			@PathVariable String puzzle, @Valid @RequestBody ReportarPuzzleRequest request,
			@Parameter(hidden = true) @RequestAttribute(TokenInterceptor.CREDENCIAL) SalaService.Credencial credencial) {
		return puzzleService.reportar(credencial, puzzle, request.datos());
	}

}
