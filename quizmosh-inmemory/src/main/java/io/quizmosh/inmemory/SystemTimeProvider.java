package io.quizmosh.inmemory;
import io.quizmosh.application.port.TimeProvider;
import java.time.Instant;
public final class SystemTimeProvider implements TimeProvider {
    public Instant now() { return Instant.now(); }
}
