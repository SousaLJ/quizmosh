package io.quizmosh.domain.event;
import io.quizmosh.domain.common.*;
import java.time.Instant;
public record MatchStartedEvent(RoomId roomId, MatchId matchId, Instant occurredAt) implements DomainEvent {
    public String type() { return "MATCH_STARTED"; }
}
