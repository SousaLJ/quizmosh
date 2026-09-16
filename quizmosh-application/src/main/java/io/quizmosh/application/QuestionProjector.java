package io.quizmosh.application;

import io.quizmosh.application.view.*;
import io.quizmosh.domain.game.GameRound;
import io.quizmosh.domain.quiz.*;
import java.util.*;

final class QuestionProjector {
    QuestionView project(GameRound round) {
        QuestionDefinition q = round.question();
        QuestionContent content = q.content();

        if (content instanceof ChoiceContent choice) {
            return new QuestionView(q.id(), q.prompt(), content.contentType(),
                    choice.options().stream().map(o -> new PublicChoice(o.id(), o.text())).toList(),
                    List.of(), "");
        }
        if (content instanceof GuessContent guess) {
            int visible = Math.min(round.clueIndex() + 1, guess.clues().size());
            return new QuestionView(q.id(), q.prompt(), content.contentType(),
                    List.of(), guess.clues().subList(0, visible), "");
        }
        if (content instanceof NumericContent numeric) {
            return new QuestionView(q.id(), q.prompt(), content.contentType(),
                    List.of(), List.of(), numeric.unit());
        }
        return new QuestionView(q.id(), q.prompt(), content.contentType(), List.of(), List.of(), "");
    }
}
