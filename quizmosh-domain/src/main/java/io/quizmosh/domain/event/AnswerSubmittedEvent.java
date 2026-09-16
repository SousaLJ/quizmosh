package io.quizmosh.domain.event;
import io.quizmosh.domain.common.*;
import java.time.Instant;

public record AnswerSubmittedEvent(
        MatchId matchId,
        RoundId roundId,
        ParticipantId participantId,
        Instant occurredAt
) implements DomainEvent {
    public String type() { return "ANSWER_SUBMITTED"; }
}
