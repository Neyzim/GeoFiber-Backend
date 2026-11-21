package com.geofiber.geofiber_backend.dto.tray;

import java.util.UUID;

public record TrayResponse(UUID id, UUID boxId, Integer trayNumber, Integer capacity, long usedPositions) {
}
