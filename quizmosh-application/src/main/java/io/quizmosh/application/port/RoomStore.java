package io.quizmosh.application.port;
import io.quizmosh.domain.common.*;
import io.quizmosh.domain.room.Room;
import java.util.Optional;
public interface RoomStore {
    Optional<Room> find(RoomId id);
    Optional<Room> findByCode(RoomCode code);
    void save(Room room);
}
