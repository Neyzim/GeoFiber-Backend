package com.geofiber.geofiber_backend.dto.imports;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;
import java.util.UUID;

public record NetworkImportResponse(
    Created created,
    @Schema(description = "Quantidade de MOVE feitos") int moved,
    @Schema(description = "Quantidade de RESHAPE feitos") int reshaped,
    @Schema(description = "uuid de todo ref da requisição (criados, movidos e redesenhados)") Map<String, UUID> ids
) {
    public record Created(int boxes, int olts, int cables, int attachments) {
    }
}
