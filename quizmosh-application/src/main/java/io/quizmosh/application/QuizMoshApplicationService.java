package io.quizmosh.application;

import io.quizmosh.application.command.*;
import io.quizmosh.application.port.*;
import io.quizmosh.application.view.*;
import io.quizmosh.domain.common.*;
import io.quizmosh.domain.event.*;
import io.quizmosh.domain.game.*;
import io.quizmosh.domain.quiz.QuestionDefinition;
import io.quizmosh.domain.room.*;

import java.time.Instant;
import java.util.*;

public final class QuizMoshApplicationService {
    private final RoomStore rooms;
    private final MatchStore matches;
    private final QuestionCatalog questions;
    private final DomainEventPublisher events;
    private final IdGenerator ids;
    private final RoomCodeGenerator roomCodes;
    private final TimeProvider time;
    private final GameModeRegistry gameModes;
    private final RoundScoringPolicy scoring;
    private final QuestionProjector projector = new QuestionProjector();

    public QuizMoshApplicationService(
            RoomStore rooms, MatchStore matches, QuestionCatalog questions,
            DomainEventPublisher events, IdGenerator ids, RoomCodeGenerator roomCodes,
            TimeProvider time, GameModeRegistry gameModes
    ) {
        this(rooms,matches,questions,events,ids,roomCodes,time,gameModes,(round,base)->base);
    }

    public QuizMoshApplicationService(
            RoomStore rooms, MatchStore matches, QuestionCatalog questions,
            DomainEventPublisher events, IdGenerator ids, RoomCodeGenerator roomCodes,
            TimeProvider time, GameModeRegistry gameModes, RoundScoringPolicy scoring
    ) {
        this.rooms = Objects.requireNonNull(rooms);
        this.matches = Objects.requireNonNull(matches);
        this.questions = Objects.requireNonNull(questions);
        this.events = Objects.requireNonNull(events);
        this.ids = Objects.requireNonNull(ids);
        this.roomCodes = Objects.requireNonNull(roomCodes);
        this.time = Objects.requireNonNull(time);
        this.gameModes = Objects.requireNonNull(gameModes);
        this.scoring = Objects.requireNonNull(scoring);
    }

    public CreatedRoomView createRoom(CreateRoomCommand command) {
        Instant now = time.now();
        ParticipantId ownerId = ids.newParticipantId();
        Participant owner = new Participant(ownerId, command.nickname(), ParticipantRole.PLAYER, now);
        Room room = new Room(ids.newRoomId(), uniqueRoomCode(), owner,
                command.settings() == null ? RoomSettings.defaults() : command.settings());
        rooms.save(room);
        events.publish(new RoomCreatedEvent(room.id(), room.code(), ownerId, now));
        return new CreatedRoomView(toView(room), ownerId);
    }

    public JoinedRoomView joinRoom(JoinRoomCommand command) {
        Instant now = time.now();
        Room room = rooms.findByCode(command.roomCode()).orElseThrow(() -> new DomainException("room not found"));
        ParticipantId id = ids.newParticipantId();
        Participant p = new Participant(id, command.nickname(),
                command.role() == null ? ParticipantRole.PLAYER : command.role(), now);
        room.join(p);
        rooms.save(room);
        events.publish(new ParticipantJoinedEvent(room.id(), id, p.nickname(), p.role(), now));
        return new JoinedRoomView(toView(room), id);
    }

    public RoomView leaveRoom(LeaveRoomCommand command) {
        Instant now = time.now();
        Room room = requireRoom(command.roomId());
        room.leave(command.participantId());
        rooms.save(room);
        events.publish(new ParticipantLeftEvent(room.id(), command.participantId(), now));
        return toView(room);
    }

    public MatchView startMatch(StartMatchCommand command) {
        Instant now = time.now();
        Room room = requireRoom(command.roomId());
        MatchId matchId = ids.newMatchId();
        List<ParticipantId> players = room.players().stream().map(Participant::id).toList();
        Match match = new Match(matchId, room.id(), command.settings(), players);
        room.startMatch(matchId, command.requesterId());
        rooms.save(room);
        matches.save(match);
        events.publish(new MatchStartedEvent(room.id(), match.id(), now));
        return toView(match);
    }

    public MatchView startNextRound(StartNextRoundCommand command) {
        Instant now = time.now();
        Room room = requireRoom(command.roomId());
        room.requireOwner(command.requesterId());
        Match match = requireCurrentMatch(room);

        if (!match.hasMoreRounds()) throw new DomainException("match has no remaining rounds");
        if (match.currentRound().isPresent()) throw new DomainException("a round is already active");

        int nextIndex = match.completedRounds();
        GameModeId modeId = match.settings().modeForRound(nextIndex);
        GameMode mode = gameModes.require(modeId);

        QuestionDefinition q = questions.next(new QuestionCatalog.QuestionQuery(
                modeId, match.settings().categories(), match.usedQuestionIds(),
                match.settings().questionLanguage(), match.settings().contentScope(), match.settings().questionRegion()
        )).orElseThrow(() -> new DomainException("no compatible question available"));

        GameRound round = mode.openRound(ids.newRoundId(), nextIndex + 1, q,
                new LinkedHashSet<>(match.players()), now, match.settings().roundDuration());
        match.beginRound(round);
        matches.save(match);
        events.publish(new RoundStartedEvent(match.id(), round.id(), round.number(), q.id(), modeId, now));
        return toView(match);
    }

    public AnswerReceipt submitAnswer(SubmitAnswerCommand command) {
        Instant now = time.now();
        Room room = requireRoom(command.roomId());
        Match match = requireCurrentMatch(room);
        GameRound round = match.currentRound().orElseThrow(() -> new DomainException("there is no active round"));
        if (!match.players().contains(command.participantId()))
            throw new DomainException("participant is not a player in this match");

        GameMode mode = gameModes.require(round.modeId());
        SubmissionResult result = mode.submit(round, command.participantId(), command.answer(), now);
        matches.save(match);

        if (result.accepted()) {
            events.publish(new AnswerSubmittedEvent(match.id(), round.id(), command.participantId(), now));
        }
        if (result.accepted() && mode.shouldAutoClose(round, now)) {
            closeRoundInternal(room, match, mode, now);
        }
        boolean visible = mode.feedbackPolicy() == AnswerFeedbackPolicy.IMMEDIATE;
        return new AnswerReceipt(
                result.accepted(),
                result.playerLocked(),
                visible && result.accepted(),
                visible && result.accepted() ? result.solved() : null,
                visible && result.accepted() ? result.provisionalScoreDelta() : null,
                result.messageKey()
        );
    }

    public MatchView revealNextClue(RevealNextClueCommand command) {
        Instant now = time.now();
        Room room = requireRoom(command.roomId());
        room.requireOwner(command.requesterId());
        Match match = requireCurrentMatch(room);
        GameRound round = match.currentRound().orElseThrow(() -> new DomainException("there is no active round"));
        GameMode mode = gameModes.require(round.modeId());
        mode.revealNextClue(round).orElseThrow(() -> new DomainException("this mode does not support progressive clues"));
        matches.save(match);
        events.publish(new ClueRevealedEvent(match.id(), round.id(), round.clueIndex(), now));
        return toView(match);
    }

    public MatchView closeRound(CloseRoundCommand command) {
        Instant now = time.now();
        Room room = requireRoom(command.roomId());
        room.requireOwner(command.requesterId());
        Match match = requireCurrentMatch(room);
        GameRound round = match.currentRound().orElseThrow(() -> new DomainException("there is no active round"));
        closeRoundInternal(room, match, gameModes.require(round.modeId()), now);
        return toView(match);
    }

    public RoomView room(RoomId roomId) { return toView(requireRoom(roomId)); }
    public MatchView match(RoomId roomId) { return toView(requireCurrentMatch(requireRoom(roomId))); }

    private void closeRoundInternal(Room room, Match match, GameMode mode, Instant now) {
        GameRound round = match.currentRound().orElseThrow(() -> new DomainException("there is no active round"));
        RoundOutcome outcome = scoring.apply(round, mode.close(round, now));
        match.completeCurrentRound(outcome);
        matches.save(match);
        events.publish(new RoundFinishedEvent(match.id(), outcome.roundId(), outcome.scoreDeltas(), match.scores(), now));

        if (match.status() == MatchStatus.FINISHED) {
            room.finishMatch(match.id());
            rooms.save(room);
            events.publish(new MatchFinishedEvent(room.id(), match.id(), match.scores(), now));
        }
    }

    private Room requireRoom(RoomId roomId) {
        return rooms.find(roomId).orElseThrow(() -> new DomainException("room not found"));
    }

    private Match requireCurrentMatch(Room room) {
        MatchId matchId = room.currentMatchId().orElseThrow(() -> new DomainException("room has no active match"));
        return matches.find(matchId).orElseThrow(() -> new DomainException("match not found"));
    }

    private RoomCode uniqueRoomCode() {
        for (int i = 0; i < 20; i++) {
            RoomCode code = roomCodes.next();
            if (rooms.findByCode(code).isEmpty()) return code;
        }
        throw new DomainException("could not allocate a unique room code");
    }

    private RoomView toView(Room room) {
        List<ParticipantView> participants = room.participants().stream()
                .map(p -> new ParticipantView(p.id(), p.nickname(), p.role(), room.isOwner(p.id())))
                .toList();
        return new RoomView(room.id(), room.code(), room.status(), room.ownerId(), participants);
    }

    private MatchView toView(Match match) {
        RoundView roundView = match.currentRound().map(r -> new RoundView(
                r.id(), r.number(), r.modeId(), r.startedAt(), r.endsAt(), projector.project(r)
        )).orElse(null);
        return new MatchView(match.id(), match.status(), match.completedRounds(),
                match.settings().totalRounds(), match.scores(), roundView);
    }
}
