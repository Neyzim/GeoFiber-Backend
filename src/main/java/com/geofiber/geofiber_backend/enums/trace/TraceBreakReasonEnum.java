package com.geofiber.geofiber_backend.enums.trace;

public enum TraceBreakReasonEnum {
    /** Ponta de fibra sem ligação nenhuma. */
    NO_CONNECTION,
    /** Porta que não recebe sinal (entrada do splitter sem fibra). */
    NO_SIGNAL,
    /** Porta de atendimento sem cliente. */
    NO_CUSTOMER,
    /** O caminho volta sobre um ponto já percorrido. */
    LOOP,
    /** Passou do limite de passos. */
    DEPTH_LIMIT
}
