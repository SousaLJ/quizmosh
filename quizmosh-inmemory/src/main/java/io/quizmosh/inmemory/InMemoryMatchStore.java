package io.quizmosh.inmemory;
import io.quizmosh.application.port.MatchStore;
import io.quizmosh.domain.common.MatchId;
import io.quizmosh.domain.game.Match;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMatchStore implements MatchStore {
    private final Map<MatchId,Match> matches = new ConcurrentHashMap<>();
    @Override public Optional<Match> find(MatchId id) { return Optional.ofNullable(matches.get(id)); }
    @Override public void save(Match match) { matches.put(match.id(), match); }
}
