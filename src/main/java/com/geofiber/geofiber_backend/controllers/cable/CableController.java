package com.geofiber.geofiber_backend.controllers.cable;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.geofiber.geofiber_backend.dto.cable.CableRequest;
import com.geofiber.geofiber_backend.dto.cable.CableResponse;
import com.geofiber.geofiber_backend.services.cable.CableService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Cabos", description = "Cabos com traçado (LineString) e comprimento calculado pelo PostGIS.")
@RequestMapping("/api/v1/cables")
public class CableController {
    private final CableService cableService;

    public CableController(CableService cableService) {
        this.cableService = cableService;
    }

    @PostMapping
    @Operation(summary = "Cadastra um cabo com traçado e capacidade de fibras")
    public ResponseEntity<CableResponse> create(@Valid @RequestBody CableRequest request) {
        CableResponse response = cableService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/cables/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "Lista os cabos")
    public List<CableResponse> findAll() {
        return cableService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um cabo")
    public CableResponse findById(@PathVariable UUID id) {
        return cableService.findById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza um cabo (capacidade não pode ficar abaixo da soma dos tubos)")
    public CableResponse update(@PathVariable UUID id, @Valid @RequestBody CableRequest request) {
        return cableService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui um cabo sem tubos (a lista de elementos é removida junto)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        cableService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
