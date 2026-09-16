package io.quizmosh.application.view;
import io.quizmosh.domain.common.*;
import io.quizmosh.domain.game.MatchStatus;
import java.util.*;
public record MatchView(MatchId id, MatchStatus status, int completedRounds, int totalRounds,
                        Map<ParticipantId,Integer> scores, RoundView currentRound) {
    public MatchView { scores = Map.copyOf(scores); }
}
