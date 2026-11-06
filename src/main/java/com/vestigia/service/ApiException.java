package com.vestigia.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Un error que, además del estado y el mensaje, llena {@code codigo} y {@code details} del cuerpo de error uniforme.
 * Un {@link ResponseStatusException} común sale con {@code codigo} nulo y {@code details} vacío.
 */
public class ApiException extends ResponseStatusException {

	/** Un elemento de {@code details}: el campo que falló y por qué. */
	public record Detalle(String campo, String mensaje) {
	}

	private final String codigo;

	private final List<Detalle> detalles;

	public ApiException(HttpStatus status, String codigo, String mensaje, List<Detalle> detalles) {
		super(status, mensaje);
		this.codigo = codigo;
		this.detalles = List.copyOf(detalles);
	}

	public String getCodigo() {
		return codigo;
	}

	public List<Detalle> getDetalles() {
		return detalles;
	}

}
