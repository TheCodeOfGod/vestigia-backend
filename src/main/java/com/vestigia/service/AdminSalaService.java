package com.vestigia.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.dto.PaginaSalas;
import com.vestigia.dto.Sala;
import com.vestigia.dto.SalaAdmin;
import com.vestigia.dto.SalaResumen;
import com.vestigia.model.DescubrimientoModel;
import com.vestigia.model.EventoModel;
import com.vestigia.model.PuzzleEstadoModel;
import com.vestigia.model.SalaModel;
import com.vestigia.repository.EventoRepository;
import com.vestigia.repository.PuzzleEstadoRepository;
import com.vestigia.repository.SalaRepository;

/** Lo que ve y puede hacer un administrador con las salas, vivas o terminadas. */
@Service
public class AdminSalaService {

	public static final int TAMANO_POR_DEFECTO = 20;

	public static final int TAMANO_MAXIMO = 100;

	private final SalaRepository salas;

	private final PuzzleEstadoRepository puzzles;

	private final EventoRepository eventos;

	private final SalaService salaService;

	private final ActividadService actividad;

	private final SalaService.Presencia presencia;

	public AdminSalaService(SalaRepository salas, PuzzleEstadoRepository puzzles, EventoRepository eventos,
			SalaService salaService, ActividadService actividad, SalaService.Presencia presencia) {
		this.salas = salas;
		this.puzzles = puzzles;
		this.eventos = eventos;
		this.salaService = salaService;
		this.actividad = actividad;
		this.presencia = presencia;
	}

	/**
	 * Una página de salas, las más nuevas primero, con el filtro de estado si viene. {@code pagina} cuenta desde 0 y
	 * no baja de 0; {@code tamano} se acota a 1..100.
	 */
	@Transactional(readOnly = true)
	public PaginaSalas listar(SalaModel.Estado estado, Integer pagina, Integer tamano) {
		int numero = pagina == null ? 0 : Math.max(0, pagina);
		int tope = tamano == null ? TAMANO_POR_DEFECTO : Math.max(1, Math.min(tamano, TAMANO_MAXIMO));
		PageRequest peticion = PageRequest.of(numero, tope, Sort.by(Sort.Direction.DESC, "creadaEn").and(Sort.by("id")));
		Page<SalaModel> encontradas = estado == null ? salas.findAll(peticion) : salas.findByEstado(estado, peticion);
		Map<UUID, List<PuzzleEstadoModel>> puzzlesPorSala = puzzlesDe(
				encontradas.getContent().stream().map(SalaModel::getId).toList());
		List<SalaResumen> contenido = encontradas.getContent().stream()
				.map(s -> resumen(s, puzzlesPorSala.getOrDefault(s.getId(), List.of()))).toList();
		return new PaginaSalas(contenido, numero, tope, encontradas.getTotalElements(), encontradas.getTotalPages());
	}

	@Transactional(readOnly = true)
	public SalaAdmin detalle(UUID id) {
		SalaModel sala = buscar(id);
		Sala estado = salaService.estado(id);
		List<SalaAdmin.Evento> bitacora = eventos.findBySalaIdOrderByCreadoEnAscIdAsc(id).stream()
				.map(e -> new SalaAdmin.Evento(e.getTipo().valor(), e.getOrigen(), e.getPayload(), e.getCreadoEn()))
				.toList();
		return new SalaAdmin(sala.getId(), sala.getCodigo(), sala.getEstado(), sala.getEscenaActual(), estado.puzzles(),
				estado.descubrimientos(), estado.dispositivos(), sala.getTokenAppHash() != null, sala.getCreadaEn(),
				sala.getUltimaActividad(), sala.getExpiraEn(), bitacora);
	}

	/**
	 * Cierra una sala viva a la fuerza: pasa a finalizada, su código queda libre y los dos tokens dejan de servir. Los
	 * dispositivos reciben sala.cerrada. Si la sala ya terminó no hace nada.
	 */
	@Transactional
	public void cerrar(UUID id, UUID usuarioId) {
		buscar(id);
		SalaModel sala = salas.bloquear(id).orElseThrow();
		if (!SalaModel.Estado.VIVOS.contains(sala.getEstado())) {
			return;
		}
		sala.setEstado(SalaModel.Estado.finalizada);
		actividad.registrar(sala, EventoModel.Tipo.sala_finalizada, DescubrimientoModel.Origen.servidor,
				Map.of("cerrada_por", "admin", "usuario", usuarioId.toString()));
	}

	private SalaModel buscar(UUID id) {
		return salas.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la sala " + id));
	}

	private Map<UUID, List<PuzzleEstadoModel>> puzzlesDe(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return puzzles.findByClaveSalaIdIn(ids).stream()
				.collect(Collectors.groupingBy(p -> p.getClave().salaId(), Collectors.toList()));
	}

	private SalaResumen resumen(SalaModel sala, List<PuzzleEstadoModel> suyos) {
		long resueltos = suyos.stream().filter(p -> p.getEstado() == PuzzleEstadoModel.Estado.resuelto).count();
		return new SalaResumen(sala.getId(), sala.getCodigo(), sala.getEstado(), sala.getEscenaActual(),
				(int) resueltos, suyos.size(), sala.getTokenAppHash() != null, presencia.dispositivos(sala.getId()),
				sala.getCreadaEn(), sala.getUltimaActividad(), sala.getExpiraEn());
	}

}
