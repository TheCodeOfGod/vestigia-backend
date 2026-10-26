package com.vestigia.dto;

import com.vestigia.model.SalaModel;

/** Respuesta de POST /salas. Es la única vez que el token del juego viaja en claro. */
public record SalaCreada(String codigo, SalaModel.Estado estado, String token) {
}
