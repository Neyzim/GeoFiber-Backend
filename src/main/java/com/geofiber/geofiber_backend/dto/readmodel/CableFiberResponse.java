package com.geofiber.geofiber_backend.dto.readmodel;

import com.geofiber.geofiber_backend.enums.FiberStatusEnum;
import com.geofiber.geofiber_backend.enums.OpticalColorEnum;

import java.util.List;
import java.util.UUID;

/** Fibra do cabo com seu tubo e o estado das duas pontas. */
public record CableFiberResponse(
    UUID id,
    UUID tubeId,
    Integer tubeNumber,
    OpticalColorEnum tubeColor,
    Integer numberInTube,
    Integer absoluteNumber,
    OpticalColorEnum color,
    FiberStatusEnum status,
    List<FiberEndResponse> ends
) {
}
