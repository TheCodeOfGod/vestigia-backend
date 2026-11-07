package com.vestigia.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "salas")
public class SalaModel {

	/** Las constantes se escriben igual que en la base y en el JSON. */
	public enum Estado {

		esperando_app, activa, finalizada, expirada;

		public static final List<Estado> VIVOS = List.of(esperando_app, activa);

	}

	/** Escenas del juego, en orden. Provisionales: la base no las restringe, solo este enum. */
	public enum Escena {

		introduccion, pueblo, faro, observatorio, desenlace

	}

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String codigo;

	@Enumerated(EnumType.STRING)
	private Estado estado;

	@Enumerated(EnumType.STRING)
	private Escena escenaActual;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 64)
	private String tokenJuegoHash;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 64)
	private String tokenAppHash;

	private Instant creadaEn;

	private Instant ultimaActividad;

	private Instant expiraEn;

	public UUID getId() {
		return id;
	}

	public String getCodigo() {
		return codigo;
	}

	public void setCodigo(String codigo) {
		this.codigo = codigo;
	}

	public Estado getEstado() {
		return estado;
	}

	public void setEstado(Estado estado) {
		this.estado = estado;
	}

	public Escena getEscenaActual() {
		return escenaActual;
	}

	public void setEscenaActual(Escena escenaActual) {
		this.escenaActual = escenaActual;
	}

	public String getTokenJuegoHash() {
		return tokenJuegoHash;
	}

	public void setTokenJuegoHash(String tokenJuegoHash) {
		this.tokenJuegoHash = tokenJuegoHash;
	}

	public String getTokenAppHash() {
		return tokenAppHash;
	}

	public void setTokenAppHash(String tokenAppHash) {
		this.tokenAppHash = tokenAppHash;
	}

	public Instant getCreadaEn() {
		return creadaEn;
	}

	public void setCreadaEn(Instant creadaEn) {
		this.creadaEn = creadaEn;
	}

	public Instant getUltimaActividad() {
		return ultimaActividad;
	}

	public void setUltimaActividad(Instant ultimaActividad) {
		this.ultimaActividad = ultimaActividad;
	}

	public Instant getExpiraEn() {
		return expiraEn;
	}

	public void setExpiraEn(Instant expiraEn) {
		this.expiraEn = expiraEn;
	}

}
