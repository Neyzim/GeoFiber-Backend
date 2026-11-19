package com.geofiber.geofiber_backend.repositories.connection;

import com.geofiber.geofiber_backend.entities.connection.OpticalConnectionEntity;
import com.geofiber.geofiber_backend.enums.OpticalConnectionTypeEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OpticalConnectionRepository extends JpaRepository<OpticalConnectionEntity, UUID> {
    boolean existsByTerminationA_IdOrTerminationB_Id(UUID terminationAId, UUID terminationBId);

    List<OpticalConnectionEntity> findAllByBox_IdAndType(UUID boxId, OpticalConnectionTypeEnum type);

    boolean existsByBox_Id(UUID boxId);
}
