package com.geofiber.geofiber_backend.dto.splitter;

import com.geofiber.geofiber_backend.enums.FiberEndEnum;
import com.geofiber.geofiber_backend.enums.PortDirectionEnum;
import com.geofiber.geofiber_backend.enums.PortStatusEnum;

import java.util.UUID;

public record SplitterPortResponse(
    UUID id, UUID splitterId, Integer portNumber, Double attenuationDb,
    PortStatusEnum status, PortDirectionEnum direction,
    UUID fiberId, FiberEndEnum fiberEnd, UUID servicePortId
) {
}
