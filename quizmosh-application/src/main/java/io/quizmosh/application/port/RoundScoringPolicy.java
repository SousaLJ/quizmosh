package io.quizmosh.application.port;

import io.quizmosh.domain.game.*;

/** Optional match-specific scoring, applied before history, totals and events are persisted. */
@FunctionalInterface
public interface RoundScoringPolicy {
    RoundOutcome apply(GameRound round, RoundOutcome base);
}
