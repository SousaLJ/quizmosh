package io.quizmosh.inmemory;
import io.quizmosh.application.port.DomainEventPublisher;
import io.quizmosh.domain.event.DomainEvent;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CollectingEventPublisher implements DomainEventPublisher {
    private final List<DomainEvent> events = new CopyOnWriteArrayList<>();
    public void publish(DomainEvent event) { events.add(event); }
    public List<DomainEvent> events() { return List.copyOf(events); }
    public void clear() { events.clear(); }
}
