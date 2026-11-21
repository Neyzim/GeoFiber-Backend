package com.geofiber.geofiber_backend.dto.impact;

import com.geofiber.geofiber_backend.dto.geo.CoordinateDto;
import com.geofiber.geofiber_backend.enums.impact.ImpactTargetKindEnum;

import java.util.List;
import java.util.UUID;

/**
 * Clientes que dependem de um cabo, caixa ou OLT, com as caixas e OLTs do caminho deles.
 * {@code totals} é exato mesmo quando {@code boxes} foi cortado em {@code MAX_LISTED_CUSTOMERS}.
 */
public record ImpactResponse(
    ImpactTarget target,
    ImpactTotals totals,
    List<ImpactBox> boxes,
    List<ImpactOlt> olts,
    boolean truncated
) {
    public record ImpactTarget(ImpactTargetKindEnum kind, UUID id, String label, List<CoordinateDto> coordinates) {
    }

    public record ImpactTotals(int customers, int boxes, int olts) {
    }

    public record ImpactBox(UUID id, String label, List<CoordinateDto> coordinates, List<ImpactCustomer> customers) {
    }

    public record ImpactCustomer(UUID id, String name, List<CoordinateDto> coordinates) {
    }

    public record ImpactOlt(UUID id, String label, List<CoordinateDto> coordinates) {
    }
}
