package com.vestigia.dto;

import com.vestigia.model.SalaModel;

/** Respuesta de PUT /salas/{codigo}/escena. */
public record EscenaActual(SalaModel.Escena escenaActual) {
}
