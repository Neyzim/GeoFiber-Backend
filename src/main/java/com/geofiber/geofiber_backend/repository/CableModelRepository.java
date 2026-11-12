package com.geofiber.geofiber_backend.repository;

import com.geofiber.geofiber_backend.entities.cable.CableModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository

public interface CableModelRepository extends JpaRepository<CableModelEntity, UUID> {
}
