package io.quizmosh.application.port;
import io.quizmosh.domain.event.DomainEvent;
public interface DomainEventPublisher { void publish(DomainEvent event); }
