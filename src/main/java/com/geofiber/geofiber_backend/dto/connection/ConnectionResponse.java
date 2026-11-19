package com.geofiber.geofiber_backend.dto.connection;

import com.geofiber.geofiber_backend.enums.FiberEndEnum;
import com.geofiber.geofiber_backend.enums.OpticalConnectionTypeEnum;

import java.util.UUID;

public record ConnectionResponse(
    UUID id, UUID boxId, OpticalConnectionTypeEnum type,
    UUID fiberAId, FiberEndEnum fiberAEnd, UUID fiberBId, FiberEndEnum fiberBEnd, Double attenuationDb
) {
}
