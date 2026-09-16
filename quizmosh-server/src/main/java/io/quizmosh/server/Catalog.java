package io.quizmosh.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import io.quizmosh.application.port.QuestionCatalog;
import io.quizmosh.domain.common.*;
import io.quizmosh.domain.game.*;
import io.quizmosh.domain.quiz.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.math.BigDecimal;

/** Server-only content. No answer catalog is ever served as a static resource. */
@Component
public final class Catalog implements QuestionCatalog {
    public record Entry(String id, String category, String type, String prompt, List<String> options,
                        Integer correctIndex, List<String> clues, List<String> answers,
                        BigDecimal value, String unit, String explanation) {}
    private final Map<QuestionId, QuestionDefinition> definitions = new LinkedHashMap<>();
    private final Map<QuestionId, Entry> entries = new LinkedHashMap<>();
    public Catalog(ObjectMapper mapper) throws Exception {
        try (var stream = getClass().getResourceAsStream("/questions.json")) {
            if (stream == null) throw new IllegalStateException("Missing questions.json");
            List<Entry> loaded = mapper.readValue(stream, new TypeReference<>() {});
            for (Entry e : loaded) {
                QuestionContent content;
                Set<GameModeId> modes;
                switch (e.type()) {
                    case "choice" -> {
                        List<ChoiceOption> choices = new ArrayList<>();
                        for (int i=0;i<e.options().size();i++) choices.add(new ChoiceOption(""+(char)('A'+i), e.options().get(i)));
                        content = new ChoiceContent(choices, ""+(char)('A'+e.correctIndex()));
                        modes = Set.of(CoreGameModes.CLASSIC_TRIVIA, CoreGameModes.QUICK_FIRE);
                    }
                    case "guess" -> { content = new GuessContent(e.clues(),e.answers()); modes=Set.of(CoreGameModes.GUESS_IT); }
                    case "numeric" -> { content = new NumericContent(e.value(),e.unit()); modes=Set.of(CoreGameModes.CLOSEST_WINS); }
                    default -> throw new IllegalArgumentException("Unknown content type: "+e.type());
                }
                QuestionId id=QuestionId.of(e.id());
                if(entries.put(id,e)!=null) throw new IllegalArgumentException("Duplicate question: "+id);
                definitions.put(id, new QuestionDefinition(id, PackId.of("starter"), CategoryId.of(e.category()), modes,
                        Difficulty.EASY,e.prompt(),content,Set.of()));
            }
        }
    }
    @Override public Optional<QuestionDefinition> next(QuestionQuery query) {
        List<QuestionDefinition> candidates = definitions.values().stream()
                .filter(q->q.supports(query.modeId()))
                .filter(q->query.categories().isEmpty() || query.categories().contains(q.categoryId()))
                .filter(q->!query.excludedIds().contains(q.id())).toList();
        return candidates.isEmpty()? Optional.empty():Optional.of(candidates.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(candidates.size())));
    }
    public void validateCapacity(MatchSettings settings) {
        Set<QuestionId> used = new HashSet<>();
        for(int i=0;i<settings.totalRounds();i++) {
            var q=next(new QuestionQuery(settings.modeForRound(i),settings.categories(),used))
                    .orElseThrow(()->new ApiException(400,"Este pacote não tem perguntas suficientes para esta configuração."));
            used.add(q.id());
        }
    }
    public Entry entry(QuestionId id) { return entries.get(id); }
    public int size() { return entries.size(); }
    public String answer(QuestionDefinition q) {
        if(q.content() instanceof ChoiceContent c) return c.options().stream().filter(o->o.id().equals(c.correctOptionId())).findFirst().orElseThrow().text();
        if(q.content() instanceof GuessContent g) return g.acceptedAnswers().getFirst();
        var n=(NumericContent)q.content();
        return n.correctValue().stripTrailingZeros().toPlainString()+" "+n.unit();
    }
}
