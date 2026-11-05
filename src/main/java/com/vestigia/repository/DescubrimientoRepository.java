package com.vestigia.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vestigia.model.DescubrimientoModel;

public interface DescubrimientoRepository extends JpaRepository<DescubrimientoModel, Long> {

	List<DescubrimientoModel> findBySalaIdOrderByCreadoEnAscIdAsc(UUID salaId);

}
