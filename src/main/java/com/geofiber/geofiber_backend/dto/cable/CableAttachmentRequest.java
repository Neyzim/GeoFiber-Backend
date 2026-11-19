package com.geofiber.geofiber_backend.dto.cable;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import com.geofiber.geofiber_backend.enums.cable.CableAttachmentRoleEnum;

import java.util.UUID;

/** Um elemento da lista do cabo; informe exatamente um entre {@code boxId} e {@code oltId}. */
public record CableAttachmentRequest(
    @Schema(description = "START no primeiro elemento, END no último, PASS_THROUGH nos do meio", example = "START") @NotNull CableAttachmentRoleEnum role,
    @Schema(description = "Caixa por onde o cabo passa (informe boxId ou oltId)") UUID boxId,
    @Schema(description = "OLT onde o cabo está ligado (informe boxId ou oltId)") UUID oltId
) {
}
