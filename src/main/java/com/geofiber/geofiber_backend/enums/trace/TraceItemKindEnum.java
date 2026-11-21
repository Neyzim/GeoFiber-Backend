package com.geofiber.geofiber_backend.enums.trace;

/** Tipo de um item do rastreio: ponto inicial, passo do caminho ou local de quebra. */
public enum TraceItemKindEnum {
    FIBER_END,
    FIBER,
    BOX,
    SPLITTER,
    SPLICE,
    SPLITTER_PORT,
    CABLE,
    OLT_PORT,
    SERVICE_PORT
}
