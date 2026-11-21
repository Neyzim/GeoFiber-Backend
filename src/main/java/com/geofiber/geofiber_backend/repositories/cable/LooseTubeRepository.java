package com.geofiber.geofiber_backend.repositories.cable;

import com.geofiber.geofiber_backend.entities.cable.LooseTubeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LooseTubeRepository extends JpaRepository<LooseTubeEntity, UUID> {
    List<LooseTubeEntity> findAllByCable_IdOrderByTubeNumberAsc(UUID cableId);

    @Query("select coalesce(sum(t.capacity), 0) from LooseTubeEntity t where t.cable.id = :cableId")
    int sumCapacityByCable(@Param("cableId") UUID cableId);

    boolean existsByCable_Id(UUID cableId);
}
