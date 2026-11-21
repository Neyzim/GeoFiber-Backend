package com.geofiber.geofiber_backend.repositories.splice;

import com.geofiber.geofiber_backend.entities.splice.SpliceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SpliceRepository extends JpaRepository<SpliceEntity, UUID> {
    List<SpliceEntity> findAllByBox_IdOrderByTray_TrayNumberAscPositionNumberAsc(UUID boxId);

    long countByTray_Id(UUID trayId);

    @Query("select coalesce(max(s.positionNumber), 0) from SpliceEntity s where s.tray.id = :trayId")
    int findLastPositionInTray(@Param("trayId") UUID trayId);

    /** Menor posição livre da bandeja, ou nulo se todas as posições até a capacidade estão ocupadas. */
    @Query(value = """
        SELECT g FROM generate_series(1, :capacity) g
        WHERE NOT EXISTS (SELECT 1 FROM splice s WHERE s.tray_id = :trayId AND s.position_number = g)
        ORDER BY g LIMIT 1""", nativeQuery = true)
    Integer findFirstFreePosition(@Param("trayId") UUID trayId, @Param("capacity") int capacity);
}
