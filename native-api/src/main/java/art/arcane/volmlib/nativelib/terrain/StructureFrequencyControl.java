package art.arcane.volmlib.nativelib.terrain;

public interface StructureFrequencyControl {
    StructureFrequencyControl NONE = new StructureFrequencyControl() {
        @Override
        public double frequencyMultiplier(String structureSetKey) {
            return 1D;
        }

        @Override
        public boolean hasFrequencyOverrides() {
            return false;
        }
    };

    double frequencyMultiplier(String structureSetKey);

    boolean hasFrequencyOverrides();
}
