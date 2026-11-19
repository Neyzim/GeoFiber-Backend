package com.geofiber.geofiber_backend.controllers.connection;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.geofiber.geofiber_backend.dto.connection.ConnectionRequest;
import com.geofiber.geofiber_backend.dto.connection.ConnectionResponse;
import com.geofiber.geofiber_backend.services.connection.ConnectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Conexões", description = "Ligações por conector entre pontas de fibra (ex.: cordão no DIO).")
@RequestMapping("/api/v1/boxes/{boxId}/connections")
public class ConnectionController {
    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Liga duas pontas de fibra por conector")
    public ConnectionResponse create(@PathVariable UUID boxId, @Valid @RequestBody ConnectionRequest request) {
        return connectionService.create(boxId, request);
    }

    @GetMapping
    @Operation(summary = "Lista as conexões por conector da caixa")
    public List<ConnectionResponse> findByBox(@PathVariable UUID boxId) {
        return connectionService.findByBox(boxId);
    }

    @DeleteMapping("/{connectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a conexão por conector e libera as pontas")
    public void delete(@PathVariable UUID boxId, @PathVariable UUID connectionId) {
        connectionService.delete(boxId, connectionId);
    }
}
