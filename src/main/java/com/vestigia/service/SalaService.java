package com.vestigia.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.dto.Pueblo;
import com.vestigia.dto.SalaCreada;
import com.vestigia.model.PuzzleEstadoModel;
import com.vestigia.model.SalaModel;
import com.vestigia.repository.CodigoPuebloRepository;
import com.vestigia.repository.PuzzleEstadoRepository;
import com.vestigia.repository.SalaRepository;

@Service
public class SalaService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final String INDICE_SALA_VIVA = "salas_codigo_viva";

	private final CodigoPuebloRepository pueblos;

	private final SalaRepository salas;

	private final PuzzleEstadoRepository puzzles;

	private final Clock clock;

	private final Duration expiracion;

	public SalaService(CodigoPuebloRepository pueblos, SalaRepository salas, PuzzleEstadoRepository puzzles,
			Clock clock, @Value("${vestigia.salas.expiracion}") Duration expiracion) {
		this.pueblos = pueblos;
		this.salas = salas;
		this.puzzles = puzzles;
		this.clock = clock;
		this.expiracion = expiracion;
	}

	/** Los códigos llegan del cliente con cualquier capitalización o espacios alrededor. */
	public static String normalizarCodigo(String codigo) {
		return codigo.trim().toUpperCase(Locale.ROOT);
	}

	@Transactional(readOnly = true)
	public List<Pueblo> listarPueblos(boolean soloDisponibles) {
		Set<String> ocupados = new HashSet<>(salas.findCodigosByEstadoIn(SalaModel.Estado.VIVOS));
		return pueblos.findAll(Sort.by("codigo")).stream()
				.map(p -> new Pueblo(p.getCodigo(), !ocupados.contains(p.getCodigo())))
				.filter(p -> !soloDisponibles || p.libre())
				.toList();
	}

	@Transactional
	public SalaCreada crear(String codigoRecibido) {
		String codigo = normalizarCodigo(codigoRecibido);
		if (!pueblos.existsById(codigo)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'" + codigo + "' no está en el catálogo de pueblos");
		}
		Instant ahora = clock.instant();
		String token = nuevoToken();
		SalaModel sala = new SalaModel();
		sala.setCodigo(codigo);
		sala.setEstado(SalaModel.Estado.esperando_app);
		sala.setEscenaActual("introduccion");
		sala.setTokenJuegoHash(hashToken(token));
		sala.setCreadaEn(ahora);
		sala.setUltimaActividad(ahora);
		sala.setExpiraEn(ahora.plus(expiracion));
		try {
			sala = salas.saveAndFlush(sala);
		}
		catch (DataIntegrityViolationException ex) {
			// El índice único parcial decide quién gana una carrera por el mismo pueblo.
			if (ex.getCause() instanceof ConstraintViolationException violacion
					&& INDICE_SALA_VIVA.equals(violacion.getConstraintName())) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "'" + codigo + "' ya tiene una sala en curso");
			}
			throw ex;
		}
		for (PuzzleEstadoModel.Puzzle puzzle : PuzzleEstadoModel.Puzzle.values()) {
			puzzles.save(new PuzzleEstadoModel(sala.getId(), puzzle, PuzzleEstadoModel.Estado.bloqueado, ahora));
		}
		return new SalaCreada(codigo, sala.getEstado(), token);
	}

	private static String nuevoToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hashToken(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
