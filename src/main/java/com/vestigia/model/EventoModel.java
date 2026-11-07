package com.vestigia.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Bitácora de la sala para las métricas del plan de pruebas. Una fila por escritura exitosa. */
@Entity
@Table(name = "eventos")
public class EventoModel {

	public enum Tipo {

		sala_creada("sala.creada"), app_unida("app.unida"), descubrimiento_registrado("descubrimiento.registrado"),
		puzzle_habilitado("puzzle.habilitado"), escena_cambiada("escena.cambiada"),
		sala_finalizada("sala.finalizada");

		private final String valor;

		Tipo(String valor) {
			this.valor = valor;
		}

		/** Lo que se guarda en la columna tipo. */
		public String valor() {
			return valor;
		}

	}

	/** Guarda el tipo con su punto ({@code sala.creada}), que no cabe en el nombre de una constante. */
	public static class TipoConverter implements AttributeConverter<Tipo, String> {

		@Override
		public String convertToDatabaseColumn(Tipo tipo) {
			return tipo == null ? null : tipo.valor();
		}

		@Override
		public Tipo convertToEntityAttribute(String valor) {
			return valor == null ? null
					: Arrays.stream(Tipo.values()).filter(t -> t.valor().equals(valor)).findFirst().orElseThrow();
		}

	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID salaId;

	@Convert(converter = TipoConverter.class)
	private Tipo tipo;

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> payload = new HashMap<>();

	@Enumerated(EnumType.STRING)
	private DescubrimientoModel.Origen origen;

	private Instant creadoEn;

	protected EventoModel() {
	}

	public EventoModel(UUID salaId, Tipo tipo, DescubrimientoModel.Origen origen, Map<String, Object> payload,
			Instant creadoEn) {
		this.salaId = salaId;
		this.tipo = tipo;
		this.origen = origen;
		this.payload = payload;
		this.creadoEn = creadoEn;
	}

	public Long getId() {
		return id;
	}

	public UUID getSalaId() {
		return salaId;
	}

	public Tipo getTipo() {
		return tipo;
	}

	public Map<String, Object> getPayload() {
		return payload;
	}

	public DescubrimientoModel.Origen getOrigen() {
		return origen;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

}
