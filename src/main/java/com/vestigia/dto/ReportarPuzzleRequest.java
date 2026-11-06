package com.vestigia.dto;

import java.util.Map;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de PUT /salas/{codigo}/puzzles/{puzzle}. El servicio valida que {@code datos} sea un aviso que ese puzzle
 * acepta, como {@code {"completada": true}}.
 */
public record ReportarPuzzleRequest(@NotNull(message = "es obligatorio") Map<String, Object> datos) {
}
