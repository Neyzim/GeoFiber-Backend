package com.geofiber.geofiber_backend.dto.splitter;

import io.swagger.v3.oas.annotations.media.Schema;
import com.geofiber.geofiber_backend.enums.SplitterRatioEnum;
import jakarta.validation.constraints.NotNull;

public record SplitterRequest(
    @NotNull SplitterRatioEnum ratio,
    boolean balanced,
    @Schema(description = "Gera uma porta de atendimento por saída. Padrão: true em CTO, false nas demais") Boolean createServicePorts
) {
}
