package com.geofiber.geofiber_backend.dto.serviceport;

import io.swagger.v3.oas.annotations.media.Schema;
import com.geofiber.geofiber_backend.enums.FiberEndEnum;
import com.geofiber.geofiber_backend.enums.PortStatusEnum;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @param splitterPortId saída de splitter da mesma caixa que alimenta a porta
 * @param fiberId        fibra que alimenta a porta (alternativa ao splitter)
 * @param fiberEnd       ponta da fibra; padrão B (lado do cliente)
 */
public record ServicePortRequest(
    @NotNull @Positive Integer portNumber,
    @Size(max = 80) String label,
    PortStatusEnum status,
    @Schema(description = "Saída de splitter da mesma caixa que alimenta a porta (alternativa a fiberId)") UUID splitterPortId,
    @Schema(description = "Fibra que alimenta a porta (alternativa a splitterPortId); o cabo precisa estar anexado à caixa") UUID fiberId,
    @Schema(description = "Ponta da fibra; padrão B (lado do cliente)", example = "B") FiberEndEnum fiberEnd
) {
}
