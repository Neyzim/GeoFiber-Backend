package com.geofiber.geofiber_backend.controllers.cable;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.geofiber.geofiber_backend.dto.cable.FiberRequest;
import com.geofiber.geofiber_backend.dto.cable.FiberResponse;
import com.geofiber.geofiber_backend.services.cable.FiberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Tubos e fibras", description = "Tubos loose de um cabo e suas fibras. Cada fibra nasce com as pontas A e B.")
@RequestMapping("/api/v1/tubes/{tubeId}/fibers")
public class FiberController {
    private final FiberService fiberService;

    public FiberController(FiberService fiberService) {
        this.fiberService = fiberService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra uma fibra no tubo (as pontas A e B são criadas junto)")
    public FiberResponse create(@PathVariable UUID tubeId, @Valid @RequestBody FiberRequest request) {
        return fiberService.create(tubeId, request);
    }

    @GetMapping
    @Operation(summary = "Lista as fibras do tubo")
    public List<FiberResponse> findByTube(@PathVariable UUID tubeId) {
        return fiberService.findByTube(tubeId);
    }

    @PutMapping("/{fiberId}")
    @Operation(summary = "Atualiza número e cor da fibra")
    public FiberResponse update(
        @PathVariable UUID tubeId, @PathVariable UUID fiberId, @Valid @RequestBody FiberRequest request
    ) {
        return fiberService.update(tubeId, fiberId, request);
    }

    @PutMapping("/{fiberId}/status")
    @Operation(summary = "Marca a fibra como AVAILABLE, BROKEN ou RESERVED (só desconectada)")
    public FiberResponse updateStatus(
        @PathVariable UUID tubeId, @PathVariable UUID fiberId,
        @Valid @RequestBody com.geofiber.geofiber_backend.dto.cable.FiberStatusRequest request
    ) {
        return fiberService.updateStatus(tubeId, fiberId, request.status());
    }

    @DeleteMapping("/{fiberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui uma fibra desconectada")
    public void delete(@PathVariable UUID tubeId, @PathVariable UUID fiberId) {
        fiberService.delete(tubeId, fiberId);
    }
}
