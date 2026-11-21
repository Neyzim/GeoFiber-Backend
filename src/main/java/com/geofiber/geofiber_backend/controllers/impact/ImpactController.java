package com.geofiber.geofiber_backend.controllers.impact;

import com.geofiber.geofiber_backend.dto.impact.ImpactResponse;
import com.geofiber.geofiber_backend.services.impact.ImpactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Impacto", description = "Clientes que dependem de um cabo, caixa ou OLT para manutenção.")
public class ImpactController {
    private final ImpactService impactService;

    public ImpactController(ImpactService impactService) {
        this.impactService = impactService;
    }

    @GetMapping("/api/v1/impact/cables/{id}")
    @Operation(summary = "Impacto de um cabo",
        description = "Clientes cujo caminho até a OLT usa algum trecho do cabo. Lista cortada em 2000 clientes; totais exatos.")
    public ImpactResponse cable(@PathVariable UUID id) {
        return impactService.cable(id);
    }

    @GetMapping("/api/v1/impact/boxes/{id}")
    @Operation(summary = "Impacto de uma caixa",
        description = "Clientes cujo caminho passa por algum elemento da caixa (fusão, conector, splitter ou porta).")
    public ImpactResponse box(@PathVariable UUID id) {
        return impactService.box(id);
    }

    @GetMapping("/api/v1/impact/olts/{id}")
    @Operation(summary = "Impacto de uma OLT",
        description = "Clientes atendidos pelas portas PON da OLT.")
    public ImpactResponse olt(@PathVariable UUID id) {
        return impactService.olt(id);
    }
}
