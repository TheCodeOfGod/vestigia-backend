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
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
			+ "booleano true y exactamente una clave. Un aviso solo mueve el puzzle hacia adelante; repetir uno que "
			+ "ya no lo mueve responde 200 con el estado actual. Al resolverse, el servidor crea el descubrimiento "
			+ "resultado (coordenada_a, coordenada_b, observatorio_visible o magnetar_localizado) y habilita los "
			+ "puzzles que dependen de él. El servidor no mide ni valida números.",
			security = @SecurityRequirement(name = SeguridadConfig.ESQUEMA),
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReportarPuzzleRequest.class),
							examples = {
									@ExampleObject(name = "photo, lookout u observatory completado",
											value = EjemplosOpenApi.AVISO_COMPLETADA),
									@ExampleObject(name = "photo, lookout u observatory abierto",
											value = EjemplosOpenApi.AVISO_INICIADO),
									@ExampleObject(name = "radio sintonizada", value = EjemplosOpenApi.AVISO_SINTONIZADA) })))
	@Parameter(name = "codigo", in = ParameterIn.PATH, required = true, description = "Código de la sala",
			example = "BACALAR", schema = @Schema(type = "string"))
	@ApiResponse(responseCode = "200", description = "Estado del puzzle tras el aviso",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = Sala.Puzzle.class),
					examples = @ExampleObject(value = EjemplosOpenApi.PUZZLE)))
	@ApiResponse(responseCode = "400", description = "Puzzle desconocido, o datos que no son un aviso válido para él",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class), examples = {
					@ExampleObject(name = "Aviso inválido", value = EjemplosOpenApi.ERROR_400_VALIDACION),
					@ExampleObject(name = "Puzzle desconocido", value = EjemplosOpenApi.ERROR_400_PUZZLE) }))
	@ApiResponse(responseCode = "401", description = "Falta el token, es inválido o la sala ya terminó o venció",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_401)))
	@ApiResponse(responseCode = "403", description = "El token es de otra sala",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_403_OTRA_SALA)))
	@ApiResponse(responseCode = "409", description = "El puzzle está bloqueado (codigo: puzzle_bloqueado)",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_409_BLOQUEADO)))
	public Sala.Puzzle reportar(
			@Parameter(description = "Id del puzzle", example = "photo",
					schema = @Schema(allowableValues = { "radio", "photo", "lookout", "observatory" }))
			@PathVariable String puzzle, @Valid @RequestBody ReportarPuzzleRequest request,
			@Parameter(hidden = true) @RequestAttribute(TokenInterceptor.CREDENCIAL)
			SalaService.Credencial credencial) {
		return puzzleService.reportar(credencial, puzzle, request.datos());
	}

}
