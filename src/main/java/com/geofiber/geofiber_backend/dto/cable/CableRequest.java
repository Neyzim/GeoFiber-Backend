package com.geofiber.geofiber_backend.dto.cable;

import io.swagger.v3.oas.annotations.media.Schema;
import com.geofiber.geofiber_backend.dto.geo.CoordinateDto;
import com.geofiber.geofiber_backend.enums.cable.CableRoleEnum;
import com.geofiber.geofiber_backend.enums.InfrastructureStatusEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CableRequest(
    @NotBlank @Size(max = 160) String name,
    @Size(max = 1000) String description,
    @NotNull CableRoleEnum role,
    InfrastructureStatusEnum usageStatus,
    @Size(max = 120) String manufacturer,
    @Size(max = 120) String model,
    @Schema(description = "Quantidade total de fibras do cabo", example = "12") @NotNull @Positive Integer fiberCapacity,
    @Schema(description = "Traçado do cabo, no mínimo 2 pontos, na ordem do percurso") @NotEmpty @Size(min = 2) List<@NotNull @Valid CoordinateDto> coordinates
) {
}
