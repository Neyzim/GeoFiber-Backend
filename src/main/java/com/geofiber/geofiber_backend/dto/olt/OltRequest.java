package com.geofiber.geofiber_backend.dto.olt;

import com.geofiber.geofiber_backend.dto.geo.CoordinateDto;
import com.geofiber.geofiber_backend.enums.InfrastructureStatusEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OltRequest(
    @NotBlank @Size(max = 160) String name,
    @Size(max = 1000) String description,
    InfrastructureStatusEnum usageStatus,
    @Size(max = 120) String model,
    @NotNull @Valid CoordinateDto location
) {
}
