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
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
			+ "recalcula qué puzzles pasan a disponible y avisa por WebSocket (descubrimiento.nuevo y "
			+ "puzzle.actualizado). clave: ^[a-z0-9_]{1,40}$. datos: objeto JSON opcional de hasta 4096 bytes. Las "
			+ "claves coordenada_a, coordenada_b, observatorio_visible y magnetar_localizado las genera el servidor. "
			+ "Las claves pescador_dialogo_completo, pieza_1, pieza_2, pieza_3, lookout_enabled y observatory_enabled "
			+ "solo las registra el juego: con el token de la app dan 403 con codigo clave_solo_juego.",
			security = @SecurityRequirement(name = SeguridadConfig.ESQUEMA),
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = RegistrarDescubrimientoRequest.class),
							examples = {
									@ExampleObject(name = "Pieza con datos", value = EjemplosOpenApi.REGISTRAR_DESCUBRIMIENTO),
									@ExampleObject(name = "Bandera del juego", value = EjemplosOpenApi.BANDERA_JUEGO) })))
	@Parameter(name = "codigo", in = ParameterIn.PATH, required = true, description = "Código de la sala",
			example = "BACALAR", schema = @Schema(type = "string"))
	@ApiResponse(responseCode = "201", description = "Descubrimiento nuevo",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = Sala.Descubrimiento.class),
					examples = @ExampleObject(value = EjemplosOpenApi.DESCUBRIMIENTO)))
	@ApiResponse(responseCode = "200", description = "La clave ya existía; se devuelve tal como estaba",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = Sala.Descubrimiento.class),
					examples = @ExampleObject(value = EjemplosOpenApi.DESCUBRIMIENTO)))
	@ApiResponse(responseCode = "400", description = "Clave con formato inválido, datos que no son un objeto JSON o "
			+ "de más de 4096 bytes", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_400_JSON)))
	@ApiResponse(responseCode = "401", description = "Falta el token, es inválido o la sala ya terminó o venció",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_401)))
	@ApiResponse(responseCode = "403", description = "El token es de otra sala, la clave la genera el servidor, o la "
			+ "clave es del juego y la manda la app (codigo: clave_solo_juego)",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = {
							@ExampleObject(name = "Clave del servidor", value = EjemplosOpenApi.ERROR_403_CLAVE_SERVIDOR),
							@ExampleObject(name = "Clave del juego enviada por la app",
									value = EjemplosOpenApi.ERROR_403_CLAVE_SOLO_JUEGO) }))
	public ResponseEntity<Sala.Descubrimiento> registrar(@Valid @RequestBody RegistrarDescubrimientoRequest request,
			@Parameter(hidden = true) @RequestAttribute(TokenInterceptor.CREDENCIAL) SalaService.Credencial credencial) {
		DescubrimientoService.Registro registro = descubrimientoService.registrar(credencial, request.clave(),
				request.datos());
		return ResponseEntity.status(registro.nuevo() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(registro.descubrimiento());
	}

}
