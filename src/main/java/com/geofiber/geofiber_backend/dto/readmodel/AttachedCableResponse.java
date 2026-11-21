package com.geofiber.geofiber_backend.dto.readmodel;

import com.geofiber.geofiber_backend.enums.InfrastructureStatusEnum;
import com.geofiber.geofiber_backend.enums.cable.CableAttachmentRoleEnum;
import com.geofiber.geofiber_backend.enums.cable.CableRoleEnum;

import java.util.UUID;

/** Cabo anexado a uma caixa ou OLT, com o papel desse elemento na lista do cabo. */
public record AttachedCableResponse(
    UUID cableId,
    String name,
    CableRoleEnum role,
    InfrastructureStatusEnum usageStatus,
    Integer fiberCapacity,
    CableAttachmentRoleEnum attachmentRole,
    Integer sequenceNumber
) {
}
