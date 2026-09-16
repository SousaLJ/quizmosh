package io.quizmosh.domain.event;
import io.quizmosh.domain.common.*;
import java.time.Instant;
import java.util.Map;
public record MatchFinishedEvent(RoomId roomId, MatchId matchId, Map<ParticipantId,Integer> finalScores, Instant occurredAt) implements DomainEvent {
    public MatchFinishedEvent { finalScores = Map.copyOf(finalScores); }
    public String type() { return "MATCH_FINISHED"; }
}
