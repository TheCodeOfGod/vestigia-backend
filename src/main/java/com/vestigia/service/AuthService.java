package com.vestigia.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.dto.SesionIniciada;
import com.vestigia.dto.Usuario;
import com.vestigia.model.SesionAdminModel;
import com.vestigia.model.UsuarioModel;
import com.vestigia.repository.SesionAdminRepository;
import com.vestigia.repository.UsuarioRepository;

/** Inicio y cierre de sesión de los usuarios de administración, y la validación de su token. */
@Service
public class AuthService {

	public static final String CREDENCIALES_INVALIDAS = "Correo o contraseña incorrectos";

	public static final String DEMASIADOS_INTENTOS = "demasiados_intentos";

	/** Quién hace la petición, según su token de sesión. */
	public record CredencialAdmin(UUID usuarioId, UsuarioModel.Rol rol, String tokenHash) {
	}

	private final UsuarioRepository usuarios;

	private final SesionAdminRepository sesiones;

	private final PasswordEncoder encoder;

	private final Clock clock;

	private final Duration vigencia;

	private final int intentosMax;

	private final Duration ventana;

	private final String hashFalso;

	private final ConcurrentHashMap<String, Deque<Instant>> fallos = new ConcurrentHashMap<>();

	public AuthService(UsuarioRepository usuarios, SesionAdminRepository sesiones, PasswordEncoder encoder, Clock clock,
			@Value("${vestigia.auth.sesion}") Duration vigencia,
			@Value("${vestigia.auth.intentos-max}") int intentosMax,
			@Value("${vestigia.auth.intentos-ventana}") Duration ventana) {
		this.usuarios = usuarios;
		this.sesiones = sesiones;
		this.encoder = encoder;
		this.clock = clock;
		this.vigencia = vigencia;
		this.intentosMax = intentosMax;
		this.ventana = ventana;
		this.hashFalso = encoder.encode("sin-usuario");
	}

	public static String normalizarCorreo(String correo) {
		return correo.trim().toLowerCase(Locale.ROOT);
	}

	@Transactional
	public SesionIniciada iniciarSesion(String correoRecibido, String password) {
		String correo = normalizarCorreo(correoRecibido);
		Instant ahora = clock.instant();
		exigirCupo(correo, ahora);
		Optional<UsuarioModel> encontrado = usuarios.findByCorreo(correo);
		// Se compara siempre, aunque el correo no exista, para que el tiempo de respuesta no lo delate.
		boolean coincide = encoder.matches(password, encontrado.map(UsuarioModel::getPasswordHash).orElse(hashFalso));
		if (encontrado.isEmpty() || !coincide || !encontrado.get().isActivo()) {
			registrarFallo(correo, ahora);
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, CREDENCIALES_INVALIDAS);
		}
		fallos.remove(correo);
		UsuarioModel usuario = encontrado.get();
		String token = TokenOpaco.nuevo();
		SesionAdminModel sesion = new SesionAdminModel();
		sesion.setTokenHash(TokenOpaco.hash(token));
		sesion.setUsuarioId(usuario.getId());
		sesion.setCreadaEn(ahora);
		sesion.setExpiraEn(ahora.plus(vigencia));
		sesiones.save(sesion);
		return new SesionIniciada(token, sesion.getExpiraEn(), Usuario.de(usuario));
	}

	@Transactional(readOnly = true)
	public Optional<CredencialAdmin> autenticar(String token) {
		String hash = TokenOpaco.hash(token);
		Instant ahora = clock.instant();
		return sesiones.findById(hash)
				.filter(s -> s.getExpiraEn().isAfter(ahora))
				.flatMap(s -> usuarios.findById(s.getUsuarioId()))
				.filter(UsuarioModel::isActivo)
				.map(u -> new CredencialAdmin(u.getId(), u.getRol(), hash));
	}

	@Transactional
	public void cerrarSesion(CredencialAdmin credencial) {
		sesiones.deleteById(credencial.tokenHash());
	}

	@Transactional
	public void cerrarSesionesDe(UUID usuarioId) {
		sesiones.deleteByUsuarioId(usuarioId);
	}

	@Scheduled(fixedDelayString = "${vestigia.auth.limpieza}")
	@Transactional
	public void purgarVencidas() {
		sesiones.deleteByExpiraEnBefore(clock.instant());
	}

	private void exigirCupo(String correo, Instant ahora) {
		Deque<Instant> intentos = fallos.get(correo);
		if (intentos == null) {
			return;
		}
		synchronized (intentos) {
			podar(intentos, ahora);
			if (intentos.size() >= intentosMax) {
				throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, DEMASIADOS_INTENTOS,
						"Demasiados intentos fallidos. Espera unos minutos e inténtalo de nuevo", List.of());
			}
		}
	}

	private void registrarFallo(String correo, Instant ahora) {
		if (fallos.size() > 10_000) {
			fallos.values().removeIf(d -> {
				synchronized (d) {
					podar(d, ahora);
					return d.isEmpty();
				}
			});
		}
		Deque<Instant> intentos = fallos.computeIfAbsent(correo, k -> new ArrayDeque<>());
		synchronized (intentos) {
			intentos.addLast(ahora);
		}
	}

	private void podar(Deque<Instant> intentos, Instant ahora) {
		Instant corte = ahora.minus(ventana);
		while (!intentos.isEmpty() && intentos.peekFirst().isBefore(corte)) {
			intentos.removeFirst();
		}
	}

}
