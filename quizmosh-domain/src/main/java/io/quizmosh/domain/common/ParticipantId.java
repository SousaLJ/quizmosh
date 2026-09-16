package io.quizmosh.domain.common;

import java.util.Objects;

public record ParticipantId(String value) {
    public ParticipantId {
        Objects.requireNonNull(value, "participant id cannot be null");
        if (value.isBlank()) throw new IllegalArgumentException("participant id cannot be blank");
    }
    public static ParticipantId of(String value) { return new ParticipantId(value); }
    @Override public String toString() { return value; }
}
