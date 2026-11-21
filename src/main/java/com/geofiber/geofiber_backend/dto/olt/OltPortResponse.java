package com.geofiber.geofiber_backend.dto.olt;

import com.geofiber.geofiber_backend.enums.FiberEndEnum;
import com.geofiber.geofiber_backend.enums.PortStatusEnum;

import java.util.UUID;

public record OltPortResponse(
    UUID id, UUID oltId, Integer slotNumber, Integer portNumber,
    PortStatusEnum status, UUID fiberId, FiberEndEnum fiberEnd
) {
}
