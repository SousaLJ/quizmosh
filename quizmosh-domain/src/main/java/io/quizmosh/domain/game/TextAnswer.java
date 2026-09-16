package io.quizmosh.domain.game;

import java.util.Objects;

public record TextAnswer(String text) implements AnswerValue {
    public TextAnswer {
        Objects.requireNonNull(text);
        text = text.strip();
        if (text.isBlank() || text.length() > 160)
            throw new IllegalArgumentException("text answer must have between 1 and 160 chars");
    }
    @Override public String answerType() { return "text"; }
}
