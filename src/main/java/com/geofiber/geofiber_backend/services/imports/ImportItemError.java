package com.geofiber.geofiber_backend.services.imports;

/** Um campo inválido de um item da importação. {@code ref} é nulo quando o erro não é de um item. */
public record ImportItemError(String ref, String field, String message) {
}
