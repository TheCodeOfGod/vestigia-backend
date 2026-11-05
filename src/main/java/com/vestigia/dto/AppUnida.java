package com.vestigia.dto;

/** Respuesta de POST /salas/{codigo}/unirse. Es la única vez que el token de la app viaja en claro. */
public record AppUnida(String token) {
}
