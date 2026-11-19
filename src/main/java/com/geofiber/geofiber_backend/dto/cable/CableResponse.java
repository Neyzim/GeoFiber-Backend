package com.geofiber.geofiber_backend.dto.cable;

import com.geofiber.geofiber_backend.dto.geo.CoordinateDto;
import com.geofiber.geofiber_backend.enums.cable.CableRoleEnum;
import com.geofiber.geofiber_backend.enums.InfrastructureStatusEnum;

import java.util.List;
import java.util.UUID;

public record CableResponse(
    UUID id,
    String name,
    String description,
    CableRoleEnum role,
    InfrastructureStatusEnum usageStatus,
    String manufacturer,
    String model,
    Integer fiberCapacity,
    Double lengthMeters,
    List<CoordinateDto> coordinates
) {
}
