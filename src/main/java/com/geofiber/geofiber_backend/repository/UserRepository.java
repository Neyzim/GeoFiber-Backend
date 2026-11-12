package com.geofiber.geofiber_backend.repository;

import com.geofiber.geofiber_backend.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
}
