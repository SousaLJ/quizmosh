package io.quizmosh.domain.quiz;

import java.util.List;
import java.util.Objects;

public record GuessContent(List<String> clues, List<String> acceptedAnswers) implements QuestionContent {
    public GuessContent {
        clues = List.copyOf(Objects.requireNonNull(clues));
        acceptedAnswers = List.copyOf(Objects.requireNonNull(acceptedAnswers));
        if (clues.isEmpty()) throw new IllegalArgumentException("guess question needs at least one clue");
        if (acceptedAnswers.isEmpty()) throw new IllegalArgumentException("guess question needs accepted answers");
        if (clues.stream().anyMatch(String::isBlank) || acceptedAnswers.stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("clues/answers cannot be blank");
    }
    @Override public String contentType() { return "guess"; }
}
