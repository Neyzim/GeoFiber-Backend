package com.geofiber.geofiber_backend.controllers.auth;

import com.geofiber.geofiber_backend.dto.auth.LoginRequest;
import com.geofiber.geofiber_backend.dto.auth.TokenResponse;
import com.geofiber.geofiber_backend.security.LoginAttemptLimiter;
import com.geofiber.geofiber_backend.services.auth.AuthService;
import com.geofiber.geofiber_backend.services.auth.AuthTokens;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação", description = "Login, renovação de sessão e logout. O refresh token vai em cookie httpOnly.")
public class AuthController {
    static final String COOKIE = "geofiber_refresh";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final LoginAttemptLimiter limiter;

    public AuthController(AuthService authService, LoginAttemptLimiter limiter) {
        this.authService = authService;
        this.limiter = limiter;
    }

    @PostMapping("/login")
    @Operation(summary = "Entra com e-mail e senha",
        description = "Devolve o access token (15 min) e grava o refresh token (14 dias) em cookie httpOnly. "
            + "5 senhas erradas em 15 min para o mesmo e-mail e origem bloqueiam por 15 min (429, Retry-After).")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String key = LoginAttemptLimiter.key(request.email(), http.getRemoteAddr());
        Optional<Duration> wait = limiter.blockedFor(key);
        if (wait.isPresent()) {
            throw new TooManyLoginAttemptsException(wait.get());
        }
        AuthTokens tokens;
        try {
            tokens = authService.login(request.email(), request.password());
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                limiter.recordFailure(key);
            }
            throw exception;
        }
        limiter.reset(key);
        return withCookie(tokens);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renova o access token",
        description = "Lê o refresh token do cookie e gira um novo par. Reuso de token antigo encerra todas as sessões do usuário.")
    public ResponseEntity<TokenResponse> refresh(@CookieValue(name = COOKIE, required = false) String refreshToken) {
        return withCookie(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Sai da sessão atual", description = "Revoga o refresh token do cookie e apaga o cookie.")
    public ResponseEntity<Void> logout(@CookieValue(name = COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
            .build();
    }

    private static ResponseEntity<TokenResponse> withCookie(AuthTokens tokens) {
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie(tokens.refreshToken(), AuthService.REFRESH_TTL).toString())
            .body(new TokenResponse(tokens.accessToken(), tokens.expiresIn()));
    }

    private static ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path(COOKIE_PATH)
            .maxAge(maxAge)
            .build();
    }
}
