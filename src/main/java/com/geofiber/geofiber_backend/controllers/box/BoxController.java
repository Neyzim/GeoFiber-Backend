package com.geofiber.geofiber_backend.controllers.box;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.geofiber.geofiber_backend.dto.box.BoxRequest;
import com.geofiber.geofiber_backend.dto.box.BoxResponse;
import com.geofiber.geofiber_backend.services.box.BoxService;
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
@Tag(name = "Caixas", description = "CTOs, CEOs e DIOs. Em CTO, powerCapacity é o número máximo de portas de atendimento.")
@RequestMapping("/api/v1/boxes")
public class BoxController {
    private final BoxService boxService;

    public BoxController(BoxService boxService) {
        this.boxService = boxService;
    }

    @PostMapping
    @Operation(summary = "Cadastra uma caixa (CTO, CEO ou DIO)")
    public ResponseEntity<BoxResponse> create(@Valid @RequestBody BoxRequest request) {
        BoxResponse response = boxService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/boxes/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "Lista as caixas")
    public List<BoxResponse> findAll() {
        return boxService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca uma caixa")
    public BoxResponse findById(@PathVariable UUID id) {
        return boxService.findById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza uma caixa (CTO com portas de atendimento não muda de tipo)")
    public BoxResponse update(@PathVariable UUID id, @Valid @RequestBody BoxRequest request) {
        return boxService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui uma caixa sem splitters, bandejas, portas, conexões ou cabos anexados")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        boxService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
