package io.quizmosh.application.view;
import io.quizmosh.domain.common.ParticipantId;
public record CreatedRoomView(RoomView room, ParticipantId ownerId) {}
