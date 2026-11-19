package com.geofiber.geofiber_backend.repositories.cable;

import com.geofiber.geofiber_backend.entities.cable.CableAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CableRepository extends JpaRepository<CableAssetEntity, UUID> {
}
