package com.geofiber.geofiber_backend.dto.customer;

import com.geofiber.geofiber_backend.enums.customer.CustomerStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import com.geofiber.geofiber_backend.dto.geo.CoordinateDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CustomerRequest(
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Size(max = 80) String contractCode,
    @Size(max = 500) String address,
    @NotNull @Valid CoordinateDto location,
    @Schema(description = "ACTIVE (padrão), BLOCKED mantém a porta, CANCELLED libera a porta e mantém o cliente como histórico") CustomerStatusEnum status,
    @Schema(description = "Porta de atendimento livre e com fonte óptica que atende o cliente") UUID servicePortId
) {
}
