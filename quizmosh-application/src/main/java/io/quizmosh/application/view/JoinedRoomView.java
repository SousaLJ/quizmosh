package io.quizmosh.application.view;
import io.quizmosh.domain.common.ParticipantId;
public record JoinedRoomView(RoomView room, ParticipantId participantId) {}
