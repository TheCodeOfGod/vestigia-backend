package com.vestigia.dto;

import com.vestigia.model.UsuarioModel;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Cambios a un usuario. Cada campo es opcional; el que falta no se toca. El correo no cambia")
public record ActualizarUsuarioRequest(
		@Schema(example = "Raúl Cano") @Size(max = 80, message = "máximo 80 caracteres") @Pattern(regexp = ".*\\S.*", message = "no puede ir vacío") String nombre,
		UsuarioModel.Rol rol, Boolean activo,
		@Schema(description = "Contraseña nueva, de 8 a 72 caracteres. Cierra las sesiones del usuario") @Size(min = 8, max = 72, message = "debe tener de 8 a 72 caracteres") String password) {
}
