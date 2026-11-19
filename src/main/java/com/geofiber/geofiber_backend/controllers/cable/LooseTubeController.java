package com.geofiber.geofiber_backend.controllers.cable;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.geofiber.geofiber_backend.dto.cable.LooseTubeRequest;
import com.geofiber.geofiber_backend.dto.cable.LooseTubeResponse;
import com.geofiber.geofiber_backend.services.cable.LooseTubeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Tubos e fibras", description = "Tubos loose de um cabo e suas fibras. Cada fibra nasce com as pontas A e B.")
@RequestMapping("/api/v1/cables/{cableId}/tubes")
public class LooseTubeController {
    private final LooseTubeService looseTubeService;

    public LooseTubeController(LooseTubeService looseTubeService) {
        this.looseTubeService = looseTubeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um tubo no cabo")
    public LooseTubeResponse create(@PathVariable UUID cableId, @Valid @RequestBody LooseTubeRequest request) {
        return looseTubeService.create(cableId, request);
    }

    @GetMapping
    @Operation(summary = "Lista os tubos do cabo")
    public List<LooseTubeResponse> findByCable(@PathVariable UUID cableId) {
        return looseTubeService.findByCable(cableId);
    }

    @PutMapping("/{tubeId}")
    @Operation(summary = "Atualiza um tubo")
    public LooseTubeResponse update(
        @PathVariable UUID cableId, @PathVariable UUID tubeId, @Valid @RequestBody LooseTubeRequest request
    ) {
        return looseTubeService.update(cableId, tubeId, request);
    }

    @DeleteMapping("/{tubeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui um tubo sem fibras")
    public void delete(@PathVariable UUID cableId, @PathVariable UUID tubeId) {
        looseTubeService.delete(cableId, tubeId);
    }
}
