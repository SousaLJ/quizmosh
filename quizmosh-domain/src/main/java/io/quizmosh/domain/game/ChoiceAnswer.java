package io.quizmosh.domain.game;

import java.util.Objects;

public record ChoiceAnswer(String optionId) implements AnswerValue {
    public ChoiceAnswer {
        Objects.requireNonNull(optionId);
        optionId = optionId.strip();
        if (optionId.isBlank()) throw new IllegalArgumentException("optionId cannot be blank");
    }
    @Override public String answerType() { return "choice"; }
}
