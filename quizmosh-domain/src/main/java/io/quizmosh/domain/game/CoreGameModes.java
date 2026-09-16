package io.quizmosh.domain.game;

public final class CoreGameModes {
    public static final GameModeId CLASSIC_TRIVIA = GameModeId.of("classic-trivia");
    public static final GameModeId QUICK_FIRE = GameModeId.of("quick-fire");
    public static final GameModeId GUESS_IT = GameModeId.of("guess-it");
    public static final GameModeId CLOSEST_WINS = GameModeId.of("closest-wins");
    private CoreGameModes() {}
}
