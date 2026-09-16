package io.quizmosh.application.view;
import io.quizmosh.domain.common.QuestionId;
import java.util.List;
public record QuestionView(
        QuestionId id,
        String prompt,
        String contentType,
        List<PublicChoice> choices,
        List<String> visibleClues,
        String unit
) {
    public QuestionView {
        choices = choices == null ? List.of() : List.copyOf(choices);
        visibleClues = visibleClues == null ? List.of() : List.copyOf(visibleClues);
        unit = unit == null ? "" : unit;
    }
}
