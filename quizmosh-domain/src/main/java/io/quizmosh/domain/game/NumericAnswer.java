package io.quizmosh.domain.game;

import java.math.BigDecimal;
import java.util.Objects;

public record NumericAnswer(BigDecimal value) implements AnswerValue {
    public NumericAnswer { Objects.requireNonNull(value); }
    @Override public String answerType() { return "numeric"; }
}
