package io.quizmosh.domain.game;

import io.quizmosh.domain.common.*;
import io.quizmosh.domain.quiz.QuestionDefinition;
import java.time.*;
import java.util.*;

public final class GameRound {
    private final RoundId id;
    private final int number;
    private final GameModeId modeId;
    private final QuestionDefinition question;
    private final Instant startedAt;
    private final Instant endsAt;
    private final Set<ParticipantId> players;
    private final LinkedHashMap<ParticipantId,List<SubmittedAnswer>> submissions = new LinkedHashMap<>();
    private final Set<ParticipantId> lockedPlayers = new HashSet<>();
    private int clueIndex;
    private RoundStatus status = RoundStatus.OPEN;

    public GameRound(RoundId id, int number, GameModeId modeId, QuestionDefinition question,
                     Set<ParticipantId> players, Instant startedAt, Duration duration) {
        this.id = Objects.requireNonNull(id);
        if (number < 1) throw new IllegalArgumentException("round number must be >= 1");
        this.number = number;
        this.modeId = Objects.requireNonNull(modeId);
        this.question = Objects.requireNonNull(question);
        this.players = Set.copyOf(Objects.requireNonNull(players));
        this.startedAt = Objects.requireNonNull(startedAt);
        Objects.requireNonNull(duration);
        this.endsAt = startedAt.plus(duration);
        this.clueIndex = 0;
    }

    public RoundId id() { return id; }
    public int number() { return number; }
    public GameModeId modeId() { return modeId; }
    public QuestionDefinition question() { return question; }
    public Instant startedAt() { return startedAt; }
    public Instant endsAt() { return endsAt; }
    public Set<ParticipantId> players() { return players; }
    public int clueIndex() { return clueIndex; }
    public RoundStatus status() { return status; }
    public boolean isOpen() { return status == RoundStatus.OPEN; }
    public boolean isExpired(Instant now) { return !now.isBefore(endsAt); }
    public boolean isLocked(ParticipantId playerId) { return lockedPlayers.contains(playerId); }
    public boolean hasAnySubmission(ParticipantId playerId) {
        return submissions.containsKey(playerId) && !submissions.get(playerId).isEmpty();
    }
    public boolean hasSubmittedAtCurrentClue(ParticipantId playerId) {
        return submissions.getOrDefault(playerId, List.of()).stream().anyMatch(a -> a.clueIndex() == clueIndex);
    }
    public List<SubmittedAnswer> submissionsFor(ParticipantId playerId) {
        return List.copyOf(submissions.getOrDefault(playerId, List.of()));
    }
    public Map<ParticipantId,List<SubmittedAnswer>> allSubmissions() {
        LinkedHashMap<ParticipantId,List<SubmittedAnswer>> copy = new LinkedHashMap<>();
        submissions.forEach((k,v) -> copy.put(k, List.copyOf(v)));
        return Collections.unmodifiableMap(copy);
    }

    public void recordSubmission(SubmittedAnswer submission, boolean lockPlayer) {
        requireOpen();
        if (!players.contains(submission.participantId())) throw new DomainException("participant is not a player in this round");
        if (isLocked(submission.participantId())) throw new DomainException("player is already locked");
        submissions.computeIfAbsent(submission.participantId(), ignored -> new ArrayList<>()).add(submission);
        if (lockPlayer) lockedPlayers.add(submission.participantId());
    }

    public boolean allPlayersLocked() { return players.stream().allMatch(this::isLocked); }

    public int revealNextClue(int clueCount) {
        requireOpen();
        if (clueCount < 1) throw new IllegalArgumentException("clueCount must be positive");
        if (clueIndex + 1 >= clueCount) throw new DomainException("no more clues");
        return ++clueIndex;
    }

    public void close() { requireOpen(); status = RoundStatus.CLOSED; }
    private void requireOpen() { if (!isOpen()) throw new DomainException("round is closed"); }
}
