package com.geofiber.geofiber_backend.services.imports;

import java.util.List;

/** Itens inválidos da importação. O handler devolve 400 "Invalid import" com a lista em {@code errors}. */
public class ImportValidationException extends RuntimeException {
    private final transient List<ImportItemError> errors;

    public ImportValidationException(List<ImportItemError> errors) {
        super("Invalid import");
        this.errors = List.copyOf(errors);
    }

    public List<ImportItemError> getErrors() {
        return errors;
    }
}
