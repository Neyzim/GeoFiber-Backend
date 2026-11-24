package com.geofiber.geofiber_backend.dto.auth;

/** Access token e validade em segundos. O refresh token fica no cookie {@code httpOnly}. */
public record TokenResponse(String accessToken, long expiresIn) {
}
