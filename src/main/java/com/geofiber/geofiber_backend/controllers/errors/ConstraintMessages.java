package com.geofiber.geofiber_backend.controllers.errors;

import java.util.Map;
import java.util.Optional;

/** Traduz nomes de constraints do banco em mensagens legíveis para o cliente da API. */
public final class ConstraintMessages {
    private static final Map<String, String> MESSAGES = Map.ofEntries(
        Map.entry("uq_loose_tube_number", "A tube with this number already exists in the cable"),
        Map.entry("uq_fiber_absolute_number", "A fiber with this absolute number already exists in the cable"),
        Map.entry("uq_fiber_tube_number", "A fiber with this number already exists in the tube"),
        Map.entry("uq_olt_port_position", "An OLT port already exists at this slot and port number"),
        Map.entry("uq_splitter_port_number", "A port with this number already exists in the splitter"),
        Map.entry("uq_tray_box_number", "A tray with this number already exists in the box"),
        Map.entry("uq_splice_tray_position", "This tray position is already used by another splice"),
        Map.entry("uq_cable_attachment_box", "Element is already attached to this cable"),
        Map.entry("uq_cable_attachment_olt", "Element is already attached to this cable"),
        Map.entry("uq_service_port_box_number", "A service port with this number already exists in the box"),
        Map.entry("customer_contract_code_key", "A customer with this contract code already exists")
    );

    private ConstraintMessages() {
    }

    public static Optional<String> describe(String constraintName) {
        return constraintName == null ? Optional.empty()
            : Optional.ofNullable(MESSAGES.get(constraintName.toLowerCase()));
    }
}
