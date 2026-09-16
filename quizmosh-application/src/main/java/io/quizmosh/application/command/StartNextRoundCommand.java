package io.quizmosh.application.command;
import io.quizmosh.domain.common.*;
public record StartNextRoundCommand(RoomId roomId, ParticipantId requesterId) {}
