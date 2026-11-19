package com.geofiber.geofiber_backend.dto.cable;

import com.geofiber.geofiber_backend.enums.cable.CableAttachmentRoleEnum;

import java.util.UUID;

public record CableAttachmentResponse(
    Integer sequenceNumber, CableAttachmentRoleEnum role, UUID boxId, UUID oltId, String elementName
) {
}
