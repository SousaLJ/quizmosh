package io.quizmosh.application.view;

/**
 * Safe response for a client submission.
 * correct/scoreDelta remain null when the mode hides feedback until reveal.
 */
public record AnswerReceipt(
        boolean accepted,
        boolean playerLocked,
        boolean feedbackVisible,
        Boolean correct,
        Integer scoreDelta,
        String messageKey
) {}
