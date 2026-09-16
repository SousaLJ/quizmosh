package io.quizmosh.protocol;

public enum ClientCommandType {
    CREATE_ROOM,
    JOIN_ROOM,
    LEAVE_ROOM,
    START_MATCH,
    START_NEXT_ROUND,
    SUBMIT_ANSWER,
    REVEAL_NEXT_CLUE,
    CLOSE_ROUND
}
