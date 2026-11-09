package com.vestigia.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.vestigia.model.UsuarioModel;

import jakarta.persistence.LockModeType;

public interface UsuarioRepository extends JpaRepository<UsuarioModel, UUID> {

	Optional<UsuarioModel> findByCorreo(String correo);

	boolean existsByCorreo(String correo);

	/**
	 * Los administradores activos, bloqueados hasta el commit. Quien va a quitar uno los pide así para que dos
	 * peticiones simultáneas no dejen el sistema sin administradores.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from UsuarioModel u where u.rol = com.vestigia.model.UsuarioModel.Rol.admin and u.activo = true")
	List<UsuarioModel> bloquearAdminsActivos();

}
