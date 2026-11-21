package com.geofiber.geofiber_backend.controllers.readmodel;

import com.geofiber.geofiber_backend.dto.readmodel.AttachedCableResponse;
import com.geofiber.geofiber_backend.dto.readmodel.BoxOverviewResponse;
import com.geofiber.geofiber_backend.dto.readmodel.CableFiberResponse;
import com.geofiber.geofiber_backend.services.readmodel.BoxOverviewService;
import com.geofiber.geofiber_backend.services.readmodel.CableReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Consultas", description = "Leituras agregadas para o frontend: cabos de um elemento, fibras de um cabo e visão geral da caixa.")
@RequestMapping("/api/v1")
public class NetworkReadController {
    private final CableReadService cableReadService;
    private final BoxOverviewService boxOverviewService;

    public NetworkReadController(CableReadService cableReadService, BoxOverviewService boxOverviewService) {
        this.cableReadService = cableReadService;
        this.boxOverviewService = boxOverviewService;
    }

    @GetMapping("/boxes/{boxId}/cables")
    @Operation(summary = "Lista os cabos anexados à caixa, com o papel dela em cada cabo")
    public List<AttachedCableResponse> cablesOfBox(@PathVariable UUID boxId) {
        return cableReadService.findByBox(boxId);
    }

    @GetMapping("/olts/{oltId}/cables")
    @Operation(summary = "Lista os cabos anexados à OLT")
    public List<AttachedCableResponse> cablesOfOlt(@PathVariable UUID oltId) {
        return cableReadService.findByOlt(oltId);
    }

    @GetMapping("/cables/{cableId}/fibers")
    @Operation(summary = "Lista as fibras do cabo com tubo, status e a ligação de cada ponta (A e B)")
    public List<CableFiberResponse> fibersOfCable(@PathVariable UUID cableId) {
        return cableReadService.findFibers(cableId);
    }

    @GetMapping("/boxes/{boxId}/overview")
    @Operation(summary = "Visão geral da caixa: cabos, bandejas, emendas, splitters, portas, conexões e clientes")
    public BoxOverviewResponse overview(@PathVariable UUID boxId) {
        return boxOverviewService.findByBox(boxId);
    }
}
