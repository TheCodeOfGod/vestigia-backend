package com.vestigia.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.vestigia.model.SalaModel;

/** Respuesta de POST /salas. Es la única vez que el token del juego viaja en claro. */
@Schema(description = "Respuesta de crear la sala")
public record SalaCreada(@Schema(description = "Código de la sala, en mayúsculas", example = "BACALAR") String codigo,
		@Schema(description = "Siempre esperando_app al crearla") SalaModel.Estado estado,
		@Schema(description = "Token del juego. Se entrega una sola vez: guárdalo", example = "K8srOIYIL0jr89daDSmv9IkyzKT2L3mdJzQOSMmGGjk") String token) {
}
