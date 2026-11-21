package com.geofiber.geofiber_backend.repositories.box;

import com.geofiber.geofiber_backend.entities.box.BoxAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BoxRepository extends JpaRepository<BoxAssetEntity, UUID> {
}
