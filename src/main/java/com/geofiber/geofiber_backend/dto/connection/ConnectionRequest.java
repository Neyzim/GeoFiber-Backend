package com.geofiber.geofiber_backend.dto.connection;

import io.swagger.v3.oas.annotations.media.Schema;
import com.geofiber.geofiber_backend.enums.FiberEndEnum;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Conexão por conector entre duas pontas de fibra (ex.: cordão no DIO). */
public record ConnectionRequest(
    @NotNull UUID fiberAId,
    @NotNull FiberEndEnum fiberAEnd,
    @NotNull UUID fiberBId,
    @NotNull FiberEndEnum fiberBEnd,
    @Schema(description = "Perda do conector em dB", example = "0.3") @DecimalMin("0.0") Double attenuationDb
) {
}
