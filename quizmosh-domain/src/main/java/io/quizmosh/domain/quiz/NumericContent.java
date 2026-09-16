package io.quizmosh.domain.quiz;

import java.math.BigDecimal;
import java.util.Objects;

public record NumericContent(BigDecimal correctValue, String unit) implements QuestionContent {
    public NumericContent {
        Objects.requireNonNull(correctValue);
        unit = unit == null ? "" : unit.strip();
    }
    @Override public String contentType() { return "numeric"; }
}
