package art.arcane.volmlib.nativelib.terrain.structure;

public interface JigsawSettings {
    String getStartPool();
    String getStartJigsawName();
    Integer getMaxDepth();
    Integer getMaxDistanceHorizontal();
    Integer getMaxDistanceVertical();
    Boolean getUseExpansionHack();
    JigsawHeightmap getProjectStartToHeightmap();
    Integer getDimensionPaddingBottom();
    Integer getDimensionPaddingTop();
    JigsawLiquidSettings getLiquidSettings();
}
