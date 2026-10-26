package com.vestigia.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Registra una línea {@code [API]} por petición a /api/** con método, ruta, código y duración. No registra la query
 * string ni encabezados: ahí viajan los tokens.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		long inicio = System.nanoTime();
		try {
			filterChain.doFilter(request, response);
		} finally {
			long ms = (System.nanoTime() - inicio) / 1_000_000;
			log.info("[API] {} {} -> {} | {} ms", request.getMethod(), request.getRequestURI(), response.getStatus(),
					ms);
		}
	}

}
