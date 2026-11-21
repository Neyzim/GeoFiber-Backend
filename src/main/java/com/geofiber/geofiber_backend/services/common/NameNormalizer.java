package com.geofiber.geofiber_backend.services.common;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Chave de comparação de nomes: sem acento, minúsculas, "-" e "_" viram espaço e as pontas são aparadas.
 * Dois nomes com a mesma chave são considerados o mesmo nome.
 */
public final class NameNormalizer {
    private NameNormalizer() {
    }

    public static String normalize(String name) {
        String withoutAccents = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return withoutAccents.toLowerCase(Locale.ROOT).replaceAll("[-_]", " ").strip();
    }
}
