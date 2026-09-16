package io.quizmosh.domain.game;

import io.quizmosh.domain.common.*;
import io.quizmosh.domain.quiz.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GameModesTest {
    private static final ParticipantId P1 = ParticipantId.of("p1");
    private static final ParticipantId P2 = ParticipantId.of("p2");

    @Test
    void guessItRewardsEarlierCluesMore() {
        QuestionDefinition q = new QuestionDefinition(
                QuestionId.of("q1"), PackId.of("base"), CategoryId.of("movies"),
                Set.of(CoreGameModes.GUESS_IT), Difficulty.EASY, "Guess the movie",
                new GuessContent(List.of("1999", "Keanu Reeves"), List.of("The Matrix", "Matrix")),
                Set.of()
        );
        GuessItMode mode = new GuessItMode();
        Instant t = Instant.parse("2026-09-14T12:00:00Z");
        GameRound round = mode.openRound(RoundId.of("rd1"), 1, q, Set.of(P1,P2), t, Duration.ofSeconds(30));

        assertEquals(-100, mode.submit(round, P1, new TextAnswer("Titanic"), t.plusSeconds(2)).provisionalScoreDelta());
        mode.revealNextClue(round);
        assertEquals(800, mode.submit(round, P1, new TextAnswer("matrix"), t.plusSeconds(5)).provisionalScoreDelta());
        assertEquals(700, mode.close(round, t.plusSeconds(6)).scoreDeltas().get(P1));
    }

    @Test
    void closestWinsRanksByDistance() {
        QuestionDefinition q = new QuestionDefinition(
                QuestionId.of("q2"), PackId.of("base"), CategoryId.of("science"),
                Set.of(CoreGameModes.CLOSEST_WINS), Difficulty.MEDIUM, "Speed of light in km/s?",
                new NumericContent(new BigDecimal("299792.458"), "km/s"), Set.of()
        );
        ClosestWinsMode mode = new ClosestWinsMode();
        Instant t = Instant.parse("2026-09-14T12:00:00Z");
        GameRound round = mode.openRound(RoundId.of("rd2"), 1, q, Set.of(P1,P2), t, Duration.ofSeconds(30));
        mode.submit(round, P1, new NumericAnswer(new BigDecimal("300000")), t.plusSeconds(2));
        mode.submit(round, P2, new NumericAnswer(new BigDecimal("250000")), t.plusSeconds(3));
        RoundOutcome outcome = mode.close(round, t.plusSeconds(4));
        assertEquals(1000, outcome.scoreDeltas().get(P1));
        assertEquals(600, outcome.scoreDeltas().get(P2));
    }
}
