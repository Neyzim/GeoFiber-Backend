package com.geofiber.geofiber_backend.repository;

import com.geofiber.geofiber_backend.entities.box.BoxModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BoxModelRepository extends JpaRepository<BoxModelEntity, UUID> {
}
