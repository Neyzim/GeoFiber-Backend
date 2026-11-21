package com.geofiber.geofiber_backend.dto.olt;

import io.swagger.v3.oas.annotations.media.Schema;
import com.geofiber.geofiber_backend.enums.FiberEndEnum;
import com.geofiber.geofiber_backend.enums.PortStatusEnum;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * @param fiberId  fibra ligada à porta; nulo deixa a porta sem fibra
 * @param fiberEnd ponta da fibra ligada à porta; padrão A (lado da OLT)
 */
public record OltPortRequest(
    @NotNull @Positive Integer slotNumber,
    @NotNull @Positive Integer portNumber,
    PortStatusEnum status,
    @Schema(description = "Fibra ligada à porta; nulo deixa a porta sem fibra. O cabo precisa estar anexado à OLT") UUID fiberId,
    @Schema(description = "Ponta da fibra ligada; padrão A (lado da OLT)", example = "A") FiberEndEnum fiberEnd
) {
}
