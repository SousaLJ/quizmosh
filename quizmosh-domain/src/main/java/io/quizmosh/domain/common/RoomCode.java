package io.quizmosh.domain.common;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record RoomCode(String value) {
    private static final Pattern VALID = Pattern.compile("[A-Z0-9]{4,8}");

    public RoomCode {
        Objects.requireNonNull(value, "room code cannot be null");
        value = value.strip().toUpperCase(Locale.ROOT);
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("room code must contain 4-8 uppercase alphanumeric characters");
        }
    }

    public static RoomCode of(String value) { return new RoomCode(value); }
    @Override public String toString() { return value; }
}
