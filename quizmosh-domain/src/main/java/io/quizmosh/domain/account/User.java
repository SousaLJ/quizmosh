package io.quizmosh.domain.account;
import java.time.Instant;
public record User(String id, String nickname, Instant createdAt) {
    public boolean has(Entitlement entitlement) { return entitlement == Entitlement.HOST_ROOM; }
}
