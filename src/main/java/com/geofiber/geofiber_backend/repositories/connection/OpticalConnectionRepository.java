package com.geofiber.geofiber_backend.repositories.connection;

import com.geofiber.geofiber_backend.entities.connection.OpticalConnectionEntity;
import com.geofiber.geofiber_backend.enums.OpticalConnectionTypeEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OpticalConnectionRepository extends JpaRepository<OpticalConnectionEntity, UUID> {
    boolean existsByTerminationA_IdOrTerminationB_Id(UUID terminationAId, UUID terminationBId);

    List<OpticalConnectionEntity> findAllByBox_IdAndType(UUID boxId, OpticalConnectionTypeEnum type);

    boolean existsByBox_Id(UUID boxId);

    /** Conexão que usa a ponta (como A ou como B); cada ponta está em no máximo uma conexão. */
    Optional<OpticalConnectionEntity> findFirstByTerminationA_IdOrTerminationB_Id(UUID terminationAId, UUID terminationBId);

    Optional<OpticalConnectionEntity> findFirstByBox_IdOrderByIdAsc(UUID boxId);

    List<OpticalConnectionEntity> findAllByTerminationA_IdInOrTerminationB_IdIn(
        java.util.Collection<UUID> terminationAIds, java.util.Collection<UUID> terminationBIds);
}
