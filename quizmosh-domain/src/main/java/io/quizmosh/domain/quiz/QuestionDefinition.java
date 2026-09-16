package io.quizmosh.domain.quiz;

import io.quizmosh.domain.common.*;
import io.quizmosh.domain.game.GameModeId;
import java.util.*;

public record QuestionDefinition(
        QuestionId id,
        PackId packId,
        CategoryId categoryId,
        Set<GameModeId> supportedModes,
        Difficulty difficulty,
        String prompt,
        QuestionContent content,
        Set<String> tags
) {
    public QuestionDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(packId);
        Objects.requireNonNull(categoryId);
        supportedModes = Set.copyOf(Objects.requireNonNull(supportedModes));
        Objects.requireNonNull(difficulty);
        Objects.requireNonNull(prompt);
        Objects.requireNonNull(content);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        prompt = prompt.strip();
        if (prompt.isBlank()) throw new IllegalArgumentException("prompt cannot be blank");
        if (supportedModes.isEmpty()) throw new IllegalArgumentException("question must support at least one game mode");
    }
    public boolean supports(GameModeId modeId) { return supportedModes.contains(modeId); }
}
