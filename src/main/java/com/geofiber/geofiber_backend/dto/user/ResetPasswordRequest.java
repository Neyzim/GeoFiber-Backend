package com.geofiber.geofiber_backend.dto.user;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(@NotBlank String password) {
}
