package io.quizmosh.protocol;

public enum ServerEventType {
    ROOM_STATE,
    MATCH_STATE,
    ROUND_STATE,
    ANSWER_ACK,
    PARTICIPANT_JOINED,
    PARTICIPANT_LEFT,
    CLUE_REVEALED,
    ROUND_FINISHED,
    MATCH_FINISHED,
    ERROR
}
