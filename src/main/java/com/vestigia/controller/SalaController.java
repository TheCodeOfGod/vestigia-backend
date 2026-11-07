package com.vestigia.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.vestigia.config.SeguridadConfig;
import com.vestigia.config.SoloJuego;
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
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
			+ "se pasa a mayúsculas. La sala nace en esperando_app, con la escena introduccion y los cuatro puzzles "
			+ "bloqueados. Devuelve el token del juego una sola vez: guárdalo. Ante un 409, vuelve a pedir "
			+ "GET /pueblos?disponibles=true.",
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = CrearSalaRequest.class),
							examples = @ExampleObject(name = "Reclamar BACALAR", value = EjemplosOpenApi.CREAR_SALA))))
	@ApiResponse(responseCode = "201", description = "Sala creada en esperando_app",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = SalaCreada.class),
					examples = @ExampleObject(value = EjemplosOpenApi.SALA_CREADA)))
	@ApiResponse(responseCode = "400", description = "Código vacío, que no es texto o fuera del catálogo",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_400_CATALOGO)))
	@ApiResponse(responseCode = "409", description = "El pueblo ya tiene una sala en curso",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_409_OCUPADO)))
	public SalaCreada crear(@Valid @RequestBody CrearSalaRequest request) {
		return salaService.crear(request.codigo());
	}

	@PostMapping("/{codigo}/unirse")
	@Operation(summary = "Enlaza la app a la sala (app)", description = "Sin token y sin cuerpo. El código se recorta y "
			+ "se pasa a mayúsculas. La sala pasa a activa. Devuelve el token de la app una sola vez: guárdalo. Solo "
			+ "cabe una app por sala.")
	@ApiResponse(responseCode = "200", description = "App enlazada",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = AppUnida.class),
					examples = @ExampleObject(value = EjemplosOpenApi.APP_UNIDA)))
	@ApiResponse(responseCode = "404", description = "No hay sala en curso con ese código (nunca existió, terminó o "
			+ "venció)", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_404)))
	@ApiResponse(responseCode = "409", description = "La sala ya tiene una app enlazada",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_409_APP)))
	public AppUnida unirse(@Parameter(description = "Código de la sala, el pueblo que eligió el jugador",
			example = "BACALAR") @PathVariable String codigo) {
		return salaService.unirse(codigo);
	}

	@GetMapping("/{codigo}/estado")
	@Operation(summary = "Estado completo de la sala (juego o app)", description = "Pide el token de esa sala. Trae la "
			+ "escena, los cuatro puzzles en orden, los descubrimientos en orden de llegada y qué dispositivos tienen "
			+ "el WebSocket conectado. Pídelo al arrancar y después de cada reconexión del WebSocket. No renueva la "
			+ "expiración.", security = @SecurityRequirement(name = SeguridadConfig.ESQUEMA))
	@Parameter(name = "codigo", in = ParameterIn.PATH, required = true, description = "Código de la sala",
			example = "BACALAR", schema = @Schema(type = "string"))
	@ApiResponse(responseCode = "200", description = "Estado actual",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = Sala.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ESTADO)))
	@ApiResponse(responseCode = "401", description = "Falta el token, es inválido o la sala ya terminó o venció",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_401)))
	@ApiResponse(responseCode = "403", description = "El token es de otra sala",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_403_OTRA_SALA)))
	public Sala estado(@Parameter(hidden = true) @RequestAttribute(TokenInterceptor.CREDENCIAL)
	SalaService.Credencial credencial) {
		return salaService.estado(credencial.salaId());
	}

	@PutMapping("/{codigo}/escena")
	@Operation(summary = "Cambia la escena actual (solo juego)", description = "Escenas: introduccion, pueblo, faro, "
			+ "observatorio, desenlace, en minúsculas. Repetir la escena actual responde 200 sin cambios. La app "
			+ "recibe escena.cambiada por WebSocket. El token de la app recibe 403 antes de que se lea el cuerpo.",
			security = @SecurityRequirement(name = SeguridadConfig.ESQUEMA),
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = CambiarEscenaRequest.class),
							examples = @ExampleObject(name = "Llegar al faro", value = EjemplosOpenApi.CAMBIAR_ESCENA))))
	@Parameter(name = "codigo", in = ParameterIn.PATH, required = true, description = "Código de la sala",
			example = "BACALAR", schema = @Schema(type = "string"))
	@ApiResponse(responseCode = "200", description = "Escena actual",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = EscenaActual.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ESCENA)))
	@ApiResponse(responseCode = "400", description = "Falta la escena, no es texto o no es una de las válidas",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_400_ESCENA)))
	@ApiResponse(responseCode = "401", description = "Falta el token, es inválido o la sala ya terminó o venció",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_401)))
	@ApiResponse(responseCode = "403", description = "El token es de otra sala o es el de la app",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_403_ESCENA)))
	@SoloJuego(SalaService.SOLO_JUEGO_ESCENA)
	public EscenaActual cambiarEscena(@Valid @RequestBody CambiarEscenaRequest request,
			@Parameter(hidden = true) @RequestAttribute(TokenInterceptor.CREDENCIAL) SalaService.Credencial credencial) {
		return salaService.cambiarEscena(credencial, request.escena());
	}

	@DeleteMapping("/{codigo}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Cierra la sala (solo juego)", description = "La sala pasa a finalizada y se conserva como "
			+ "historial. El código queda libre y los tokens del juego y de la app dejan de servir (401). Los dos "
			+ "dispositivos reciben sala.cerrada por WebSocket y el servidor cierra sus conexiones con 1000.",
			security = @SecurityRequirement(name = SeguridadConfig.ESQUEMA))
	@Parameter(name = "codigo", in = ParameterIn.PATH, required = true, description = "Código de la sala",
			example = "BACALAR", schema = @Schema(type = "string"))
	@ApiResponse(responseCode = "204", description = "Sala cerrada, sin cuerpo")
	@ApiResponse(responseCode = "401", description = "Falta el token, es inválido o la sala ya terminó o venció",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_401)))
	@ApiResponse(responseCode = "403", description = "El token es de otra sala o es el de la app",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_403_CIERRE)))
	@SoloJuego(SalaService.SOLO_JUEGO_CIERRE)
	public void finalizar(@Parameter(hidden = true) @RequestAttribute(TokenInterceptor.CREDENCIAL)
	SalaService.Credencial credencial) {
		salaService.finalizar(credencial);
	}

}
