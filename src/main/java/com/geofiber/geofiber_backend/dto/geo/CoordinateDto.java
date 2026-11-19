package com.geofiber.geofiber_backend.dto.geo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CoordinateDto(
    @Schema(description = "Longitude WGS 84", example = "-46.6333") @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
    @Schema(description = "Latitude WGS 84", example = "-23.5505") @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude
) {
}
