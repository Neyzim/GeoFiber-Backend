package com.geofiber.geofiber_backend.repository;

import com.geofiber.geofiber_backend.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, String> {
}
