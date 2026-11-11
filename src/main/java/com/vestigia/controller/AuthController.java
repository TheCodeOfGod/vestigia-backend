package com.vestigia.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.vestigia.config.AdminInterceptor;
import com.vestigia.config.SeguridadConfig;
import com.vestigia.controller.ApiExceptionHandler.ErrorBody;
import com.vestigia.dto.LoginRequest;
import com.vestigia.dto.SesionIniciada;
import com.vestigia.dto.Usuario;
import com.vestigia.service.AuthService;
import com.vestigia.service.UsuarioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Sesión", description = "Inicio y cierre de sesión de los usuarios de administración")
public class AuthController {

	private final AuthService authService;

	private final UsuarioService usuarioService;

	public AuthController(AuthService authService, UsuarioService usuarioService) {
		this.authService = authService;
		this.usuarioService = usuarioService;
	}

	@PostMapping("/login")
	@Operation(summary = "Inicia sesión (público)", description = "Sin token. Devuelve el token de la sesión, que dura "
			+ "8 horas y no se renueva. El correo se recorta y se pasa a minúsculas. Un correo desconocido, una "
			+ "contraseña incorrecta y un usuario desactivado dan el mismo 401. Tras 5 intentos fallidos con el mismo "
			+ "correo en 15 minutos responde 429 con codigo demasiados_intentos.",
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginRequest.class),
							examples = @ExampleObject(name = "Iniciar sesión", value = EjemplosOpenApi.LOGIN))))
	@ApiResponse(responseCode = "200", description = "Sesión abierta",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = SesionIniciada.class),
					examples = @ExampleObject(value = EjemplosOpenApi.SESION)))
	@ApiResponse(responseCode = "401", description = "Correo o contraseña incorrectos",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_401_LOGIN)))
	@ApiResponse(responseCode = "429", description = "Demasiados intentos fallidos con ese correo",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_429_LOGIN)))
	public SesionIniciada login(@Valid @RequestBody LoginRequest request) {
		return authService.iniciarSesion(request.correo(), request.password());
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Cierra la sesión (admin)", description = "Borra la sesión del token. Después ese token da 401.",
			security = @SecurityRequirement(name = SeguridadConfig.ESQUEMA_ADMIN))
	@ApiResponse(responseCode = "204", description = "Sesión cerrada, sin cuerpo")
	@ApiResponse(responseCode = "401", description = "Falta el token, es inválido o la sesión venció",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_401_ADMIN)))
	@ApiResponse(responseCode = "403", description = "La sesión no tiene el rol necesario",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_403_ADMIN)))
	public void logout(@Parameter(hidden = true) @RequestAttribute(AdminInterceptor.CREDENCIAL)
	AuthService.CredencialAdmin credencial) {
		authService.cerrarSesion(credencial);
	}

	@GetMapping("/yo")
	@Operation(summary = "Usuario de la sesión (admin)", description = "Sirve para comprobar, al abrir la PWA, que el "
			+ "token guardado sigue vigente.", security = @SecurityRequirement(name = SeguridadConfig.ESQUEMA_ADMIN))
	@ApiResponse(responseCode = "200", description = "El usuario dueño del token",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = Usuario.class),
					examples = @ExampleObject(value = EjemplosOpenApi.USUARIO)))
	@ApiResponse(responseCode = "401", description = "Falta el token, es inválido o la sesión venció",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_401_ADMIN)))
	@ApiResponse(responseCode = "403", description = "La sesión no tiene el rol necesario",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorBody.class),
					examples = @ExampleObject(value = EjemplosOpenApi.ERROR_403_ADMIN)))
	public Usuario yo(@Parameter(hidden = true) @RequestAttribute(AdminInterceptor.CREDENCIAL)
	AuthService.CredencialAdmin credencial) {
		return usuarioService.obtener(credencial.usuarioId());
	}

}
