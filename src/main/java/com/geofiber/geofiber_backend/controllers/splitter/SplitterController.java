package com.geofiber.geofiber_backend.controllers.splitter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.geofiber.geofiber_backend.dto.splitter.SplitterPortResponse;
import com.geofiber.geofiber_backend.dto.splitter.SplitterPortUpdateRequest;
import com.geofiber.geofiber_backend.dto.splitter.SplitterRequest;
import com.geofiber.geofiber_backend.dto.splitter.SplitterResponse;
import com.geofiber.geofiber_backend.services.splitter.SplitterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Splitters", description = "Splitters (entrada = porta 1, saídas = 2..N+1). Em CTO geram portas de atendimento.")
@RequestMapping("/api/v1")
public class SplitterController {
    private final SplitterService splitterService;

    public SplitterController(SplitterService splitterService) {
        this.splitterService = splitterService;
    }

    @PostMapping("/boxes/{boxId}/splitters")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um splitter; em CTO gera uma porta de atendimento por saída")
    public SplitterResponse create(@PathVariable UUID boxId, @Valid @RequestBody SplitterRequest request) {
        return splitterService.create(boxId, request);
    }

    @GetMapping("/boxes/{boxId}/splitters")
    @Operation(summary = "Lista os splitters da caixa com suas portas")
    public List<SplitterResponse> findByBox(@PathVariable UUID boxId) {
        return splitterService.findByBox(boxId);
    }

    @PutMapping("/splitters/{splitterId}/ports/{portId}")
    @Operation(summary = "Atualiza uma porta do splitter e a fibra ligada")
    public SplitterPortResponse updatePort(
        @PathVariable UUID splitterId, @PathVariable UUID portId,
        @Valid @RequestBody SplitterPortUpdateRequest request
    ) {
        return splitterService.updatePort(splitterId, portId, request);
    }

    @PutMapping("/boxes/{boxId}/splitters/{splitterId}")
    @Operation(summary = "Atualiza o balanceamento (a razão é imutável)")
    public SplitterResponse update(
        @PathVariable UUID boxId, @PathVariable UUID splitterId, @Valid @RequestBody SplitterRequest request
    ) {
        return splitterService.update(boxId, splitterId, request);
    }

    @DeleteMapping("/boxes/{boxId}/splitters/{splitterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui um splitter sem ligações, junto com as portas de atendimento que ele gerou")
    public void delete(@PathVariable UUID boxId, @PathVariable UUID splitterId) {
        splitterService.delete(boxId, splitterId);
    }
}
