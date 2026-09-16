package io.quizmosh.domain.common;

import java.util.Objects;

public record MatchId(String value) {
    public MatchId {
        Objects.requireNonNull(value, "match id cannot be null");
        if (value.isBlank()) throw new IllegalArgumentException("match id cannot be blank");
    }
    public static MatchId of(String value) { return new MatchId(value); }
    @Override public String toString() { return value; }
}
