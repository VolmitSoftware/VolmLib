package art.arcane.volmlib.nativelib.terrain.structure;

import art.arcane.volmlib.nativelib.terrain.NativeBlockPositionPredicate;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.RNG;
import java.util.function.Function;
import art.arcane.volmlib.nativelib.terrain.StructureLocateProbe;
import java.util.function.IntBinaryOperator;

public interface StructureStagePolicy<C, P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>> {
    C current();
    StructureReferencePolicy<P, O> structurePolicy(C context);
    boolean hasPlacement(C context, String key);
    <S> StructureLocateSearchAccess<S, O> locateSearch(C context, LocateRequest<S, O> request);
    int locatorY(O ownership);
    StructurePlacementDecision restoredDecision(O ownership);
    boolean allowsFootprint(C context, Footprint footprint);
    boolean allowsChunkWrite(C context, int chunkX, int chunkZ);
    boolean historicalStructure(C context, long activation);
    boolean stacked(C context);
    NativeBlockPositionPredicate protectedPositions(C context);
    IntBinaryOperator surfaceHeight(C context);
    IntBinaryOperator worldgenHeight(C context, int runtimeMinY, boolean floor);
    NativeBlockState paletteBlock(StructurePalette palette, RNG rng, int x, int y, int z);
    RuntimeException structuresDisabled(int chunkX, int chunkZ);
    String generationLabel(String structureId);
    public record Footprint(int minX, int minZ, int maxX, int maxZ) {
    }

    public record LocateRequest<S, O>(String key, int x, int z, int radius,
                                      StructureLocateProbe<S> probe, Function<S, O> ownership) {
    }

}
