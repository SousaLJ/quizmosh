package io.quizmosh.domain.game;

import io.quizmosh.domain.common.*;
import java.time.Instant;
import java.util.Map;

public record RoundResult(
        RoundId roundId,
        QuestionId questionId,
        GameModeId modeId,
        Map<ParticipantId,Integer> scoreDeltas,
        Instant endedAt
) {}
