package io.quizmosh.domain.event;
import io.quizmosh.domain.common.*;
import java.time.Instant;
import java.util.Map;
public record RoundFinishedEvent(MatchId matchId, RoundId roundId, Map<ParticipantId,Integer> scoreDeltas, Map<ParticipantId,Integer> totals, Instant occurredAt) implements DomainEvent {
    public RoundFinishedEvent { scoreDeltas = Map.copyOf(scoreDeltas); totals = Map.copyOf(totals); }
    public String type() { return "ROUND_FINISHED"; }
}
