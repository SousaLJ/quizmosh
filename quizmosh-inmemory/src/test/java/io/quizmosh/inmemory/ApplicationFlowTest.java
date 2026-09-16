package io.quizmosh.inmemory;

import io.quizmosh.application.QuizMoshApplicationService;
import io.quizmosh.application.command.*;
import io.quizmosh.application.view.*;
import io.quizmosh.domain.common.*;
import io.quizmosh.domain.game.*;
import io.quizmosh.domain.room.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationFlowTest {
    @Test
    void createsRoomJoinsStartsRoundAndScoresWithoutPlatformKnowledge() {
        var app = new QuizMoshApplicationService(
                new InMemoryRoomStore(),
                new InMemoryMatchStore(),
                new InMemoryQuestionCatalog(SampleQuestions.all()),
                new CollectingEventPublisher(),
                new DeterministicIds(),
                () -> RoomCode.of("AB12"),
                () -> Instant.parse("2026-09-14T12:00:00Z"),
                GameModeRegistry.defaults()
        );

        CreatedRoomView room = app.createRoom(new CreateRoomCommand("Ana", RoomSettings.defaults()));
        JoinedRoomView leo = app.joinRoom(new JoinRoomCommand(room.room().code(), "Leo", ParticipantRole.PLAYER));

        var settings = new MatchSettings(
                1, Set.of(CategoryId.of("science")),
                List.of(CoreGameModes.CLASSIC_TRIVIA), Duration.ofSeconds(20));

        app.startMatch(new StartMatchCommand(room.room().id(), room.ownerId(), settings));
        MatchView started = app.startNextRound(new StartNextRoundCommand(room.room().id(), room.ownerId()));

        assertEquals("Which planet has Olympus Mons?", started.currentRound().question().prompt());
        assertFalse(started.currentRound().question().choices().isEmpty());

        app.submitAnswer(new SubmitAnswerCommand(room.room().id(), room.ownerId(), new ChoiceAnswer("B")));
        app.submitAnswer(new SubmitAnswerCommand(room.room().id(), leo.participantId(), new ChoiceAnswer("A")));

        assertEquals(RoomStatus.LOBBY, app.room(room.room().id()).status());
    }

    private static final class DeterministicIds implements io.quizmosh.application.port.IdGenerator {
        private int i;
        private String next(String prefix) { return prefix + (++i); }
        public RoomId newRoomId() { return RoomId.of(next("r")); }
        public ParticipantId newParticipantId() { return ParticipantId.of(next("p")); }
        public MatchId newMatchId() { return MatchId.of(next("m")); }
        public RoundId newRoundId() { return RoundId.of(next("rd")); }
    }
}
