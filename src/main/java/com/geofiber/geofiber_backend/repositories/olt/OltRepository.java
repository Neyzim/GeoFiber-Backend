package com.geofiber.geofiber_backend.repositories.olt;

import com.geofiber.geofiber_backend.entities.olt.OltAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OltRepository extends JpaRepository<OltAssetEntity, UUID> {
}
