package art.arcane.volmlib.nativelib.terrain.feature;

/**
 * One chunk's imported placed-feature pass, opened before structure placement so features and structures can
 * interleave per decoration step the way vanilla's {@code applyBiomeDecoration} does.
 */
public interface NativeChunkFeaturePass extends AutoCloseable {
    NativeChunkFeaturePass INERT = new NativeChunkFeaturePass() {
        @Override
        public boolean active() {
            return false;
        }

        @Override
        public void placeThroughStep(int step) {
        }

        @Override
        public void placeRemainingSteps() {
        }

        @Override
        public void close() {
        }
    };

    boolean active();

    void placeThroughStep(int step);

    void placeRemainingSteps();

    @Override
    void close();
}
