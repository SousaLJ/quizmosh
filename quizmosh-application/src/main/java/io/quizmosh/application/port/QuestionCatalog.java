package io.quizmosh.application.port;
import io.quizmosh.domain.common.*;
import io.quizmosh.domain.game.GameModeId;
import io.quizmosh.domain.quiz.QuestionDefinition;
import java.util.*;
public interface QuestionCatalog {
    Optional<QuestionDefinition> next(QuestionQuery query);
    record QuestionQuery(GameModeId modeId, Set<CategoryId> categories, Set<QuestionId> excludedIds,
                         String language, String scope, String region) {
        public QuestionQuery(GameModeId modeId, Set<CategoryId> categories, Set<QuestionId> excludedIds) {
            this(modeId,categories,excludedIds,"pt-BR","ALL","BR");
        }
        public QuestionQuery {
            categories = categories == null ? Set.of() : Set.copyOf(categories);
            excludedIds = excludedIds == null ? Set.of() : Set.copyOf(excludedIds);
        }
    }
}
