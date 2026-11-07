package com.vestigia.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vestigia.model.EventoModel;

public interface EventoRepository extends JpaRepository<EventoModel, Long> {

}
