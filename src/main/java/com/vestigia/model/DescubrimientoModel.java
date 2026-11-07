package com.vestigia.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "descubrimientos")
public class DescubrimientoModel {

	public enum Origen {

		juego, app, servidor

	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID salaId;

	private String clave;

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> datos = new HashMap<>();

	@Enumerated(EnumType.STRING)
	private Origen origen;

	private Instant creadoEn;

	public Long getId() {
		return id;
	}

	public UUID getSalaId() {
		return salaId;
	}

	public void setSalaId(UUID salaId) {
		this.salaId = salaId;
	}

	public String getClave() {
		return clave;
	}

	public void setClave(String clave) {
		this.clave = clave;
	}

	public Map<String, Object> getDatos() {
		return datos;
	}

	public void setDatos(Map<String, Object> datos) {
		this.datos = datos;
	}

	public Origen getOrigen() {
		return origen;
	}

	public void setOrigen(Origen origen) {
		this.origen = origen;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	public void setCreadoEn(Instant creadoEn) {
		this.creadoEn = creadoEn;
	}

}
