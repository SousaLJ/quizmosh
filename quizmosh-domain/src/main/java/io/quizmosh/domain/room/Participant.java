package io.quizmosh.domain.room;

import io.quizmosh.domain.common.ParticipantId;
import java.time.Instant;
import java.util.Objects;

public record Participant(ParticipantId id, String nickname, ParticipantRole role, Instant joinedAt) {
    public Participant {
        Objects.requireNonNull(id);
        Objects.requireNonNull(nickname);
        Objects.requireNonNull(role);
        Objects.requireNonNull(joinedAt);
        nickname = nickname.strip();
        if (nickname.isBlank() || nickname.length() > 24)
            throw new IllegalArgumentException("nickname must have between 1 and 24 characters");
    }
    public boolean canPlay() { return role == ParticipantRole.PLAYER; }
}
