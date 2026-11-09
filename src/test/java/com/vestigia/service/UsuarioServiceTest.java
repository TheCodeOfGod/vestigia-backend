package com.vestigia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.RelojDePrueba;
import com.vestigia.dto.ActualizarUsuarioRequest;
import com.vestigia.dto.CrearUsuarioRequest;
import com.vestigia.dto.Usuario;
import com.vestigia.model.UsuarioModel;
import com.vestigia.repository.UsuarioRepository;

class UsuarioServiceTest {

	private final UsuarioRepository usuarios = mock(UsuarioRepository.class);

	private final AuthService auth = mock(AuthService.class);

	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

	private UsuarioService servicio;

	private UsuarioModel raul;

	private UsuarioModel maria;

	@BeforeEach
	void preparar() {
		servicio = new UsuarioService(usuarios, auth, encoder, new RelojDePrueba());
		raul = admin("raul@vestigia.site");
		maria = admin("maria@vestigia.site");
		when(usuarios.findById(raul.getId())).thenReturn(Optional.of(raul));
		when(usuarios.findById(maria.getId())).thenReturn(Optional.of(maria));
		when(usuarios.saveAndFlush(any(UsuarioModel.class))).thenAnswer(i -> i.getArgument(0));
	}

	@Test
	@DisplayName("Crear recorta el nombre, pasa el correo a minúsculas y guarda la contraseña con BCrypt")
	void crear() {
		Usuario creado = servicio.crear(new CrearUsuarioRequest(" Ana ", " Ana@Vestigia.SITE ", "contraseña-nueva",
				UsuarioModel.Rol.admin));

		assertThat(creado.nombre()).isEqualTo("Ana");
		assertThat(creado.correo()).isEqualTo("ana@vestigia.site");
		assertThat(creado.activo()).isTrue();
		org.mockito.ArgumentCaptor<UsuarioModel> guardado = org.mockito.ArgumentCaptor.forClass(UsuarioModel.class);
		verify(usuarios).saveAndFlush(guardado.capture());
		assertThat(guardado.getValue().getPasswordHash()).startsWith("$2").isNotEqualTo("contraseña-nueva");
		assertThat(encoder.matches("contraseña-nueva", guardado.getValue().getPasswordHash())).isTrue();
	}

	@Test
	@DisplayName("Un correo repetido da 409 correo_duplicado, lo detecte la consulta o el índice único")
	void correoDuplicado() {
		when(usuarios.existsByCorreo("raul@vestigia.site")).thenReturn(true);
		CrearUsuarioRequest repetido = new CrearUsuarioRequest("Otro", "RAUL@vestigia.site", "contraseña-nueva",
				UsuarioModel.Rol.admin);

		assertThatThrownBy(() -> servicio.crear(repetido)).isInstanceOfSatisfying(ApiException.class, e -> {
			assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
			assertThat(e.getCodigo()).isEqualTo(UsuarioService.CORREO_DUPLICADO);
			assertThat(e.getDetalles()).extracting(ApiException.Detalle::campo).containsExactly("correo");
		});

		when(usuarios.existsByCorreo("carrera@vestigia.site")).thenReturn(false);
		when(usuarios.saveAndFlush(any(UsuarioModel.class))).thenThrow(new DataIntegrityViolationException("unico"));
		assertThatThrownBy(() -> servicio.crear(new CrearUsuarioRequest("C", "carrera@vestigia.site",
				"contraseña-nueva", UsuarioModel.Rol.admin))).isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCodigo()).isEqualTo(UsuarioService.CORREO_DUPLICADO));
	}

	@Test
	@DisplayName("No deja desactivar, quitar el rol ni eliminar al único administrador activo")
	void ultimoAdmin() {
		when(usuarios.bloquearAdminsActivos()).thenReturn(List.of(raul));

		assertThatThrownBy(() -> servicio.actualizar(raul.getId(), new ActualizarUsuarioRequest(null, null, false, null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCodigo()).isEqualTo(UsuarioService.ULTIMO_ADMIN));
		assertThatThrownBy(() -> servicio.eliminar(raul.getId())).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
		assertThat(raul.isActivo()).isTrue();
		verify(usuarios, never()).delete(any());
	}

	@Test
	@DisplayName("Con otro administrador activo sí se puede desactivar o eliminar, y desactivar cierra sus sesiones")
	void conOtroAdmin() {
		when(usuarios.bloquearAdminsActivos()).thenReturn(List.of(raul, maria));

		Usuario cambiado = servicio.actualizar(maria.getId(), new ActualizarUsuarioRequest(null, null, false, null));
		assertThat(cambiado.activo()).isFalse();
		verify(auth).cerrarSesionesDe(maria.getId());

		servicio.eliminar(raul.getId());
		verify(usuarios).delete(raul);
	}

	@Test
	@DisplayName("Cambiar la contraseña la guarda con BCrypt y cierra las sesiones; cambiar solo el nombre no")
	void cambios() {
		servicio.actualizar(maria.getId(), new ActualizarUsuarioRequest(" María ", null, null, null));
		verify(auth, never()).cerrarSesionesDe(any());
		assertThat(maria.getNombre()).isEqualTo("María");

		servicio.actualizar(maria.getId(), new ActualizarUsuarioRequest(null, null, null, "contraseña-otra"));
		assertThat(encoder.matches("contraseña-otra", maria.getPasswordHash())).isTrue();
		verify(auth).cerrarSesionesDe(maria.getId());
	}

	@Test
	void usuarioInexistenteDa404() {
		UUID id = UUID.randomUUID();
		when(usuarios.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.eliminar(id)).isInstanceOfSatisfying(ResponseStatusException.class,
				e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
	}

	private UsuarioModel admin(String correo) {
		UsuarioModel u = new UsuarioModel();
		ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
		u.setCorreo(correo);
		u.setNombre("Prueba");
		u.setRol(UsuarioModel.Rol.admin);
		u.setPasswordHash("$2a$04$x");
		u.setActivo(true);
		return u;
	}

}
