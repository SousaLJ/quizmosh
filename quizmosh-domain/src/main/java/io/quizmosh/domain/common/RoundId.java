package io.quizmosh.domain.common;

import java.util.Objects;

public record RoundId(String value) {
    public RoundId {
        Objects.requireNonNull(value, "round id cannot be null");
        if (value.isBlank()) throw new IllegalArgumentException("round id cannot be blank");
    }
    public static RoundId of(String value) { return new RoundId(value); }
    @Override public String toString() { return value; }
}
