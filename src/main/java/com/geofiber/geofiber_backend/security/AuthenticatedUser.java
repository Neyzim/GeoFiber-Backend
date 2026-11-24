package com.geofiber.geofiber_backend.security;

import com.geofiber.geofiber_backend.enums.user.UserRoleEnum;

import java.util.UUID;

/** Usuário autenticado pela requisição atual, extraído do access token. */
public record AuthenticatedUser(UUID id, UserRoleEnum role) {
}
