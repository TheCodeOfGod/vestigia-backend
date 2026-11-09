package com.vestigia.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vestigia.model.EventoModel;

public interface EventoRepository extends JpaRepository<EventoModel, Long> {

	List<EventoModel> findBySalaIdOrderByCreadoEnAscIdAsc(UUID salaId);

}
