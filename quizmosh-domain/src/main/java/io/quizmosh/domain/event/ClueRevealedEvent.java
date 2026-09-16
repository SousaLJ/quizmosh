package io.quizmosh.domain.event;
import io.quizmosh.domain.common.*;
import java.time.Instant;
public record ClueRevealedEvent(MatchId matchId, RoundId roundId, int clueIndex, Instant occurredAt) implements DomainEvent {
    public String type() { return "CLUE_REVEALED"; }
}
