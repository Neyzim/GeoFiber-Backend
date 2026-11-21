package com.geofiber.geofiber_backend.controllers.olt;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.geofiber.geofiber_backend.dto.olt.OltPortRequest;
import com.geofiber.geofiber_backend.dto.olt.OltPortResponse;
import com.geofiber.geofiber_backend.services.olt.OltPortService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "OLTs", description = "OLTs e suas portas PON.")
@RequestMapping("/api/v1/olts/{oltId}/ports")
public class OltPortController {
    private final OltPortService oltPortService;

    public OltPortController(OltPortService oltPortService) {
        this.oltPortService = oltPortService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra uma porta PON, opcionalmente ligada a uma fibra (ponta A por padrão)")
    public OltPortResponse create(@PathVariable UUID oltId, @Valid @RequestBody OltPortRequest request) {
        return oltPortService.create(oltId, request);
    }

    @GetMapping
    @Operation(summary = "Lista as portas da OLT")
    public List<OltPortResponse> findByOlt(@PathVariable UUID oltId) {
        return oltPortService.findByOlt(oltId);
    }

    @PutMapping("/{portId}")
    @Operation(summary = "Atualiza a porta e troca ou remove a fibra ligada")
    public OltPortResponse update(
        @PathVariable UUID oltId, @PathVariable UUID portId, @Valid @RequestBody OltPortRequest request
    ) {
        return oltPortService.update(oltId, portId, request);
    }

    @DeleteMapping("/{portId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui uma porta sem fibra")
    public void delete(@PathVariable UUID oltId, @PathVariable UUID portId) {
        oltPortService.delete(oltId, portId);
    }
}
