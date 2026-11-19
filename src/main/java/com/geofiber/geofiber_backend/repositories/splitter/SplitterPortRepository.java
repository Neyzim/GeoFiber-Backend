package com.geofiber.geofiber_backend.repositories.splitter;

import com.geofiber.geofiber_backend.entities.splitter.SplitterPortEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SplitterPortRepository extends JpaRepository<SplitterPortEntity, UUID> {
    List<SplitterPortEntity> findAllBySplitter_IdOrderByPortNumberAsc(UUID splitterId);

    List<SplitterPortEntity> findAllBySplitter_Id(UUID splitterId);
}
