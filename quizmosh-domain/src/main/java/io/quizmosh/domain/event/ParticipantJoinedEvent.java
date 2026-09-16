package io.quizmosh.domain.event;
import io.quizmosh.domain.common.*;
import io.quizmosh.domain.room.ParticipantRole;
import java.time.Instant;
public record ParticipantJoinedEvent(RoomId roomId, ParticipantId participantId, String nickname, ParticipantRole role, Instant occurredAt) implements DomainEvent {
    public String type() { return "PARTICIPANT_JOINED"; }
}
