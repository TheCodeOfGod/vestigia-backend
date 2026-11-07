package com.vestigia.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de PUT /salas/{codigo}/puzzles/{puzzle}. {@code datos} se lee con cualquier forma JSON para que el servicio
 * responda con {@code details} también cuando no es un objeto, y valida que sea un aviso que ese puzzle acepta, como
 * {@code {"completada": true}}.
 */
public record ReportarPuzzleRequest(@NotNull(message = "es obligatorio") Object datos) {
}
