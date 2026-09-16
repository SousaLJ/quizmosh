package io.quizmosh.domain.game;

import io.quizmosh.domain.common.*;
import java.util.*;

public final class Match {
    private final MatchId id;
    private final RoomId roomId;
    private final MatchSettings settings;
    private final List<ParticipantId> players;
    private final LinkedHashMap<ParticipantId,Integer> scores = new LinkedHashMap<>();
    private final List<RoundResult> history = new ArrayList<>();
    private final Set<QuestionId> usedQuestionIds = new HashSet<>();
    private MatchStatus status = MatchStatus.CREATED;
    private GameRound currentRound;

    public Match(MatchId id, RoomId roomId, MatchSettings settings, List<ParticipantId> players) {
        this.id = Objects.requireNonNull(id);
        this.roomId = Objects.requireNonNull(roomId);
        this.settings = Objects.requireNonNull(settings);
        this.players = List.copyOf(Objects.requireNonNull(players));
        if (players.size() < 2) throw new DomainException("match requires at least two players");
        players.forEach(p -> scores.put(p, 0));
    }

    public MatchId id() { return id; }
    public RoomId roomId() { return roomId; }
    public MatchSettings settings() { return settings; }
    public List<ParticipantId> players() { return players; }
    public Map<ParticipantId,Integer> scores() { return Collections.unmodifiableMap(scores); }
    public List<RoundResult> history() { return List.copyOf(history); }
    public Set<QuestionId> usedQuestionIds() { return Set.copyOf(usedQuestionIds); }
    public MatchStatus status() { return status; }
    public Optional<GameRound> currentRound() { return Optional.ofNullable(currentRound); }
    public int completedRounds() { return history.size(); }
    public boolean hasMoreRounds() { return completedRounds() < settings.totalRounds(); }

    public void beginRound(GameRound round) {
        Objects.requireNonNull(round);
        if (status == MatchStatus.FINISHED) throw new DomainException("match is finished");
        if (currentRound != null && currentRound.isOpen()) throw new DomainException("current round is still open");
        if (!hasMoreRounds()) throw new DomainException("match has no remaining rounds");
        currentRound = round;
        usedQuestionIds.add(round.question().id());
        status = MatchStatus.IN_PROGRESS;
    }

    public void completeCurrentRound(RoundOutcome outcome) {
        if (currentRound == null) throw new DomainException("there is no current round");
        if (!currentRound.id().equals(outcome.roundId())) throw new DomainException("round outcome does not match current round");
        if (currentRound.isOpen()) throw new DomainException("round must be closed before applying outcome");

        outcome.scoreDeltas().forEach((player,delta) -> {
            if (!scores.containsKey(player)) throw new DomainException("score delta references unknown player");
            scores.merge(player, delta, Integer::sum);
        });
        history.add(new RoundResult(currentRound.id(), currentRound.question().id(), currentRound.modeId(), outcome.scoreDeltas(), outcome.endedAt()));
        currentRound = null;
        if (!hasMoreRounds()) status = MatchStatus.FINISHED;
    }
}
