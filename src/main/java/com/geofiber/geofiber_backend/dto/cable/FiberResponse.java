package com.geofiber.geofiber_backend.dto.cable;

import com.geofiber.geofiber_backend.enums.FiberStatusEnum;
import com.geofiber.geofiber_backend.enums.OpticalColorEnum;

import java.util.UUID;

public record FiberResponse(
    UUID id, UUID cableId, UUID tubeId, Integer numberInTube, Integer absoluteNumber,
    OpticalColorEnum color, FiberStatusEnum status
) {
}
