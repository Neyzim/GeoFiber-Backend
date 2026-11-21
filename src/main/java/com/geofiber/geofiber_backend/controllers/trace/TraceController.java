package com.geofiber.geofiber_backend.controllers.trace;

import com.geofiber.geofiber_backend.dto.trace.TraceResponse;
import com.geofiber.geofiber_backend.enums.trace.TraceDirectionEnum;
import com.geofiber.geofiber_backend.enums.trace.TraceKindEnum;
import com.geofiber.geofiber_backend.services.trace.TraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Rastreio", description = "Caminho do sinal a partir de um ponto da rede: até a OLT (UPSTREAM) ou até os clientes (DOWNSTREAM).")
public class TraceController {
    private final TraceService traceService;

    public TraceController(TraceService traceService) {
        this.traceService = traceService;
    }

    @GetMapping("/api/v1/trace/{kind}/{id}")
    @Operation(summary = "Rastreia a fibra a partir de um ponto",
        description = "Só leitura. Para em quebras, laços e no limite de 50 passos. Em splitter descendo, devolve o resumo das saídas.")
    public TraceResponse trace(
        @PathVariable TraceKindEnum kind,
        @PathVariable UUID id,
        @RequestParam TraceDirectionEnum direction
    ) {
        return traceService.trace(kind, id, direction);
    }
}
