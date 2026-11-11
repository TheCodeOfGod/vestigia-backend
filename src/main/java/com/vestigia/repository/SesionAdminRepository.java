package com.vestigia.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import com.vestigia.model.SesionAdminModel;

public interface SesionAdminRepository extends JpaRepository<SesionAdminModel, String> {

	@Modifying
	int deleteByUsuarioId(UUID usuarioId);

	@Modifying
	int deleteByExpiraEnBefore(Instant limite);

}
