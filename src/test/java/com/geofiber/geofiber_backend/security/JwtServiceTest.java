package com.geofiber.geofiber_backend.security;

import com.geofiber.geofiber_backend.enums.user.UserRoleEnum;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {
    private static final String SECRET = "segredo-de-teste-com-mais-de-trinta-e-dois-bytes";
    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

    @Test
    void issuedTokenVerifiesToSameUserAndRole() {
        JwtService jwt = new JwtService(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
        UUID userId = UUID.randomUUID();

        Optional<AuthenticatedUser> user = jwt.verify(jwt.issue(userId, UserRoleEnum.ADMIN));

        assertEquals(new AuthenticatedUser(userId, UserRoleEnum.ADMIN), user.orElseThrow());
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwt = new JwtService(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
        String token = jwt.issue(UUID.randomUUID(), UserRoleEnum.MEMBER);
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        assertFalse(jwt.verify(tampered).isPresent());
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService issuer = new JwtService(SECRET, Clock.fixed(NOW.minus(Duration.ofMinutes(16)), ZoneOffset.UTC));
        JwtService verifier = new JwtService(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

        assertFalse(verifier.verify(issuer.issue(UUID.randomUUID(), UserRoleEnum.MEMBER)).isPresent());
    }

    @Test
    void malformedTokenIsRejected() {
        JwtService jwt = new JwtService(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

        assertFalse(jwt.verify("nao-e-um-jwt").isPresent());
    }

    @Test
    void secretShorterThan32BytesIsRefused() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> new JwtService("curto", Clock.systemUTC()));

        assertTrue(error.getMessage().contains("GEOFIBER_JWT_SECRET"));
    }
}
