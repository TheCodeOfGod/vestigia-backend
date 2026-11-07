package com.vestigia.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.vestigia.model.SalaModel;

/** Respuesta de PUT /salas/{codigo}/escena. */
@Schema(description = "Escena en la que queda la sala")
public record EscenaActual(@Schema(example = "faro") SalaModel.Escena escenaActual) {
}
