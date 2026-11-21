package com.geofiber.geofiber_backend.controllers.tray;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.geofiber.geofiber_backend.dto.tray.TrayRequest;
import com.geofiber.geofiber_backend.dto.tray.TrayResponse;
import com.geofiber.geofiber_backend.services.tray.TrayService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Bandejas", description = "Bandejas de emenda de uma caixa.")
@RequestMapping("/api/v1/boxes/{boxId}/trays")
public class TrayController {
    private final TrayService trayService;

    public TrayController(TrayService trayService) {
        this.trayService = trayService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra uma bandeja na caixa")
    public TrayResponse create(@PathVariable UUID boxId, @Valid @RequestBody TrayRequest request) {
        return trayService.create(boxId, request);
    }

    @GetMapping
    @Operation(summary = "Lista as bandejas com posições usadas")
    public List<TrayResponse> findByBox(@PathVariable UUID boxId) {
        return trayService.findByBox(boxId);
    }

    @PutMapping("/{trayId}")
    @Operation(summary = "Atualiza a bandeja (capacidade não fica abaixo da maior posição usada)")
    public TrayResponse update(
        @PathVariable UUID boxId, @PathVariable UUID trayId, @Valid @RequestBody TrayRequest request
    ) {
        return trayService.update(boxId, trayId, request);
    }

    @DeleteMapping("/{trayId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui uma bandeja sem fusões")
    public void delete(@PathVariable UUID boxId, @PathVariable UUID trayId) {
        trayService.delete(boxId, trayId);
    }
}
