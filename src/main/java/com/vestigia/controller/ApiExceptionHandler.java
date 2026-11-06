package com.vestigia.controller;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.service.ApiException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Convierte las excepciones en un cuerpo JSON de error uniforme. {@code codigo} siempre viaja, como {@code null} salvo
 * en los errores que lo definen ({@link ApiException}), para que los clientes lean siempre la misma forma.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@Schema(description = "Cuerpo de todos los errores")
	public record ErrorBody(Instant timestamp, @Schema(example = "409") int status,
			@Schema(example = "Conflict") String error,
			@Schema(description = "Identificador estable. Siempre viaja: null salvo en los errores que definen uno",
					nullable = true, example = "puzzle_bloqueado") String codigo,
			@Schema(description = "Mensaje en español, se puede mostrar") String message,
			@Schema(example = "/api/v1/salas/BACALAR/puzzles/radio") String path,
			@Schema(description = "Un elemento por campo en los errores de validación; vacío en los demás")
			List<FieldDetail> details) {
	}

	@Schema(description = "Error de un campo")
	public record FieldDetail(@Schema(example = "datos") String field, @Schema(example = "es obligatorio") String message) {
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorBody> validacion(MethodArgumentNotValidException ex, HttpServletRequest request) {
		List<FieldDetail> details = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> new FieldDetail(e.getField(), e.getDefaultMessage()))
				.toList();
		return body(HttpStatus.BAD_REQUEST, null, "Datos inválidos", request, details);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorBody> jsonMalformado(HttpMessageNotReadableException ex, HttpServletRequest request) {
		return body(HttpStatus.BAD_REQUEST, null, "JSON malformado o ilegible", request, List.of());
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorBody> parametroInvalido(MethodArgumentTypeMismatchException ex,
			HttpServletRequest request) {
		return body(HttpStatus.BAD_REQUEST, null, "Valor inválido para '" + ex.getName() + "'", request, List.of());
	}

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorBody> conCodigo(ApiException ex, HttpServletRequest request) {
		List<FieldDetail> details = ex.getDetalles().stream()
				.map(d -> new FieldDetail(d.campo(), d.mensaje()))
				.toList();
		return body(ex.getStatusCode(), ex.getCodigo(), ex.getReason(), request, details);
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ErrorBody> conEstado(ResponseStatusException ex, HttpServletRequest request) {
		return body(ex.getStatusCode(), null, ex.getReason(), request, List.of());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorBody> inesperado(Exception ex, HttpServletRequest request) {
		// Errores propios de Spring MVC (405, 415, ruta inexistente...) conservan su código.
		if (ex instanceof ErrorResponse errorResponse) {
			return body(errorResponse.getStatusCode(), null, errorResponse.getBody().getDetail(), request, List.of());
		}
		log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
		return body(HttpStatus.INTERNAL_SERVER_ERROR, null, "Error interno del servidor", request, List.of());
	}

	private ResponseEntity<ErrorBody> body(HttpStatusCode status, String codigo, String message,
			HttpServletRequest request, List<FieldDetail> details) {
		HttpStatus httpStatus = HttpStatus.resolve(status.value());
		String error = httpStatus != null ? httpStatus.getReasonPhrase() : String.valueOf(status.value());
		ErrorBody errorBody = new ErrorBody(Instant.now(), status.value(), error, codigo, message,
				request.getRequestURI(), details);
		// Con el tipo fijo, Spring no negocia contra Accept: un cliente que pide XML recibe el error en JSON, no vacío.
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(errorBody);
	}

}
