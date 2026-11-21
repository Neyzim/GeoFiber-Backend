package com.geofiber.geofiber_backend.repositories.cable;

import com.geofiber.geofiber_backend.entities.cable.CableAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CableAttachmentRepository extends JpaRepository<CableAttachmentEntity, UUID> {
    List<CableAttachmentEntity> findAllByCable_IdOrderBySequenceNumberAsc(UUID cableId);

    boolean existsByCable_IdAndBox_Id(UUID cableId, UUID boxId);

    boolean existsByCable_IdAndOlt_Id(UUID cableId, UUID oltId);

    boolean existsByBox_Id(UUID boxId);

    boolean existsByOlt_Id(UUID oltId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from CableAttachmentEntity a where a.cable.id = :cableId")
    void deleteAllByCableId(@Param("cableId") UUID cableId);
}
