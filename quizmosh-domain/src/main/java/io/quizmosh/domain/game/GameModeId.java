package io.quizmosh.domain.game;

import java.util.Objects;

public record GameModeId(String value) {
    public GameModeId {
        Objects.requireNonNull(value);
        value = value.strip().toLowerCase();
        if (value.isBlank()) throw new IllegalArgumentException("game mode id cannot be blank");
    }
    public static GameModeId of(String value) { return new GameModeId(value); }
    @Override public String toString() { return value; }
}
