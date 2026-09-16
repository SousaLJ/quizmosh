package io.quizmosh.domain.room;

import io.quizmosh.domain.common.*;
import java.util.*;

public final class Room {
    private final RoomId id;
    private final RoomCode code;
    private final RoomSettings settings;
    private final LinkedHashMap<ParticipantId, Participant> participants = new LinkedHashMap<>();
    private ParticipantId ownerId;
    private RoomStatus status;
    private MatchId currentMatchId;

    public Room(RoomId id, RoomCode code, Participant owner, RoomSettings settings) {
        this.id = Objects.requireNonNull(id);
        this.code = Objects.requireNonNull(code);
        this.settings = Objects.requireNonNull(settings);
        Objects.requireNonNull(owner);
        if (!owner.canPlay()) throw new DomainException("room owner must join as a player");
        ownerId = owner.id();
        participants.put(owner.id(), owner);
        status = RoomStatus.LOBBY;
    }

    public RoomId id() { return id; }
    public RoomCode code() { return code; }
    public RoomSettings settings() { return settings; }
    public ParticipantId ownerId() { return ownerId; }
    public RoomStatus status() { return status; }
    public Optional<MatchId> currentMatchId() { return Optional.ofNullable(currentMatchId); }
    public List<Participant> participants() { return List.copyOf(participants.values()); }
    public List<Participant> players() { return participants.values().stream().filter(Participant::canPlay).toList(); }
    public Optional<Participant> participant(ParticipantId id) { return Optional.ofNullable(participants.get(id)); }
    public boolean isOwner(ParticipantId participantId) { return ownerId.equals(participantId); }

    public void join(Participant participant) {
        Objects.requireNonNull(participant);
        if (status == RoomStatus.CLOSED) throw new DomainException("room is closed");
        if (participants.containsKey(participant.id())) throw new DomainException("participant already joined");
        if (participants.values().stream().anyMatch(p -> p.nickname().equalsIgnoreCase(participant.nickname())))
            throw new DomainException("nickname already in use");
        if (participant.role() == ParticipantRole.SPECTATOR && !settings.allowSpectators())
            throw new DomainException("spectators are disabled");
        if (participant.role() == ParticipantRole.DISPLAY && !settings.allowDisplays())
            throw new DomainException("display clients are disabled");
        if (participant.canPlay() && players().size() >= settings.maxPlayers())
            throw new DomainException("room is full");
        participants.put(participant.id(), participant);
    }

    public void leave(ParticipantId participantId) {
        Participant removed = participants.remove(participantId);
        if (removed == null) return;
        if (participantId.equals(ownerId)) {
            Participant next = participants.values().stream().filter(Participant::canPlay).findFirst().orElse(null);
            if (next == null) close();
            else ownerId = next.id();
        }
    }

    public void startMatch(MatchId matchId, ParticipantId requester) {
        requireOwner(requester);
        if (status != RoomStatus.LOBBY) throw new DomainException("room is not in lobby");
        if (players().size() < 2) throw new DomainException("at least two players are required");
        currentMatchId = Objects.requireNonNull(matchId);
        status = RoomStatus.IN_MATCH;
    }

    public void finishMatch(MatchId matchId) {
        if (currentMatchId == null || !currentMatchId.equals(matchId))
            throw new DomainException("match does not belong to room");
        currentMatchId = null;
        status = RoomStatus.LOBBY;
    }

    public void close() {
        currentMatchId = null;
        status = RoomStatus.CLOSED;
    }

    public void requireOwner(ParticipantId requester) {
        if (!isOwner(requester)) throw new DomainException("operation requires room owner");
    }

    /** Application adapters decide when an absent host should be replaced. */
    public void transferOwnership(ParticipantId nextOwner) {
        Participant next = participants.get(nextOwner);
        if (next == null || !next.canPlay()) throw new DomainException("new owner must be a player");
        ownerId = nextOwner;
    }
}
