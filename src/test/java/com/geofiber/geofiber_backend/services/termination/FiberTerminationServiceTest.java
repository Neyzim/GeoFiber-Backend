package com.geofiber.geofiber_backend.services.termination;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FiberTerminationServiceTest {
    @Test
    void ordersUuidsLikePostgresUnsignedByteComparison() {
        UUID highBitSet = UUID.fromString("f0000000-0000-0000-0000-000000000000");
        UUID lowValue = UUID.fromString("10000000-0000-0000-0000-000000000000");

        // UUID.compareTo usa long com sinal e coloca f0... antes de 10..., ao contrário do PostgreSQL.
        assertTrue(highBitSet.compareTo(lowValue) < 0);
        assertTrue(FiberTerminationService.compareAsPostgres(lowValue, highBitSet) < 0);
        assertTrue(FiberTerminationService.compareAsPostgres(highBitSet, lowValue) > 0);
    }

    @Test
    void comparesLeastSignificantBitsWhenMostSignificantAreEqual() {
        UUID first = UUID.fromString("00000000-0000-0000-1000-000000000000");
        UUID second = UUID.fromString("00000000-0000-0000-f000-000000000000");

        assertTrue(FiberTerminationService.compareAsPostgres(first, second) < 0);
    }
}
