package io.quizmosh.domain.game;

public record SubmissionResult(
        boolean accepted,
        boolean solved,
        boolean playerLocked,
        int provisionalScoreDelta,
        String messageKey
) {
    public static SubmissionResult rejected(String messageKey) {
        return new SubmissionResult(false, false, false, 0, messageKey);
    }
}
