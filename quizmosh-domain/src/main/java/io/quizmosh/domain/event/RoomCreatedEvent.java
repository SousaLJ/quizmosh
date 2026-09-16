package io.quizmosh.domain.event;
import io.quizmosh.domain.common.*;
import java.time.Instant;
public record RoomCreatedEvent(RoomId roomId, RoomCode code, ParticipantId ownerId, Instant occurredAt) implements DomainEvent {
    public String type() { return "ROOM_CREATED"; }
}
