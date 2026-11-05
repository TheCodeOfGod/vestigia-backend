package com.vestigia.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vestigia.dto.Pueblo;
import com.vestigia.service.SalaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/pueblos")
@Tag(name = "Pueblos", description = "Catálogo de nombres de sala")
public class PuebloController {

	private final SalaService salaService;

	public PuebloController(SalaService salaService) {
		this.salaService = salaService;
	}

	@GetMapping
	@Operation(summary = "Lista el catálogo de pueblos", description = "Público. Ordenado alfabéticamente. Un pueblo "
			+ "está ocupado mientras tiene una sala en esperando_app o activa.")
	public List<Pueblo> listar(@Parameter(description = "true: solo los pueblos libres")
	@RequestParam(defaultValue = "false") boolean disponibles) {
		return salaService.listarPueblos(disponibles);
	}

}
