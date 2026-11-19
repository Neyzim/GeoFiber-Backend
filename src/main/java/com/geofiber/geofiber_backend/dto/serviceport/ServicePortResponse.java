package com.geofiber.geofiber_backend.dto.serviceport;

import com.geofiber.geofiber_backend.enums.FiberEndEnum;
import com.geofiber.geofiber_backend.enums.PortStatusEnum;

import java.util.UUID;

public record ServicePortResponse(
    UUID id, UUID boxId, Integer portNumber, String label, PortStatusEnum status,
    UUID splitterPortId, UUID fiberId, FiberEndEnum fiberEnd, UUID customerId
) {
}
