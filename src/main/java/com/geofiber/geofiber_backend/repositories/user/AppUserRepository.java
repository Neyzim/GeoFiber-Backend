package com.geofiber.geofiber_backend.repositories.user;

import com.geofiber.geofiber_backend.entities.user.AppUserEntity;
import com.geofiber.geofiber_backend.enums.user.UserRoleEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {
    Optional<AppUserEntity> findByEmailIgnoreCase(String email);

    Optional<AppUserEntity> findFirstByRoleOrderByCreatedAtAsc(UserRoleEnum role);

    List<AppUserEntity> findAllByOrderByNameAsc();
}
