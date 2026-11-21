package com.geofiber.geofiber_backend.services.common;

import com.geofiber.geofiber_backend.enums.PortStatusEnum;

/** OCCUPIED é derivado das ligações; os demais estados podem ser escolhidos pelo usuário. */
public final class PortStatusResolver {
    private PortStatusResolver() {
    }

    /** Porta em uso fica OCCUPIED, salvo se marcada FAULTY: o defeito vale mesmo com algo ligado. */
    public static PortStatusEnum resolve(PortStatusEnum requested, boolean occupied) {
        if (occupied) {
            return requested == PortStatusEnum.FAULTY ? PortStatusEnum.FAULTY : PortStatusEnum.OCCUPIED;
        }
        if (requested == null || requested == PortStatusEnum.OCCUPIED) {
            return PortStatusEnum.FREE;
        }
        return requested;
    }
}
