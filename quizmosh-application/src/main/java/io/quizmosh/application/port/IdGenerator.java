package io.quizmosh.application.port;
import io.quizmosh.domain.common.*;
public interface IdGenerator {
    RoomId newRoomId();
    ParticipantId newParticipantId();
    MatchId newMatchId();
    RoundId newRoundId();
}
