package com.vestigia.service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.dto.Sala;
import com.vestigia.model.DescubrimientoModel;
import com.vestigia.model.EventoModel;
import com.vestigia.model.SalaModel;
import com.vestigia.repository.DescubrimientoRepository;

@Service
public class DescubrimientoService {

	/** Resultados de los puzzles. Solo el servidor los crea, al resolver cada puzzle (Paso 3b). */
	public static final Set<String> CLAVES_DEL_SERVIDOR = Set.of("coordenada_a", "coordenada_b",
			"observatorio_visible", "magnetar_localizado");

	/** El descubrimiento guardado y si esta petición lo creó (201) o ya existía (200). */
	public record Registro(Sala.Descubrimiento descubrimiento, boolean nuevo) {
	}

	private final DescubrimientoRepository descubrimientos;

	private final ActividadService actividad;

	private final HabilitacionPuzzlesService habilitacion;

	private final Clock clock;

	public DescubrimientoService(DescubrimientoRepository descubrimientos, ActividadService actividad,
			HabilitacionPuzzlesService habilitacion, Clock clock) {
		this.descubrimientos = descubrimientos;
		this.actividad = actividad;
		this.habilitacion = habilitacion;
		this.clock = clock;
	}

	/**
	 * Registra un descubrimiento del juego o de la app. Repetir la clave devuelve el que ya existe, sin cambiar sus datos
	 * ni guardar otro evento. Las escrituras de una sala se serializan con el bloqueo de su fila, así que dos peticiones
	 * simultáneas con la misma clave no chocan contra {@code UNIQUE (sala_id, clave)}: la segunda ya ve la primera.
	 */
	@Transactional
	public Registro registrar(SalaService.Credencial credencial, String clave, Map<String, Object> datos) {
		if (CLAVES_DEL_SERVIDOR.contains(clave)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"La clave '" + clave + "' la genera el servidor al resolver un puzzle; los clientes no pueden enviarla");
		}
		SalaModel sala = actividad.bloquear(credencial.salaId());
		Optional<DescubrimientoModel> existente = descubrimientos.findBySalaIdAndClave(sala.getId(), clave);
		if (existente.isPresent()) {
			actividad.renovar(sala);
			return new Registro(dto(existente.get()), false);
		}
		DescubrimientoModel descubrimiento = new DescubrimientoModel();
		descubrimiento.setSalaId(sala.getId());
		descubrimiento.setClave(clave);
		descubrimiento.setDatos(datos == null ? new LinkedHashMap<>() : datos);
		descubrimiento.setOrigen(credencial.rol().origen());
		descubrimiento.setCreadoEn(clock.instant());
		descubrimiento = descubrimientos.save(descubrimiento);
		actividad.registrar(sala, EventoModel.Tipo.descubrimiento_registrado, descubrimiento.getOrigen(),
				Map.of("clave", clave, "datos", descubrimiento.getDatos()));
		habilitacion.recalcular(sala);
		return new Registro(dto(descubrimiento), true);
	}

	private static Sala.Descubrimiento dto(DescubrimientoModel d) {
		return new Sala.Descubrimiento(d.getClave(), d.getDatos(), d.getOrigen(), d.getCreadoEn());
	}

}
