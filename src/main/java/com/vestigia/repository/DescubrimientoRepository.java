package com.vestigia.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.vestigia.model.DescubrimientoModel;

public interface DescubrimientoRepository extends JpaRepository<DescubrimientoModel, Long> {

	List<DescubrimientoModel> findBySalaIdOrderByCreadoEnAscIdAsc(UUID salaId);

	Optional<DescubrimientoModel> findBySalaIdAndClave(UUID salaId, String clave);

	@Query("select d.clave from DescubrimientoModel d where d.salaId = :salaId")
	List<String> findClavesBySalaId(UUID salaId);

}
