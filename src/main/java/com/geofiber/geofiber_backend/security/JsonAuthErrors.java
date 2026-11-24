package com.geofiber.geofiber_backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Escreve os erros de autenticação e autorização no mesmo formato ProblemDetail da API. */
@Component
public class JsonAuthErrors {

    public void unauthorized(HttpServletRequest request, HttpServletResponse response,
                             AuthenticationException exception) throws IOException {
        write(response, 401, "Unauthorized", "Autenticação necessária.");
    }

    public void forbidden(HttpServletRequest request, HttpServletResponse response,
                          AccessDeniedException exception) throws IOException {
        write(response, 403, "Forbidden", "Acesso negado.");
    }

    private static void write(HttpServletResponse response, int status, String title, String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter().write(
            "{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status
                + ",\"detail\":\"" + detail + "\"}");
    }
}
