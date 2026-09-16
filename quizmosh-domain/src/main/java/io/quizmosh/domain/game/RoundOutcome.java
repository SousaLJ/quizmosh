package io.quizmosh.domain.game;

import io.quizmosh.domain.common.ParticipantId;
import io.quizmosh.domain.common.RoundId;
import java.time.Instant;
import java.util.*;

public record RoundOutcome(RoundId roundId, Map<ParticipantId,Integer> scoreDeltas, Instant endedAt) {
    public RoundOutcome {
        Objects.requireNonNull(roundId);
        scoreDeltas = Map.copyOf(Objects.requireNonNull(scoreDeltas));
        Objects.requireNonNull(endedAt);
    }
}
