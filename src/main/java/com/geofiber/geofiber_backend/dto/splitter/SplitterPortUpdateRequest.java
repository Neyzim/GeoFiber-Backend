package com.geofiber.geofiber_backend.dto.splitter;

import io.swagger.v3.oas.annotations.media.Schema;
import com.geofiber.geofiber_backend.enums.FiberEndEnum;
import com.geofiber.geofiber_backend.enums.PortStatusEnum;
import jakarta.validation.constraints.DecimalMin;

import java.util.UUID;

/**
 * @param fiberId  fibra ligada à porta; nulo deixa a porta sem fibra
 * @param fiberEnd ponta da fibra; padrão B na porta de entrada e A nas portas de saída
 */
public record SplitterPortUpdateRequest(
    @DecimalMin("0.0") Double attenuationDb,
    PortStatusEnum status,
    @Schema(description = "Fibra ligada à porta; nulo desliga. Saída que alimenta porta de atendimento não aceita fibra") UUID fiberId,
    @Schema(description = "Padrão B na porta de entrada e A nas saídas") FiberEndEnum fiberEnd
) {
}
