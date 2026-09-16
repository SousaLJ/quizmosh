package io.quizmosh.inmemory;
import io.quizmosh.application.port.IdGenerator;
import io.quizmosh.domain.common.*;
import java.util.UUID;

public final class UuidIdGenerator implements IdGenerator {
    private static String next(String prefix) { return prefix + "_" + UUID.randomUUID(); }
    public RoomId newRoomId() { return RoomId.of(next("room")); }
    public ParticipantId newParticipantId() { return ParticipantId.of(next("player")); }
    public MatchId newMatchId() { return MatchId.of(next("match")); }
    public RoundId newRoundId() { return RoundId.of(next("round")); }
}
