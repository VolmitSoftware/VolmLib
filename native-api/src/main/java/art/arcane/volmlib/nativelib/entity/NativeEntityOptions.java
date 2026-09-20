package art.arcane.volmlib.nativelib.entity;

public interface NativeEntityOptions {
    boolean isCustomNameVisible();
    boolean isGlowing();
    boolean isGravity();
    boolean isInvulnerable();
    boolean isSilent();
    boolean isAi();
    boolean isAware();
    boolean isPickupItems();
    boolean isRemovable();
    boolean isBaby();
    String getPandaMainGene();
    String getPandaHiddenGene();
}
