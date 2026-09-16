package io.quizmosh.application.command;
import io.quizmosh.domain.common.*;
import io.quizmosh.domain.game.AnswerValue;
public record SubmitAnswerCommand(RoomId roomId, ParticipantId participantId, AnswerValue answer) {}
