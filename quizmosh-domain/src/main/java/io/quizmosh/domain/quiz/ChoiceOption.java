package io.quizmosh.domain.quiz;

import java.util.Objects;

public record ChoiceOption(String id, String text) {
    public ChoiceOption {
        Objects.requireNonNull(id);
        Objects.requireNonNull(text);
        id = id.strip();
        text = text.strip();
        if (id.isBlank() || text.isBlank()) throw new IllegalArgumentException("choice id/text cannot be blank");
    }
}
