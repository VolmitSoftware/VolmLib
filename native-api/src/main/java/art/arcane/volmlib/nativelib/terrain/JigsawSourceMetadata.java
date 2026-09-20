package art.arcane.volmlib.nativelib.terrain;

public record JigsawSourceMetadata(int maxDistanceHorizontal, int referenceExpansion,
                            int maxStartElementHorizontalSpan) {
    public JigsawSourceMetadata(int maxDistanceHorizontal, int referenceExpansion) {
        this(maxDistanceHorizontal, referenceExpansion, 0);
    }

    public JigsawSourceMetadata {
        if (maxDistanceHorizontal < 1 || maxDistanceHorizontal > 128) {
            throw new IllegalArgumentException("Jigsaw horizontal distance must be between 1 and 128");
        }
        if (referenceExpansion < 0) {
            throw new IllegalArgumentException("Jigsaw reference expansion must not be negative");
        }
        if (maxStartElementHorizontalSpan < 0) {
            throw new IllegalArgumentException("Jigsaw start element horizontal span must not be negative");
        }
    }
}
