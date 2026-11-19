package com.geofiber.geofiber_backend.repositories.splitter;

import com.geofiber.geofiber_backend.entities.splitter.SplitterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SplitterRepository extends JpaRepository<SplitterEntity, UUID> {
    List<SplitterEntity> findAllByBox_Id(UUID boxId);

    boolean existsByBox_Id(UUID boxId);
}
