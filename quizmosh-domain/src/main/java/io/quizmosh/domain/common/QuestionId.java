package io.quizmosh.domain.common;

import java.util.Objects;

public record QuestionId(String value) {
    public QuestionId {
        Objects.requireNonNull(value, "question id cannot be null");
        if (value.isBlank()) throw new IllegalArgumentException("question id cannot be blank");
    }
    public static QuestionId of(String value) { return new QuestionId(value); }
    @Override public String toString() { return value; }
}
