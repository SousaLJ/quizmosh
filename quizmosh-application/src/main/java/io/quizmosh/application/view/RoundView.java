package io.quizmosh.application.view;
import io.quizmosh.domain.common.RoundId;
import io.quizmosh.domain.game.GameModeId;
import java.time.Instant;
public record RoundView(RoundId id, int number, GameModeId modeId, Instant startedAt, Instant endsAt, QuestionView question) {}
