package io.quizmosh.inmemory;
import io.quizmosh.application.port.RoomStore;
import io.quizmosh.domain.common.*;
import io.quizmosh.domain.room.Room;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryRoomStore implements RoomStore {
    private final Map<RoomId,Room> byId = new ConcurrentHashMap<>();
    @Override public Optional<Room> find(RoomId id) { return Optional.ofNullable(byId.get(id)); }
    @Override public Optional<Room> findByCode(RoomCode code) {
        return byId.values().stream().filter(room -> room.code().equals(code)).findFirst();
    }
    @Override public void save(Room room) { byId.put(room.id(), room); }
}
