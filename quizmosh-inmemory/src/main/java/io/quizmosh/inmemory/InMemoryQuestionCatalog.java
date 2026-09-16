package io.quizmosh.inmemory;
import io.quizmosh.application.port.QuestionCatalog;
import io.quizmosh.domain.quiz.QuestionDefinition;
import java.util.*;

public final class InMemoryQuestionCatalog implements QuestionCatalog {
    private final List<QuestionDefinition> questions;
    public InMemoryQuestionCatalog(Collection<QuestionDefinition> questions) { this.questions = List.copyOf(questions); }
    @Override public Optional<QuestionDefinition> next(QuestionQuery query) {
        return questions.stream()
                .filter(q -> q.supports(query.modeId()))
                .filter(q -> query.categories().isEmpty() || query.categories().contains(q.categoryId()))
                .filter(q -> !query.excludedIds().contains(q.id()))
                .findFirst();
    }
}
