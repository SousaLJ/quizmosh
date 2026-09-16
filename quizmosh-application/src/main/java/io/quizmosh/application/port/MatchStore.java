package io.quizmosh.application.port;
import io.quizmosh.domain.common.MatchId;
import io.quizmosh.domain.game.Match;
import java.util.Optional;
public interface MatchStore {
    Optional<Match> find(MatchId id);
    void save(Match match);
}
