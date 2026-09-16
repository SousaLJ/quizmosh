package io.quizmosh.application.command;
import io.quizmosh.domain.common.RoomCode;
import io.quizmosh.domain.room.ParticipantRole;
public record JoinRoomCommand(RoomCode roomCode, String nickname, ParticipantRole role) {}
