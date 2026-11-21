package com.geofiber.geofiber_backend.dto.box;

import io.swagger.v3.oas.annotations.media.Schema;
import com.geofiber.geofiber_backend.dto.geo.CoordinateDto;

import com.geofiber.geofiber_backend.enums.InfrastructureStatusEnum;
import com.geofiber.geofiber_backend.enums.box.BoxTypeEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record BoxRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 1000) String description,
        @NotNull BoxTypeEnum type,
        @Schema(description = "Padrão PLANNED") InfrastructureStatusEnum usageStatus,
        @Schema(description = "Em CTO: número máximo de portas de atendimento (portas numeradas de 1 até este valor)", example = "8") @Positive Integer powerCapacity,
        @Schema(description = "Número máximo de bandejas de emenda", example = "2") @Positive Integer trayCapacity,
        @Size(max = 120) String manufacturer,
        @Size(max = 120) String model,
        @Size(max = 500) String address,
        @NotNull @Valid CoordinateDto location
) {
}