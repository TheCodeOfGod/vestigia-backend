package com.vestigia.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Una sesión de administración. Solo se guarda el hash del token, como en las salas. */
@Entity
@Table(name = "sesiones_admin")
public class SesionAdminModel {

	@Id
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 64)
	private String tokenHash;

	private UUID usuarioId;

	private Instant creadaEn;

	private Instant expiraEn;

	public String getTokenHash() {
		return tokenHash;
	}

	public void setTokenHash(String tokenHash) {
		this.tokenHash = tokenHash;
	}

	public UUID getUsuarioId() {
		return usuarioId;
	}

	public void setUsuarioId(UUID usuarioId) {
		this.usuarioId = usuarioId;
	}

	public Instant getCreadaEn() {
		return creadaEn;
	}

	public void setCreadaEn(Instant creadaEn) {
		this.creadaEn = creadaEn;
	}

	public Instant getExpiraEn() {
		return expiraEn;
	}

	public void setExpiraEn(Instant expiraEn) {
		this.expiraEn = expiraEn;
	}

}
