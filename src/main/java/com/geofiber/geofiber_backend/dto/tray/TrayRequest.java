package com.geofiber.geofiber_backend.dto.tray;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** @param capacity quantidade de fusões que a bandeja comporta */
public record TrayRequest(
    @NotNull @Positive Integer trayNumber,
    @NotNull @Positive Integer capacity
) {
}
