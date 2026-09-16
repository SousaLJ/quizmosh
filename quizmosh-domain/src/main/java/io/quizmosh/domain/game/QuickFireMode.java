package io.quizmosh.domain.game;

import io.quizmosh.domain.common.*;
import io.quizmosh.domain.quiz.*;
import java.time.*;

public final class QuickFireMode implements GameMode {
    @Override public GameModeId id() { return CoreGameModes.QUICK_FIRE; }

    @Override public boolean supports(QuestionDefinition question) {
        return question.supports(id()) && question.content() instanceof ChoiceContent;
    }

    @Override
    public SubmissionResult submit(GameRound round, ParticipantId playerId, AnswerValue answer, Instant now) {
        validateRound(round, now);
        if (!(answer instanceof ChoiceAnswer choiceAnswer)) return SubmissionResult.rejected("answer.type.invalid");
        if (round.hasAnySubmission(playerId)) return SubmissionResult.rejected("answer.already_submitted");

        ChoiceContent content = (ChoiceContent) round.question().content();
        if (content.options().stream().noneMatch(o -> o.id().equals(choiceAnswer.optionId())))
            return SubmissionResult.rejected("answer.option.invalid");

        boolean correct = content.correctOptionId().equals(choiceAnswer.optionId());
        int delta = correct ? speedScore(round, now) : 0;
        round.recordSubmission(new SubmittedAnswer(playerId, answer, now, 0, correct, delta), true);
        return new SubmissionResult(true, correct, true, delta, "answer.accepted");
    }

    @Override
    public RoundOutcome close(GameRound round, Instant now) {
        if (round.isOpen()) round.close();
        return new RoundOutcome(round.id(), ClassicTriviaMode.sumDeltas(round), now);
    }

    @Override public boolean shouldAutoClose(GameRound round, Instant now) {
        return round.isExpired(now) || round.allPlayersLocked();
    }

    private static int speedScore(GameRound round, Instant now) {
        long total = Duration.between(round.startedAt(), round.endsAt()).toMillis();
        long remaining = Math.max(0, Duration.between(now, round.endsAt()).toMillis());
        double ratio = total == 0 ? 0 : (double) remaining / total;
        return 500 + (int) Math.round(500 * ratio);
    }

    private void validateRound(GameRound round, Instant now) {
        if (!round.modeId().equals(id())) throw new DomainException("wrong mode for round");
        if (!round.isOpen()) throw new DomainException("round is closed");
        if (round.isExpired(now)) throw new DomainException("round has expired");
    }
}
