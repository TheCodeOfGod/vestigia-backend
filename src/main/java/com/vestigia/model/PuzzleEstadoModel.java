package com.vestigia.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "puzzles_estado")
public class PuzzleEstadoModel {

	/** El orden de las constantes es el orden del juego. */
	public enum Puzzle {

		radio, fotografia, mirador, observatorio

	}

	public enum Estado {

		bloqueado, disponible, en_curso, resuelto

	}

	@Embeddable
	public record Clave(UUID salaId, @Enumerated(EnumType.STRING) Puzzle puzzle) {
	}

	@EmbeddedId
	private Clave clave;

	@Enumerated(EnumType.STRING)
	private Estado estado;

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> datos = new HashMap<>();

	private Instant actualizadoEn;

	protected PuzzleEstadoModel() {
	}

	public PuzzleEstadoModel(UUID salaId, Puzzle puzzle, Estado estado, Instant actualizadoEn) {
		this.clave = new Clave(salaId, puzzle);
		this.estado = estado;
		this.actualizadoEn = actualizadoEn;
	}

	public Clave getClave() {
		return clave;
	}

	public Puzzle getPuzzle() {
		return clave.puzzle();
	}

	public Estado getEstado() {
		return estado;
	}

	public void setEstado(Estado estado) {
		this.estado = estado;
	}

	public Map<String, Object> getDatos() {
		return datos;
	}

	public void setDatos(Map<String, Object> datos) {
		this.datos = datos;
	}

	public Instant getActualizadoEn() {
		return actualizadoEn;
	}

	public void setActualizadoEn(Instant actualizadoEn) {
		this.actualizadoEn = actualizadoEn;
	}

}
