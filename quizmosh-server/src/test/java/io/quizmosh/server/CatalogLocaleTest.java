package io.quizmosh.server;

import io.quizmosh.application.port.QuestionCatalog.QuestionQuery;
import io.quizmosh.domain.common.*;
import io.quizmosh.domain.game.*;
import io.quizmosh.domain.quiz.GuessContent;
import org.junit.jupiter.api.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogLocaleTest {
    Catalog catalog;
    @BeforeEach void setup() throws Exception { catalog=CatalogTestSupport.catalog(); }
    @Test void everyFilteredMixCanDrawTwelveUniqueQuestionsInBothLanguages() {
        for(String category:catalog.categoryIdsWithAll()) for(String language:List.of("pt-BR","en")) for(String scope:List.of("ALL","GLOBAL","REGIONAL")) {
            var modes=List.of(CoreGameModes.CLASSIC_TRIVIA,CoreGameModes.QUICK_FIRE,CoreGameModes.GUESS_IT,CoreGameModes.CLOSEST_WINS);
            Set<CategoryId> selected=category.equals("all")?Set.of():Set.of(CategoryId.of(category));
            var settings=new MatchSettings(12,selected,modes,Duration.ofSeconds(25),language,scope,"BR");
            assertDoesNotThrow(()->catalog.validateCapacity(settings));
            Set<QuestionId> seen=new HashSet<>();
            for(int round=0;round<12;round++) {
                var q=catalog.next(new QuestionQuery(settings.modeForRound(round),selected,seen,language,scope,"BR")).orElseThrow();
                assertTrue(seen.add(q.id()));
                var entry=catalog.entry(q.id(),language);
                assertEquals(entry.prompt(),q.prompt());
                if(scope.equals("GLOBAL")) assertTrue(entry.regions().isEmpty());
                if(scope.equals("REGIONAL")) assertEquals(List.of("BR"),entry.regions());
                assertFalse(catalog.next(new QuestionQuery(settings.modeForRound(round),Set.of(CategoryId.of("missing")),Set.of(),language,scope,"BR")).isPresent());
            }
        }
    }
    @Test void choiceModesShareCapacityAndInsufficientPoolsAreRejected() {
        // Regional videogames has seven choices: the two choice modes share those IDs.
        var shared=new MatchSettings(8,Set.of(CategoryId.of("videogames")),List.of(CoreGameModes.CLASSIC_TRIVIA,CoreGameModes.QUICK_FIRE),Duration.ofSeconds(25),"en","REGIONAL","BR");
        var error=assertThrows(ApiException.class,()->catalog.validateCapacity(shared));
        assertEquals("error.catalogCapacity",error.getMessage());
        assertEquals(7L,error.arguments().get("available"));
        assertEquals(8,error.arguments().get("required"));
        var cinema=new MatchSettings(12,Set.of(CategoryId.of("cinema")),List.of(CoreGameModes.GUESS_IT),Duration.ofSeconds(25),"pt-BR","REGIONAL","BR");
        assertThrows(ApiException.class,()->catalog.validateCapacity(cinema));
    }
    @Test void localizedRevealKeepsAliasesFromBothLanguagesAndCanonicalExclusions() {
        Set<QuestionId> excluded=new HashSet<>(catalog.questionIds());
        excluded.remove(QuestionId.of("geral-059"));
        var en=catalog.next(new QuestionQuery(CoreGameModes.GUESS_IT,Set.of(CategoryId.of("geral")),excluded,"en","GLOBAL","BR")).orElseThrow();
        assertEquals(QuestionId.of("geral-059"),en.id());
        assertEquals("Moon",catalog.answer(en));
        assertTrue(((GuessContent)en.content()).acceptedAnswers().containsAll(List.of("Moon","Lua")));
        var pt=catalog.next(new QuestionQuery(CoreGameModes.GUESS_IT,Set.of(CategoryId.of("geral")),excluded,"pt-BR","GLOBAL","BR")).orElseThrow();
        assertEquals(en.id(),pt.id());assertEquals("Lua",catalog.answer(pt));
        excluded.add(en.id());
        assertTrue(catalog.next(new QuestionQuery(CoreGameModes.GUESS_IT,Set.of(CategoryId.of("geral")),excluded,"pt-BR","GLOBAL","BR")).isEmpty());
    }
}
