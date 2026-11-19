package com.geofiber.geofiber_backend.dto.cable;

import com.geofiber.geofiber_backend.enums.OpticalColorEnum;

import java.util.UUID;

public record LooseTubeResponse(
    UUID id, UUID cableId, Integer tubeNumber, Integer capacity, OpticalColorEnum color
) {
}
