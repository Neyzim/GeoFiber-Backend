package com.geofiber.geofiber_backend.services.trace;

import java.util.Set;
import java.util.UUID;

/** Elementos que o sinal atravessa no caminho UPSTREAM até a OLT (ou até o ponto onde para). */
public record UpstreamPath(Set<UUID> cableIds, Set<UUID> boxIds, Set<UUID> oltIds) {
}
