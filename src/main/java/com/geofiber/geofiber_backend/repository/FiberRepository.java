package com.geofiber.geofiber_backend.repository;

import com.geofiber.geofiber_backend.entities.CableEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FiberRepository extends JpaRepository<CableEntity, String> {
}
