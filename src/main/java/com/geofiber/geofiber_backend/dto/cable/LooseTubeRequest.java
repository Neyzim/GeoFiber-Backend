package com.geofiber.geofiber_backend.dto.cable;

import com.geofiber.geofiber_backend.enums.OpticalColorEnum;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record LooseTubeRequest(
    @NotNull @Positive Integer tubeNumber,
    @NotNull @Positive Integer capacity,
    @NotNull OpticalColorEnum color
) {
}
