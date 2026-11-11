package com.vestigia.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.vestigia.config.SeguridadConfig;
import com.vestigia.controller.ApiExceptionHandler.ErrorBody;
import com.vestigia.dto.ActualizarUsuarioRequest;
import com.vestigia.dto.CrearUsuarioRequest;
import com.vestigia.dto.Usuario;
import com.vestigia.service.UsuarioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/usuarios")
@Tag(name = "Usuarios", description = "Administración de los usuarios que pueden iniciar sesión (solo admin)")
public class UsuarioController {

	private static final String NO_ADMIN = "La sesión no tiene el rol admin";

	private static final String NO_SESION = "Falta el token, es inválido o la sesión venció";

	private final UsuarioService usuarioService;

	public UsuarioController(UsuarioService usuarioService) {
		this.usuarioService = usuarioService;
	}

	@GetMapping
	@Operation(summary = "Lista los usuarios (admin)", description = "Del más antiguo al más nuevo. Nunca trae "
			+ "contraseñas.", security = @SecurityRequirement(name = SeguridadConfig.ESQUEMA_ADMIN))
	@ApiResponse(responseCode = "200", description = "Todos los usuarios",
			content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Usuario.class)),
					examples = @ExampleObject(value = EjemplosOpenApi.USUARIOS)))
	@ApiResponse(responseCode = "401", description = NO_SESION,
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_401_ADMIN)))
	@ApiResponse(responseCode = "403", description = NO_ADMIN,
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_403_ADMIN)))
	public List<Usuario> listar() {
		return usuarioService.listar();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Crea un usuario (admin)", description = "El correo es único sin importar mayúsculas. La "
			+ "contraseña se guarda con BCrypt y no se puede volver a leer.",
			security = @SecurityRequirement(name = SeguridadConfig.ESQUEMA_ADMIN),
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = CrearUsuarioRequest.class),
							examples = @ExampleObject(name = "Nuevo admin", value = EjemplosOpenApi.CREAR_USUARIO))))
	@ApiResponse(responseCode = "201", description = "Usuario creado",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = Usuario.class),
					examples = @ExampleObject(value = EjemplosOpenApi.USUARIO)))
	@ApiResponse(responseCode = "400", description = "Faltan campos o alguno es inválido",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_400_USUARIO)))
	@ApiResponse(responseCode = "401", description = NO_SESION,
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_401_ADMIN)))
	@ApiResponse(responseCode = "403", description = NO_ADMIN,
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_403_ADMIN)))
	@ApiResponse(responseCode = "409", description = "Ya hay un usuario con ese correo (codigo correo_duplicado)",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_409_CORREO)))
	public Usuario crear(@Valid @RequestBody CrearUsuarioRequest request) {
		return usuarioService.crear(request);
	}

	@PutMapping("/{id}")
	@Operation(summary = "Cambia un usuario (admin)", description = "Cada campo es opcional: nombre, rol, activo y "
			+ "password. Desactivarlo o cambiarle la contraseña cierra sus sesiones. El correo no cambia. No deja "
			+ "desactivar ni quitar el rol al último administrador activo (409 con codigo ultimo_admin).",
			security = @SecurityRequirement(name = SeguridadConfig.ESQUEMA_ADMIN),
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = ActualizarUsuarioRequest.class),
							examples = @ExampleObject(name = "Desactivar", value = EjemplosOpenApi.ACTUALIZAR_USUARIO))))
	@ApiResponse(responseCode = "200", description = "El usuario ya cambiado",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = Usuario.class),
					examples = @ExampleObject(value = EjemplosOpenApi.USUARIO)))
	@ApiResponse(responseCode = "400", description = "Algún campo es inválido",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_400_USUARIO)))
	@ApiResponse(responseCode = "401", description = NO_SESION,
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_401_ADMIN)))
	@ApiResponse(responseCode = "403", description = NO_ADMIN,
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_403_ADMIN)))
	@ApiResponse(responseCode = "404", description = "No existe ese usuario",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_404_USUARIO)))
	@ApiResponse(responseCode = "409", description = "Dejaría al sistema sin administradores activos",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_409_ULTIMO_ADMIN)))
	public Usuario actualizar(@PathVariable UUID id, @Valid @RequestBody ActualizarUsuarioRequest request) {
		return usuarioService.actualizar(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Elimina un usuario (admin)", description = "También borra sus sesiones. No deja eliminar al "
			+ "último administrador activo (409 con codigo ultimo_admin).",
			security = @SecurityRequirement(name = SeguridadConfig.ESQUEMA_ADMIN))
	@ApiResponse(responseCode = "204", description = "Usuario eliminado, sin cuerpo")
	@ApiResponse(responseCode = "401", description = NO_SESION,
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_401_ADMIN)))
	@ApiResponse(responseCode = "403", description = NO_ADMIN,
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_403_ADMIN)))
	@ApiResponse(responseCode = "404", description = "No existe ese usuario",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_404_USUARIO)))
	@ApiResponse(responseCode = "409", description = "Dejaría al sistema sin administradores activos",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_409_ULTIMO_ADMIN)))
	public void eliminar(@PathVariable UUID id) {
		usuarioService.eliminar(id);
	}

}
