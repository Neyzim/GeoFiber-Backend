package com.geofiber.geofiber_backend.controllers.splice;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.geofiber.geofiber_backend.dto.splice.SpliceRequest;
import com.geofiber.geofiber_backend.dto.splice.SpliceResponse;
import com.geofiber.geofiber_backend.services.splice.SpliceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Emendas", description = "Fusões entre fibras, ocupando posições de uma bandeja.")
@RequestMapping("/api/v1/boxes/{boxId}/splices")
public class SpliceController {
    private final SpliceService spliceService;

    public SpliceController(SpliceService spliceService) {
        this.spliceService = spliceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra uma fusão na bandeja; posição pode ser fornecida ou será a próxima livre")
    public SpliceResponse create(@PathVariable UUID boxId, @Valid @RequestBody SpliceRequest request) {
        return spliceService.create(boxId, request);
    }

    @GetMapping
    @Operation(summary = "Lista as fusões da caixa")
    public List<SpliceResponse> findByBox(@PathVariable UUID boxId) {
        return spliceService.findByBox(boxId);
    }

    @DeleteMapping("/{spliceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desfaz a fusão e libera as pontas das fibras")
    public void delete(@PathVariable UUID boxId, @PathVariable UUID spliceId) {
        spliceService.delete(boxId, spliceId);
    }
}
