package io.quizmosh.domain.game;

import io.quizmosh.domain.common.*;
import io.quizmosh.domain.quiz.*;
import java.time.Instant;
import java.util.Optional;

public final class GuessItMode implements GameMode {
    private static final int WRONG_GUESS_PENALTY = -100;

    @Override public GameModeId id() { return CoreGameModes.GUESS_IT; }

    @Override public boolean supports(QuestionDefinition question) {
        return question.supports(id()) && question.content() instanceof GuessContent;
    }

    @Override
    public SubmissionResult submit(GameRound round, ParticipantId playerId, AnswerValue answer, Instant now) {
        validateRound(round, now);
        if (!(answer instanceof TextAnswer textAnswer)) return SubmissionResult.rejected("answer.type.invalid");
        if (round.isLocked(playerId)) return SubmissionResult.rejected("answer.player_locked");
        if (round.hasSubmittedAtCurrentClue(playerId)) return SubmissionResult.rejected("answer.wait_for_next_clue");

        GuessContent content = (GuessContent) round.question().content();
        String submitted = AnswerNormalizer.normalize(textAnswer.text());
        boolean correct = content.acceptedAnswers().stream()
                .map(AnswerNormalizer::normalize)
                .anyMatch(submitted::equals);

        int delta = correct ? scoreForClue(round.clueIndex()) : WRONG_GUESS_PENALTY;
        round.recordSubmission(new SubmittedAnswer(playerId, answer, now, round.clueIndex(), correct, delta), correct);
        return new SubmissionResult(true, correct, correct, delta, correct ? "guess.correct" : "guess.incorrect");
    }

    @Override
    public Optional<String> revealNextClue(GameRound round) {
        if (!round.modeId().equals(id())) throw new DomainException("wrong mode for round");
        GuessContent content = (GuessContent) round.question().content();
        int index = round.revealNextClue(content.clues().size());
        return Optional.of(content.clues().get(index));
    }

    @Override
    public RoundOutcome close(GameRound round, Instant now) {
        if (round.isOpen()) round.close();
        return new RoundOutcome(round.id(), ClassicTriviaMode.sumDeltas(round), now);
    }

    @Override public AnswerFeedbackPolicy feedbackPolicy() {
        return AnswerFeedbackPolicy.IMMEDIATE;
    }

    @Override public boolean shouldAutoClose(GameRound round, Instant now) {
        return round.isExpired(now) || round.allPlayersLocked();
    }

    private static int scoreForClue(int clueIndex) { return Math.max(100, 1000 - clueIndex * 200); }

    private void validateRound(GameRound round, Instant now) {
        if (!round.modeId().equals(id())) throw new DomainException("wrong mode for round");
        if (!round.isOpen()) throw new DomainException("round is closed");
        if (round.isExpired(now)) throw new DomainException("round has expired");
    }
}
