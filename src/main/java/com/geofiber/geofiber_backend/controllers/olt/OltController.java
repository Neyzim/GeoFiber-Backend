package com.geofiber.geofiber_backend.controllers.olt;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.geofiber.geofiber_backend.dto.olt.OltRequest;
import com.geofiber.geofiber_backend.dto.olt.OltResponse;
import com.geofiber.geofiber_backend.services.olt.OltService;
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
@Tag(name = "OLTs", description = "OLTs e suas portas PON.")
@RequestMapping("/api/v1/olts")
public class OltController {
    private final OltService oltService;

    public OltController(OltService oltService) {
        this.oltService = oltService;
    }

    @PostMapping
    @Operation(summary = "Cadastra uma OLT")
    public ResponseEntity<OltResponse> create(@Valid @RequestBody OltRequest request) {
        OltResponse response = oltService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/olts/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "Lista as OLTs")
    public List<OltResponse> findAll() {
        return oltService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca uma OLT")
    public OltResponse findById(@PathVariable UUID id) {
        return oltService.findById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza uma OLT")
    public OltResponse update(@PathVariable UUID id, @Valid @RequestBody OltRequest request) {
        return oltService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui uma OLT sem portas nem cabos anexados")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        oltService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
