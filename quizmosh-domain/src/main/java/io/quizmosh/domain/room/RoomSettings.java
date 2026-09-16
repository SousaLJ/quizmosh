package io.quizmosh.domain.room;

public record RoomSettings(int maxPlayers, boolean allowGuests, boolean allowSpectators, boolean allowDisplays) {
    public RoomSettings {
        if (maxPlayers < 2 || maxPlayers > 32)
            throw new IllegalArgumentException("maxPlayers must be between 2 and 32");
    }
    public static RoomSettings defaults() { return new RoomSettings(12, true, true, true); }
}
