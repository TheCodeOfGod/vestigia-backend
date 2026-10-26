package com.vestigia.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.dto.AppUnida;
import com.vestigia.dto.Pueblo;
import com.vestigia.dto.Sala;
import com.vestigia.dto.SalaCreada;
import com.vestigia.model.PuzzleEstadoModel;
import com.vestigia.model.SalaModel;
import com.vestigia.repository.CodigoPuebloRepository;
import com.vestigia.repository.DescubrimientoRepository;
import com.vestigia.repository.PuzzleEstadoRepository;
import com.vestigia.repository.SalaRepository;

@Service
public class SalaService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final String INDICE_SALA_VIVA = "salas_codigo_viva";

	public enum Rol {

		juego, app

	}

	/** Quién hace la petición, según su token. */
	public record Credencial(UUID salaId, String codigo, Rol rol) {
	}

	private final CodigoPuebloRepository pueblos;

	private final SalaRepository salas;

	private final PuzzleEstadoRepository puzzles;

	private final DescubrimientoRepository descubrimientos;

	private final Clock clock;

	private final Duration expiracion;

	public SalaService(CodigoPuebloRepository pueblos, SalaRepository salas, PuzzleEstadoRepository puzzles,
			DescubrimientoRepository descubrimientos, Clock clock, @Value("${vestigia.salas.expiracion}") Duration expiracion) {
		this.pueblos = pueblos;
		this.salas = salas;
		this.puzzles = puzzles;
		this.descubrimientos = descubrimientos;
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

	/**
	 * Enlaza la única app de la sala. 404 si no hay sala viva con ese código; 409 si la sala viva ya tiene app (incluye
	 * perder una carrera contra otra app).
	 */
	@Transactional
	public AppUnida unirse(String codigoRecibido) {
		String codigo = normalizarCodigo(codigoRecibido);
		Instant ahora = clock.instant();
		String token = nuevoToken();
		int enlazadas = salas.unirApp(codigo, hashToken(token), ahora, ahora.plus(expiracion),
				SalaModel.Estado.esperando_app, SalaModel.Estado.activa);
		if (enlazadas == 1) {
			return new AppUnida(token);
		}
		if (salas.findByCodigoAndEstadoIn(codigo, SalaModel.Estado.VIVOS).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "La sala '" + codigo + "' ya tiene una app enlazada");
		}
		throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay una sala en curso con el código '" + codigo + "'");
	}

	@Transactional(readOnly = true)
	public Optional<Credencial> autenticar(String token) {
		String hash = hashToken(token);
		return salas.findByTokenHashAndEstadoIn(hash, SalaModel.Estado.VIVOS)
				.map(s -> new Credencial(s.getId(), s.getCodigo(),
						hash.equals(s.getTokenJuegoHash()) ? Rol.juego : Rol.app));
	}

	@Transactional(readOnly = true)
	public Sala estado(UUID salaId) {
		SalaModel sala = salas.findById(salaId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La sala no existe"));
		List<Sala.Puzzle> puzzlesSala = puzzles.findByClaveSalaId(salaId).stream()
				.sorted(Comparator.comparing(PuzzleEstadoModel::getPuzzle))
				.map(p -> new Sala.Puzzle(p.getPuzzle(), p.getEstado(), p.getDatos()))
				.toList();
		List<Sala.Descubrimiento> descubrimientosSala = descubrimientos.findBySalaIdOrderByCreadoEnAscIdAsc(salaId)
				.stream()
				.map(d -> new Sala.Descubrimiento(d.getClave(), d.getDatos(), d.getOrigen(), d.getCreadoEn()))
				.toList();
		return new Sala(sala.getCodigo(), sala.getEstado(), sala.getEscenaActual(), puzzlesSala, descubrimientosSala,
				new Sala.Dispositivos(false, false), sala.getCreadaEn(), sala.getExpiraEn());
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
