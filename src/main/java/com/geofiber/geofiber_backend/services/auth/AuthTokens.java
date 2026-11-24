package com.geofiber.geofiber_backend.services.auth;

/** Par emitido no login e no refresh. O refresh token vai só no cookie, nunca no corpo da resposta. */
public record AuthTokens(String accessToken, String refreshToken, long expiresIn) {
}
