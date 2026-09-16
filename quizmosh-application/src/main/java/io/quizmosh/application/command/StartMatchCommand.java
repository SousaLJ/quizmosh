package io.quizmosh.application.command;
import io.quizmosh.domain.common.*;
import io.quizmosh.domain.game.MatchSettings;
public record StartMatchCommand(RoomId roomId, ParticipantId requesterId, MatchSettings settings) {}
