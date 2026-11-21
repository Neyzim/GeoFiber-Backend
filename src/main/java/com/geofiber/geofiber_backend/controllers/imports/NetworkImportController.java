package com.geofiber.geofiber_backend.controllers.imports;

import com.geofiber.geofiber_backend.dto.imports.NetworkImportRequest;
import com.geofiber.geofiber_backend.dto.imports.NetworkImportResponse;
import com.geofiber.geofiber_backend.services.imports.NetworkImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Importação", description = "Importação de rede (KML/KMZ) já lida e agrupada pelo front.")
public class NetworkImportController {
    private final NetworkImportService networkImportService;

    public NetworkImportController(NetworkImportService networkImportService) {
        this.networkImportService = networkImportService;
    }

    @PostMapping("/api/v1/imports/network")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Importa caixas, OLTs, cabos e pontas numa única transação",
        description = "Tudo ou nada: qualquer falha desfaz a importação inteira. Não apaga nada.")
    public NetworkImportResponse importNetwork(@RequestBody NetworkImportRequest request) {
        return networkImportService.importNetwork(request);
    }
}
