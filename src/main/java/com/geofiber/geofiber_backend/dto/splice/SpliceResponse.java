package com.geofiber.geofiber_backend.dto.splice;

import com.geofiber.geofiber_backend.enums.FiberEndEnum;

import java.util.UUID;

public record SpliceResponse(
    UUID id, UUID boxId, Integer trayNumber, Integer positionNumber, Double attenuationDb,
    UUID inputFiberId, FiberEndEnum inputFiberEnd,
    UUID outputFiberId, FiberEndEnum outputFiberEnd
) {
}
