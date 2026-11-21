package com.geofiber.geofiber_backend.dto.imports;

import com.geofiber.geofiber_backend.dto.geo.CoordinateDto;
import com.geofiber.geofiber_backend.enums.InfrastructureStatusEnum;
import com.geofiber.geofiber_backend.enums.box.BoxTypeEnum;
import com.geofiber.geofiber_backend.enums.cable.CableRoleEnum;
import com.geofiber.geofiber_backend.enums.imports.ImportActionEnum;
import com.geofiber.geofiber_backend.enums.imports.ImportKindEnum;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Lista pronta de itens montada pelo front a partir de um KML/KMZ. O backend não lê arquivo.
 * {@code ref} é uma chave livre escolhida pelo front, válida só dentro desta requisição.
 */
@Schema(description = "Importação de rede: elementos (caixas e OLTs), cabos e suas pontas, gravados numa transação")
public record NetworkImportRequest(
    @Schema(description = "Situação dos itens criados", requiredMode = Schema.RequiredMode.REQUIRED)
    InfrastructureStatusEnum usageStatus,
    List<ElementItem> elements,
    List<CableItem> cables
) {
    public record ElementItem(
        @Schema(description = "Chave única na requisição", requiredMode = Schema.RequiredMode.REQUIRED) String ref,
        @Schema(description = "CREATE cria; MOVE só troca a localização de um elemento existente", requiredMode = Schema.RequiredMode.REQUIRED)
        ImportActionEnum action,
        @Schema(description = "BOX ou OLT", requiredMode = Schema.RequiredMode.REQUIRED) ImportKindEnum kind,
        @Schema(description = "Obrigatório em BOX (CTO, CEO ou DIO); proibido em OLT") BoxTypeEnum type,
        @Schema(description = "Obrigatório em CREATE") String name,
        String description,
        @Schema(description = "Obrigatório em MOVE: id do elemento existente") UUID id,
        CoordinateDto location
    ) {
    }

    public record CableItem(
        @Schema(description = "Chave única na requisição", requiredMode = Schema.RequiredMode.REQUIRED) String ref,
        @Schema(description = "CREATE cria o cabo; RESHAPE troca só o traçado de um cabo existente", requiredMode = Schema.RequiredMode.REQUIRED)
        ImportActionEnum action,
        @Schema(description = "Obrigatório em RESHAPE: id do cabo existente") UUID id,
        @Schema(description = "Obrigatório em CREATE") String name,
        String description,
        @Schema(description = "Obrigatório em CREATE") CableRoleEnum role,
        @Schema(description = "Obrigatório em CREATE, inteiro maior que zero") Integer fiberCapacity,
        @Schema(description = "Traçado na ordem do percurso, no mínimo 2 pontos") List<CoordinateDto> coordinates,
        @Schema(description = "Ponta inicial (opcional). Só em CREATE") EndpointRef start,
        @Schema(description = "Ponta final (opcional). Só em CREATE") EndpointRef end
    ) {
    }

    /** Exatamente uma chave: ref (elemento CREATE desta requisição), boxId ou oltId (existente). */
    public record EndpointRef(String ref, UUID boxId, UUID oltId) {
    }
}
