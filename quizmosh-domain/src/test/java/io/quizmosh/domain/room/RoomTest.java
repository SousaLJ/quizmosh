package io.quizmosh.domain.room;

import io.quizmosh.domain.common.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class RoomTest {
    @Test
    void transfersOwnershipWhenOwnerLeaves() {
        Participant owner = new Participant(ParticipantId.of("p1"), "Ana", ParticipantRole.PLAYER, Instant.EPOCH);
        Participant next = new Participant(ParticipantId.of("p2"), "Leo", ParticipantRole.PLAYER, Instant.EPOCH);
        Room room = new Room(RoomId.of("r1"), RoomCode.of("AB12"), owner, RoomSettings.defaults());
        room.join(next);
        room.leave(owner.id());
        assertEquals(next.id(), room.ownerId());
        assertEquals(RoomStatus.LOBBY, room.status());
    }
}
