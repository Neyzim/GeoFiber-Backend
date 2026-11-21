package com.geofiber.geofiber_backend.dto.readmodel;

import com.geofiber.geofiber_backend.dto.cable.LooseTubeRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CableStructureRequest(
    @Schema(description = "Tubos a criar; cada um é preenchido com fibras até a capacidade, nas cores ABNT")
    @NotEmpty List<@NotNull @Valid LooseTubeRequest> tubes
) {
}
