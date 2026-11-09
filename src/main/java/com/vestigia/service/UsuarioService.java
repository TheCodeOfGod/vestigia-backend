package com.vestigia.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.dto.ActualizarUsuarioRequest;
import com.vestigia.dto.CrearUsuarioRequest;
import com.vestigia.dto.Usuario;
import com.vestigia.model.UsuarioModel;
import com.vestigia.repository.UsuarioRepository;

/** Alta, cambio y baja de los usuarios de administración. Siempre queda al menos un administrador activo. */
@Service
public class UsuarioService {

	public static final String CORREO_DUPLICADO = "correo_duplicado";

	public static final String ULTIMO_ADMIN = "ultimo_admin";

	private final UsuarioRepository usuarios;

	private final AuthService auth;

	private final PasswordEncoder encoder;

	private final Clock clock;

	public UsuarioService(UsuarioRepository usuarios, AuthService auth, PasswordEncoder encoder, Clock clock) {
		this.usuarios = usuarios;
		this.auth = auth;
		this.encoder = encoder;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<Usuario> listar() {
		return usuarios.findAll(Sort.by("creadoEn", "correo")).stream().map(Usuario::de).toList();
	}

	@Transactional(readOnly = true)
	public Usuario obtener(UUID id) {
		return Usuario.de(buscar(id));
	}

	@Transactional
	public Usuario crear(CrearUsuarioRequest request) {
		String correo = AuthService.normalizarCorreo(request.correo());
		if (usuarios.existsByCorreo(correo)) {
			throw correoDuplicado();
		}
		UsuarioModel usuario = new UsuarioModel();
		usuario.setNombre(request.nombre().trim());
		usuario.setCorreo(correo);
		usuario.setPasswordHash(encoder.encode(request.password()));
		usuario.setRol(request.rol());
		usuario.setActivo(true);
		usuario.setCreadoEn(clock.instant());
		try {
			return Usuario.de(usuarios.saveAndFlush(usuario));
		}
		catch (DataIntegrityViolationException ex) {
			throw correoDuplicado();
		}
	}

	@Transactional
	public Usuario actualizar(UUID id, ActualizarUsuarioRequest request) {
		UsuarioModel usuario = buscar(id);
		boolean pierdeAdminActivo = usuario.isActivo() && usuario.getRol() == UsuarioModel.Rol.admin
				&& (Boolean.FALSE.equals(request.activo())
						|| (request.rol() != null && request.rol() != UsuarioModel.Rol.admin));
		if (pierdeAdminActivo) {
			exigirOtroAdminActivo(usuario);
		}
		if (request.nombre() != null) {
			usuario.setNombre(request.nombre().trim());
		}
		if (request.rol() != null) {
			usuario.setRol(request.rol());
		}
		boolean desactiva = usuario.isActivo() && Boolean.FALSE.equals(request.activo());
		if (request.activo() != null) {
			usuario.setActivo(request.activo());
		}
		if (request.password() != null) {
			usuario.setPasswordHash(encoder.encode(request.password()));
		}
		if (desactiva || request.password() != null) {
			auth.cerrarSesionesDe(usuario.getId());
		}
		return Usuario.de(usuario);
	}

	@Transactional
	public void eliminar(UUID id) {
		UsuarioModel usuario = buscar(id);
		if (usuario.isActivo() && usuario.getRol() == UsuarioModel.Rol.admin) {
			exigirOtroAdminActivo(usuario);
		}
		usuarios.delete(usuario);
	}

	private void exigirOtroAdminActivo(UsuarioModel quitado) {
		boolean hayOtro = usuarios.bloquearAdminsActivos().stream().anyMatch(a -> !a.getId().equals(quitado.getId()));
		if (!hayOtro) {
			throw new ApiException(HttpStatus.CONFLICT, ULTIMO_ADMIN,
					"No se puede quitar al último administrador activo", List.of());
		}
	}

	private UsuarioModel buscar(UUID id) {
		return usuarios.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el usuario " + id));
	}

	private static ApiException correoDuplicado() {
		return new ApiException(HttpStatus.CONFLICT, CORREO_DUPLICADO, "Ya hay un usuario con ese correo",
				List.of(new ApiException.Detalle("correo", "ya está registrado")));
	}

}
