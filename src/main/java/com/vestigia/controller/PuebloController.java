package com.vestigia.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vestigia.controller.ApiExceptionHandler.ErrorBody;
import com.vestigia.dto.Pueblo;
import com.vestigia.service.SalaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/pueblos")
@Tag(name = "Pueblos", description = "Catálogo de nombres de sala")
public class PuebloController {

	private final SalaService salaService;

	public PuebloController(SalaService salaService) {
		this.salaService = salaService;
	}

	@GetMapping
	@Operation(summary = "Lista el catálogo de pueblos (público)", description = "Sin token. Ordenado alfabéticamente. "
			+ "Un pueblo está ocupado mientras tiene una sala en esperando_app o activa. Sin el parámetro vienen los "
			+ "101 pueblos, y los ocupados traen libre: false.")
	@ApiResponse(responseCode = "200", description = "Pueblos del catálogo",
			content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Pueblo.class)),
					examples = @ExampleObject(value = EjemplosOpenApi.PUEBLOS)))
	@ApiResponse(responseCode = "400", description = "disponibles no es true ni false",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_400_PUEBLOS)))
	public List<Pueblo> listar(@Parameter(description = "true: solo los pueblos libres. false o ausente: todos",
			example = "true") @RequestParam(defaultValue = "false") boolean disponibles) {
		return salaService.listarPueblos(disponibles);
	}

}
