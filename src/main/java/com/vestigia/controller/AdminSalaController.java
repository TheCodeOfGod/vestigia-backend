package com.vestigia.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.vestigia.config.AdminInterceptor;
import com.vestigia.config.SeguridadConfig;
import com.vestigia.controller.ApiExceptionHandler.ErrorBody;
import com.vestigia.dto.SalaAdmin;
import com.vestigia.dto.SalaResumen;
import com.vestigia.model.SalaModel;
import com.vestigia.service.AdminSalaService;
import com.vestigia.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/admin/salas")
@Tag(name = "Salas (admin)", description = "Ver todas las salas, vivas o terminadas, y cerrar una a la fuerza (solo admin)")
public class AdminSalaController {

	private static final String NO_ADMIN = "La sesión no tiene el rol admin";

	private static final String NO_SESION = "Falta el token, es inválido o la sesión venció";

	private final AdminSalaService adminSalaService;

	public AdminSalaController(AdminSalaService adminSalaService) {
		this.adminSalaService = adminSalaService;
	}

	@GetMapping
	@Operation(summary = "Lista las salas (admin)", description = "De la más nueva a la más antigua, vivas y terminadas. "
			+ "Un mismo código puede aparecer varias veces: la sala vigente y las que ya terminaron. Usa el id para "
			+ "pedir el detalle. Sin el parámetro limite vienen 100; el máximo es 500.",
			security = @SecurityRequirement(name = SeguridadConfig.ESQUEMA_ADMIN))
	@ApiResponse(responseCode = "200", description = "Resumen de cada sala",
			content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = SalaResumen.class)),
					examples = @ExampleObject(value = EjemplosOpenApi.SALAS_ADMIN)))
	@ApiResponse(responseCode = "400", description = "estado no es esperando_app, activa, finalizada ni expirada, o limite no es un número",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_400_ESTADO)))
	@ApiResponse(responseCode = "401", description = NO_SESION,
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_401_ADMIN)))
	@ApiResponse(responseCode = "403", description = NO_ADMIN,
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_403_ADMIN)))
	public List<SalaResumen> listar(
			@Parameter(description = "Solo las salas en ese estado", example = "activa") @RequestParam(required = false) SalaModel.Estado estado,
			@Parameter(description = "Cuántas salas devolver, de 1 a 500", example = "100") @RequestParam(required = false) Integer limite) {
		return adminSalaService.listar(estado, limite);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Detalle de una sala (admin)", description = "Funciona con salas vivas y terminadas. Trae la "
			+ "escena, los puzzles, los descubrimientos, qué dispositivos están conectados y la bitácora completa. "
			+ "No devuelve tokens ni hashes.", security = @SecurityRequirement(name = SeguridadConfig.ESQUEMA_ADMIN))
	@ApiResponse(responseCode = "200", description = "La sala",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = SalaAdmin.class),
					examples = @ExampleObject(value = EjemplosOpenApi.SALA_ADMIN)))
	@ApiResponse(responseCode = "400", description = "El id no es un UUID",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_400_ESTADO)))
	@ApiResponse(responseCode = "401", description = NO_SESION,
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_401_ADMIN)))
	@ApiResponse(responseCode = "403", description = NO_ADMIN,
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_403_ADMIN)))
	@ApiResponse(responseCode = "404", description = "No existe esa sala",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_404_SALA)))
	public SalaAdmin detalle(@PathVariable UUID id) {
		return adminSalaService.detalle(id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Cierra una sala a la fuerza (admin)", description = "Si la sala está viva pasa a finalizada, "
			+ "su código queda libre y los tokens del juego y de la app dejan de servir (401). Los dos dispositivos "
			+ "reciben sala.cerrada por WebSocket. Si la sala ya había terminado no cambia nada y también responde 204.",
			security = @SecurityRequirement(name = SeguridadConfig.ESQUEMA_ADMIN))
	@ApiResponse(responseCode = "204", description = "La sala quedó cerrada, sin cuerpo")
	@ApiResponse(responseCode = "400", description = "El id no es un UUID",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_400_ESTADO)))
	@ApiResponse(responseCode = "401", description = NO_SESION,
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_401_ADMIN)))
	@ApiResponse(responseCode = "403", description = NO_ADMIN,
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_403_ADMIN)))
	@ApiResponse(responseCode = "404", description = "No existe esa sala",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_404_SALA)))
	public void cerrar(@PathVariable UUID id,
			@Parameter(hidden = true) @RequestAttribute(AdminInterceptor.CREDENCIAL) AuthService.CredencialAdmin credencial) {
		adminSalaService.cerrar(id, credencial.usuarioId());
	}

}
