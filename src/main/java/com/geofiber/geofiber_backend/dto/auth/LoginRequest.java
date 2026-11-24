package com.geofiber.geofiber_backend.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @Schema(example = "admin@equipe.com") @NotBlank @Email String email,
    @Schema(example = "senha-do-admin") @NotBlank String password
) {
}
