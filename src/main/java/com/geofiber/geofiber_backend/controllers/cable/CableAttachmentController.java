package com.geofiber.geofiber_backend.controllers.cable;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.geofiber.geofiber_backend.dto.cable.CableAttachmentRequest;
import com.geofiber.geofiber_backend.dto.cable.CableAttachmentResponse;
import com.geofiber.geofiber_backend.services.cable.CableAttachmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Cabos", description = "Cabos com traçado (LineString) e comprimento calculado pelo PostGIS.")
@RequestMapping("/api/v1/cables/{cableId}/attachments")
public class CableAttachmentController {
    private final CableAttachmentService cableAttachmentService;

    public CableAttachmentController(CableAttachmentService cableAttachmentService) {
        this.cableAttachmentService = cableAttachmentService;
    }

    @PutMapping
    @Operation(summary = "Define a lista ordenada de elementos do cabo (START, PASS_THROUGH..., END)")
    public List<CableAttachmentResponse> replace(
        @PathVariable UUID cableId, @RequestBody List<@NotNull @Valid CableAttachmentRequest> request
    ) {
        return cableAttachmentService.replace(cableId, request);
    }

    @GetMapping
    @Operation(summary = "Lista os elementos por onde o cabo passa")
    public List<CableAttachmentResponse> findByCable(@PathVariable UUID cableId) {
        return cableAttachmentService.findByCable(cableId);
    }
}
