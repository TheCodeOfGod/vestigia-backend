package com.vestigia.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vestigia.model.PuzzleEstadoModel;

public interface PuzzleEstadoRepository extends JpaRepository<PuzzleEstadoModel, PuzzleEstadoModel.Clave> {

	List<PuzzleEstadoModel> findByClaveSalaId(UUID salaId);

	List<PuzzleEstadoModel> findByClaveSalaIdIn(Collection<UUID> salaIds);

}
