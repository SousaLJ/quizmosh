package io.quizmosh.domain.game;

import java.text.Normalizer;
import java.util.Locale;

final class AnswerNormalizer {
    private AnswerNormalizer() {}
    static String normalize(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
