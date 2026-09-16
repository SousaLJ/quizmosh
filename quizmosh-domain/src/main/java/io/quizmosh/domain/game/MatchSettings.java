package io.quizmosh.domain.game;

import io.quizmosh.domain.common.CategoryId;
import java.time.Duration;
import java.util.*;

public record MatchSettings(int totalRounds, Set<CategoryId> categories, List<GameModeId> modes, Duration roundDuration) {
    public MatchSettings {
        if (totalRounds < 1 || totalRounds > 100) throw new IllegalArgumentException("totalRounds must be between 1 and 100");
        categories = categories == null ? Set.of() : Set.copyOf(categories);
        modes = List.copyOf(Objects.requireNonNull(modes));
        Objects.requireNonNull(roundDuration);
        if (modes.isEmpty()) throw new IllegalArgumentException("at least one game mode is required");
        if (roundDuration.isZero() || roundDuration.isNegative()) throw new IllegalArgumentException("round duration must be positive");
    }
    public GameModeId modeForRound(int zeroBasedRoundIndex) { return modes.get(zeroBasedRoundIndex % modes.size()); }
}
