package com.geofiber.geofiber_backend.dto.splitter;

import com.geofiber.geofiber_backend.enums.SplitterRatioEnum;

import java.util.List;
import java.util.UUID;

public record SplitterResponse(
    UUID id, UUID boxId, SplitterRatioEnum ratio, boolean balanced,
    List<SplitterPortResponse> ports
) {
}
