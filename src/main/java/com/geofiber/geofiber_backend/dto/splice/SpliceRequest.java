package com.geofiber.geofiber_backend.dto.splice;

import io.swagger.v3.oas.annotations.media.Schema;
import com.geofiber.geofiber_backend.enums.FiberEndEnum;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * @param trayNumber     bandeja da caixa onde a fusão fica (precisa estar cadastrada)
 * @param inputFiberEnd  ponta da fibra de entrada; padrão B (chega vindo da OLT)
 * @param outputFiberEnd ponta da fibra de saída; padrão A (sai em direção ao cliente)
 */
public record SpliceRequest(
    @Schema(description = "Bandeja já cadastrada na caixa; a fusão ocupa a próxima posição livre", example = "1") @NotNull @Positive Integer trayNumber,
    @DecimalMin("0.0") Double attenuationDb,
    @NotNull UUID inputFiberId,
    @Schema(description = "Ponta da fibra de entrada; padrão B (chega vinda da OLT)", example = "B") FiberEndEnum inputFiberEnd,
    @NotNull UUID outputFiberId,
    @Schema(description = "Ponta da fibra de saída; padrão A (segue para o cliente)", example = "A") FiberEndEnum outputFiberEnd
) {
}
