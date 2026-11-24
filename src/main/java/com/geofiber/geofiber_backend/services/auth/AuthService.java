package com.geofiber.geofiber_backend.services.auth;

import com.geofiber.geofiber_backend.entities.user.AppUserEntity;
import com.geofiber.geofiber_backend.entities.user.RefreshTokenEntity;
import com.geofiber.geofiber_backend.repositories.user.AppUserRepository;
import com.geofiber.geofiber_backend.repositories.user.RefreshTokenRepository;
import com.geofiber.geofiber_backend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import static com.geofiber.geofiber_backend.services.common.ServiceErrors.unauthorized;

/**
 * Login, refresh com rotação e logout. Refresh token aleatório, guardado só como SHA-256.
 *
 * <p>{@code noRollbackFor}: a revogação de todos os tokens, feita quando um token já rotacionado
 * é reapresentado, precisa ser gravada mesmo com a resposta de erro.
 */
@Service
@Transactional(noRollbackFor = ResponseStatusException.class)
public class AuthService {
    public static final Duration REFRESH_TTL = Duration.ofDays(14);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final RefreshTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String dummyHash;

    public AuthService(AppUserRepository users, RefreshTokenRepository tokens,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        // Hash de referência: e-mail inexistente também gasta o tempo de uma verificação de senha.
        this.dummyHash = passwordEncoder.encode("senha-inexistente-referencia");
    }

    public AuthTokens login(String email, String password) {
        Optional<AppUserEntity> found = users.findByEmailIgnoreCase(email);
        String hash = found.map(AppUserEntity::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(password, hash);
        if (found.isEmpty() || !passwordMatches || !found.get().isActive()) {
            throw invalidCredentials();
        }
        return issue(found.get());
    }

    public AuthTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw invalidCredentials();
        }
        Instant now = Instant.now();
        RefreshTokenEntity current = tokens.findByTokenHash(sha256(rawRefreshToken))
            .orElseThrow(AuthService::invalidCredentials);

        if (current.getRevokedAt() != null) {
            // Token já rotacionado foi reapresentado: sinal de roubo. Encerra todas as sessões do usuário.
            tokens.revokeAllByUserId(current.getUser().getId(), now);
            throw invalidCredentials();
        }
        if (!current.getExpiresAt().isAfter(now) || !current.getUser().isActive()) {
            throw invalidCredentials();
        }
        current.setRevokedAt(now);
        return issue(current.getUser());
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        tokens.findByTokenHash(sha256(rawRefreshToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
            }
        });
    }

    private AuthTokens issue(AppUserEntity user) {
        String raw = newRawToken();
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setUser(user);
        entity.setTokenHash(sha256(raw));
        entity.setExpiresAt(Instant.now().plus(REFRESH_TTL));
        tokens.save(entity);
        return new AuthTokens(
            jwtService.issue(user.getId(), user.getRole()),
            raw,
            JwtService.ACCESS_TTL.toSeconds());
    }

    private static ResponseStatusException invalidCredentials() {
        return unauthorized("Credenciais inválidas ou sessão expirada.");
    }

    private static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", exception);
        }
    }
}
