package io.quizmosh.application.command;
import io.quizmosh.domain.common.*;
public record CloseRoundCommand(RoomId roomId, ParticipantId requesterId) {}
