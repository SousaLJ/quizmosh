package io.quizmosh.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import io.quizmosh.application.port.QuestionCatalog;
import io.quizmosh.domain.common.*;
import io.quizmosh.domain.game.*;
import io.quizmosh.domain.quiz.*;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.math.BigDecimal;

/** Private canonical questions with complete, validated language variants. */
@Component
@DependsOnDatabaseInitialization
public final class Catalog implements QuestionCatalog {
    public record Entry(String id,String category,String type,String prompt,List<String> options,
                        Integer correctIndex,List<String> clues,List<String> answers,
                        BigDecimal value,String unit,String explanation,List<String> regions) {
        public Entry { regions=regions==null?List.of():List.copyOf(regions); }
    }
    private final Map<String,Map<QuestionId,Entry>> entries=new LinkedHashMap<>();
    private final Map<String,Map<QuestionId,QuestionDefinition>> definitions=new LinkedHashMap<>();
    public record Category(String id,Map<String,String> names) {}
    private final List<Category> categories;

    /** Flyway completes before this snapshot is loaded; the database is the only runtime source. */
    public Catalog(JdbcTemplate jdbc,ObjectMapper mapper) {
        var names=new LinkedHashMap<String,Map<String,String>>();
        jdbc.query("""
                SELECT c.id,t.language,t.name FROM quiz_categories c
                LEFT JOIN quiz_category_texts t ON t.category_id=c.id
                WHERE c.enabled=TRUE ORDER BY c.sort_order,c.id,t.language
                """,(org.springframework.jdbc.core.RowCallbackHandler)rs -> {
            var localized=names.computeIfAbsent(rs.getString("id"),key->new LinkedHashMap<>());
            if(rs.getString("language")!=null) localized.put(rs.getString("language"),rs.getString("name"));
        });
        names.forEach((id,translations)-> {
            if(id.equals("all") || !translations.keySet().containsAll(List.of("pt-BR","en"))
                    || translations.values().stream().anyMatch(String::isBlank))
                throw new IllegalStateException("Incomplete category: "+id);
        });
        categories=names.entrySet().stream().map(e->new Category(e.getKey(),Map.copyOf(e.getValue()))).toList();
        if(categories.isEmpty()) throw new IllegalStateException("No active quiz categories in database");
        Map<String,List<String>> regions=new LinkedHashMap<>();
        jdbc.query("SELECT question_id,region FROM quiz_question_regions ORDER BY question_id,region",
                (org.springframework.jdbc.core.RowCallbackHandler)rs -> regions.computeIfAbsent(rs.getString(1),key->new ArrayList<>()).add(rs.getString(2)));
        for(String language:List.of("pt-BR","en")) {
            Map<QuestionId,Entry> translated=new LinkedHashMap<>();
            jdbc.query("""
                    SELECT q.id,q.category_id,q.question_type,q.correct_index,q.numeric_value,
                           t.prompt,t.explanation,t.options_json,t.clues_json,t.answers_json,t.unit
                    FROM quiz_questions q JOIN quiz_categories c ON c.id=q.category_id
                    LEFT JOIN quiz_question_texts t ON t.question_id=q.id AND t.language=?
                    WHERE q.enabled=TRUE AND c.enabled=TRUE ORDER BY c.sort_order,q.id
                    """,(org.springframework.jdbc.core.RowCallbackHandler)rs -> {
                Entry entry=new Entry(rs.getString("id"),rs.getString("category_id"),rs.getString("question_type"),
                        rs.getString("prompt"),strings(rs,"options_json",mapper),rs.getObject("correct_index",Integer.class),
                        strings(rs,"clues_json",mapper),strings(rs,"answers_json",mapper),rs.getBigDecimal("numeric_value"),
                        rs.getString("unit"),rs.getString("explanation"),regions.getOrDefault(rs.getString("id"),List.of()));
                validateEntry(entry);
                if(translated.put(QuestionId.of(entry.id()),entry)!=null) throw new IllegalStateException("Duplicate question: "+entry.id());
            },language);
            entries.put(language,Collections.unmodifiableMap(translated));
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
    private static List<String> strings(ResultSet rs,String column,ObjectMapper mapper) throws SQLException {
        String value=rs.getString(column);
        if(value==null) return List.of();
        try {return List.copyOf(mapper.readValue(value,new TypeReference<List<String>>() {}));}
        catch(Exception e) {throw new IllegalStateException("Invalid "+column+" for "+rs.getString("id"),e);}
    }
    private static void validateEntry(Entry entry) {
        if(entry.prompt()==null || entry.prompt().isBlank() || entry.explanation()==null || entry.explanation().isBlank())
            throw new IllegalStateException("Incomplete question: "+entry.id());
        if(!entry.regions().stream().allMatch("BR"::equals)) throw new IllegalStateException("Unknown region: "+entry.id());
        boolean valid=switch(entry.type()) {
            case "choice" -> entry.options().size()==4 && new HashSet<>(entry.options()).size()==4
                    && entry.options().stream().noneMatch(String::isBlank)
                    && entry.correctIndex()!=null && entry.correctIndex()>=0 && entry.correctIndex()<4;
            case "guess" -> entry.clues().size()==4 && new HashSet<>(entry.clues()).size()==4
                    && entry.clues().stream().noneMatch(String::isBlank) && !entry.answers().isEmpty()
                    && entry.answers().stream().noneMatch(String::isBlank);
            case "numeric" -> entry.value()!=null && entry.unit()!=null && !entry.unit().isBlank();
            default -> false;
        };
        if(!valid) throw new IllegalStateException("Invalid question content: "+entry.id());
    }
    public List<Category> categories() {return categories;}
    public boolean supportsCategory(String id) {return "all".equals(id)||categories.stream().anyMatch(c->c.id().equals(id));}
    public List<String> categoryIdsWithAll() {
        List<String> ids=new ArrayList<>(List.of("all"));categories.forEach(c->ids.add(c.id()));return List.copyOf(ids);
    }
    Set<QuestionId> questionIds() {return entries.get("pt-BR").keySet();}
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
        for(String language:entries.keySet()) for(String category:categoryIdsWithAll()) for(String scope:List.of("ALL","GLOBAL","REGIONAL")) {
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
