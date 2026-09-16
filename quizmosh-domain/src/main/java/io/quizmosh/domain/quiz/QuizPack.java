package io.quizmosh.domain.quiz;

import io.quizmosh.domain.common.PackId;
import java.util.Objects;

public record QuizPack(PackId id, String name, String version, boolean premium) {
    public QuizPack {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(version);
        name = name.strip();
        version = version.strip();
    }
}
