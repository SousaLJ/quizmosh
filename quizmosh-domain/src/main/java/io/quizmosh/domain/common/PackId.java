package io.quizmosh.domain.common;

import java.util.Objects;

public record PackId(String value) {
    public PackId {
        Objects.requireNonNull(value, "pack id cannot be null");
        if (value.isBlank()) throw new IllegalArgumentException("pack id cannot be blank");
    }
    public static PackId of(String value) { return new PackId(value); }
    @Override public String toString() { return value; }
}
