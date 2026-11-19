package com.geofiber.geofiber_backend.repositories.cable;

import com.geofiber.geofiber_backend.entities.cable.FiberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FiberRepository extends JpaRepository<FiberEntity, UUID> {
    List<FiberEntity> findAllByTube_IdOrderByNumberInTubeAsc(UUID tubeId);

    boolean existsByTube_Id(UUID tubeId);

    @org.springframework.data.jpa.repository.Query(
        "select coalesce(max(f.numberInTube), 0) from FiberEntity f where f.tube.id = :tubeId")
    int findHighestNumberInTube(@org.springframework.data.repository.query.Param("tubeId") UUID tubeId);
}
