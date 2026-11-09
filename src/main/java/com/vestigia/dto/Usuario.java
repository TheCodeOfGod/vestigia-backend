package com.vestigia.dto;

import java.time.Instant;
import java.util.UUID;

import com.vestigia.model.UsuarioModel;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Un usuario de administración. Nunca incluye la contraseña")
public record Usuario(UUID id, @Schema(example = "Raúl") String nombre,
		@Schema(example = "raul@vestigia.site") String correo, UsuarioModel.Rol rol, boolean activo,
		Instant creadoEn) {

	public static Usuario de(UsuarioModel u) {
		return new Usuario(u.getId(), u.getNombre(), u.getCorreo(), u.getRol(), u.isActivo(), u.getCreadoEn());
	}

}
