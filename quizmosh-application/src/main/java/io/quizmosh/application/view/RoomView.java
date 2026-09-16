package io.quizmosh.application.view;
import io.quizmosh.domain.common.*;
import io.quizmosh.domain.room.RoomStatus;
import java.util.List;
public record RoomView(RoomId id, RoomCode code, RoomStatus status, ParticipantId ownerId, List<ParticipantView> participants) {
    public RoomView { participants = List.copyOf(participants); }
}
