package io.quizmosh.application.command;
import io.quizmosh.domain.common.*;
public record RevealNextClueCommand(RoomId roomId, ParticipantId requesterId) {}
