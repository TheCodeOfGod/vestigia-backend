package com.vestigia.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Una página del listado de salas de administración")
public record PaginaSalas(List<SalaResumen> contenido, @Schema(description = "Número de esta página, desde 0", example = "0") int pagina,
		@Schema(description = "Cuántas salas caben por página", example = "20") int tamano,
		@Schema(description = "Salas que cumplen el filtro, sumando todas las páginas", example = "42") long total,
		@Schema(description = "Cuántas páginas hay con ese tamaño", example = "3") int totalPaginas) {
}
