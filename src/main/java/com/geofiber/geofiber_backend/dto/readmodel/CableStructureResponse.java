package com.geofiber.geofiber_backend.dto.readmodel;

import com.geofiber.geofiber_backend.dto.cable.FiberResponse;
import com.geofiber.geofiber_backend.enums.OpticalColorEnum;

import java.util.List;
import java.util.UUID;

public record CableStructureResponse(UUID cableId, List<Tube> tubes) {
    public record Tube(UUID id, Integer tubeNumber, Integer capacity, OpticalColorEnum color, List<FiberResponse> fibers) {
    }
}
