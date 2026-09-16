package io.quizmosh.domain.event;
import io.quizmosh.domain.common.*;
import io.quizmosh.domain.game.GameModeId;
import java.time.Instant;
public record RoundStartedEvent(MatchId matchId, RoundId roundId, int roundNumber, QuestionId questionId, GameModeId modeId, Instant occurredAt) implements DomainEvent {
    public String type() { return "ROUND_STARTED"; }
}
