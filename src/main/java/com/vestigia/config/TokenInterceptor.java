package com.vestigia.config;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import com.vestigia.service.SalaService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Exige {@code Authorization: Bearer <token>} de una sala viva, que el {@code {codigo}} de la ruta sea el de esa sala y,
 * en los endpoints con {@link SoloJuego}, que el token sea el del juego. Deja la credencial en el atributo
 * {@link #CREDENCIAL} de la petición.
 */
@Component
public class TokenInterceptor implements HandlerInterceptor {

	public static final String CREDENCIAL = "vestigia.credencial";

	private final SalaService salaService;

	public TokenInterceptor(SalaService salaService) {
		this.salaService = salaService;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		// Rutas sin controlador (404, recursos estáticos) siguen su curso normal.
		if (!(handler instanceof HandlerMethod metodo)) {
			return true;
		}
		String token = Bearer.token(request.getHeader(HttpHeaders.AUTHORIZATION));
		SalaService.Credencial credencial = salaService.autenticar(token)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
						"Token inválido o de una sala que ya terminó"));
		String codigo = codigoDeLaRuta(request);
		if (codigo != null && !SalaService.normalizarCodigo(codigo).equals(credencial.codigo())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El token no pertenece a esta sala");
		}
		SoloJuego soloJuego = metodo.getMethodAnnotation(SoloJuego.class);
		if (soloJuego != null && credencial.rol() != SalaService.Rol.juego) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, soloJuego.value());
		}
		request.setAttribute(CREDENCIAL, credencial);
		return true;
	}

	@SuppressWarnings("unchecked")
	private static String codigoDeLaRuta(HttpServletRequest request) {
		Map<String, String> variables = (Map<String, String>) request
				.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
		return variables == null ? null : variables.get("codigo");
	}

}
