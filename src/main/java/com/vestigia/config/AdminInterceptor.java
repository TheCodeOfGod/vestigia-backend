package com.vestigia.config;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import com.vestigia.model.UsuarioModel;
import com.vestigia.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Exige una sesión de administración vigente ({@code Authorization: Bearer <token>}) en las rutas de /admin y en las
 * de sesión. Los tokens de sala no sirven aquí. Deja la credencial en el atributo {@link #CREDENCIAL}.
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

	public static final String CREDENCIAL = "vestigia.credencial_admin";

	private final AuthService authService;

	public AdminInterceptor(AuthService authService) {
		this.authService = authService;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!(handler instanceof HandlerMethod)) {
			return true;
		}
		String token = Bearer.token(request.getHeader(HttpHeaders.AUTHORIZATION));
		AuthService.CredencialAdmin credencial = authService.autenticar(token)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
						"Sesión inválida o vencida: vuelve a iniciar sesión"));
		if (request.getRequestURI().startsWith("/api/v1/admin/") && credencial.rol() != UsuarioModel.Rol.admin) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se necesita el rol admin");
		}
		request.setAttribute(CREDENCIAL, credencial);
		return true;
	}

}
