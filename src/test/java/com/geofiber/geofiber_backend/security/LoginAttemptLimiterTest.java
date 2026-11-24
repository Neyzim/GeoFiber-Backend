package com.geofiber.geofiber_backend.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptLimiterTest {
    private static final String KEY = LoginAttemptLimiter.key(" Membro@Teste.com ", "10.0.0.1");

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-22T12:00:00Z"));
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(now::get);

    @Test
    void keyIgnoresCaseAndSpacesOfTheEmail() {
        assertEquals(LoginAttemptLimiter.key("membro@teste.com", "10.0.0.1"), KEY);
    }

    @Test
    void blocksAfterFiveFailuresForFifteenMinutes() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(KEY);
        }
        assertTrue(limiter.blockedFor(KEY).isEmpty());

        limiter.recordFailure(KEY);
        assertEquals(Optional.of(Duration.ofMinutes(15)), limiter.blockedFor(KEY));

        now.set(now.get().plus(Duration.ofMinutes(10)));
        assertEquals(Optional.of(Duration.ofMinutes(5)), limiter.blockedFor(KEY));

        now.set(now.get().plus(Duration.ofMinutes(5)));
        assertTrue(limiter.blockedFor(KEY).isEmpty());
    }

    @Test
    void failuresOutsideTheWindowStartOver() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(KEY);
        }
        now.set(now.get().plus(Duration.ofMinutes(16)));
        limiter.recordFailure(KEY);
        assertTrue(limiter.blockedFor(KEY).isEmpty());
    }

    @Test
    void successResetsTheCountAndOtherKeysAreIndependent() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(KEY);
        }
        limiter.reset(KEY);
        limiter.recordFailure(KEY);
        assertTrue(limiter.blockedFor(KEY).isEmpty());

        String other = LoginAttemptLimiter.key("membro@teste.com", "10.0.0.2");
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(other);
        }
        assertTrue(limiter.blockedFor(other).isPresent());
        assertTrue(limiter.blockedFor(KEY).isEmpty());
    }
}
