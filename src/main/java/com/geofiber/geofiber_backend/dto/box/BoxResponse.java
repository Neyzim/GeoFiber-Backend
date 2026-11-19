package com.geofiber.geofiber_backend.dto.box;

import com.geofiber.geofiber_backend.dto.geo.CoordinateDto;
import com.geofiber.geofiber_backend.enums.box.BoxTypeEnum;
import com.geofiber.geofiber_backend.enums.InfrastructureStatusEnum;

import java.util.UUID;

public record BoxResponse(
    UUID id,
    String name,
    String description,
    BoxTypeEnum type,
    InfrastructureStatusEnum usageStatus,
    Integer powerCapacity,
    Integer trayCapacity,
    String manufacturer,
    String model,
    String address,
    CoordinateDto location
) {
}
