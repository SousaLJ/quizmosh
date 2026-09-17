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

/** Private canonical questions with complete, validated language variants. */
@Component
public final class Catalog implements QuestionCatalog {
    public record Entry(String id,String category,String type,String prompt,List<String> options,
                        Integer correctIndex,List<String> clues,List<String> answers,
                        BigDecimal value,String unit,String explanation,List<String> regions) {
        public Entry { regions=regions==null?List.of():List.copyOf(regions); }
    }
    private final Map<String,Map<QuestionId,Entry>> entries=new LinkedHashMap<>();
    private final Map<String,Map<QuestionId,QuestionDefinition>> definitions=new LinkedHashMap<>();
    public Catalog(ObjectMapper mapper) throws Exception {
        for(String language:List.of("pt-BR","en")) {
            String resource=language.equals("pt-BR")?"/questions.json":"/questions.en.json";
            try(var stream=getClass().getResourceAsStream(resource)) {
                if(stream==null) throw new IllegalStateException("Missing "+resource);
                Map<QuestionId,Entry> translated=new LinkedHashMap<>();
                for(Entry entry:mapper.readValue(stream,new TypeReference<List<Entry>>() {})) {
                    if(entry.prompt()==null || entry.prompt().isBlank() || entry.explanation()==null || entry.explanation().isBlank())
                        throw new IllegalStateException("Incomplete question: "+entry.id());
                    if(!entry.regions().stream().allMatch("BR"::equals)) throw new IllegalStateException("Unknown region: "+entry.id());
                    if(translated.put(QuestionId.of(entry.id()),entry)!=null) throw new IllegalStateException("Duplicate question: "+entry.id());
                }
                entries.put(language,translated);
            }
        }
        var canonical=entries.get("pt-BR");
        if(!canonical.keySet().equals(entries.get("en").keySet())) throw new IllegalStateException("Every question must have both language variants");
        entries.forEach((language,localized)-> {
            Map<QuestionId,QuestionDefinition> content=new LinkedHashMap<>();
            localized.forEach((id,entry)-> {
                Entry original=canonical.get(id);
                if(!original.type().equals(entry.type()) || !original.category().equals(entry.category())
                        || !original.regions().equals(entry.regions()) || !Objects.equals(original.correctIndex(),entry.correctIndex())
                        || !Objects.equals(original.value(),entry.value())) throw new IllegalStateException("Translation changes question semantics: "+id);
                QuestionContent question;Set<GameModeId> modes;
                switch(entry.type()) {
                    case "choice" -> {
                        if(entry.options().size()!=original.options().size()) throw new IllegalStateException("Choice count differs: "+id);
                        List<ChoiceOption> choices=new ArrayList<>();
                        for(int i=0;i<entry.options().size();i++) choices.add(new ChoiceOption(""+(char)('A'+i),entry.options().get(i)));
                        question=new ChoiceContent(choices,""+(char)('A'+entry.correctIndex()));
                        modes=Set.of(CoreGameModes.CLASSIC_TRIVIA,CoreGameModes.QUICK_FIRE);
                    }
                    case "guess" -> {
                        if(entry.clues().size()!=original.clues().size() || entry.answers()==null || entry.answers().isEmpty())
                            throw new IllegalStateException("Incomplete clues/answers: "+id);
                        // Localized answer first for reveal; accept aliases from both editions fairly.
                        LinkedHashSet<String> aliases=new LinkedHashSet<>(entry.answers());
                        entries.values().forEach(all->aliases.addAll(all.get(id).answers()));
                        question=new GuessContent(entry.clues(),List.copyOf(aliases));modes=Set.of(CoreGameModes.GUESS_IT);
                    }
                    case "numeric" -> {question=new NumericContent(entry.value(),entry.unit());modes=Set.of(CoreGameModes.CLOSEST_WINS);}
                    default -> throw new IllegalStateException("Unknown type: "+entry.type());
                }
                content.put(id,new QuestionDefinition(id,PackId.of("starter"),CategoryId.of(entry.category()),modes,Difficulty.EASY,entry.prompt(),question,Set.of()));
            });
            definitions.put(language,content);
        });
    }
    private List<QuestionDefinition> candidates(QuestionQuery query) {
        return definitions.getOrDefault(query.language(),Map.of()).values().stream()
                .filter(q->q.supports(query.modeId()))
                .filter(q->query.categories().isEmpty() || query.categories().contains(q.categoryId()))
                .filter(q->!query.excludedIds().contains(q.id()))
                .filter(q->matches(entry(q.id()),query.scope(),query.region())).toList();
    }
    private boolean matches(Entry entry,String scope,String region) {
        boolean global=entry.regions().isEmpty(),regional=entry.regions().contains(region);
        return switch(scope) {case "GLOBAL" -> global;case "REGIONAL" -> regional;case "ALL" -> global||regional;default -> false;};
    }
    @Override public Optional<QuestionDefinition> next(QuestionQuery query) {
        var candidates=candidates(query);
        return candidates.isEmpty()?Optional.empty():Optional.of(candidates.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(candidates.size())));
    }
    public void validateCapacity(MatchSettings settings) {
        Map<String,Integer> demand=new LinkedHashMap<>();
        for(int i=0;i<settings.totalRounds();i++) {
            String type=switch(settings.modeForRound(i).value()) {case "guess-it" -> "guess";case "closest-wins" -> "numeric";default -> "choice";};
            demand.merge(type,1,Integer::sum);
        }
        for(var need:demand.entrySet()) {
            long available=entries.getOrDefault(settings.questionLanguage(),Map.of()).values().stream()
                    .filter(e->e.type().equals(need.getKey()))
                    .filter(e->settings.categories().isEmpty()||settings.categories().contains(CategoryId.of(e.category())))
                    .filter(e->matches(e,settings.contentScope(),settings.questionRegion())).count();
            if(available<need.getValue()) throw new ApiException(400,"error.catalogCapacity",Map.of("available",available,"required",need.getValue()));
        }
    }
    public List<Map<String,Object>> inventory() {
        List<Map<String,Object>> result=new ArrayList<>();
        for(String language:entries.keySet()) for(String category:List.of("all","cinema","geral")) for(String scope:List.of("ALL","GLOBAL","REGIONAL")) {
            Map<String,Integer> counts=new LinkedHashMap<>(Map.of("choice",0,"guess",0,"numeric",0));
            entries.get(language).values().stream().filter(e->category.equals("all")||category.equals(e.category()))
                    .filter(e->matches(e,scope,"BR")).forEach(e->counts.merge(e.type(),1,Integer::sum));
            result.add(GameService.obj("language",language,"category",category,"scope",scope,"region","BR","counts",counts));
        }
        return result;
    }
    public Entry entry(QuestionId id) {return entry(id,"pt-BR");}
    public Entry entry(QuestionId id,String language) {return entries.get(language).get(id);}
    public int size() {return entries.get("pt-BR").size();}
    public String answer(QuestionDefinition q) {
        if(q.content() instanceof ChoiceContent c) return c.options().stream().filter(o->o.id().equals(c.correctOptionId())).findFirst().orElseThrow().text();
        if(q.content() instanceof GuessContent g) return g.acceptedAnswers().getFirst();
        var n=(NumericContent)q.content();return n.correctValue().stripTrailingZeros().toPlainString()+" "+n.unit();
    }
}
