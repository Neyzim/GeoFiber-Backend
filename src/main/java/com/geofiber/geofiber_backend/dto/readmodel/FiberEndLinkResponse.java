package com.geofiber.geofiber_backend.dto.readmodel;

import com.geofiber.geofiber_backend.enums.FiberEndEnum;
import com.geofiber.geofiber_backend.enums.readmodel.FiberEndLinkTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Ligação de uma ponta de fibra. */
public record FiberEndLinkResponse(
    FiberEndLinkTypeEnum type,
    @Schema(description = "Id da porta, da emenda (FUSION) ou da conexão (CONNECTOR)") UUID id,
    @Schema(description = "OLT (OLT_PORT) ou caixa onde a ligação está") UUID elementId,
    @Schema(description = "Fibra do outro lado, em FUSION e CONNECTOR") UUID peerFiberId,
    FiberEndEnum peerFiberEnd
) {
}
