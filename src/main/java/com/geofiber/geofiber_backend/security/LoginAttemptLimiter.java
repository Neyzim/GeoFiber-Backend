package com.geofiber.geofiber_backend.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Limita as tentativas de login erradas por e-mail + endereço de origem: 5 erros em 15 minutos
 * bloqueiam esse par por 15 minutos. Um login certo zera a contagem.
 *
 * <p>Fica em memória: vale para uma instância do backend e zera ao reiniciar. Atrás de um proxy
 * reverso, o endereço só é o do cliente com {@code server.forward-headers-strategy=native}; sem isso,
 * a contagem fica por e-mail.
 */
@Component
public class LoginAttemptLimiter {
    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(15);
    public static final Duration BLOCK = Duration.ofMinutes(15);
    /** Acima disso, cada erro novo varre as entradas vencidas (o mapa não cresce sem limite). */
    private static final int PRUNE_THRESHOLD = 10_000;

    private final Supplier<Instant> now;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptLimiter() {
        this(Instant::now);
    }

    LoginAttemptLimiter(Supplier<Instant> now) {
        this.now = now;
    }

    /** Chave da contagem: e-mail sem diferença de maiúsculas e espaços, mais o endereço de origem. */
    public static String key(String email, String remoteAddress) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return normalized + "|" + (remoteAddress == null ? "" : remoteAddress);
    }

    /** Quanto falta para poder tentar de novo; vazio quando está liberado. */
    public Optional<Duration> blockedFor(String key) {
        Instant current = now.get();
        Attempts entry = attempts.get(key);
        if (entry == null || entry.blockedUntil() == null || !current.isBefore(entry.blockedUntil())) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(current, entry.blockedUntil()));
    }

    public void recordFailure(String key) {
        Instant current = now.get();
        if (attempts.size() > PRUNE_THRESHOLD) {
            attempts.entrySet().removeIf(entry -> entry.getValue().expired(current));
        }
        attempts.compute(key, (ignored, entry) -> {
            Attempts base = entry == null || entry.expired(current) ? new Attempts(0, current, null) : entry;
            int failures = base.failures() + 1;
            Instant blockedUntil = failures >= MAX_FAILURES ? current.plus(BLOCK) : null;
            return new Attempts(failures, base.windowStart(), blockedUntil);
        });
    }

    public void reset(String key) {
        attempts.remove(key);
    }

    /** Zera tudo (testes). */
    public void clear() {
        attempts.clear();
    }

    private record Attempts(int failures, Instant windowStart, Instant blockedUntil) {
        boolean expired(Instant current) {
            return blockedUntil != null
                ? !current.isBefore(blockedUntil)
                : !current.isBefore(windowStart.plus(WINDOW));
        }
    }
}
