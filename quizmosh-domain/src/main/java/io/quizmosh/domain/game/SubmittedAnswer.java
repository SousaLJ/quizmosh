package io.quizmosh.domain.game;

import io.quizmosh.domain.common.ParticipantId;
import java.time.Instant;
import java.util.Objects;

public record SubmittedAnswer(
        ParticipantId participantId,
        AnswerValue answer,
        Instant submittedAt,
        int clueIndex,
        boolean correct,
        int provisionalScoreDelta
) {
    public SubmittedAnswer {
        Objects.requireNonNull(participantId);
        Objects.requireNonNull(answer);
        Objects.requireNonNull(submittedAt);
        if (clueIndex < 0) throw new IllegalArgumentException("clueIndex cannot be negative");
    }
}
