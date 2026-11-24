package com.geofiber.geofiber_backend.security;

import com.geofiber.geofiber_backend.enums.user.UserRoleEnum;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/** Emite e valida o access token JWT (HS256). Expira em 15 minutos. */
@Service
public class JwtService {
    public static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Clock clock;

    @Autowired
    public JwtService(@Value("${geofiber.security.jwt-secret:}") String secret) {
        this(secret, Clock.systemUTC());
    }

    JwtService(String secret, Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "Defina GEOFIBER_JWT_SECRET com pelo menos " + MIN_SECRET_BYTES + " bytes.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String issue(UUID userId, UserRoleEnum role) {
        Instant now = clock.instant();
        return Jwts.builder()
            .subject(userId.toString())
            .claim("role", role.name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ACCESS_TTL)))
            .signWith(key)
            .compact();
    }

    /** Devolve o usuário do token, ou vazio se o token for inválido, adulterado ou expirado. */
    public Optional<AuthenticatedUser> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                .clock(() -> Date.from(clock.instant()))
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return Optional.of(new AuthenticatedUser(
                UUID.fromString(claims.getSubject()),
                UserRoleEnum.valueOf(claims.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
