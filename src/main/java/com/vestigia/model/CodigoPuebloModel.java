package com.vestigia.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "codigos_pueblo")
public class CodigoPuebloModel {

	@Id
	private String codigo;

	protected CodigoPuebloModel() {
	}

	public CodigoPuebloModel(String codigo) {
		this.codigo = codigo;
	}

	public String getCodigo() {
		return codigo;
	}

}
