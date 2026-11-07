package com.vestigia.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.vestigia.model.SalaModel;
import com.vestigia.model.SalaModel.Estado;

import jakarta.persistence.LockModeType;

public interface SalaRepository extends JpaRepository<SalaModel, UUID> {

	@Query("select s.codigo from SalaModel s where s.estado in :estados")
	List<String> findCodigosByEstadoIn(Collection<Estado> estados);

	Optional<SalaModel> findByCodigoAndEstadoIn(String codigo, Collection<Estado> estados);

	@Query("""
			select s from SalaModel s
			where (s.tokenJuegoHash = :hash or s.tokenAppHash = :hash) and s.estado in :estados
			""")
	Optional<SalaModel> findByTokenHashAndEstadoIn(String hash, Collection<Estado> estados);

	/**
	 * Enlaza la app en un solo UPDATE condicionado: si dos apps llegan a la vez, Postgres serializa las filas y la
	 * segunda ya no cumple {@code tokenAppHash is null}. Una sala con expira_en vencido no se enlaza aunque el job no la
	 * haya marcado (mismo corte que el job: vencida si expira_en < ahora). Devuelve las filas actualizadas (0 o 1).
	 */
	@Modifying
	@Query("""
			update SalaModel s
			set s.estado = :activa, s.tokenAppHash = :hash, s.ultimaActividad = :ahora, s.expiraEn = :expiraEn
			where s.codigo = :codigo and s.estado = :esperandoApp and s.tokenAppHash is null and s.expiraEn >= :ahora
			""")
	int unirApp(String codigo, String hash, Instant ahora, Instant expiraEn, Estado esperandoApp, Estado activa);

	/**
	 * Bloquea la fila de la sala hasta el final de la transacción ({@code SELECT ... FOR UPDATE}). Las escrituras de una
	 * misma sala se ejecutan así una tras otra: cada una ve lo que la anterior confirmó.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from SalaModel s where s.id = :id")
	Optional<SalaModel> bloquear(UUID id);

	@Modifying
	@Query("update SalaModel s set s.estado = :expirada where s.estado in :vivos and s.expiraEn < :ahora")
	int expirarVencidas(Instant ahora, Collection<Estado> vivos, Estado expirada);

}
