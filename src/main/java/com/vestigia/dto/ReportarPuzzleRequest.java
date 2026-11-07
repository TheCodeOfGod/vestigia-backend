package com.vestigia.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de PUT /salas/{codigo}/puzzles/{puzzle}. {@code datos} se lee con cualquier forma JSON para que el servicio
 * responda con {@code details} también cuando no es un objeto, y valida que sea un aviso que ese puzzle acepta, como
 * {@code {"completada": true}}.
 */
public record ReportarPuzzleRequest(@Schema(description = "Aviso con una sola clave y el booleano true: {\"sintonizada\": true} en radio; {\"iniciado\": true} o {\"completada\": true} en photo, lookout y observatory", type = "object", example = "{\"completada\": true}") @NotNull(message = "es obligatorio") Object datos) {
}
