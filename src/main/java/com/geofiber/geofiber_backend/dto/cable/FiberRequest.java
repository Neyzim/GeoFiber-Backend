package com.geofiber.geofiber_backend.dto.cable;

import com.geofiber.geofiber_backend.enums.OpticalColorEnum;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FiberRequest(
    @NotNull @Positive Integer numberInTube,
    @NotNull @Positive Integer absoluteNumber,
    @NotNull OpticalColorEnum color
) {
}
