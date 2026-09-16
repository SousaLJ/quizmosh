package io.quizmosh.application.command;
import io.quizmosh.domain.room.RoomSettings;
public record CreateRoomCommand(String nickname, RoomSettings settings) {}
