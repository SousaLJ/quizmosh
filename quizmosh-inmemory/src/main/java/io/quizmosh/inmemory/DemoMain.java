package io.quizmosh.inmemory;

import io.quizmosh.application.QuizMoshApplicationService;
import io.quizmosh.application.command.*;
import io.quizmosh.application.view.*;
import io.quizmosh.domain.common.CategoryId;
import io.quizmosh.domain.game.*;
import io.quizmosh.domain.room.*;
import java.time.Duration;
import java.util.*;

public final class DemoMain {
    public static void main(String[] args) {
        var events = new CollectingEventPublisher();
        var app = new QuizMoshApplicationService(
                new InMemoryRoomStore(),
                new InMemoryMatchStore(),
                new InMemoryQuestionCatalog(SampleQuestions.all()),
                events,
                new UuidIdGenerator(),
                new RandomRoomCodeGenerator(),
                new SystemTimeProvider(),
                GameModeRegistry.defaults()
        );

        CreatedRoomView created = app.createRoom(new CreateRoomCommand("Ana", RoomSettings.defaults()));
        JoinedRoomView joined = app.joinRoom(new JoinRoomCommand(created.room().code(), "Leandro", ParticipantRole.PLAYER));

        MatchSettings settings = new MatchSettings(
                2,
                Set.of(CategoryId.of("science"), CategoryId.of("games")),
                List.of(CoreGameModes.CLASSIC_TRIVIA, CoreGameModes.QUICK_FIRE),
                Duration.ofSeconds(20)
        );

        app.startMatch(new StartMatchCommand(created.room().id(), created.ownerId(), settings));
        MatchView match = app.startNextRound(new StartNextRoundCommand(created.room().id(), created.ownerId()));

        System.out.println("Room: " + created.room().code());
        System.out.println("Round: " + match.currentRound().question().prompt());

        app.submitAnswer(new SubmitAnswerCommand(created.room().id(), created.ownerId(), new ChoiceAnswer("B")));
        app.submitAnswer(new SubmitAnswerCommand(created.room().id(), joined.participantId(), new ChoiceAnswer("A")));

        System.out.println("Score: " + app.match(created.room().id()).scores());
        System.out.println("Events: " + events.events().size());
    }
}
