package io.quizmosh.domain.event;
import io.quizmosh.domain.common.*;
import java.time.Instant;
public record ParticipantLeftEvent(RoomId roomId, ParticipantId participantId, Instant occurredAt) implements DomainEvent {
    public String type() { return "PARTICIPANT_LEFT"; }
}
