package com.vestigia.dto;

import com.vestigia.model.UsuarioModel;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Cuerpo de crear un usuario")
public record CrearUsuarioRequest(
		@Schema(example = "Raúl") @NotBlank(message = "es obligatorio") @Size(max = 80, message = "máximo 80 caracteres") String nombre,
		@Schema(description = "Se recorta y se pasa a minúsculas. Es único", example = "raul@vestigia.site")
		@NotBlank(message = "es obligatorio") @Email(message = "no es un correo válido") @Size(max = 254, message = "máximo 254 caracteres") String correo,
		@Schema(description = "De 8 a 72 caracteres") @NotBlank(message = "es obligatorio") @Size(min = 8, max = 72, message = "debe tener de 8 a 72 caracteres") String password,
		@Schema(example = "admin") @NotNull(message = "es obligatorio") UsuarioModel.Rol rol) {
}
