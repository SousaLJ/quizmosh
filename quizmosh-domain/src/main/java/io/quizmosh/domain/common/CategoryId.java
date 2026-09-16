package io.quizmosh.domain.common;

import java.util.Objects;

public record CategoryId(String value) {
    public CategoryId {
        Objects.requireNonNull(value, "category id cannot be null");
        if (value.isBlank()) throw new IllegalArgumentException("category id cannot be blank");
    }
    public static CategoryId of(String value) { return new CategoryId(value); }
    @Override public String toString() { return value; }
}
