package io.quizmosh.domain.game;

import io.quizmosh.domain.common.*;
import io.quizmosh.domain.quiz.QuestionDefinition;
import java.time.*;
import java.util.*;

public interface GameMode {
    GameModeId id();
    boolean supports(QuestionDefinition question);

    default GameRound openRound(RoundId roundId, int roundNumber, QuestionDefinition question,
                                Set<ParticipantId> players, Instant now, Duration duration) {
        if (!supports(question)) throw new DomainException("question is not compatible with mode " + id());
        return new GameRound(roundId, roundNumber, id(), question, players, now, duration);
    }

    SubmissionResult submit(GameRound round, ParticipantId playerId, AnswerValue answer, Instant now);
    RoundOutcome close(GameRound round, Instant now);

    default Optional<String> revealNextClue(GameRound round) { return Optional.empty(); }

    default AnswerFeedbackPolicy feedbackPolicy() {
        return AnswerFeedbackPolicy.HIDDEN_UNTIL_REVEAL;
    }

    boolean shouldAutoClose(GameRound round, Instant now);
}
