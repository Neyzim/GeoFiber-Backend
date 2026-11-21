package com.geofiber.geofiber_backend.services.common;

import com.geofiber.geofiber_backend.enums.PortStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortStatusResolverTest {
    @Test
    void connectedPortIsOccupiedUnlessFaulty() {
        assertEquals(PortStatusEnum.FAULTY, PortStatusResolver.resolve(PortStatusEnum.FAULTY, true));
        assertEquals(PortStatusEnum.OCCUPIED, PortStatusResolver.resolve(PortStatusEnum.RESERVED, true));
    }

    @Test
    void freePortKeepsRequestedManualStatus() {
        assertEquals(PortStatusEnum.RESERVED, PortStatusResolver.resolve(PortStatusEnum.RESERVED, false));
    }

    @Test
    void freePortCannotClaimOccupied() {
        assertEquals(PortStatusEnum.FREE, PortStatusResolver.resolve(PortStatusEnum.OCCUPIED, false));
        assertEquals(PortStatusEnum.FREE, PortStatusResolver.resolve(null, false));
    }
}
