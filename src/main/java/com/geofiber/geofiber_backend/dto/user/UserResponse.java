package com.geofiber.geofiber_backend.dto.user;

import com.geofiber.geofiber_backend.entities.user.AppUserEntity;
import com.geofiber.geofiber_backend.enums.user.UserRoleEnum;

import java.util.UUID;

/** Usuário sem a senha. */
public record UserResponse(UUID id, String name, String email, UserRoleEnum role, boolean active) {

    public static UserResponse from(AppUserEntity user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isActive());
    }
}
