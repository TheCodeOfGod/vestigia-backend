package com.vestigia.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.vestigia.dto.CrearUsuarioRequest;
import com.vestigia.model.UsuarioModel;
import com.vestigia.service.UsuarioService;

/**
 * Crea el primer administrador al arrancar, solo si no hay ningún usuario y las variables ADMIN_BOOTSTRAP_EMAIL y
 * ADMIN_BOOTSTRAP_PASSWORD existen. Es la única forma de tener el primero, porque crear usuarios exige ser admin.
 */
@Component
public class AdminInicial implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminInicial.class);

	private final UsuarioService usuarios;

	private final String correo;

	private final String password;

	public AdminInicial(UsuarioService usuarios, @Value("${vestigia.admin.correo:}") String correo,
			@Value("${vestigia.admin.password:}") String password) {
		this.usuarios = usuarios;
		this.correo = correo;
		this.password = password;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (correo.isBlank() || password.isBlank() || !usuarios.listar().isEmpty()) {
			return;
		}
		try {
			usuarios.crear(new CrearUsuarioRequest("Administrador", correo, password, UsuarioModel.Rol.admin));
			log.info("Administrador inicial creado: {}", correo.trim());
		}
		catch (RuntimeException ex) {
			log.error("No se pudo crear el administrador inicial: {}", ex.getMessage());
		}
	}

}
