package com.geofiber.geofiber_backend.controllers.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/** 429 do login bloqueado, com {@code Retry-After} em segundos. */
class TooManyLoginAttemptsException extends ResponseStatusException {
    static final String MESSAGE = "Muitas tentativas de entrar. Espere 15 minutos e tente de novo.";

    private final long retryAfterSeconds;

    TooManyLoginAttemptsException(Duration wait) {
        super(HttpStatus.TOO_MANY_REQUESTS, MESSAGE);
        this.retryAfterSeconds = Math.max(1, (wait.toMillis() + 999) / 1000);
    }

    @Override
    public HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        return headers;
    }
}
