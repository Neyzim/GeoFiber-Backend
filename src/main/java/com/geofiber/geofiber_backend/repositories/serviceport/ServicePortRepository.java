package com.geofiber.geofiber_backend.repositories.serviceport;

import com.geofiber.geofiber_backend.entities.serviceport.ServicePortEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServicePortRepository extends JpaRepository<ServicePortEntity, UUID> {
    List<ServicePortEntity> findAllByBox_IdOrderByPortNumberAsc(UUID boxId);

    boolean existsByBox_Id(UUID boxId);

    Optional<ServicePortEntity> findBySplitterPort_Id(UUID splitterPortId);

    List<ServicePortEntity> findAllBySplitterPort_IdIn(Collection<UUID> splitterPortIds);

    @Query("select coalesce(max(p.portNumber), 0) from ServicePortEntity p where p.box.id = :boxId")
    int findLastPortNumber(@Param("boxId") UUID boxId);
}
