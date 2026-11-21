package com.geofiber.geofiber_backend.dto.trace;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.geofiber.geofiber_backend.dto.geo.CoordinateDto;
import com.geofiber.geofiber_backend.enums.OpticalColorEnum;
import com.geofiber.geofiber_backend.enums.trace.TraceBranchStatusEnum;
import com.geofiber.geofiber_backend.enums.trace.TraceBreakReasonEnum;
import com.geofiber.geofiber_backend.enums.trace.TraceDirectionEnum;
import com.geofiber.geofiber_backend.enums.trace.TraceItemKindEnum;
import com.geofiber.geofiber_backend.enums.trace.TraceStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/** Caminho do sinal a partir de um ponto da rede. Campos sem valor saem ausentes do JSON. */
public record TraceResponse(
    TraceItem start,
    TraceDirectionEnum direction,
    TraceStatusEnum status,
    List<TraceStep> steps,
    List<TraceBreak> breaks,
    @Schema(description = "Só no rastreio de splitter (ou que desce até um): resumo de cada saída")
    List<TraceBranch> branches,
    @Schema(description = "Clientes alcançados pelo caminho (descida até porta de atendimento)")
    List<TraceCustomer> customers
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TraceItem(TraceItemKindEnum kind, UUID id, String label, List<CoordinateDto> coordinates) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TraceStep(
        TraceItemKindEnum kind, UUID id, String label,
        FiberInfo fiber, TracePosition position, List<CoordinateDto> coordinates
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FiberInfo(OpticalColorEnum color, int number, OpticalColorEnum tube) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TracePosition(int tray, int number) {
    }

    public record TraceBreak(TraceBreakReasonEnum reason, TraceItem at) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TraceBranch(int outputNumber, TraceBranchStatusEnum status, int customerCount, UUID traceRef) {
    }

    public record TraceCustomer(UUID id, String name) {
    }
}
