package art.arcane.volmlib.util.board;

public class BoardSettings {
    private final BoardProvider boardProvider;
    private final ScoreDirection scoreDirection;

    public BoardSettings(BoardProvider boardProvider, ScoreDirection scoreDirection) {
        this.boardProvider = boardProvider;
        this.scoreDirection = scoreDirection;
    }

    public BoardProvider getBoardProvider() {
        return boardProvider;
    }

    public ScoreDirection getScoreDirection() {
        return scoreDirection;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private BoardProvider boardProvider;
        private ScoreDirection scoreDirection;

        public Builder boardProvider(BoardProvider boardProvider) {
            this.boardProvider = boardProvider;
            return this;
        }

        public Builder scoreDirection(ScoreDirection scoreDirection) {
            this.scoreDirection = scoreDirection;
            return this;
        }

        public BoardSettings build() {
            return new BoardSettings(boardProvider, scoreDirection);
        }
    }
}
