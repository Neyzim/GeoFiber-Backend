package com.geofiber.geofiber_backend.controllers.serviceport;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.geofiber.geofiber_backend.dto.serviceport.ServicePortRequest;
import com.geofiber.geofiber_backend.dto.serviceport.ServicePortResponse;
import com.geofiber.geofiber_backend.services.serviceport.ServicePortService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Portas de atendimento", description = "Portas da CTO onde o drop do cliente é conectado; alimentadas por saída de splitter ou por fibra.")
@RequestMapping("/api/v1/boxes/{boxId}/service-ports")
public class ServicePortController {
    private final ServicePortService servicePortService;

    public ServicePortController(ServicePortService servicePortService) {
        this.servicePortService = servicePortService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra uma porta de atendimento na CTO")
    public ServicePortResponse create(@PathVariable UUID boxId, @Valid @RequestBody ServicePortRequest request) {
        return servicePortService.create(boxId, request);
    }

    @GetMapping
    @Operation(summary = "Lista as portas de atendimento da CTO")
    public List<ServicePortResponse> findByBox(@PathVariable UUID boxId) {
        return servicePortService.findByBox(boxId);
    }

    @PutMapping("/{portId}")
    @Operation(summary = "Atualiza a porta e sua fonte óptica (splitter ou fibra)")
    public ServicePortResponse update(
        @PathVariable UUID boxId, @PathVariable UUID portId, @Valid @RequestBody ServicePortRequest request
    ) {
        return servicePortService.update(boxId, portId, request);
    }

    @DeleteMapping("/{portId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui uma porta de atendimento sem cliente")
    public void delete(@PathVariable UUID boxId, @PathVariable UUID portId) {
        servicePortService.delete(boxId, portId);
    }
}
