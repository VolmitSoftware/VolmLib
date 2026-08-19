package art.arcane.volmlib.util.board;

public class BoardSettings {
    private final BoardProvider boardProvider;
    private final ScoreDirection scoreDirection;
    private final int updateIntervalTicks;

    public BoardSettings(BoardProvider boardProvider, ScoreDirection scoreDirection) {
        this(boardProvider, scoreDirection, 20);
    }

    public BoardSettings(BoardProvider boardProvider, ScoreDirection scoreDirection, int updateIntervalTicks) {
        this.boardProvider = boardProvider;
        this.scoreDirection = scoreDirection;
        this.updateIntervalTicks = Math.max(1, updateIntervalTicks);
    }

    public BoardProvider getBoardProvider() {
        return boardProvider;
    }

    public ScoreDirection getScoreDirection() {
        return scoreDirection;
    }

    public int getUpdateIntervalTicks() {
        return updateIntervalTicks;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private BoardProvider boardProvider;
        private ScoreDirection scoreDirection;
        private int updateIntervalTicks = 20;

        public Builder boardProvider(BoardProvider boardProvider) {
            this.boardProvider = boardProvider;
            return this;
        }

        public Builder scoreDirection(ScoreDirection scoreDirection) {
            this.scoreDirection = scoreDirection;
            return this;
        }

        public Builder updateIntervalTicks(int updateIntervalTicks) {
            this.updateIntervalTicks = updateIntervalTicks;
            return this;
        }

        public BoardSettings build() {
            return new BoardSettings(boardProvider, scoreDirection, updateIntervalTicks);
        }
    }
}
