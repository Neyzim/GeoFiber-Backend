package com.geofiber.geofiber_backend.repositories.olt;

import com.geofiber.geofiber_backend.entities.olt.OltPortEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OltPortRepository extends JpaRepository<OltPortEntity, UUID> {
    List<OltPortEntity> findAllByOlt_IdOrderBySlotNumberAscPortNumberAsc(UUID oltId);

    boolean existsByOlt_Id(UUID oltId);
}
