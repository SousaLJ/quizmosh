package io.quizmosh.inmemory;

import io.quizmosh.domain.common.*;
import io.quizmosh.domain.game.*;
import io.quizmosh.domain.quiz.*;
import java.math.BigDecimal;
import java.util.*;

public final class SampleQuestions {
    private SampleQuestions() {}
    public static List<QuestionDefinition> all() {
        PackId base = PackId.of("starter");
        return List.of(
                new QuestionDefinition(
                        QuestionId.of("movie_matrix"), base, CategoryId.of("movies"),
                        Set.of(CoreGameModes.GUESS_IT), Difficulty.EASY, "Guess the movie",
                        new GuessContent(
                                List.of("Released in 1999", "Reality may not be what it seems", "Keanu Reeves is the protagonist"),
                                List.of("The Matrix", "Matrix")),
                        Set.of("movies","sci-fi")),
                new QuestionDefinition(
                        QuestionId.of("science_planet"), base, CategoryId.of("science"),
                        Set.of(CoreGameModes.CLASSIC_TRIVIA, CoreGameModes.QUICK_FIRE), Difficulty.EASY,
                        "Which planet has Olympus Mons?",
                        new ChoiceContent(List.of(
                                new ChoiceOption("A","Venus"),
                                new ChoiceOption("B","Mars"),
                                new ChoiceOption("C","Jupiter"),
                                new ChoiceOption("D","Mercury")), "B"),
                        Set.of("space")),
                new QuestionDefinition(
                        QuestionId.of("science_light"), base, CategoryId.of("science"),
                        Set.of(CoreGameModes.CLOSEST_WINS), Difficulty.MEDIUM,
                        "Approximately how fast does light travel in vacuum?",
                        new NumericContent(new BigDecimal("299792.458"), "km/s"),
                        Set.of("physics")),
                new QuestionDefinition(
                        QuestionId.of("games_minecraft"), base, CategoryId.of("games"),
                        Set.of(CoreGameModes.CLASSIC_TRIVIA, CoreGameModes.QUICK_FIRE), Difficulty.EASY,
                        "Which company originally created Minecraft?",
                        new ChoiceContent(List.of(
                                new ChoiceOption("A","Valve"),
                                new ChoiceOption("B","Mojang"),
                                new ChoiceOption("C","Epic Games"),
                                new ChoiceOption("D","Ubisoft")), "B"),
                        Set.of("games"))
        );
    }
}
