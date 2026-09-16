package io.quizmosh.domain.quiz;

import java.util.List;
import java.util.Objects;

public record ChoiceContent(List<ChoiceOption> options, String correctOptionId) implements QuestionContent {
    public ChoiceContent {
        options = List.copyOf(Objects.requireNonNull(options));
        Objects.requireNonNull(correctOptionId);
        if (options.size() < 2) throw new IllegalArgumentException("choice question needs at least two options");
        if (options.stream().map(ChoiceOption::id).distinct().count() != options.size())
            throw new IllegalArgumentException("choice ids must be unique");
        if (options.stream().noneMatch(o -> o.id().equals(correctOptionId)))
            throw new IllegalArgumentException("correct option must exist");
    }
    @Override public String contentType() { return "choice"; }
}
