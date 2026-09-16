package io.quizmosh.application.command;
import io.quizmosh.domain.common.*;
public record LeaveRoomCommand(RoomId roomId, ParticipantId participantId) {}
