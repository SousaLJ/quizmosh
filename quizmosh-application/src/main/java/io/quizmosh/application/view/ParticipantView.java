package io.quizmosh.application.view;
import io.quizmosh.domain.common.ParticipantId;
import io.quizmosh.domain.room.ParticipantRole;
public record ParticipantView(ParticipantId id, String nickname, ParticipantRole role, boolean owner) {}
