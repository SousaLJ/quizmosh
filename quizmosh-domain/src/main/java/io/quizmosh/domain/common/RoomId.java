package io.quizmosh.domain.common;

import java.util.Objects;

public record RoomId(String value) {
    public RoomId {
        Objects.requireNonNull(value, "room id cannot be null");
        if (value.isBlank()) throw new IllegalArgumentException("room id cannot be blank");
    }
    public static RoomId of(String value) { return new RoomId(value); }
    @Override public String toString() { return value; }
}
