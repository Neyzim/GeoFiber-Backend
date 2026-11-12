package com.geofiber.geofiber_backend.repository;

import com.geofiber.geofiber_backend.entities.cable.CableEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CableRepository extends JpaRepository<CableEntity, UUID> {

}
