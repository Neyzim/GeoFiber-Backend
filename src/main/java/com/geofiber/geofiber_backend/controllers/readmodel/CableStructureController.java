package com.geofiber.geofiber_backend.controllers.readmodel;

import com.geofiber.geofiber_backend.dto.readmodel.CableStructureRequest;
import com.geofiber.geofiber_backend.dto.readmodel.CableStructureResponse;
import com.geofiber.geofiber_backend.services.readmodel.CableStructureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Tubos e fibras", description = "Tubos loose de um cabo e suas fibras. Cada fibra nasce com as pontas A e B.")
public class CableStructureController {
    private final CableStructureService cableStructureService;

    public CableStructureController(CableStructureService cableStructureService) {
        this.cableStructureService = cableStructureService;
    }

    @PostMapping("/api/v1/cables/{cableId}/structure")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria tubos e todas as suas fibras (cores ABNT) numa única transação")
    public CableStructureResponse create(@PathVariable UUID cableId, @Valid @RequestBody CableStructureRequest request) {
        return cableStructureService.create(cableId, request);
    }
}
