package com.geofiber.geofiber_backend.repositories.tray;

import com.geofiber.geofiber_backend.entities.tray.TrayEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrayRepository extends JpaRepository<TrayEntity, UUID> {
    Optional<TrayEntity> findByBox_IdAndTrayNumber(UUID boxId, Integer trayNumber);

    List<TrayEntity> findAllByBox_IdOrderByTrayNumberAsc(UUID boxId);

    long countByBox_Id(UUID boxId);

    boolean existsByBox_Id(UUID boxId);

    @org.springframework.data.jpa.repository.Query(
        "select coalesce(max(t.trayNumber), 0) from TrayEntity t where t.box.id = :boxId")
    int findHighestTrayNumber(@org.springframework.data.repository.query.Param("boxId") UUID boxId);
}
