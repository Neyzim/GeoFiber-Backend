package com.geofiber.geofiber_backend.repositories.termination;

import com.geofiber.geofiber_backend.entities.termination.FiberTerminationEntity;
import com.geofiber.geofiber_backend.enums.FiberEndEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FiberTerminationRepository extends JpaRepository<FiberTerminationEntity, UUID> {
    Optional<FiberTerminationEntity> findByFiber_IdAndFiberEnd(UUID fiberId, FiberEndEnum fiberEnd);

    List<FiberTerminationEntity> findAllByFiber_Id(UUID fiberId);

    Optional<FiberTerminationEntity> findByOltPort_Id(UUID oltPortId);

    Optional<FiberTerminationEntity> findBySplitterPort_Id(UUID splitterPortId);

    Optional<FiberTerminationEntity> findByServicePort_Id(UUID servicePortId);

    List<FiberTerminationEntity> findAllByServicePort_IdIn(Collection<UUID> servicePortIds);

    List<FiberTerminationEntity> findAllByOltPort_IdIn(Collection<UUID> oltPortIds);

    List<FiberTerminationEntity> findAllBySplitterPort_IdIn(Collection<UUID> splitterPortIds);

    /** Alguma ponta de fibra do cabo está ligada a algo dentro da caixa (porta de splitter, porta de atendimento, emenda ou conector)? */
    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM fiber_termination t
            JOIN fiber f ON f.id = t.fiber_id
            LEFT JOIN splitter_port sp ON sp.id = t.splitter_port_id
            LEFT JOIN splitter s ON s.id = sp.splitter_id
            LEFT JOIN service_port svc ON svc.id = t.service_port_id
            WHERE f.cable_id = :cableId
              AND (s.box_id = :boxId OR svc.box_id = :boxId OR EXISTS (
                  SELECT 1 FROM optical_connection c
                  WHERE c.box_id = :boxId
                    AND (c.termination_a_id = t.id OR c.termination_b_id = t.id)))
        )
        """, nativeQuery = true)
    boolean existsCableConnectionInBox(@Param("cableId") UUID cableId, @Param("boxId") UUID boxId);

    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM fiber_termination t
            JOIN fiber f ON f.id = t.fiber_id
            JOIN olt_port p ON p.id = t.olt_port_id
            WHERE f.cable_id = :cableId AND p.olt_id = :oltId
        )
        """, nativeQuery = true)
    boolean existsCableConnectionInOlt(@Param("cableId") UUID cableId, @Param("oltId") UUID oltId);
}
