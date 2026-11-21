package com.geofiber.geofiber_backend.dto.olt;

import com.geofiber.geofiber_backend.dto.geo.CoordinateDto;
import com.geofiber.geofiber_backend.enums.InfrastructureStatusEnum;

import java.util.UUID;

public record OltResponse(
    UUID id,
    String name,
    String description,
    InfrastructureStatusEnum usageStatus,
    String model,
    CoordinateDto location
) {
}
