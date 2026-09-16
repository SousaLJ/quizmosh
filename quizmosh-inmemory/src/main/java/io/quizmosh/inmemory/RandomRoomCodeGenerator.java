package io.quizmosh.inmemory;
import io.quizmosh.application.port.RoomCodeGenerator;
import io.quizmosh.domain.common.RoomCode;
import java.security.SecureRandom;

public final class RandomRoomCodeGenerator implements RoomCodeGenerator {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final SecureRandom random = new SecureRandom();
    @Override public RoomCode next() {
        char[] value = new char[4];
        for (int i=0;i<value.length;i++) value[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        return RoomCode.of(new String(value));
    }
}
