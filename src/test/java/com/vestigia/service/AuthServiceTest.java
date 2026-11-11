package com.vestigia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.vestigia.RelojDePrueba;
import com.vestigia.dto.SesionIniciada;
import com.vestigia.model.SesionAdminModel;
import com.vestigia.model.UsuarioModel;
import com.vestigia.repository.SesionAdminRepository;
import com.vestigia.repository.UsuarioRepository;

class AuthServiceTest {

	private final UsuarioRepository usuarios = mock(UsuarioRepository.class);

	private final SesionAdminRepository sesiones = mock(SesionAdminRepository.class);

	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

	private final RelojDePrueba reloj = new RelojDePrueba();

	private AuthService servicio;

	private UsuarioModel raul;

	@BeforeEach
	void preparar() {
		servicio = new AuthService(usuarios, sesiones, encoder, reloj, Duration.ofHours(8), 3, Duration.ofMinutes(15));
		raul = usuario("raul@vestigia.site", "contraseña-larga", true);
		when(usuarios.findByCorreo("raul@vestigia.site")).thenReturn(Optional.of(raul));
		when(usuarios.findById(raul.getId())).thenReturn(Optional.of(raul));
	}

	@Test
	@DisplayName("Un inicio de sesión correcto guarda solo el hash del token y vence a las 8 horas")
	void loginCorrecto() {
		SesionIniciada sesion = servicio.iniciarSesion("  Raul@Vestigia.site ", "contraseña-larga");

		ArgumentCaptor<SesionAdminModel> guardada = ArgumentCaptor.forClass(SesionAdminModel.class);
		verify(sesiones).save(guardada.capture());
		assertThat(sesion.token()).hasSize(43);
		assertThat(guardada.getValue().getTokenHash()).hasSize(64).isNotEqualTo(sesion.token());
		assertThat(guardada.getValue().getUsuarioId()).isEqualTo(raul.getId());
		assertThat(Duration.between(guardada.getValue().getCreadaEn(), sesion.expiraEn())).isEqualTo(Duration.ofHours(8));
		assertThat(sesion.usuario().correo()).isEqualTo("raul@vestigia.site");
	}

	@Test
	@DisplayName("Contraseña mala, correo desconocido y usuario desactivado dan el mismo 401")
	void fallosIndistinguibles() {
		raul.setActivo(false);
		when(usuarios.findByCorreo("nadie@vestigia.site")).thenReturn(Optional.empty());
		UsuarioModel otro = usuario("otro@vestigia.site", "otra-contraseña", true);
		when(usuarios.findByCorreo("otro@vestigia.site")).thenReturn(Optional.of(otro));

		for (String[] intento : new String[][] { { "raul@vestigia.site", "contraseña-larga" },
				{ "nadie@vestigia.site", "lo-que-sea" }, { "otro@vestigia.site", "equivocada" } }) {
			assertThatThrownBy(() -> servicio.iniciarSesion(intento[0], intento[1]))
					.isInstanceOfSatisfying(ResponseStatusException.class, e -> {
						assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
						assertThat(e.getReason()).isEqualTo(AuthService.CREDENCIALES_INVALIDAS);
					});
		}
		verify(sesiones, never()).save(any());
	}

	@Test
	@DisplayName("Tras 3 fallos seguidos con el mismo correo responde 429, incluso con la contraseña buena; pasada la ventana vuelve a dejar entrar")
	void limiteDeIntentos() {
		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> servicio.iniciarSesion("raul@vestigia.site", "mala"))
					.isInstanceOf(ResponseStatusException.class);
		}

		assertThatThrownBy(() -> servicio.iniciarSesion("raul@vestigia.site", "contraseña-larga"))
				.isInstanceOfSatisfying(ApiException.class, e -> {
					assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
					assertThat(e.getCodigo()).isEqualTo(AuthService.DEMASIADOS_INTENTOS);
				});

		reloj.avanzar(Duration.ofMinutes(16));
		assertThat(servicio.iniciarSesion("raul@vestigia.site", "contraseña-larga").token()).isNotBlank();
	}

	@Test
	@DisplayName("Un inicio de sesión correcto borra los fallos acumulados")
	void exitoLimpiaLosFallos() {
		for (int i = 0; i < 2; i++) {
			assertThatThrownBy(() -> servicio.iniciarSesion("raul@vestigia.site", "mala"))
					.isInstanceOf(ResponseStatusException.class);
		}
		servicio.iniciarSesion("raul@vestigia.site", "contraseña-larga");

		for (int i = 0; i < 2; i++) {
			assertThatThrownBy(() -> servicio.iniciarSesion("raul@vestigia.site", "mala"))
					.isInstanceOf(ResponseStatusException.class)
					.extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		}
	}

	@Test
	@DisplayName("autenticar acepta una sesión vigente y rechaza la vencida, la desconocida y la de un usuario desactivado")
	void autenticar() {
		when(sesiones.findById(any())).thenReturn(Optional.empty());

		assertThat(servicio.autenticar("token-desconocido")).isEmpty();

		SesionIniciada real = servicio.iniciarSesion("raul@vestigia.site", "contraseña-larga");
		ArgumentCaptor<SesionAdminModel> guardada = ArgumentCaptor.forClass(SesionAdminModel.class);
		verify(sesiones).save(guardada.capture());
		when(sesiones.findById(guardada.getValue().getTokenHash())).thenReturn(Optional.of(guardada.getValue()));

		assertThat(servicio.autenticar(real.token())).hasValueSatisfying(c -> {
			assertThat(c.usuarioId()).isEqualTo(raul.getId());
			assertThat(c.rol()).isEqualTo(UsuarioModel.Rol.admin);
		});

		reloj.avanzar(Duration.ofHours(8).plusSeconds(1));
		assertThat(servicio.autenticar(real.token())).isEmpty();

		reloj.reiniciar();
		raul.setActivo(false);
		assertThat(servicio.autenticar(real.token())).isEmpty();
	}

	@Test
	void cerrarSesionBorraLaFila() {
		servicio.cerrarSesion(new AuthService.CredencialAdmin(raul.getId(), UsuarioModel.Rol.admin, "hash"));

		verify(sesiones).deleteById("hash");
	}

	private UsuarioModel usuario(String correo, String password, boolean activo) {
		UsuarioModel u = new UsuarioModel();
		ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
		u.setCorreo(correo);
		u.setNombre("Prueba");
		u.setRol(UsuarioModel.Rol.admin);
		u.setPasswordHash(encoder.encode(password));
		u.setActivo(activo);
		return u;
	}

}
