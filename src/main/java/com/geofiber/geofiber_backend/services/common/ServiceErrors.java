package com.geofiber.geofiber_backend.services.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Supplier;

/** Atalhos para os erros HTTP devolvidos pelos serviços (o Spring os converte em ProblemDetail). */
public final class ServiceErrors {
    private ServiceErrors() {
    }

    public static Supplier<ResponseStatusException> notFound(String resource) {
        return () -> new ResponseStatusException(HttpStatus.NOT_FOUND, resource + " not found");
    }

    public static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    public static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
