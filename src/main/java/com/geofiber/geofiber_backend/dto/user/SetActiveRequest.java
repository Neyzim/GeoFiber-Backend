package com.geofiber.geofiber_backend.dto.user;

import jakarta.validation.constraints.NotNull;

public record SetActiveRequest(@NotNull Boolean active) {
}
