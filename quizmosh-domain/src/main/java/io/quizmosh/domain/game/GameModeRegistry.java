package io.quizmosh.domain.game;

import io.quizmosh.domain.common.DomainException;
import java.util.*;

public final class GameModeRegistry {
    private final Map<GameModeId,GameMode> modes;

    public GameModeRegistry(Collection<? extends GameMode> modes) {
        Objects.requireNonNull(modes);
        Map<GameModeId,GameMode> map = new HashMap<>();
        for (GameMode mode : modes) {
            if (map.put(mode.id(), mode) != null) throw new IllegalArgumentException("duplicate game mode: " + mode.id());
        }
        this.modes = Map.copyOf(map);
    }

    public GameMode require(GameModeId id) {
        GameMode mode = modes.get(id);
        if (mode == null) throw new DomainException("unknown game mode: " + id);
        return mode;
    }

    public Set<GameModeId> ids() { return modes.keySet(); }

    public static GameModeRegistry defaults() {
        return new GameModeRegistry(List.of(
                new ClassicTriviaMode(),
                new QuickFireMode(),
                new GuessItMode(),
                new ClosestWinsMode()
        ));
    }
}
