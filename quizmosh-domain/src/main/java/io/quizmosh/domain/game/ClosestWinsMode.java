package io.quizmosh.domain.game;

import io.quizmosh.domain.common.*;
import io.quizmosh.domain.quiz.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class ClosestWinsMode implements GameMode {
    private static final int[] POSITION_POINTS = {1000, 600, 300, 100};

    @Override public GameModeId id() { return CoreGameModes.CLOSEST_WINS; }

    @Override public boolean supports(QuestionDefinition question) {
        return question.supports(id()) && question.content() instanceof NumericContent;
    }

    @Override
    public SubmissionResult submit(GameRound round, ParticipantId playerId, AnswerValue answer, Instant now) {
        validateRound(round, now);
        if (!(answer instanceof NumericAnswer)) return SubmissionResult.rejected("answer.type.invalid");
        if (round.hasAnySubmission(playerId)) return SubmissionResult.rejected("answer.already_submitted");
        round.recordSubmission(new SubmittedAnswer(playerId, answer, now, 0, false, 0), true);
        return new SubmissionResult(true, false, true, 0, "answer.accepted");
    }

    @Override
    public RoundOutcome close(GameRound round, Instant now) {
        NumericContent content = (NumericContent) round.question().content();
        record DistanceEntry(ParticipantId player, BigDecimal distance) {}

        List<DistanceEntry> ranking = new ArrayList<>();
        for (ParticipantId player : round.players()) {
            List<SubmittedAnswer> submissions = round.submissionsFor(player);
            if (submissions.isEmpty()) continue;
            NumericAnswer answer = (NumericAnswer) submissions.get(0).answer();
            ranking.add(new DistanceEntry(player, answer.value().subtract(content.correctValue()).abs()));
        }
        ranking.sort(Comparator.comparing(DistanceEntry::distance));

        Map<ParticipantId,Integer> deltas = new LinkedHashMap<>();
        round.players().forEach(p -> deltas.put(p, 0));

        BigDecimal previousDistance = null;
        int position = -1;
        for (int i = 0; i < ranking.size(); i++) {
            DistanceEntry entry = ranking.get(i);
            if (previousDistance == null || entry.distance().compareTo(previousDistance) != 0) {
                position = i;
                previousDistance = entry.distance();
            }
            int points = position < POSITION_POINTS.length ? POSITION_POINTS[position] : 0;
            if (entry.distance().compareTo(BigDecimal.ZERO) == 0) points += 200;
            deltas.put(entry.player(), points);
        }

        if (round.isOpen()) round.close();
        return new RoundOutcome(round.id(), deltas, now);
    }

    @Override public boolean shouldAutoClose(GameRound round, Instant now) {
        return round.isExpired(now) || round.allPlayersLocked();
    }

    private void validateRound(GameRound round, Instant now) {
        if (!round.modeId().equals(id())) throw new DomainException("wrong mode for round");
        if (!round.isOpen()) throw new DomainException("round is closed");
        if (round.isExpired(now)) throw new DomainException("round has expired");
    }
}
